# Fully Commented Code: Key Files

Use these as reference during your interview. Copy any section to explain a concept.

---

## 1. HomeViewModel.kt (MVI + Coroutines)

```kotlin
package com.example.randomdog.ui.home

import androidx.lifecycle.viewModelScope
import com.example.randomdog.domain.usecase.GetRandomDogUseCase
import com.example.randomdog.domain.usecase.ObserveIsFavouriteUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
import com.example.randomdog.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================================
// MVI ViewModel: Immutable state, sealed intents, pure reducer
// ============================================================================

@HiltViewModel  // Hilt creates this & injects dependencies; survives rotation
class HomeViewModel @Inject constructor(
    // Dependencies injected by Hilt via DI graph
    private val getRandomDog: GetRandomDogUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val observeIsFavourite: ObserveIsFavouriteUseCase,
) : MviViewModel<HomeUiState, HomeIntent, HomeEffect>(HomeUiState()) {
    // Extends reusable MVI base with <State, Intent, Effect> types
    // Initial state: HomeUiState() with all fields defaulted (no dog, not loading, etc.)

    private var favouriteJob: Job? = null
    // Tracks the favourite Flow subscription so we can cancel it and restart

    init {
        // Auto-load a random dog when screen first appears
        loadNewDog()
    }

    override fun onIntent(intent: HomeIntent) {
        // ONLY entry point for user actions (enforces unidirectional data flow)
        // "onIntent" is the contract: UI sends intents UP, never calls functions directly
        
        when (intent) {
            HomeIntent.LoadNewDog -> loadNewDog()   // User tapped "New dog"
            HomeIntent.Retry -> loadNewDog()         // User tapped retry on error
            HomeIntent.ToggleFavourite -> toggleCurrent()  // User tapped heart icon
        }
    }

    private fun loadNewDog() {
        // Step 1: Update state immediately (optimistic UI)
        reduce { it.copy(isLoading = true, errorMessage = null) }
        // reduce { } is the ONLY way to mutate state (immutable update via copy)
        
        // Step 2: Launch coroutine to fetch dog (async, non-blocking)
        viewModelScope.launch {
            // Launch: scope tied to ViewModel lifecycle, cancelled when destroyed
            // Dispatcher: Main (default), so work is assumed lightweight or can await suspend
            
            getRandomDog()
            // Call suspend function (from use case)
            // Pause here until result is ready (network call happening on IO dispatcher inside repo)
            // Returns Result<Dog> (error is a value, never throws)
            
                .onSuccess { dog ->
                    // Network succeeded, have a Dog
                    
                    // Update state: loading done, dog loaded, no error
                    reduce { it.copy(isLoading = false, dog = dog) }
                    
                    // Start observing is this dog a favourite? (reactive Flow)
                    observeFavouriteFor(dog.imageUrl)
                    // collectLatest on a new Flow; cancels previous if exists
                }
                .onFailure { error ->
                    // Network failed (offline, timeout, bad payload, etc.)
                    
                    // Update state: loading done, dog still null, set error message
                    reduce { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                    // errorMessage is now non-null; UI branch shows ErrorState + Retry button
                    
                    // Also send a one-off effect (snackbar) to parent
                    sendEffect(HomeEffect.ShowMessage(error.toUserMessage()))
                    // sendEffect queues onto effects Channel; UI collects & shows snackbar
                    // Note: Both state update AND effect; state determines UI, effect is transient
                }
        }
    }

    private fun observeFavouriteFor(imageUrl: String) {
        // Watch whether this dog URL is in the favourites table (Room)
        // Result: update isFavourite state whenever the DB changes
        
        // Cancel old subscription (if switching dogs)
        favouriteJob?.cancel()
        // If we were observing a previous dog's favourite status, stop
        
        // Launch new subscription
        favouriteJob = viewModelScope.launch {
            // Store the Job so we can cancel it later
            
            observeIsFavourite(imageUrl)
            // Call use case, get Flow<Boolean>
            // Flow is cold: no work happens until we collect
            
                .collectLatest { fav ->
                    // collectLatest: "only care about the latest value"
                    // If a new value arrives while processing prev, cancel prev and run this
                    // (Contrast: collect waits for prev block to finish)
                    
                    reduce { it.copy(isFavourite = fav) }
                    // Update state: isFavourite = true or false
                    // UI sees change, recomposes, heart icon updates (filled or outline)
                    
                    // When user switches dogs:
                    // old Flow subscription is cancelled (favouriteJob?.cancel())
                    // new subscription starts
                    // new isFavourite value flows in → state updates → heart icon follows dog
                }
        }
    }

    private fun toggleCurrent() {
        // User tapped heart: add or remove current dog from favourites
        
        val dog = currentState.dog ?: return
        // Get dog from current state; null-safe (if no dog loaded, do nothing)
        // currentState is a protected property from MviViewModel base
        
        viewModelScope.launch {
            // Fire-and-forget launch: don't await the result
            
            toggleFavourite(dog)
            // Call use case: suspend function
            // Inside: checks Room, inserts or deletes row
            // When insert/delete completes, Room emits new list via observeAll() Flow
            // observeAll() Flow is collected by FavouritesViewModel
            // FavouritesViewModel reduces new list into state
            // FavouritesScreen grid recomposes
            
            // No result handling here; success is implicit (no exception)
            // Error (if any): caught in repository as Result, but toggle doesn't return a value
            // If error happens: snackbar would come from effect, but toggleFavourite doesn't send one
            // (Could add error handling here if needed)
        }
    }

    private fun Throwable.toUserMessage(): String =
        "Couldn't fetch a dog. Check your connection and try again."
    // Extension function on Throwable; maps any exception to user-friendly message
    // Could be richer: map IOException → "offline", timeout → "server took too long", etc.
}
```

---

## 2. DogRepositoryImpl.kt (Data Layer + Error Handling)

```kotlin
package com.example.randomdog.data.repository

import com.example.randomdog.data.local.FavouriteDogDao
import com.example.randomdog.data.mapper.toDog
import com.example.randomdog.data.mapper.toEntity
import com.example.randomdog.data.remote.DogApi
import com.example.randomdog.di.IoDispatcher
import com.example.randomdog.domain.model.Dog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

// ============================================================================
// Repository: Single source of truth, hides remote/local data sources
// ============================================================================

class DogRepositoryImpl @Inject constructor(
    private val api: DogApi,  // Retrofit service for dog.ceo API
    private val dao: FavouriteDogDao,  // Room DAO for local SQLite
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,  // Injected thread pool
) : DogRepository {

    override suspend fun getRandomDog(): Result<Dog> = withContext(ioDispatcher) {
        // ============================================================================
        // Fetch random dog from API
        // Returns Result<Dog>: success OR failure, never throws
        // ============================================================================
        
        // withContext(ioDispatcher): switch from caller (Main) to IO thread pool
        // Caller (ViewModel on Main) pauses here until work completes, then resumes on Main
        // Work inside: network call (blocking) runs on IO dispatcher
        
        runCatching {
            // Try-catch wrapper that converts exception to Result
            // If any line throws, the exception is caught and Result.failure(e) returned
            
            api.getRandomDog()
            // Suspend function: hits dog.ceo API via Retrofit
            // Blocks on IO thread until response arrives
            // Returns DogImageDto (wire format from JSON)
            
                .toDog()
            // Mapper extension: converts DogImageDto → Dog (domain model)
            // Extracts imageUrl from DTO
            // Parses breed from URL path: "/breeds/pug/..." → "Pug"
            // Pure function, no I/O
        }
        // Result<Dog>: either success(Dog) or failure(e)
        // Returns to caller on Main thread
    }

    override fun observeFavourites(): Flow<List<Dog>> =
        // ============================================================================
        // Reactive: emit list of favourites whenever Room changes
        // ============================================================================
        
        dao.observeAll()
        // Room query returns Flow<List<FavouriteDogEntity>>
        // Cold: no work happens until someone collects
        // Hot: Room emits whenever INSERT/DELETE happens
        
            .map { entities ->
                // Transform each emission: Entity → Domain
                entities.map { it.toDog() }
                // Convert FavouriteDogEntity → Dog for each item
                // Same mapper as above: decouples Room shape from UI
            }
        // Returns: Flow<List<Dog>>
        // Collectors (FavouritesViewModel) receive updated list automatically

    override fun observeIsFavourite(imageUrl: String): Flow<Boolean> =
        // ============================================================================
        // Reactive: emit true/false whenever this URL's favourite status changes
        // ============================================================================
        
        dao.observeIsFavourite(imageUrl)
        // Room query: SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)
        // Returns Flow<Boolean>
        // Emits: true when row exists, false when deleted
        // Collectors (HomeViewModel) get live updates
        
        // Passed through as-is (no mapping needed, already Boolean)

    override suspend fun toggleFavourite(dog: Dog) = withContext(ioDispatcher) {
        // ============================================================================
        // Add or remove dog from favourites (idempotent: toggle)
        // ============================================================================
        
        // withContext(ioDispatcher): switch to IO thread pool for DB access
        
        if (dao.isFavourite(dog.imageUrl)) {
            // Suspend query on IO thread: check if row exists
            // Non-reactive; one-time check (unlike observeIsFavourite Flow)
            
            dao.deleteByUrl(dog.imageUrl)
            // Suspend delete on IO thread: remove the row
            // When this completes, Room emits new list via observeAll() Flow
        } else {
            // Row doesn't exist, add it
            
            dao.insert(dog.toEntity(addedAt = System.currentTimeMillis()))
            // Mapper: Dog → FavouriteDogEntity
            // Adds timestamp (for ordering by most recent)
            // Suspend insert on IO thread
            // When this completes, Room emits new list via observeAll() Flow
        }
        // No explicit return; all mutations complete, then function returns
    }
}
```

---

## 3. MviViewModel.kt (Reusable MVI Base)

```kotlin
package com.example.randomdog.ui.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============================================================================
// MVI ViewModel Base: Generic <State, Intent, Effect>
// Reusable for any screen following MVI pattern
// ============================================================================

abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {
    // <S> = State type (e.g., HomeUiState)
    // <I> = Intent type (e.g., HomeIntent)
    // <E> = Effect type (e.g., HomeEffect)
    // Subclasses: HomeViewModel<HomeUiState, HomeIntent, HomeEffect>

    // ========== STATE ==========
    
    private val _state = MutableStateFlow(initialState)
    // Mutable holder of state
    // Initialized to the provided initialState
    // MutableStateFlow: always has a current value, emits on changes
    
    val state: StateFlow<S> = _state.asStateFlow()
    // Expose as read-only StateFlow (public API)
    // Collectors can't mutate directly; only reduce { } can change state
    // asStateFlow(): converts MutableStateFlow to immutable StateFlow wrapper

    // ========== INTENT ==========
    
    abstract fun onIntent(intent: I)
    // ONLY entry point for user actions
    // Subclasses implement: dispatch intents to private functions
    // Enforces unidirectional data flow: intents UP, state DOWN

    // ========== EFFECT ==========
    
    private val _effects = Channel<E>(Channel.BUFFERED)
    // Buffered channel: sender doesn't wait for receiver
    // For one-off events (toasts, navigation, etc.)
    // Not part of state (state is for rendering, effects are transient)
    
    val effects: Flow<E> = _effects.receiveAsFlow()
    // Expose as Flow<E> for collection
    // receiveAsFlow(): wraps channel in a Flow interface

    // ========== STATE MUTATION ==========
    
    protected val currentState: S get() = _state.value
    // Read-only access to current state value
    // Used in: toggleCurrent() to check if dog exists before toggling

    protected fun reduce(block: (S) -> S) = _state.update(block)
    // ONLY way to mutate state (enforces immutability pattern)
    // block: (oldState) -> newState
    // Example: reduce { it.copy(isLoading = true) }
    // 
    // _state.update(block): thread-safe atomic update
    // 1. Read current state
    // 2. Apply block(state) to produce new state
    // 3. Emit new state to all collectors
    // 4. Return new state

    // ========== EFFECT DISPATCH ==========
    
    protected fun sendEffect(effect: E) {
        // Queue a one-off event (non-blocking)
        
        viewModelScope.launch {
            // Launch on viewModelScope so if coroutine is cancelled, effect is dropped
            
            _effects.send(effect)
            // Send effect into channel
            // BUFFERED means sender doesn't wait for collector; send returns immediately
            // If no collector, effect is buffered (dropped after buffer fills)
        }
    }
}
```

---

## 4. HomeScreen.kt (UI Layer + State Collection)

```kotlin
package com.example.randomdog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.randomdog.ui.components.DogImage
import com.example.randomdog.ui.components.ErrorState
import com.example.randomdog.ui.components.LoadingState
import com.example.randomdog.ui.theme.DogButton

// ============================================================================
// Stateful Wrapper: owns ViewModel, collects state + effects
// ============================================================================

@Composable
fun HomeScreen(
    onShowMessage: (String) -> Unit,  // Callback to parent (NavGraph) to show snackbar
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),  // Lazy injection from Hilt
) {
    // ============================================================================
    // Collect State (lifecycle-aware)
    // ============================================================================
    
    val state by viewModel.state.collectAsStateWithLifecycle()
    // collectAsStateWithLifecycle():
    // - Collects StateFlow<HomeUiState> when screen visible (STARTED)
    // - Pauses when hidden (PAUSED)
    // - Auto-cancels when destroyed
    // - Returns: Compose State<HomeUiState> that triggers recompose on change
    //
    // by: destructure as State delegate, access with `state` directly
    //
    // Result: state is always current value; Compose recomposes when it changes

    // ============================================================================
    // Collect Effects (one-off events)
    // ============================================================================
    
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        // LaunchedEffect: run once per key change (viewModel, lifecycleOwner)
        // If viewModel instance changes, re-run this effect
        
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // repeatOnLifecycle: restart collection each time lifecycle enters STARTED
            // - Collect when screen visible
            // - Cancel when screen hidden
            // - Restart when becomes visible again
            //
            // Why not collectAsStateWithLifecycle? Because effects are transient:
            // collectAsState would lose effects emitted while hidden
            
            viewModel.effects.collect { effect ->
                // Collect all effects from the ViewModel
                // Each effect: HomeEffect.ShowMessage(text)
                
                when (effect) {
                    is HomeEffect.ShowMessage -> onShowMessage(effect.text)
                    // Handle effect by calling parent callback
                    // Parent (NavGraph) shows snackbar via snackbarHostState.showSnackbar(text)
                }
            }
        }
    }

    // ============================================================================
    // Render: delegate to stateless content
    // ============================================================================
    
    HomeScreenContent(
        state = state,  // Pass current state down
        onNewDog = { viewModel.onIntent(HomeIntent.LoadNewDog) },  // Send intents up
        onToggleFavourite = { viewModel.onIntent(HomeIntent.ToggleFavourite) },
        onRetry = { viewModel.onIntent(HomeIntent.Retry) },
        modifier = modifier,
    )
}

// ============================================================================
// Stateless Content: pure function of state — testable, previewable
// ============================================================================

@Composable
fun HomeScreenContent(
    state: HomeUiState,  // All inputs from state
    onNewDog: () -> Unit,  // All outputs as callbacks
    onToggleFavourite: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Branch on state: exactly one branch is taken per state combination
    when {
        state.isLoading && state.dog == null -> LoadingState(modifier)
        // Loading initial dog: show spinner
        
        state.errorMessage != null && state.dog == null ->
            ErrorState(state.errorMessage, onRetry = onRetry, modifier = modifier)
        // Error before dog loaded: show error + retry button
        
        // On refresh failure with a dog already shown, keep last image
        // Error surfaces via snackbar effect (handled in stateful wrapper)
        else -> HomeContent(
            state = state,
            onNewDog = onNewDog,
            onToggleFavourite = onToggleFavourite,
            modifier = modifier,
        )
        // Dog loaded (or refresh error after dog shown): show content
    }
}

// ============================================================================
// Content: the actual UI
// ============================================================================

@Composable
private fun HomeContent(
    state: HomeUiState,
    onNewDog: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        state.dog?.let { dog ->
            // Dog is non-null; render it
            
            DogImage(url = dog.imageUrl, contentDescription = dog.breed ?: "A random dog")
            // Coil AsyncImage: loads from URL with caching
            
            dog.breed?.let { Text(it) }
            // Show breed if available
            
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (state.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    // Filled heart if favourite, outline if not
                    // This syncs with Room via ObserveIsFavouriteUseCase Flow
                    
                    contentDescription = if (state.isFavourite) "Remove from favourites" else "Add to favourites",
                )
            }
        }
        // If dog is null, nothing renders (spacedBy keeps column clean)
        
        DogButton(onClick = onNewDog, text = "New dog", leadingIcon = Icons.Filled.Refresh)
        // "New dog" button: user tap sends onNewDog() → onIntent(LoadNewDog)
    }
}
```

---

## 5. FavouriteDogDao.kt (Room Reactive Queries)

```kotlin
package com.example.randomdog.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

// ============================================================================
// DAO: Data Access Object — typed queries into SQLite via Room
// ============================================================================

@Dao
interface FavouriteDogDao {

    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteDogEntity>>
    // ============================================================================
    // Reactive query: emits whenever rows change
    // ============================================================================
    // Return type: Flow<List<Entity>> (not suspend, not List)
    // Behavior:
    //   - Cold: no DB query happens until someone collects
    //   - Hot: Room watches the table; any INSERT/DELETE triggers emission
    //   - Multiple collectors OK: each gets own subscription
    // Usage in ViewModel:
    //   getFavourites().collect { dogs -> reduce { it.copy(favourites = dogs) } }
    //   Every time a favourite is added/removed, new list emitted, state updates, gallery recomposes

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    fun observeIsFavourite(url: String): Flow<Boolean>
    // ============================================================================
    // Reactive query: emits true/false as row exists/vanishes
    // ============================================================================
    // Same pattern: cold Flow, hot emissions
    // Usage in ViewModel:
    //   observeIsFavourite(url).collectLatest { isFav -> reduce { it.copy(isFavourite = isFav) } }
    //   Heart icon stays in sync: filled when favourite, outline when not

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    suspend fun isFavourite(url: String): Boolean
    // ============================================================================
    // Non-reactive query: one-time check
    // ============================================================================
    // Return type: Boolean (not Flow)
    // Behavior:
    //   - Suspend: caller pauses until query completes
    //   - One-time: doesn't emit again if DB changes
    // Usage in Repository.toggleFavourite():
    //   if (dao.isFavourite(url)) delete else insert
    //   Just a lookup for toggle logic; don't need reactive updates

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dog: FavouriteDogEntity)
    // ============================================================================
    // Insert row (suspend)
    // ============================================================================
    // suspend: called from Repository.toggleFavourite() via viewModelScope.launch
    // onConflict = IGNORE: if URL already exists, silently do nothing
    // Side effect: when insert completes, Room emits new list via observeAll() Flow
    // No explicit return; insert is the work

    @Query("DELETE FROM favourites WHERE imageUrl = :url")
    suspend fun deleteByUrl(url: String)
    // ============================================================================
    // Delete row (suspend)
    // ============================================================================
    // suspend: called from Repository.toggleFavourite()
    // Side effect: when delete completes, Room emits new list via observeAll() Flow
}
```

---

## 6. DogMappers.kt (Layer Translation)

```kotlin
package com.example.randomdog.data.mapper

import com.example.randomdog.data.local.FavouriteDogEntity
import com.example.randomdog.data.remote.dto.DogImageDto
import com.example.randomdog.domain.model.Dog

// ============================================================================
// Mappers: Translation at layer boundaries (DTO ↔ Entity ↔ Domain)
// Keep domain model decoupled from wire/DB shapes
// ============================================================================

fun DogImageDto.toDog(): Dog = Dog(
    imageUrl = message,
    // DTO field "message" = image URL (from JSON response)
    
    breed = parseBreed(message)
    // Custom logic: extract breed from URL path
    // Domain model has breed; DTO doesn't; mapper fills the gap
)
// When to use:
//   - In Repository.getRandomDog() after Retrofit call
//   - Converts wire format to domain model
//   - If API changes shape, only this line changes

fun FavouriteDogEntity.toDog(): Dog = Dog(
    imageUrl = imageUrl,
    breed = breed
)
// When to use:
//   - In Repository.observeFavourites().map { entities.map { it.toDog() } }
//   - Converts Room entity to domain model
//   - If DB schema changes, only this line changes

fun Dog.toEntity(addedAt: Long): FavouriteDogEntity =
    FavouriteDogEntity(imageUrl = imageUrl, breed = breed, addedAt = addedAt)
// When to use:
//   - In Repository.toggleFavourite() before dao.insert(dog.toEntity(...))
//   - Adds timestamp (for DB ordering)
//   - Converts domain model to entity for storage

internal fun parseBreed(url: String): String? {
    // ============================================================================
    // Custom business logic: extract breed from dog.ceo URL
    // ============================================================================
    // URL format: https://images.dog.ceo/breeds/{breed}[-{subbreed}]/{filename}.jpg
    // Example: /breeds/hound-afghan/xyx.jpg
    // Goal: "hound-afghan" → "Afghan Hound" (reverse, capitalize)
    
    val slug = url.substringAfter("/breeds/", "")
        // Extract part after "/breeds/": "hound-afghan/xyz.jpg"
        
        .substringBefore("/", "")
        // Extract before next "/": "hound-afghan"
        
        .takeIf { it.isNotBlank() } ?: return null
        // If empty string, return null (invalid URL)
    
    return slug.split("-")
        // Split on dash: ["hound", "afghan"]
        
        .reversed()
        // Reverse: ["afghan", "hound"]
        
        .joinToString(" ") { word ->
            // Join with space, capitalize each word
            
            word.replaceFirstChar { 
                // Capitalize first char: "hound" → "Hound", "afghan" → "Afghan"
                if (it.isLowerCase()) it.titlecase() else it.toString()
            }
        }
        // Result: "Afghan Hound"
}
```

---

## 7. HomeContract.kt (MVI Contract)

```kotlin
package com.example.randomdog.ui.home

import com.example.randomdog.domain.model.Dog

// ============================================================================
// MVI CONTRACT: Three elements define the UI contract
// Every screen implementing MVI has a Contract like this
// ============================================================================

data class HomeUiState(
    // Immutable state object: single source of truth for rendering
    // All fields optional (default values provided)
    
    val isLoading: Boolean = false,
    // Is a fetch in progress? Determined by ViewModel.loadNewDog()
    // Used to show loading spinner or keep dog image+buttons shown
    
    val dog: Dog? = null,
    // Current dog (or null if not loaded/error)
    // Used to render image, breed, heart icon
    
    val isFavourite: Boolean = false,
    // Is current dog in favourites? (Heart filled or outline)
    // Updated reactively by observeIsFavourite() Flow
    
    val errorMessage: String? = null,
    // Error text (or null if no error)
    // Used to show ErrorState (message + Retry button)
)

sealed interface HomeIntent {
    // Sealed: exhaustive pattern matching; all possible user actions here
    // Each action is an object (no payload) except where noted
    
    data object LoadNewDog : HomeIntent
    // User tapped "New dog" button
    // ViewModel.onIntent() calls loadNewDog()
    
    data object ToggleFavourite : HomeIntent
    // User tapped heart icon
    // ViewModel.onIntent() calls toggleCurrent()
    
    data object Retry : HomeIntent
    // User tapped "Retry" on error
    // ViewModel.onIntent() calls loadNewDog() again
}

sealed interface HomeEffect {
    // Sealed: one-off events (not state)
    // Emitted to parent (NavGraph) for snackbars, navigation, etc.
    
    data class ShowMessage(val text: String) : HomeEffect
    // Text to show in snackbar (transient; disappears after timeout)
    // HomeScreen.effects.collect() calls onShowMessage(text)
    // Parent (NavGraph) shows snackbarHostState.showSnackbar(text)
}
```

---

## 8. DI: RepositoryModule.kt

```kotlin
package com.example.randomdog.di

import com.example.randomdog.data.repository.DogRepository
import com.example.randomdog.data.repository.DogRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// ============================================================================
// Repository Binding: interface → implementation
// Allows use cases to depend on interface, tests to swap fake
// ============================================================================

@Module  // This is a Hilt module
@InstallIn(SingletonComponent::class)  // Installs into app-wide singleton scope
abstract class RepositoryModule {  // Abstract because we use @Binds (not @Provides)

    @Binds  // Instead of: fun fun provideDogRepository(impl: DogRepositoryImpl): DogRepository
    @Singleton  // Single instance for app lifetime
    abstract fun bindDogRepository(impl: DogRepositoryImpl): DogRepository
    // ============================================================================
    // Maps DogRepositoryImpl (impl) to DogRepository (interface)
    // ============================================================================
    // Hilt generates code that:
    //   - Creates a DogRepositoryImpl instance (with all its deps injected)
    //   - Registers it as providing DogRepository
    // 
    // When something needs DogRepository:
    //   - Hilt sees: "DogRepositoryImpl provides DogRepository"
    //   - Injects: the DogRepositoryImpl instance
    //
    // Why this matters:
    //   - Use cases depend on DogRepository (interface)
    //   - Tests can @Provide a fake DogRepository without changing use cases
    //   - Prod uses real DogRepositoryImpl (Retrofit + Room)
    //   - Tests use FakeDogRepository (in-memory)
}
```


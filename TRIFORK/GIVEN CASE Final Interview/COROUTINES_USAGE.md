# Coroutines Usage: Complete Guide

All coroutine patterns in the Random Dog App, annotated and explained.

---

## 1. VIEWMODELSCOPE (UI Layer)

### HomeViewModel.kt — Launching work on main thread

```kotlin
class HomeViewModel @Inject constructor(
    private val getRandomDog: GetRandomDogUseCase,
    ...
) : MviViewModel<HomeUiState, HomeIntent, HomeEffect>(HomeUiState()) {

    init { loadNewDog() } // Runs on init block (main thread)

    private fun loadNewDog() {
        reduce { it.copy(isLoading = true, errorMessage = null) } // Immediate, main thread
        
        // ✅ COROUTINE #1: Launch work in viewModelScope
        viewModelScope.launch {  // Main thread scope; auto-cancelled when ViewModel destroyed
            // Work inside launch runs on Main (emptyDispatcher) by default
            
            getRandomDog()  // Suspend function
                .onSuccess { dog ->
                    reduce { it.copy(isLoading = false, dog = dog) }
                    observeFavouriteFor(dog.imageUrl)
                }
                .onFailure { error ->
                    reduce { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                    sendEffect(HomeEffect.ShowMessage(error.toUserMessage()))
                }
        }
    }

    private fun observeFavouriteFor(imageUrl: String) {
        favouriteJob?.cancel() // ✅ COROUTINE #2: Cancel previous subscription
        
        // ✅ COROUTINE #3: Collect a Flow reactively
        favouriteJob = viewModelScope.launch {  // viewModelScope survives rotation
            observeIsFavourite(imageUrl).collectLatest { fav ->  // collectLatest: cancels prev, starts new
                reduce { it.copy(isFavourite = fav) }  // Update state on each emission
            }
        }
    }

    private fun toggleCurrent() {
        val dog = currentState.dog ?: return
        
        // ✅ COROUTINE #4: Fire-and-forget suspend call
        viewModelScope.launch { 
            toggleFavourite(dog)  // Suspend, runs on Main by default
            // No result handling; snackbar comes via effect from repository error handling
        }
    }
}
```

**Coroutine #1 breakdown:**
```
viewModelScope.launch { }
     ↓
     Scope: tied to ViewModel lifecycle
     Dispatcher: Main (implicit, unless overridden)
     Cancellation: automatic when ViewModel cleared
     
Inside the launch:
     getRandomDog()  ← suspend function (repository)
     .onSuccess { }  ← non-blocking, returns immediately
     .onFailure { }  ← error handling as value
```

---

### FavouritesViewModel.kt — Collecting a Flow in init

```kotlin
@HiltViewModel
class FavouritesViewModel @Inject constructor(
    getFavourites: GetFavouriteDogsUseCase,
    ...
) : MviViewModel<FavouritesUiState, FavouritesIntent, FavouritesEffect>(FavouritesUiState()) {

    init {
        // ✅ COROUTINE #5: Collect a Flow in init
        viewModelScope.launch {  // Launch on Main
            getFavourites()  // Returns Flow<List<Dog>>
                .collect { dogs ->  // Collect all emissions (blocks until stream ends)
                    reduce { it.copy(isLoading = false, favourites = dogs) }
                    // Each time Room emits, this code runs and state updates
                    // Compose sees state change → recomposes gallery
                }
        }
    }

    override fun onIntent(intent: FavouritesIntent) {
        when (intent) {
            is FavouritesIntent.RemoveFavourite ->
                // ✅ COROUTINE #6: Launch side-effect + effect
                viewModelScope.launch {  // Main thread
                    toggleFavourite(intent.dog)  // Suspend write to Room
                    sendEffect(FavouritesEffect.ShowMessage("Removed from favourites"))
                }
        }
    }
}
```

---

## 2. SUSPEND FUNCTIONS (Domain & Data Layers)

### DogRepositoryImpl.kt — withContext for off-thread work

```kotlin
class DogRepositoryImpl @Inject constructor(
    private val api: DogApi,
    private val dao: FavouriteDogDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,  // Injected dispatcher
) : DogRepository {

    // ✅ COROUTINE #7: withContext — switch thread for blocking work
    override suspend fun getRandomDog(): Result<Dog> = withContext(ioDispatcher) {
        // ioDispatcher is Dispatchers.IO (thread pool for blocking I/O)
        
        runCatching {  // Wrap in try/catch, return as Result
            api.getRandomDog()  // Retrofit: calls Network I/O on IO dispatcher
                .toDog()  // Mapper: pure function (no I/O)
        }
        // Result<Dog> returned; ViewModel sees success or failure
    }

    // ✅ COROUTINE #8: Flow mapping (not suspend, but reactive)
    override fun observeFavourites(): Flow<List<Dog>> =
        dao.observeAll()  // Room returns Flow<List<Entity>> — reactive
            .map { entities ->  // Transform each emission
                entities.map { it.toDog() }  // Map Entity → Dog
            }
        // Flows are cold: no work until someone collects

    override fun observeIsFavourite(imageUrl: String): Flow<Boolean> =
        dao.observeIsFavourite(imageUrl)  // Room Flow passed through

    // ✅ COROUTINE #9: withContext for DB writes
    override suspend fun toggleFavourite(dog: Dog) = withContext(ioDispatcher) {
        if (dao.isFavourite(dog.imageUrl)) {  // Suspend query on IO dispatcher
            dao.deleteByUrl(dog.imageUrl)  // Suspend delete
        } else {
            dao.insert(dog.toEntity(addedAt = System.currentTimeMillis()))  // Suspend insert
        }
        // Room emits via observeAll() Flow → observers notified
    }
}
```

**withContext pattern:**
```
suspend fun doWork() = withContext(ioDispatcher) {
     ↓
     Caller: on Main (via viewModelScope.launch)
     Switch: to ioDispatcher (thread pool)
     Work: network/DB calls (blocking OK here)
     Return: back to Main (automatic)
     Result: StateFlow updated on Main
}
```

---

### DogApi.kt — Retrofit suspend interface

```kotlin
interface DogApi {
    @GET("api/breeds/image/random")
    // ✅ COROUTINE #10: Retrofit suspend function
    suspend fun getRandomDog(): DogImageDto
    // suspend: Retrofit wraps the call, pauses coroutine during I/O, resumes with result
    // No callbacks; linear code
}
```

---

### Use Cases — Thin suspend delegates

```kotlin
// GetRandomDogUseCase.kt
class GetRandomDogUseCase @Inject constructor(private val repository: DogRepository) {
    // ✅ COROUTINE #11: Suspend operator
    suspend operator fun invoke(): Result<Dog> = repository.getRandomDog()
    // Suspend propagates up: ViewModel.launch awaits this
}

// ToggleFavouriteUseCase.kt
class ToggleFavouriteUseCase @Inject constructor(private val repository: DogRepository) {
    // ✅ COROUTINE #12: Suspend operator
    suspend operator fun invoke(dog: Dog) = repository.toggleFavourite(dog)
}

// GetFavouriteDogsUseCase.kt — NOT suspend, returns Flow
class GetFavouriteDogsUseCase @Inject constructor(private val repository: DogRepository) {
    // ✅ COROUTINE #13: Flow operator (cold, not suspend)
    operator fun invoke(): Flow<List<Dog>> = repository.observeFavourites()
    // Cold: no work until collected; can be subscribed many times
}
```

---

## 3. FLOWS (Reactive Streams)

### Room — Flow sources

```kotlin
// FavouriteDogDao.kt
@Dao
interface FavouriteDogDao {

    // ✅ COROUTINE #14: Room Flow query
    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteDogEntity>>
    // Cold flow: emits on every DB change (insert/delete)
    // Collectors can subscribe & unsubscribe independently

    // ✅ COROUTINE #15: Room Flow boolean query
    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    fun observeIsFavourite(url: String): Flow<Boolean>
    // Emits on DB change (true when row exists, false when deleted)

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    // ✅ COROUTINE #16: Room suspend (non-reactive)
    suspend fun isFavourite(url: String): Boolean
    // Suspend: no Flow; one-time query for toggle logic
}
```

### Collecting Flows in ViewModels

```kotlin
// In HomeViewModel
private fun observeFavouriteFor(imageUrl: String) {
    favouriteJob?.cancel()
    
    // ✅ COROUTINE #17: collectLatest pattern
    favouriteJob = viewModelScope.launch {
        observeIsFavourite(imageUrl)
            .collectLatest { fav ->  // collectLatest: cancel prev, start new
                reduce { it.copy(isFavourite = fav) }
            }
    }
}

// In FavouritesViewModel
init {
    // ✅ COROUTINE #18: collect pattern
    viewModelScope.launch {
        getFavourites()
            .collect { dogs ->  // collect: every emission
                reduce { it.copy(isLoading = false, favourites = dogs) }
            }
    }
}
```

**collectLatest vs collect:**
```
collectLatest:
  New emission arrives → cancel previous collect block → run new block
  Use for: "I only care about the latest value" (e.g., favourite status)

collect:
  New emission arrives → wait for previous to finish → run next block
  Use for: "Process every emission" (e.g., list of all favourites)
```

---

## 4. STATEFLOW (State holder in ViewModel)

### MviViewModel.kt — StateFlow infrastructure

```kotlin
abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {

    // ✅ COROUTINE #19: MutableStateFlow (mutable state holder)
    private val _state = MutableStateFlow(initialState)
    
    // ✅ COROUTINE #20: Exposed as read-only StateFlow
    val state: StateFlow<S> = _state.asStateFlow()
    // StateFlow is a Flow with a current value; emits on changes
    // Unlike regular Flow, StateFlow holds the latest state

    protected fun reduce(block: (S) -> S) = _state.update(block)
    // update: thread-safe immutable update
    // Emits new state to all collectors
}
```

### Collecting StateFlow in Compose

```kotlin
// HomeScreen.kt
@Composable
fun HomeScreen(
    ...
    viewModel: HomeViewModel = hiltViewModel(),
) {
    // ✅ COROUTINE #21: collectAsStateWithLifecycle
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Lifecycle-aware collection
    // - Collects when screen visible (STARTED)
    // - Pauses when hidden
    // - Auto-cancels when lifecycle destroyed
    // Result: Compose State<UiState> that recomposes on change
    
    HomeScreenContent(
        state = state,  // Current state value
        ...
    )
}
```

**collectAsStateWithLifecycle pattern:**
```
viewModel.state: StateFlow<UiState>
  ↓
collectAsStateWithLifecycle()  — Convert Flow to Compose State with lifecycle
  ↓
by  — Destructure with delegation
  ↓
state: UiState  — Compose State, recomposes on change
```

---

## 5. EFFECT CHANNEL (One-off events)

### MviViewModel.kt — Effect stream

```kotlin
abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {

    // ✅ COROUTINE #22: Channel for effects (buffered)
    private val _effects = Channel<E>(Channel.BUFFERED)
    
    // ✅ COROUTINE #23: Expose as Flow
    val effects: Flow<E> = _effects.receiveAsFlow()
    // Channel: hot stream (work happens even if no collectors)
    // BUFFERED: sender doesn't wait for receiver
    // receiveAsFlow: convert Channel to Flow for collection

    protected fun sendEffect(effect: E) {
        // ✅ COROUTINE #24: Launch to send effect non-blockingly
        viewModelScope.launch { 
            _effects.send(effect)  // Non-blocking send (buffered)
        }
    }
}
```

### Collecting Effects in Compose

```kotlin
// HomeScreen.kt
@Composable
fun HomeScreen(
    onShowMessage: (String) -> Unit,
    ...
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // ✅ COROUTINE #25: repeatOnLifecycle for effect collection
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // ✅ COROUTINE #26: Collect effects when visible
            viewModel.effects.collect { effect ->
                when (effect) {
                    is HomeEffect.ShowMessage -> onShowMessage(effect.text)
                }
            }
        }
    }
}
```

**Effect flow:**
```
ViewModel.sendEffect(ShowMessage("…"))
  ↓
launch { _effects.send(effect) }  — Non-blocking
  ↓
HomeScreen.effects.collect { }  — Observes
  ↓
onShowMessage()  — Called
  ↓
NavGraph { snackbarHostState.showSnackbar() }  — Snackbar shown
```

---

## 6. RUNAWAY PATTERNS & ERROR HANDLING

### Result-based error handling

```kotlin
// DogRepositoryImpl.kt
override suspend fun getRandomDog(): Result<Dog> = withContext(ioDispatcher) {
    // ✅ COROUTINE #27: runCatching — wraps suspend call
    runCatching { 
        api.getRandomDog()  // Might throw
            .toDog()
    }
    // Returns: Result.success(Dog) or Result.failure(e)
    // Exception never propagates; becomes a value
}

// HomeViewModel.kt
private fun loadNewDog() {
    reduce { it.copy(isLoading = true, errorMessage = null) }
    
    viewModelScope.launch {
        getRandomDog()  // Returns Result<Dog>
            // ✅ COROUTINE #28: onSuccess/onFailure (extension functions on Result)
            .onSuccess { dog ->
                reduce { it.copy(isLoading = false, dog = dog) }
                observeFavouriteFor(dog.imageUrl)
            }
            // ✅ COROUTINE #29: Error as state, never throws
            .onFailure { error ->
                reduce { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                sendEffect(HomeEffect.ShowMessage(error.toUserMessage()))
            }
    }
}
```

---

## 7. TESTING WITH COROUTINES

### HomeViewModelTest.kt

```kotlin
class HomeViewModelTest {

    private val dispatcher = StandardTestDispatcher()  // Deterministic test dispatcher
    
    // ✅ COROUTINE #30: Set/reset Main dispatcher for tests
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `loads a dog on init and exposes Success`() = runTest(dispatcher) {
        // ✅ COROUTINE #31: runTest — deterministic coroutine test scope
        coEvery { repo.getRandomDog() } returns Result.success(dog)
        every { repo.observeIsFavourite(dog.imageUrl) } returns flowOf(false)

        val vm = viewModel()
        // ✅ COROUTINE #32: advanceUntilIdle — run all pending coroutines
        advanceUntilIdle()

        val state = vm.state.value  // Assert final state
        assertFalse(state.isLoading)
        assertEquals(dog, state.dog)
    }

    @Test
    fun `network failure emits ShowMessage effect`() = runTest(dispatcher) {
        coEvery { repo.getRandomDog() } returns Result.failure(RuntimeException("boom"))
        
        val vm = viewModel()
        advanceUntilIdle()
        
        // ✅ COROUTINE #33: Turbine library to test Flow/Channel
        vm.effects.test {  // Test scope for effects
            assertTrue(awaitItem() is HomeEffect.ShowMessage)  // Assert emission
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

### DogRepositoryImplTest.kt

```kotlin
class DogRepositoryImplTest {

    private val dispatcher = UnconfinedTestDispatcher()  // Even more permissive

    @Test
    fun `observeFavourites emits mapped domain models`() = runTest(dispatcher) {
        val dao = FakeFavouriteDao()
        val r = repo(dao = dao)
        r.toggleFavourite(Dog("...", "Pug"))

        // ✅ COROUTINE #34: Test Flow emissions with Turbine
        r.observeFavourites().test {
            val first = awaitItem()  // First emission from Room
            assertEquals(1, first.size)
            assertEquals("Pug", first.first().breed)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

---

## 8. DEPENDENCY INJECTION FOR DISPATCHERS

### DispatcherModule.kt

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DispatcherModule {

    // ✅ COROUTINE #35: Injected dispatcher for production
    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
    // Dispatchers.IO: thread pool for blocking I/O (network, DB)
    // Size: threads = number of CPUs
}
```

**Why inject?**
```
Production: @IoDispatcher → Dispatchers.IO (thread pool)
Tests: @IoDispatcher → Unconfined (instant execution)
Benefit: Tests are deterministic; production uses real threading
```

---

## COROUTINE DECISION TREE

**Use `viewModelScope.launch { }`**
- ✅ User action → fetch data → update state
- ✅ Collecting a Flow to update state
- ✅ Fire-and-forget side-effects
- ❌ NOT for returning values (use suspend instead)

**Use `suspend fun`**
- ✅ Work that should be awaited (API call, DB write)
- ✅ Repository functions, use cases
- ✅ Can be called from launch { } or other suspend contexts
- ❌ NOT for emitting multiple values (use Flow)

**Use `Flow<T>`**
- ✅ Multiple emissions over time (Room queries)
- ✅ Reactive: collectors can come and go
- ✅ Cold: no work until collected
- ❌ NOT for one-time values (use suspend)

**Use `StateFlow<T>`**
- ✅ Current value + change notifications
- ✅ UI state holder
- ✅ Always has a value
- ❌ NOT for transient events (use Channel/Effect)

**Use `Channel<T>` / Effects**
- ✅ One-off events (toasts, navigation)
- ✅ Multiple listeners possible
- ✅ Work happens immediately (hot)
- ❌ NOT for state (use StateFlow)

---

## QUICK SUMMARY TABLE

| # | Pattern | File | What | Why |
|----|---------|------|------|-----|
| 1-6 | `viewModelScope.launch { }` | HomeViewModel | Launch work on Main | Tied to ViewModel lifecycle |
| 7-9 | `withContext(ioDispatcher)` | DogRepositoryImpl | Switch to IO for blocking work | Network/DB off main thread |
| 10-13 | `suspend fun` | DogApi, UseCases | Pausable function | Linear code, testable |
| 14-18 | `Flow<T>` | Room, ViewModel | Reactive stream | Multiple emissions, cold |
| 19-21 | `StateFlow<T>` + `collectAsStateWithLifecycle()` | MviViewModel, HomeScreen | UI state | Always has value, lifecycle-aware |
| 22-26 | `Channel<E>` + `effects.collect()` | MviViewModel, HomeScreen | One-off events | Snackbars, side-effects |
| 27-29 | `Result<T>` + `runCatching` | Repository, ViewModel | Error handling | Errors as state, not exceptions |
| 30-34 | `runTest`, `advanceUntilIdle`, `Turbine` | Tests | Test coroutines | Deterministic, assertions |
| 35 | `@IoDispatcher` injected | DispatcherModule, Repository | Swappable dispatcher | Prod: Dispatchers.IO, Tests: Unconfined |


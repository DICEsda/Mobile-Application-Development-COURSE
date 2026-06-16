# Random Dog App — Design Spec

**Date:** 2026-06-10
**Status:** Approved for planning
**Author:** Yahya (with Claude)

A take-home case: a deliberately simple app made into a strong architecture showcase.
The feature surface is trivial on purpose — the value is in how cleanly it maps onto
Google's recommended app architecture and how every non-obvious choice is justified, in
the same style as the companion audiobook project's `ARCHITECTURE.md` / `DECISIONS.md`.

---

## 1. Goal & scope

### What we're building
A small Android app that shows a random dog picture, lets the user fetch another, lets
them favourite a dog, and browse a gallery of favourites — built to demonstrate
**senior-level architectural reasoning**, not feature breadth.

### Functional requirements
From the case one-pager (Random Dog App):

**Basic (required):**
- Show an image of a random dog.
- Fetch a new random dog.

**Extra (chosen for this build):**
- Favourite / like a dog.
- View a gallery of favourite dogs.

### API
- **dog.ceo** — `GET https://dog.ceo/api/breeds/image/random`
- Response shape: `{ "message": "<image-url>", "status": "success" }`
- The `message` URL also encodes the breed (`https://images.dog.ceo/breeds/<breed>/<file>.jpg`),
  which we parse into a display breed label.

### Technological requirements (from the brief — non-negotiable)
- Jetpack Compose
- Kotlin
- **Google's current recommended app architecture**
- Coroutines
- **Hilt**

### Non-goals (YAGNI)
- No multi-module split, no `:domain` Kotlin module (see Decision: Google vs strict Clean).
- No paging, no breed search/browse, no remote favourites/account, no sync.
- No formal navigation arguments beyond the two top-level destinations.

---

## 2. Architecture overview

Google's recommended layering, single module, **package-by-layer**:

```
UI layer        (Compose screens + MVI ViewModels + immutable UiState)
   ↓ intents up / state down (UDF)
Domain layer    (use cases + domain model `Dog`)
   ↓
Data layer      (DogRepository interface + Impl → Retrofit DogApi + Room FavouriteDao)

DI (Hilt) wires every layer. dog.ceo is the only external actor.
```

**Dependency direction:** `ui → domain → data`. The UI never touches Retrofit or Room
directly; it only ever talks to a ViewModel, which talks to use cases, which talk to the
repository interface.

### UI pattern: MVI (a stricter form of Google's UDF)
Google mandates UDF + a single immutable UI state but is unopinionated between MVVM and
MVI. We use **MVI** because it's a deliberate showcase and is fully brief-compatible. Each
screen has exactly three contract types plus a reducer:

- **State** — one immutable `data class`, the single source of truth for the screen.
- **Intent** — a `sealed interface` enumerating every user action; the *only* way into the VM.
- **Effect** — a `sealed interface` of one-off side effects that are **not** state
  (snackbars, transient messages), delivered over a `Channel`.

State is only ever mutated through a `reduce { }` function. The View renders `state` and
sends intents; it does nothing else.

---

## 3. Package / file structure

Single `:app` module, base package `com.example.randomdog`:

```
com.example.randomdog
├─ RandomDogApplication.kt           // @HiltAndroidApp
├─ MainActivity.kt                   // @AndroidEntryPoint, hosts NavHost
│
├─ data/
│  ├─ remote/
│  │  ├─ DogApi.kt                   // Retrofit interface
│  │  └─ dto/DogImageDto.kt          // wire model
│  ├─ local/
│  │  ├─ AppDatabase.kt              // Room @Database
│  │  ├─ FavouriteDogDao.kt          // Flow reads, suspend writes
│  │  └─ FavouriteDogEntity.kt       // @Entity
│  ├─ mapper/DogMappers.kt           // DTO/Entity ↔ domain Dog
│  └─ repository/
│     ├─ DogRepository.kt            // interface (data-layer public API)
│     └─ DogRepositoryImpl.kt        // implementation
│
├─ domain/
│  ├─ model/Dog.kt                   // domain model
│  └─ usecase/
│     ├─ GetRandomDogUseCase.kt
│     ├─ ToggleFavouriteUseCase.kt
│     ├─ GetFavouriteDogsUseCase.kt
│     └─ ObserveIsFavouriteUseCase.kt
│
├─ ui/
│  ├─ mvi/MviViewModel.kt            // reusable State/Intent/Effect base
│  ├─ home/
│  │  ├─ HomeScreen.kt
│  │  ├─ HomeViewModel.kt
│  │  └─ HomeContract.kt             // HomeUiState, HomeIntent, HomeEffect
│  ├─ favourites/
│  │  ├─ FavouritesScreen.kt
│  │  ├─ FavouritesViewModel.kt
│  │  └─ FavouritesContract.kt
│  ├─ components/                    // shared composables (DogCard, ErrorState, Loading)
│  ├─ navigation/
│  │  ├─ NavGraph.kt                 // NavHost
│  │  └─ Screen.kt                   // sealed routes
│  └─ theme/                         // Color, Theme, Type
│
└─ di/
   ├─ NetworkModule.kt               // OkHttp, Retrofit, DogApi
   ├─ DatabaseModule.kt              // Room, DAO
   └─ RepositoryModule.kt            // @Binds DogRepository
```

---

## 4. Layer-by-layer design

### 4.1 Data layer

**Remote — `DogApi`**
```kotlin
interface DogApi {
    @GET("api/breeds/image/random")
    suspend fun getRandomDog(): DogImageDto
}

@Serializable
data class DogImageDto(val message: String, val status: String)
```

**Local — Room**
```kotlin
@Entity(tableName = "favourites")
data class FavouriteDogEntity(
    @PrimaryKey val imageUrl: String,   // URL is naturally unique → primary key
    val breed: String?,
    val addedAt: Long,
)

@Dao
interface FavouriteDogDao {
    @Query("SELECT * FROM favourites ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<FavouriteDogEntity>>          // reactive read

    @Query("SELECT EXISTS(SELECT 1 FROM favourites WHERE imageUrl = :url)")
    fun observeIsFavourite(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(dog: FavouriteDogEntity)

    @Query("DELETE FROM favourites WHERE imageUrl = :url")
    suspend fun deleteByUrl(url: String)
}
```
Room is exported (`schemas/`) for a migration paper trail, mirroring the audiobook project.

**Repository — interface in the data layer (Google convention)**
```kotlin
interface DogRepository {
    suspend fun getRandomDog(): Result<Dog>          // network, wrapped
    fun observeFavourites(): Flow<List<Dog>>          // Room is the SSOT
    fun observeIsFavourite(imageUrl: String): Flow<Boolean>
    suspend fun toggleFavourite(dog: Dog)
}
```
`DogRepositoryImpl` injects `DogApi` + `FavouriteDogDao`, wraps the network call in
`runCatching`, and maps via `DogMappers`. **Room is the single source of truth** for
favourites; the gallery and the home screen's `isFavourite` flag both observe it.

**Error handling:** the repository never throws to callers. `getRandomDog()` returns
`Result<Dog>`; network/timeout/parse failures become `Result.failure`, which the
ViewModel reduces into an error state with a Retry intent. No exceptions reach Compose.

### 4.2 Domain layer

```kotlin
data class Dog(val imageUrl: String, val breed: String?)   // framework-free
```
Use cases are thin, single-responsibility, `operator fun invoke(...)`:
- `GetRandomDogUseCase` → `repo.getRandomDog()`
- `ToggleFavouriteUseCase` → `repo.toggleFavourite(dog)`
- `GetFavouriteDogsUseCase` → `repo.observeFavourites()`
- `ObserveIsFavouriteUseCase` → `repo.observeIsFavourite(url)`

They earn their place by keeping the ViewModel free of repository orchestration and being
trivially unit-testable. (The "is this over-engineering?" question is answered explicitly
in DECISIONS.)

### 4.3 UI layer (MVI)

**Reusable base**
```kotlin
abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    private val _effects = Channel<E>(Channel.BUFFERED)
    val effects: Flow<E> = _effects.receiveAsFlow()

    abstract fun onIntent(intent: I)                 // the only way in
    protected fun reduce(block: (S) -> S) = _state.update(block)
    protected fun sendEffect(effect: E) { viewModelScope.launch { _effects.send(effect) } }
}
```

**Home contract**
```kotlin
data class HomeUiState(
    val isLoading: Boolean = false,
    val dog: Dog? = null,
    val isFavourite: Boolean = false,
    val errorMessage: String? = null,
)
sealed interface HomeIntent {
    data object LoadNewDog : HomeIntent
    data object ToggleFavourite : HomeIntent
    data object Retry : HomeIntent
}
sealed interface HomeEffect {
    data class ShowMessage(val text: String) : HomeEffect
}
```
`HomeViewModel` loads a dog on init, exposes `state`, and on each intent runs the matching
use case then `reduce { }`s the result. The `isFavourite` flag is kept live by collecting
`ObserveIsFavouriteUseCase` for the current dog and reducing it into state.

**Favourites contract** — `FavouritesUiState(isLoading, favourites: List<Dog>)`, fed by
`GetFavouriteDogsUseCase().stateIn(viewModelScope, …)`; intent `RemoveFavourite(dog)`.

**Screens** are stateless: `collectAsStateWithLifecycle()` for state, a `LaunchedEffect`
collecting `effects` for snackbars, and `onIntent(...)` callbacks for every user action.
Images render with **Coil** `AsyncImage`.

### 4.4 Navigation
Single `MainActivity`, `NavHost` with two destinations in a sealed `Screen` class
(`Home`, `Favourites`). Bottom bar or a top-app-bar action toggles between them.

### 4.5 Dependency injection (Hilt)
- `@HiltAndroidApp class RandomDogApplication`
- `NetworkModule` — `OkHttpClient`, `Retrofit` (kotlinx.serialization converter), `DogApi`
- `DatabaseModule` — `AppDatabase` (Room), `FavouriteDogDao`
- `RepositoryModule` — `@Binds DogRepository → DogRepositoryImpl`
- `@HiltViewModel` ViewModels with `@Inject constructor`; `hiltViewModel()` in Compose
- `MainActivity` is `@AndroidEntryPoint`

---

## 5. Data flow

**New dog (happy path):**
`onIntent(LoadNewDog)` → `reduce { isLoading = true }` → `GetRandomDogUseCase` →
`DogRepository.getRandomDog()` → `DogApi` → map DTO→`Dog` →
`reduce { isLoading = false; dog = it }` → Compose recomposes.

**New dog (failure):**
`Result.failure` → `reduce { isLoading = false; errorMessage = … }` + `ErrorState`
composable with a Retry button that sends `HomeIntent.Retry`.

**Favourite + gallery:**
`onIntent(ToggleFavourite)` → `ToggleFavouriteUseCase` → Room insert/delete. Because the
gallery and the home `isFavourite` flag both **observe** Room `Flow`s, the UI updates
reactively with zero manual refresh — Room is the single source of truth.

---

## 6. Testing strategy

JVM unit tests (fast, no device):
- **`DogRepositoryImpl`** — fake `DogApi` + fake `FavouriteDogDao`: success maps DTO→Dog,
  failure → `Result.failure`, toggle inserts/deletes correctly.
- **Use cases** — delegate to the repository correctly.
- **ViewModels** — `kotlinx-coroutines-test` (`runTest`, `StandardTestDispatcher`) +
  **Turbine** on `state`/`effects`: `Loading → Success`, error path, favourite toggle,
  emitted `ShowMessage` effect.

Instrumented (noted as device/emulator tests, not run in CI by default):
- **`FavouriteDogDao`** — Room in-memory DB: insert/observe/delete round-trips.

This mirrors the audiobook project's "pure seams are unit-tested" ethos.

---

## 7. Documentation deliverables

The docs are a first-class deliverable, in the audiobook project's style.

### `ARCHITECTURE.md`
Mermaid diagrams (render inline on GitHub / Android Studio) + a grouped glossary:
1. **Layered architecture** — UI → Domain → Data, DI cross-cutting, dog.ceo as sole external actor.
2. **Compartment note-cards** — one card per package: its job + the rule it obeys.
3. **Hilt dependency graph** — who provides/injects whom (the graph Hilt generates).
4. **MVI loop** — `Intent → ViewModel → reduce → State → View → Intent`, with the Effect channel.
5. **Random-dog sequence** — tap → intent → use case → repo → API → reduce → recompose, incl. the failure branch.
6. **Favourite & gallery data flow** — Room as single source of truth, reactive `Flow`.
7. **Glossary** — grouped by layer: Compose, ViewModel, StateFlow/UiState, MVI (Intent/Effect/reducer), UDF, UseCase, Repository, Room/Entity/DAO, Retrofit, Hilt, Coil, Coroutines/Flow, Result.

### `DECISIONS.md`
Short ADRs (context / decision / alternatives / consequences / when-to-revisit):
1. **Hilt for DI** — and *why the trade-off flips* vs. the audiobook project's manual `AppContainer` (required by the brief; standard production choice; compile-time graph validation).
2. **MVI** — single immutable state + intents + reducer + effects; why it's still Google-UDF-aligned; trade-off (more boilerplate than MVVM, accepted as a deliberate showcase + scales cleanly). Note this diverges from the audiobook's MVVM choice on purpose.
3. **Room vs DataStore** for favourites — relational collection, reactive `Flow`, single source of truth.
4. **Domain layer with use cases** — why included on a simple app (thin VMs, testable seams, full recommended architecture) and the honest over-engineering counter-argument.
5. **Google recommended architecture vs. strict Clean Architecture** — where they differ (repo interface location, multi-module dependency rule) and why we aligned with Google per the brief.
6. **Retrofit + kotlinx.serialization** — vs. Ktor/Moshi.
7. **`Result`-based error handling** — errors as first-class state, no exceptions to the UI.
8. **Coil** for image loading — vs. Glide.
9. **Single-module, package-by-layer** — when multi-module / package-by-feature becomes correct.
10. **Single-Activity + Compose Navigation.**
11. **What I'd do differently** — honest list (e.g., UI tests, screenshot tests, paging if the gallery grew, CI).

---

## 8. Dependencies (version catalog `libs.versions.toml`)

- Kotlin, AGP, Compose BOM, Activity-Compose, Lifecycle (ViewModel + `collectAsStateWithLifecycle`)
- Navigation-Compose
- Hilt (`hilt-android`, `hilt-compiler`) + `hilt-navigation-compose`
- Retrofit + OkHttp logging + kotlinx.serialization (+ Retrofit serialization converter)
- Room (`room-runtime`, `room-ktx`, `room-compiler` via KSP)
- Coil-Compose
- Coroutines
- Test: JUnit, `kotlinx-coroutines-test`, Turbine, MockK (or hand-written fakes), Room-testing
- KSP for Room + Hilt (no kapt)

---

## 9. Verification caveat

This is a Windows dev box that most likely has **no Android SDK / Gradle toolchain**, so
the implementation phase will produce a complete, correct project but probably **cannot run
`./gradlew build` here to prove it compiles** — the final build happens in Android Studio.
Any step that can't be locally verified will be flagged as such rather than claimed as
passing. If the Android SDK + a JDK are in fact available, a build will be attempted.

---

## 10. Open questions / assumptions

- **Breed label:** parsed from the image URL path; if parsing fails, breed is `null` and
  the UI simply omits it. (No second API call for breed.)
- **Nav UI:** bottom navigation bar assumed for two destinations; trivial to swap for a
  top-bar action. Not load-bearing on the architecture.
- **Theme:** Material 3 default with a light/dark scheme; no custom branding required.
```
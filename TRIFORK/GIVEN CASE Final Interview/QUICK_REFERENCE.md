# Quick Reference: File-by-File

One-liner + key concept for each file. Use this to navigate the codebase during your interview.

---

## ENTRY POINT (2 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `RandomDogApplication.kt` | App initialization | `@HiltAndroidApp` — triggers Hilt DI graph generation at compile time |
| `MainActivity.kt` | Single Activity entry | Hosts Compose, applies theme, delegates to `RandomDogApp()` navigation |

---

## NAVIGATION (2 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `Screen.kt` | Type-safe routes | Sealed class: `Home`, `Favourites` — exhaustive, type-safe navigation |
| `NavGraph.kt` | Navigation host | `NavHost` with bottom bar, central `SnackbarHost` for effects bubbling |

---

## UI LAYER: HOME SCREEN (3 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `HomeContract.kt` | MVI contract | State (immutable), Intent (user actions), Effect (one-off events) |
| `HomeViewModel.kt` | State management | `@HiltViewModel`, `reduce { }` updates state, `sendEffect()` for toasts |
| `HomeScreen.kt` | Composables | Stateful wrapper + stateless content: state down, intents up |

---

## UI LAYER: FAVOURITES SCREEN (3 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `FavouritesContract.kt` | MVI contract | State (loading, list), Intent (remove), Effect (snackbar) |
| `FavouritesViewModel.kt` | State management | Collects `GetFavourites()` Flow in init → auto-updates gallery |
| `FavouritesScreen.kt` | Composables | Grid layout that recomposes when Room emits new list |

---

## UI LAYER: SHARED (2 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `MviViewModel.kt` | Reusable base | Generic `<State, Intent, Effect>`; provides `reduce { }` and `sendEffect()` |
| `DogStateViews.kt` | Shared components | `LoadingState`, `ErrorState`, `DogImage` (Coil) — pure, testable |

---

## DOMAIN LAYER (5 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `Dog.kt` | Domain model | Framework-agnostic: just `imageUrl` + `breed` |
| `GetRandomDogUseCase.kt` | Fetch action | Thin delegate: wraps `repo.getRandomDog()`, returns `Result<Dog>` |
| `ToggleFavouriteUseCase.kt` | Favourite action | Thin delegate: calls `repo.toggleFavourite(dog)` |
| `GetFavouriteDogsUseCase.kt` | List action | Returns `Flow<List<Dog>>` for reactive gallery |
| `ObserveIsFavouriteUseCase.kt` | Observe action | Returns `Flow<Boolean>` for reactive heart icon |

---

## DATA LAYER: REPOSITORY (2 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `DogRepository.kt` | Interface | Public API: 4 methods using domain `Dog`, lives in data layer |
| `DogRepositoryImpl.kt` | Implementation | Orchestrates Retrofit (API) + Room (DB); wraps errors in `Result` |

---

## REMOTE SOURCE: RETROFIT (2 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `DogApi.kt` | HTTP interface | `@GET /api/breeds/image/random` — suspend function via Retrofit |
| `DogImageDto.kt` | Wire format | `@Serializable`: `message` (URL) + `status` — not used beyond mapper |

---

## LOCAL SOURCE: ROOM (3 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `FavouriteDogEntity.kt` | DB row shape | `@Entity` with `imageUrl` PK, breed, `addedAt` timestamp |
| `FavouriteDogDao.kt` | Queries | `observeAll()`, `observeIsFavourite()` return `Flow` (reactive); insert/delete suspend |
| `AppDatabase.kt` | DB declaration | `@Database`, exposes DAO; `exportSchema=true` for migration tracking |

---

## MAPPERS (1 file)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `DogMappers.kt` | Layer translation | DTO→Dog, Entity→Dog, Dog→Entity; includes `parseBreed()` logic |

---

## DEPENDENCY INJECTION: HILT (4 files)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `NetworkModule.kt` | Network DI | Provides `Json`, `OkHttpClient`, `Retrofit`, `DogApi` |
| `DatabaseModule.kt` | Database DI | Provides `AppDatabase`, `FavouriteDogDao` |
| `DispatcherModule.kt` | Dispatcher DI | Provides `@IoDispatcher: Dispatchers.IO` (swappable in tests) |
| `RepositoryModule.kt` | Repository binding | `@Binds DogRepositoryImpl` → `DogRepository` interface |
| `Qualifiers.kt` | Custom annotation | `@IoDispatcher` qualifier for distinguishing dispatchers |

---

## TESTS (3 files, main examples)

| File | Purpose | Key Concept |
|------|---------|-------------|
| `HomeViewModelTest.kt` | ViewModel tests | Mock repository, assert state changes and effects via `runTest(dispatcher)` |
| `DogRepositoryImplTest.kt` | Repository tests | Hand-rolled fakes (`FakeDogApi`, `FakeFavouriteDao`), assert mapping + reactivity |
| `FavouritesViewModelTest.kt` | Gallery tests | Similar pattern: mock repo, verify Flow emissions |

---

## ARCHITECTURE FLOW

**User taps "New dog":**
```
HomeScreen 
  → onIntent(LoadNewDog) 
    → HomeViewModel.loadNewDog() 
      → reduce { isLoading = true } 
      → GetRandomDogUseCase() 
        → DogRepositoryImpl.getRandomDog() 
          → DogApi.getRandomDog() (Retrofit) 
          → dog.ceo API 
          → DogImageDto 
          → toDog() (mapper) 
          → Result.success(Dog)
          → reduce { dog = it, isLoading = false }
          → observeIsFavourite(url) (Room Flow)
          → reduce { isFavourite = it }
          → StateFlow emits 
          → HomeScreen recomposes
```

**If network fails:**
```
Exception → runCatching → Result.failure 
→ reduce { errorMessage = "…" } 
→ sendEffect(ShowMessage) 
→ HomeScreen shows ErrorState + Retry button
```

**Toggle favourite:**
```
HomeScreen 
  → onIntent(ToggleFavourite) 
    → ToggleFavouriteUseCase() 
      → DogRepositoryImpl.toggleFavourite() 
        → Room: insert or deleteByUrl 
        → FavouriteDogDao.observeAll() emits 
        → FavouritesViewModel collects 
        → reduce { favourites = it }
        → FavouritesScreen grid recomposes
```

---

## LAYERING RULES (Architecture Guardrails)

| Layer | Can use | Cannot use |
|-------|---------|-----------|
| **UI** | ViewModel, composables, domain model `Dog` | Retrofit, Room, DAO |
| **Domain** | Domain model `Dog`, use cases | Retrofit DTO, Room Entity, Android APIs |
| **Data** | Repository, Retrofit, Room, mappers | ViewModel, UI, composables |
| **DI** | All modules; wires the graph | Business logic |

**Why?** Decoupling: if API changes, only data layer touches mappers. If DB changes, only DAO/Entity change. UI never touches network/DB directly.

---

## PATTERNS TO MEMORIZE

| Pattern | Example | Why |
|---------|---------|-----|
| **MVI** | Immutable state + sealed intents + reducer | Single source of truth, testable, exhaustive |
| **Repository** | `DogRepository` interface, `DogRepositoryImpl` | Swap fakes in tests, hide remote/local complexity |
| **Use Cases** | `GetRandomDogUseCase`, `ToggleFavouriteUseCase` | Name actions, injectable, thin, testable |
| **Mappers** | `DTO.toDog()`, `Entity.toDog()` | Decouple wire/DB from domain |
| **Result<T>** | `getRandomDog(): Result<Dog>` | Errors as state, not exceptions |
| **Flow** | `observeFavourites(): Flow<List<Dog>>` | Reactive UI updates, zero manual refresh |
| **Hilt DI** | `@Module`, `@Provides`, `@Binds`, `@HiltViewModel` | Compile-time validation, injectable tests |
| **Sealed classes** | `Intent`, `Effect`, `Screen` | Type-safe, exhaustive pattern matching |

---

## COMMON INTERVIEW QUESTIONS & ANSWERS

**Q: Why MVI instead of plain MVVM?**  
A: Stricter unidirectional data flow. State is immutable, changed only via `reduce { }`, intents are the only entry point. More testable, fewer bugs.

**Q: Why is the repository interface in the data layer, not domain?**  
A: This follows Google's recommended architecture, not strict Clean Architecture. The interface is the data layer's public API.

**Q: How does the gallery stay in sync without manual refresh?**  
A: `GetFavouritesUseCase()` returns a `Flow<List<Dog>>`. The ViewModel collects it and reduces each emission into state. Room emits whenever rows change. Zero manual refresh.

**Q: How are errors handled?**  
A: Network failures return `Result.failure(e)`. The ViewModel reduces the error into state: `errorMessage = "…"`. UI shows Retry button. Errors never throw or crash.

**Q: Why inject the IO dispatcher?**  
A: So tests can swap `Dispatchers.Unconfined` for deterministic execution. Prod uses `Dispatchers.IO` (thread pool).

**Q: How do you test a ViewModel?**  
A: Mock the repository, create the ViewModel with mocks, trigger intents via `onIntent()`, assert state via `vm.state.value`, assert effects via `vm.effects.test { }`.

**Q: Why use Hilt instead of manual DI?**  
A: Compile-time graph validation (catches missing bindings at build time, not runtime). Required by the brief. Standard tool every Android team uses.

---

## QUICK MENTAL MODEL

```
User Action (Intent)
    ↓
ViewModel.onIntent()
    ↓
reduce { } — immutable state update
    ↓
StateFlow emits
    ↓
Compose recomposes
    ↓
Screen rendered

Side effects (toasts, navigation):
    ↓
sendEffect() — one-off event
    ↓
Effect channel
    ↓
Screen collects & executes (snackbar, etc.)
```

**Data flow (bottom-up):**
```
Room emits Flow<List<Entity>>
    ↓
map { it.toDog() } — mappers
    ↓
GetFavouritesUseCase returns Flow<List<Dog>>
    ↓
ViewModel collects in init
    ↓
reduce { favourites = it }
    ↓
StateFlow emits
    ↓
FavouritesScreen recomposes
```


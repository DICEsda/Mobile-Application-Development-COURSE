# State Lifecycle Diagram

Visual guide: how state flows through the MVI architecture.

---

## 1. STATE CREATION & INITIALIZATION

```
┌─────────────────────────────────────────────────────────────────┐
│ Application starts → MainActivity created                       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ RandomDogApp() NavGraph created                                 │
│   ↓                          ↓                                  │
│ HomeScreen              FavouritesScreen                        │
│   ↓                          ↓                                  │
│ viewModel =             viewModel =                             │
│ hiltViewModel()         hiltViewModel()                         │
│   ↓                          ↓                                  │
│ HomeViewModel           FavouritesViewModel                     │
│ created                 created                                 │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ MviViewModel<S, I, E>(initialState) constructor runs           │
│                                                                  │
│   private val _state = MutableStateFlow(initialState)           │
│   ↓                                                              │
│   HomeUiState(                                                  │
│     isLoading = false,                                          │
│     dog = null,                                                 │
│     isFavourite = false,                                        │
│     errorMessage = null                                         │
│   )                                                              │
│   ↓                                                              │
│   StateFlow created, ready for collectors                       │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ init { loadNewDog() } runs immediately                          │
│                                                                  │
│   reduce { it.copy(isLoading = true, errorMessage = null) }   │
│   ↓                                                              │
│   _state.update { block(oldState) }                             │
│   ↓                                                              │
│   NEW STATE EMITTED:                                            │
│   HomeUiState(                                                  │
│     isLoading = true,  ← CHANGED                                │
│     dog = null,                                                 │
│     isFavourite = false,                                        │
│     errorMessage = null                                         │
│   )                                                              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. FULL STATE LIFECYCLE: USER TAP → UI RECOMPOSE

```
┌─────────────────────────────────────────────────────────────────┐
│ USER TAPS "NEW DOG" BUTTON                                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ UI (HomeScreenContent) calls:                                   │
│   onNewDog = { viewModel.onIntent(HomeIntent.LoadNewDog) }     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeViewModel.onIntent(HomeIntent.LoadNewDog)                   │
│   ↓                                                              │
│   when (intent) {                                               │
│     HomeIntent.LoadNewDog → loadNewDog()  ← executes            │
│   }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ loadNewDog() runs                                               │
│                                                                  │
│   reduce { it.copy(isLoading = true, errorMessage = null) }   │
│   ↓                                                              │
│   STATE UPDATED #1:                                             │
│   HOME_UISTATE_1: isLoading=true, dog=null                     │
│   ↓                                                              │
│   _state.update() → emit to all collectors                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeScreen COLLECTS state change                                │
│                                                                  │
│   val state by viewModel.state.collectAsStateWithLifecycle()   │
│   ↓                                                              │
│   state = HOME_UISTATE_1  (isLoading=true)                     │
│   ↓                                                              │
│   Recompose triggered!                                          │
│   (Compose: "state changed, redraw screen")                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeScreenContent() recomposes with NEW state                   │
│                                                                  │
│   when {                                                         │
│     state.isLoading && state.dog == null →                      │
│       LoadingState(modifier)  ← SHOWS SPINNER                  │
│   }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
                        (MEANWHILE...)
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ viewModelScope.launch { } ← parallel coroutine                  │
│                                                                  │
│   getRandomDog()  ← suspend: awaits network response            │
│   ↓ (on IO dispatcher)                                          │
│   DogApi.getRandomDog() hits dog.ceo API                        │
│   ↓                                                              │
│   Response received: DogImageDto("url", "success")              │
│   ↓                                                              │
│   .toDog()  ← mapper: DTO → Dog                                 │
│   ↓                                                              │
│   Result.success(Dog(...))  ← wrapped in Result                 │
│   ↓                                                              │
│   .onSuccess { dog → ...                                        │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Success handler runs:                                            │
│                                                                  │
│   reduce { it.copy(isLoading = false, dog = dog) }             │
│   ↓                                                              │
│   STATE UPDATED #2:                                             │
│   HOME_UISTATE_2:                                               │
│     isLoading=false,                                            │
│     dog=Dog("url", "Pug"),                                      │
│     isFavourite=false,                                          │
│     errorMessage=null                                           │
│   ↓                                                              │
│   _state.update() → emit to all collectors                      │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeScreen COLLECTS state change #2                             │
│                                                                  │
│   state = HOME_UISTATE_2  (isLoading=false, dog=Pug)           │
│   ↓                                                              │
│   Recompose triggered!                                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeScreenContent() recomposes with NEW state                   │
│                                                                  │
│   when {                                                         │
│     state.isLoading && state.dog == null → NO (skip)           │
│     state.errorMessage != null && state.dog == null → NO       │
│     else →                                                       │
│       HomeContent(state) ← DOG LOADED, show image+buttons      │
│       ↓                                                          │
│       DogImage(url="...", Coil loads and caches image)         │
│       Text("Pug")                                               │
│       IconButton(heart icon: outline, not favourite)            │
│       DogButton("New dog")                                      │
│   }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Meanwhile: observeFavouriteFor(url) launches                    │
│                                                                  │
│   observeIsFavourite(url)  ← Flow<Boolean> from Room           │
│   ↓                                                              │
│   Room queries: "is this URL in favourites table?"              │
│   ↓ returns: false (not favourited yet)                         │
│   ↓                                                              │
│   .collectLatest { fav →                                        │
│     reduce { it.copy(isFavourite = fav) }  ← fav=false         │
│   }                                                              │
│   ↓                                                              │
│   STATE UPDATED #3:                                             │
│   HOME_UISTATE_3: isFavourite=false                             │
│   (heart stays outline because not favourited)                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. ERROR PATH: NETWORK FAILURE

```
┌─────────────────────────────────────────────────────────────────┐
│ User taps "New dog" while OFFLINE                               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ loadNewDog() → reduce { isLoading = true }                      │
│   ↓                                                              │
│   STATE: isLoading=true, dog=null                               │
│   ↓                                                              │
│   UI shows spinner                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ viewModelScope.launch { getRandomDog() }                        │
│   ↓                                                              │
│   DogApi.getRandomDog()                                         │
│   ↓                                                              │
│   THROWS: IOException("Network offline")                        │
│   ↓                                                              │
│   runCatching { } CATCHES exception                             │
│   ↓                                                              │
│   Result.failure(IOException(...))  ← error wrapped as value   │
│   ↓                                                              │
│   .onFailure { error →                                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Failure handler runs:                                            │
│                                                                  │
│   reduce {                                                       │
│     it.copy(                                                     │
│       isLoading = false,                                        │
│       errorMessage = "Couldn't fetch a dog..."  ← error!       │
│     )                                                            │
│   }                                                              │
│   ↓                                                              │
│   STATE UPDATED (ERROR):                                        │
│   HOME_UISTATE_ERROR:                                           │
│     isLoading=false,                                            │
│     dog=null,                                                   │
│     errorMessage="Couldn't fetch..."                            │
│   ↓                                                              │
│   sendEffect(ShowMessage("Couldn't fetch..."))  ← snackbar     │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeScreen collects state change                                │
│   ↓                                                              │
│   state = HOME_UISTATE_ERROR                                    │
│   ↓                                                              │
│   Recompose!                                                    │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeScreenContent() recomposes:                                 │
│                                                                  │
│   when {                                                         │
│     state.errorMessage != null && state.dog == null →           │
│       ErrorState(                                                │
│         message="Couldn't fetch...",                            │
│         onRetry = { onIntent(HomeIntent.Retry) }  ← button     │
│       )                                                          │
│       ↓                                                          │
│       Shows error message + "Retry" button                      │
│   }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeScreen collects effect                                      │
│   ↓                                                              │
│   effect = HomeEffect.ShowMessage("Couldn't fetch...")          │
│   ↓                                                              │
│   onShowMessage(effect.text)  ← callback to parent              │
│   ↓                                                              │
│   NavGraph { snackbarHostState.showSnackbar(text) }             │
│   ↓                                                              │
│   Snackbar appears at bottom of screen (transient)              │
└─────────────────────────────────────────────────────────────────┘
```

---

## 4. REACTIVE UPDATES: FAVOURITE TOGGLE

```
┌─────────────────────────────────────────────────────────────────┐
│ User taps heart icon ♥ (to favourite current dog)               │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeScreenContent calls:                                        │
│   onToggleFavourite = { onIntent(HomeIntent.ToggleFavourite) }│
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ HomeViewModel.onIntent(HomeIntent.ToggleFavourite)              │
│   ↓                                                              │
│   when (intent) {                                               │
│     HomeIntent.ToggleFavourite → toggleCurrent()  ← runs       │
│   }                                                              │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ toggleCurrent() runs                                             │
│                                                                  │
│   val dog = currentState.dog  ← read current state              │
│   viewModelScope.launch {                                       │
│     toggleFavourite(dog)  ← suspend: Room write                 │
│   }                                                              │
│   ↓                                                              │
│   Repository.toggleFavourite(dog)                               │
│   ↓                                                              │
│   if (dao.isFavourite(url)) → YES                               │
│     dao.deleteByUrl(url)  ← REMOVE from DB                     │
│   else                                                           │
│     dao.insert(toEntity)  ← ADD to DB                           │
│   ↓                                                              │
│   Room detects change!                                          │
└─────────────────────────────────────────────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────────────┐
│ Room's reactive queries emit new values                          │
│                                                                  │
│ ┌──────────────────────┐  ┌──────────────────────┐              │
│ │ observeAll()         │  │ observeIsFavourite() │              │
│ │                      │  │                      │              │
│ │ Flow<List<Entity>>   │  │ Flow<Boolean>        │              │
│ │ ↓ emits new list     │  │ ↓ emits: false→true  │              │
│ │ (dog added to DB)    │  │ or true→false        │              │
│ └──────────────────────┘  └──────────────────────┘              │
│          ↓                           ↓                           │
│  ┌──────────────────┐      ┌──────────────────┐                │
│  │FavouritesViewModel│      │HomeViewModel    │                │
│  │                  │      │                  │                │
│  │.collect { dogs } │      │.collectLatest {} │                │
│  │↓                 │      │↓                 │                │
│  │reduce {...}      │      │reduce {...}      │                │
│  │↓                 │      │↓                 │                │
│  │favourites updated│      │isFavourite=true  │                │
│  └──────────────────┘      └──────────────────┘                │
│          ↓                           ↓                           │
│  ┌──────────────────┐      ┌──────────────────┐                │
│  │StateFlow emits   │      │StateFlow emits   │                │
│  │new list          │      │isFavourite=true  │                │
│  └──────────────────┘      └──────────────────┘                │
│          ↓                           ↓                           │
│  ┌──────────────────┐      ┌──────────────────┐                │
│  │FavouritesScreen  │      │HomeScreen        │                │
│  │collects state    │      │collects state    │                │
│  │↓ Recompose!      │      │↓ Recompose!      │                │
│  │Grid shows dog    │      │Heart filled ♥    │                │
│  └──────────────────┘      └──────────────────┘                │
│          ↓                           ↓                           │
│      GALLERY UPDATED            HEART ICON UPDATED              │
│      (zero manual refresh!)     (reactive!)                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 5. STATE FLOW DIAGRAM: IMMUTABILITY

```
INITIAL STATE:
┌─────────────────────────────────────────────┐
│ HomeUiState(                                │
│   isLoading = false,                        │
│   dog = null,                               │
│   isFavourite = false,                      │
│   errorMessage = null                       │
│ )                                           │
└─────────────────────────────────────────────┘
    (immutable object)


USER ACTION: TAP "NEW DOG"
┌─────────────────────────────────────────────┐
│ reduce { it.copy(isLoading = true) }        │
│                                             │
│ it = old state (unchanged)                  │
│ ↓                                           │
│ it.copy(                                    │
│   isLoading = true,  ← CHANGE               │
│   dog = null,        ← unchanged            │
│   isFavourite = false,  ← unchanged         │
│   errorMessage = null   ← unchanged         │
│ )                                           │
│ ↓ returns NEW object (not modified old)    │
└─────────────────────────────────────────────┘
    (new immutable object)


STATE #1:
┌─────────────────────────────────────────────┐
│ HomeUiState(                                │
│   isLoading = true,  ← CHANGED              │
│   dog = null,                               │
│   isFavourite = false,                      │
│   errorMessage = null                       │
│ )                                           │
└─────────────────────────────────────────────┘
    (new object, old unchanged)
    ↓ emit to all collectors
    ↓ UI recomposes


NETWORK RESPONSE RECEIVED:
┌─────────────────────────────────────────────┐
│ reduce { it.copy(                           │
│   isLoading = false,                        │
│   dog = Dog(...)                            │
│ ) }                                         │
│                                             │
│ it = state #1 (unchanged)                   │
│ ↓ returns NEW object                        │
└─────────────────────────────────────────────┘


STATE #2:
┌─────────────────────────────────────────────┐
│ HomeUiState(                                │
│   isLoading = false,  ← CHANGED             │
│   dog = Dog(...),     ← CHANGED             │
│   isFavourite = false,                      │
│   errorMessage = null                       │
│ )                                           │
└─────────────────────────────────────────────┘
    (new object, state #1 unchanged)
    ↓ emit to all collectors
    ↓ UI recomposes


KEY POINT:
OLD STATE is never modified
Each reduce { } creates a NEW state object
If code mutates old state directly (BAD!):
  - UI might not recompose (StateFlow didn't emit)
  - Time-travel debugging impossible
  - Race conditions possible

immutable = predictable = testable = safe
```

---

## 6. TIMELINE: COMPLETE USER INTERACTION

```
TIME  EVENT                          STATE CHANGES
────────────────────────────────────────────────────────────────────
T0    App starts                     isLoading=false
      Screen visible                 dog=null
      UI renders empty state         (initial)
                                     
T1    User taps "New dog"            → isLoading=true
      emit STATE #1
      UI recomposes: show spinner
                                     
      (network in progress)
                                     
T2    Network response arrives       → isLoading=false
      (or error occurs)              → dog=Dog(...)
      emit STATE #2
      UI recomposes: show image
                                     
T3    Room query runs                → isFavourite=false
      (is this URL favourited?)      emit STATE #3
      UI recomposes: heart outline
                                     
T4    User taps heart icon ♥         (no state change in HomeVM)
      Room: insert favourite row
      Room's observeAll() emits
                                     
T5    ObserveIsFavourite Flow        → isFavourite=true
      emits: true (now in DB)        emit STATE #4
      UI recomposes: heart FILLED ♥
                                     
T6    FavouritesViewModel collects   favourites=[Dog(...)]
      new list from observeAll()     emit to FavouritesScreen
                                     Gallery recomposes, shows dog
────────────────────────────────────────────────────────────────────
```

---

## 7. KEY RULES: STATE LIFECYCLE

```
┌─────────────────────────────────────────────────────────────────┐
│ RULE 1: State is IMMUTABLE                                      │
├─────────────────────────────────────────────────────────────────┤
│ ✅ DO:    reduce { it.copy(field = newValue) }                 │
│ ❌ DON'T: state.field = newValue   (direct mutation)            │
│                                                                  │
│ WHY: UI recomposes on state emission, not mutation detection    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ RULE 2: reduce() is the ONLY way to change state                │
├─────────────────────────────────────────────────────────────────┤
│ ✅ DO:    reduce { it.copy(...) }                              │
│ ❌ DON'T: _state.value = newState   (direct assignment)         │
│                                                                  │
│ WHY: reduce() uses _state.update() which emits + notifies       │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ RULE 3: Emissions trigger UI recompose                          │
├─────────────────────────────────────────────────────────────────┤
│ OLD STATE:  HomeUiState(isLoading=false, dog=null)             │
│    ↓                                                             │
│ reduce { it.copy(isLoading=true) }  ← NEW object created       │
│    ↓                                                             │
│ NEW STATE: HomeUiState(isLoading=true, dog=null)               │
│    ↓                                                             │
│ _state.update() EMITS NEW STATE to StateFlow                    │
│    ↓                                                             │
│ state.collectAsStateWithLifecycle() DETECTS change              │
│    ↓                                                             │
│ Compose: "state changed, redraw"                                │
│    ↓                                                             │
│ HomeScreenContent() RECOMPOSES with new state                   │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│ RULE 4: State is source of truth for UI                         │
├─────────────────────────────────────────────────────────────────┤
│ UI should NEVER maintain its own state (no var/mutableState)    │
│ UI should ALWAYS render based on state received from ViewModel  │
│                                                                  │
│ ✅ DO:    val state by viewModel.state.collect...              │
│          when { state.isLoading → ... }                        │
│                                                                  │
│ ❌ DON'T: var isLoading by remember { mutableStateOf(...) }    │
│          (breaks single source of truth)                       │
└─────────────────────────────────────────────────────────────────┘
```

---

## Summary: State Lifecycle Flow

```
┌──────────────────────────────────────────────────────────────┐
│                    STATE LIFECYCLE                           │
└──────────────────────────────────────────────────────────────┘

1. INIT: MviViewModel created with initialState
   ↓
2. USER ACTION: Tap → onIntent(Intent)
   ↓
3. HANDLE: onIntent dispatches to private function
   ↓
4. REDUCE: reduce { it.copy(...) } → new state created
   ↓
5. EMIT: _state.update() emits new state to StateFlow
   ↓
6. COLLECT: UI's collectAsStateWithLifecycle() detects change
   ↓
7. RECOMPOSE: Compose redraws screen with new state
   ↓
8. RENDER: HomeScreenContent() uses new state for branches
   ↓
9. (LOOP BACK TO STEP 2 WHEN USER TAPS AGAIN)

All changes are predictable, testable, and observable.
State is never lost or corrupted.
Time-travel debugging possible (every state captured).
```

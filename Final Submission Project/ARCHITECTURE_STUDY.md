# Premium Audiobook — Architecture Study Guide

> Interview-prep reference for the **Trifork Android mobile-developer** role.
> Goal: be able to *explain and defend* every architectural choice in this app, trace any
> user action end-to-end, and proactively name the weak spots before an interviewer finds them.

---

## Table of Contents

1. [The 30-Second Pitch](#1-the-30-second-pitch)
2. [Tech Stack](#2-tech-stack)
3. [The Layered Architecture](#3-the-layered-architecture)
4. [Layer-by-Layer Walkthrough](#4-layer-by-layer-walkthrough)
5. [The Eight Key Decisions (and Why)](#5-the-eight-key-decisions-and-why)
6. [Kotlin & Coroutines Idioms Used](#6-kotlin--coroutines-idioms-used)
7. [End-to-End Traces](#7-end-to-end-traces)
8. [Known Weak Spots (Be Ready)](#8-known-weak-spots-be-ready)
9. [Interview Q&A](#9-interview-qa)
10. [Glossary](#10-glossary)
11. [Pre-Interview Checklist](#11-pre-interview-checklist)

---

## 1. The 30-Second Pitch

> "It's a **single-Activity, MVVM app built with Jetpack Compose**. State flows in one
> direction: **Room + Firestore** are the data layer, **Repositories** expose state as Kotlin
> `Flow`/`StateFlow`, **ViewModels** transform that into UI state, and **Compose** observes it
> reactively. Background playback is decoupled from the UI via a **Media3
> `MediaSessionService`**, and progress uses an **offline-first sync** strategy — local Room
> first, Firestore in the background for cross-device continuity."

That single paragraph covers: architecture pattern, UI toolkit, data-flow direction,
concurrency model, and two genuinely senior touches (media-service decoupling, offline-first).

---

## 2. Tech Stack

| Concern | Choice | Version (from `gradle/libs.versions.toml`) |
|---|---|---|
| Language | Kotlin | 2.0.21 |
| Build | Gradle KTS + AGP | 8.7.3 |
| UI toolkit | Jetpack Compose | BOM 2024.12.01 |
| Design system | Material 3 | — |
| Navigation | Navigation-Compose | 2.8.5 |
| Local DB | Room (KSP) | 2.6.1 |
| Preferences | DataStore (Preferences) | 1.1.1 |
| Media playback | Media3 / ExoPlayer | 1.5.1 |
| Auth + cloud | Firebase (Auth, Firestore, Messaging) | BOM 33.7.0 |
| Networking | Retrofit + OkHttp + Gson | 2.11.0 / 4.12.0 |
| Images | Coil | 2.7.0 |
| Security | AndroidX Biometric | 1.2.0-alpha05 |
| Async | Kotlin Coroutines + Flow | 1.9.0 |
| Serialization | kotlinx.serialization | 1.7.3 |

**`applicationId`:** `com.audiobook.app` · **Min/Target SDK:** see `app/build.gradle.kts`.

---

## 3. The Layered Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  UI LAYER  — Jetpack Compose                                  │
│  LibraryScreen · PlayerScreen · BookDetailScreen · SignIn/Up  │
│  ProfileScreen · SettingsScreen · LibraryLockedScreen         │
│        ▲ observes StateFlow            │ sends events          │
├────────┼────────────────────────────────┼────────────────────┤
│  PRESENTATION  — ViewModels (no Android framework deps)        │
│  PlayerViewModel · LibraryViewModel                           │
│        ▲ collects Flow / calls suspend fns                    │
├────────┼──────────────────────────────────────────────────────┤
│  DATA LAYER  — Repositories (single source of truth, facades)  │
│  AudiobookRepository · AuthRepository · ProgressSyncRepository │
│  PreferencesRepository · M2BRepository · NotificationRepository│
│        ▲                                                        │
├────────┼──────────────────────────────────────────────────────┤
│  DATA SOURCES                                                  │
│  Room (local) · Firestore (cloud) · DataStore (prefs)         │
│  OpenLibrary API (Retrofit) · ExoPlayer (Media3)              │
└────────────────────────────────────────────────────────────────┘

        ║ PARALLEL SIDE-CHANNEL FOR BACKGROUND PLAYBACK ║
   PlaybackService (MediaSessionService, owns ExoPlayer)
        ▲  Media3 IPC (MediaController ↔ MediaSession)
   AudiobookPlayer  (MediaController wrapper → exposes StateFlow)
        ▲
   PlayerViewModel
```

**Two things to internalise:**

1. **Strict downward dependency.** UI → ViewModel → Repository → data source. Nothing points
   back up. The UI never imports Room/Firestore types directly.
2. **Playback is a side-channel.** Audio lives in its own *service* (a separate Android
   lifecycle from the UI), bridged back into the normal stack by `AudiobookPlayer`.

---

## 4. Layer-by-Layer Walkthrough

### 4.1 Application & DI bootstrap

**`AudiobookApplication.kt`** — the app entry point.
- Creates the manual DI container: `container = AppContainer(this)`.
- Registers four notification channels (reminders / milestones / streaks / general).
- Kicks off a background Firestore sync on startup via an app-scoped
  `CoroutineScope(SupervisorJob() + Dispatchers.IO)`.
- Exposes an **extension property** so any `Context` can reach the container:
  ```kotlin
  val Context.appContainer: AppContainer
      get() = (applicationContext as AudiobookApplication).container
  ```

**`AppContainer.kt`** — Manual Dependency Injection container.
- Holds app-scoped singletons created with `by lazy` (DB, DAOs, repositories, player, parsers).
- Wires dependencies by constructor injection, e.g. `AudiobookRepository` receives the
  `Context`, `PreferencesRepository`, both DAOs, and the metadata repository.
- The file's own header comments document the **trade-off vs Hilt/Koin** — use that verbatim
  in the interview (see Decision #3).

### 4.2 UI layer (Compose)

- **Single Activity:** `MainActivity` extends `FragmentActivity` (needed for `BiometricPrompt`).
- `enableEdgeToEdge()`, then `setContent { PremiumAudiobookAppTheme { … } }`.
- Two gates before the app proper:
  1. **Permissions gate** — `READ_MEDIA_AUDIO` (API 33+) / `READ_EXTERNAL_STORAGE` (older) +
     `POST_NOTIFICATIONS`. Rendered by `PermissionRequestScreen` using
     `rememberLauncherForActivityResult(RequestMultiplePermissions())`.
  2. **Biometric gate** — if enabled in prefs, the start destination is `LibraryLocked`;
     unlocking uses `BiometricPrompt` with a graceful fallback chain:
     strong biometric → device credential (PIN/pattern) → deny if no secure lock screen.

### 4.3 Navigation

**`navigation/Navigation.kt`**
- Routes are a **`sealed class Screen`** with `route` strings and `createRoute(bookId)` helpers
  for parameterised routes (`book_detail/{bookId}`, `player/{bookId}`).
- A single `NavHost`; start destination depends on `isAuthenticated`.
- Custom helper `navigateTopLevel()` does the standard bottom-nav pattern:
  `popUpTo(startDestination){ saveState = true }` + `launchSingleTop` + `restoreState`.
- Screens receive **lambdas** (`onBookClick`, `onPlayClick`, …) rather than the NavController —
  keeps screens navigation-agnostic and previewable.

### 4.4 ViewModels

**`PlayerViewModel.kt`** — the busiest class; study this one hard.
- Exposes immutable `StateFlow`s, backed by private `MutableStateFlow` (`_x` / `x` convention).
- **Re-exposes player state** directly from `AudiobookPlayer` (`isPlaying`, `progress`,
  `currentPosition`, `duration`, `playbackSpeed`, `isConnected`).
- **Derives** UI state with Flow operators:
  - `combine(currentPosition, _currentChapter)` → chapter-relative progress.
  - `currentPosition.map { formatTime(it) }.stateIn(...)` → formatted "M:SS" strings.
- **Persists progress** with a `combine(...).filterNotNull().debounce(500).collect { … }`
  pipeline — writes to Room **and** mirrors to Firestore, and fires a "book completed"
  notification at ≥ 98%.
- **Sleep timer** is a cancellable `Job`: countdown mode (`delay(1000)` loop) or
  end-of-chapter mode (waits for the chapter number to change, then pauses).
- Constructed via a nested **`ViewModelProvider.Factory`** (manual DI feeding the VM).
- `onCleared()` intentionally does **not** disconnect the player — playback survives the screen.

### 4.5 Data layer (Repositories)

**`AudiobookRepository.kt`** — library state + progress + CRUD. Explicitly a **Facade**:
- Delegates folder scanning to `AudiobookScanner` (lazy).
- Delegates `.m2b` import/export to `M2BRepository`.
- Exposes `audiobooksFlow` by `combine`-ing the Room "audiobooks-with-chapters" Flow with the
  progress Flow, mapping entities → domain models. Falls back to an in-memory list if no DAO.
- `addFromUri()` reads a picked file via `ContentResolver`, parses M4B metadata/chapters, saves
  cover art, inserts entity + chapters.

**`ProgressSyncRepository.kt`** — the **offline-first** bridge between Room and Firestore.
- Firestore layout: `users/{uid}/progress/{bookId}`.
- `saveProgress()` writes to Firestore with `SetOptions.merge()`, then `markAsSynced()` locally.
- `syncUnsyncedProgress()` pushes everything flagged unsynced (called at startup / after login).
- `pullCloudProgress()` pulls the latest 50 and updates Room (conflict rule: keep further
  position — see Weak Spot #5).
- `observeProgress()` wraps a Firestore snapshot listener in a **`callbackFlow`** with
  `awaitClose { listener.remove() }`.
- Uses `Result<T>` for error propagation instead of throwing.

**`AuthRepository`** — Firebase Auth (sign in/up, password reset).
**`PreferencesRepository`** — DataStore: playback speed, last-played book, biometric toggle,
folder path, sleep-timer default.

### 4.6 Local persistence (Room)

**`AudiobookDatabase.kt`**
- `@Database(entities = [AudiobookEntity, ChapterEntity, ProgressEntity], version = 4,
  exportSchema = true)`.
- **Singleton** via double-checked locking + `@Volatile INSTANCE`.
- ⚠️ Built with `.fallbackToDestructiveMigration()` — see Weak Spot #1.
- DAOs: `audiobookDao()`, `progressDao()`.
- Entities ↔ domain models via `toEntity()` / `toDomainModel()` mapper extensions (keeps the
  storage schema separate from the UI/domain model — a clean choice).

### 4.7 Media playback (the side-channel)

**`PlaybackService.kt`** — extends **`MediaSessionService`**.
- Owns the **`ExoPlayer`** and a **`MediaSession`**.
- ExoPlayer configured for audiobooks:
  `CONTENT_TYPE_SPEECH`, `handleAudioFocus = true` (auto-pause on calls, duck for
  notifications), `setHandleAudioBecomingNoisy(true)` (pause when headphones unplugged),
  `WAKE_MODE_LOCAL` (CPU stays awake during playback).
- Custom session commands: **Rewind 15s / Forward 30s** surfaced in the media notification.
- `MediaSessionCallback` accepts controllers and registers the custom commands.
- `onTaskRemoved()` saves progress and stops playback when the app is swiped away.
- `onDestroy()` releases the player and session.

**`AudiobookPlayer.kt`** — UI-facing **Adapter/Facade** over `MediaController`.
- Connects asynchronously: `MediaController.Builder(context, sessionToken).buildAsync()`;
  `awaitConnection()` suspends until ready.
- Translates Media3's **callback** API into **`StateFlow`** (`PlayerListener` updates flows).
- Polls position every **500 ms** for a smooth progress bar (see Weak Spot #6).
- Handles **single-file** (one M4B, chapters = time offsets) **and multi-file** (MP3 folder,
  each chapter is its own `MediaItem` in an ExoPlayer playlist) audiobooks — including
  cumulative position math across playlist items.

---

## 5. The Eight Key Decisions (and Why)

> For each: **what**, **why**, and the **trade-off**. Interviewers reward the trade-off.

### Decision 1 — MVVM + unidirectional data flow
**What:** ViewModels expose immutable `StateFlow`; UI reads state, sends events.
**Why:** survives config changes, testable without UI, no business logic in Composables.
**Trade-off:** more ceremony than putting state in the Activity; chose correctness over speed.

### Decision 2 — Repository pattern (single source of truth, as a Facade)
**What:** all data access goes through repositories; `AudiobookRepository` delegates to
`AudiobookScanner` / `M2BRepository`.
**Why:** swappable data sources, one home for caching/sync, thin ViewModels.
**Trade-off:** extra indirection; justified by the multiple data sources (Room + Firestore +
DataStore + network + filesystem).

### Decision 3 — Manual DI instead of Hilt/Koin
**What:** a hand-written `AppContainer` of `by lazy` singletons; ViewModels via `Factory`.
**Why:** demonstrates understanding of DI principles (constructor injection, scoping) with zero
annotation-processing build cost; easy to read/debug.
**Trade-off:** more boilerplate, manual singleton management, no compile-time graph validation.
*"In production I'd use Hilt for compile-time safety and proper scoping."*

### Decision 4 — Media3 `MediaSessionService` for playback
**What:** playback runs in a service, not the ViewModel.
**Why:** audio must continue when backgrounded/screen-off and integrate with lock screen,
Bluetooth, Android Auto, and audio focus. This is the **recommended** approach for media apps.
**Trade-off:** IPC complexity (MediaController connection lifecycle) vs. a simple in-app player.

### Decision 5 — Offline-first sync (Room first, Firestore in background)
**What:** save locally for instant response; sync to cloud asynchronously; pull on launch.
**Why:** app stays fully usable offline; cloud gives cross-device continuity.
**Trade-off:** needs conflict resolution (currently naive — Weak Spot #5).

### Decision 6 — Jetpack Compose + Material 3
**What:** 100% declarative UI, no XML layouts.
**Why:** less boilerplate, reactive recomposition pairs naturally with `StateFlow`, modern.
**Trade-off:** newer APIs, recomposition pitfalls to understand (stability, keys).

### Decision 7 — Custom `.m2b` bookmark format
**What:** import/export of bookmarks/progress via `M2BImporter` / `M2BExporter`.
**Why:** portable, shareable audiobook state independent of the cloud account.
**Trade-off:** a bespoke format to maintain.

### Decision 8 — Single-Activity navigation with a sealed route graph
**What:** one Activity, `NavHost`, `sealed class Screen`, lambda callbacks to screens.
**Why:** type-safe routes, screens decoupled from navigation, easy deep-linking later.
**Trade-off:** all back-stack logic centralised in `Navigation.kt` (can grow large).

---

## 6. Kotlin & Coroutines Idioms Used

| Idiom | Where | Why it matters |
|---|---|---|
| `sealed class` for routes | `Screen` in `Navigation.kt` | Exhaustive, type-safe destinations |
| `by lazy` singletons | `AppContainer`, `AudiobookDatabase` | Deferred, thread-safe init |
| `@Volatile` + double-checked locking | `AudiobookDatabase.getInstance` | Safe singleton under concurrency |
| Extension property | `Context.appContainer` | Ergonomic DI access |
| `data class` + `.copy()` | `Chapter`, `Audiobook`, state | Immutable state updates |
| `StateFlow` / `MutableStateFlow` (`_x`/`x`) | every ViewModel | Reactive, lifecycle-safe state |
| `combine` | `PlayerViewModel`, `AudiobookRepository` | Derive state from multiple flows |
| `debounce(500)` | progress-save pipeline | Throttle DB/cloud writes |
| `stateIn(WhileSubscribed(5000))` | formatted-time flows | Stop work when UI not observing |
| `callbackFlow` + `awaitClose` | `observeProgress` | Wrap Firestore listener as Flow |
| `withContext(Dispatchers.IO)` | repositories | Move disk/network off main thread |
| `viewModelScope` | ViewModels | Auto-cancel coroutines on clear |
| `SupervisorJob` | app/service scopes | One child failing doesn't kill siblings |
| `Result<T>` | `ProgressSyncRepository` | Explicit error handling, no throwing |
| `Job` cancellation | sleep timer | Cancellable countdown |

---

## 7. End-to-End Traces

### Trace A — "I tap a book and it starts playing from where I left off"
1. `LibraryScreen` invokes `onBookClick(bookId)` → `navController.navigate(player/{bookId})`.
2. `PlayerScreen` reads `bookId` arg → `PlayerViewModel.loadBook(bookId)`.
3. VM calls `audiobookRepository.getAudiobook(bookId)` → Room (`getAudiobookWithChapters`) +
   progress → domain model.
4. If chapters not cached, `chapterParser.parseM4bFile(uri)` extracts them; repo persists.
5. VM calls `audiobookPlayer.setChapters(...)` then `awaitConnection()` (suspends until the
   MediaController binds to `PlaybackService`).
6. `getPlaybackPosition(bookId)` > 0 → `audiobookPlayer.resumeFromPosition(book, pos)`; else
   `playAudiobook(book)` + first-start notification.
7. ExoPlayer (in the service) starts; `PlayerListener` pushes state into `StateFlow`s; Compose
   recomposes.
8. A `combine(position, duration, book, chapter).debounce(500)` pipeline persists progress to
   Room and mirrors a `PlaybackProgress` to Firestore.

### Trace B — "I close the app; audio keeps playing; I reopen it"
1. UI is destroyed but `PlaybackService` (a `MediaSessionService`) keeps ExoPlayer alive with
   a media notification (audio focus + wake lock keep it running).
2. On reopen, `AudiobookPlayer.connect()` rebinds a `MediaController` to the live session and
   `syncPlaybackState()` restores current state — playback never stopped.
3. If the user **swipes the app away**, `onTaskRemoved()` saves progress and stops the service.

### Trace C — "I listened on another device"
1. On launch, `AudiobookApplication` runs `syncUnsyncedProgress()` then `pullCloudProgress()`.
2. Cloud docs map to `PlaybackProgress`; Room is updated where cloud shows further progress.
3. `audiobooksFlow` (combining Room audiobooks + progress) emits → Library shows updated
   "continue" positions.

---

## 8. Known Weak Spots (Be Ready)

> Raising these *first* signals seniority. Each has a one-line "production fix."

1. **Destructive Room migration.** `fallbackToDestructiveMigration()` (`AudiobookDatabase.kt`)
   wipes user data on any schema change — despite exporting schema JSONs for v1–v4.
   **Fix:** write real `Migration` objects. *Must-do before publishing to Play.*

2. **`runBlocking` on the main thread.** `MainActivity.onCreate` blocks on a DataStore read
   for the biometric flag. **Fix:** read it in a ViewModel / show a loading state.

3. **Two sources of truth** in `AudiobookRepository` — in-memory `_audiobooks` *and* the Room
   `audiobooksFlow` can drift. **Fix:** make Room authoritative; drop the in-memory list.

4. **Service-locator at the UI layer.** Composables pull `context.appContainer.xRepository`
   directly (`Navigation.kt`) — hidden dependencies, harder to test. ViewModels do it right
   (constructor injection). **Fix:** inject via ViewModels / Hilt.

5. **Naive sync conflict resolution.** `pullCloudProgress` keeps the *further* position; re-
   listening to an earlier part could be overwritten by stale cloud data. **Fix:** last-write-
   wins by the `lastUpdated` timestamp (already stored).

6. **500 ms position polling** in `AudiobookPlayer` — pragmatic but not event-driven.
   **Fix:** drive from player events / a lighter update cadence.

7. **`PlayerViewModel` does a lot** — playback, chapter tracking, persistence, sync, sleep
   timer, notifications. Trends toward a "god ViewModel." **Fix:** extract `UseCase`/interactor
   classes if it grows.

---

## 9. Interview Q&A

**Q: Why MVVM and not MVI?**
A: MVVM fit the scope — ViewModels expose `StateFlow` and the UI is already effectively
unidirectional. MVI adds a single immutable state + intent reducer, which shines when state
gets complex; here per-field `StateFlow`s were simpler. I understand the trade-off and could
migrate the Player screen to a single `UiState` if it grew.

**Q: How does Compose know to re-draw when data changes?**
A: Composables collect `StateFlow` via `collectAsStateWithLifecycle()`. Reading a `State`
inside a composable registers it as a *read*; when the value changes, Compose schedules
**recomposition** of just the affected scopes. State flows down, events flow up.

**Q: Why run playback in a service?**
A: Audio must outlive the UI (background, screen off) and integrate with the system — lock
screen, Bluetooth, Android Auto, audio focus. `MediaSessionService` is the recommended pattern;
the UI talks to it through a `MediaController`, so the player has its own lifecycle.

**Q: What's audio focus and how do you handle it?**
A: The system arbitrates who plays audio. I set `handleAudioFocus = true` on ExoPlayer so it
auto-pauses on calls and ducks for notifications, plus `handleAudioBecomingNoisy` to pause when
headphones are unplugged.

**Q: How do you keep the UI thread unblocked?**
A: Coroutines + Flow. Repo work runs under `withContext(Dispatchers.IO)`; ViewModels use
`viewModelScope`; progress saves are `debounce`d; derived flows use
`stateIn(WhileSubscribed)`. (Caveat: there's one `runBlocking` in `MainActivity` I'd remove.)

**Q: How would you test `PlayerViewModel`?**
A: It depends only on interfaces I can fake (`AudiobookPlayer`, repositories). I'd inject fakes
via the constructor, use `runTest` + a `TestDispatcher`, drive the fake player's `StateFlow`s,
and assert derived flows (chapter progress, formatted time) and that progress is persisted.

**Q: Single-file vs multi-file audiobooks?**
A: Single-file M4B = one `MediaItem`, chapters are time offsets. MP3-folder = a playlist of
`MediaItem`s (one per chapter); ExoPlayer handles playlist navigation, and I compute cumulative
position by summing prior chapter durations.

**Q: Why Room over raw SQLite or just Firestore?**
A: Room gives compile-time-checked SQL, `Flow` observability, and clean entity mapping. It's my
offline source of truth; Firestore is the sync layer on top.

**Q: How does your offline-first sync work, and where does it fall short?**
A: Save to Room first (instant, offline-safe), mark unsynced, push to Firestore in the
background, pull on launch. Shortfall: conflict resolution keeps the further position rather
than the most recent — I'd switch to timestamp-based last-write-wins.

**Q: What would you change before shipping to the Play Store?**
A: Replace destructive migration with real migrations, remove the `runBlocking`, collapse the
dual source of truth, add a privacy policy + data-safety form (Firebase collects email/UID),
and write `Migration` tests. (See weak spots above.)

---

## 10. Glossary

- **Recomposition** — Compose re-running composables whose observed state changed.
- **`StateFlow`** — hot, state-holding Flow with a current value; ideal for UI state.
- **`MediaSession` / `MediaController`** — Media3's server/client split: the service hosts the
  session; UIs and system controls connect as controllers.
- **Audio focus** — system mechanism deciding which app's audio plays; enables auto-pause/duck.
- **Offline-first** — write locally first, sync to cloud asynchronously.
- **Facade** — one class presenting a simple API over several subsystems.
- **Service locator** — components pull dependencies from a global registry (vs. having them
  injected) — convenient but hides dependencies.
- **KSP** — Kotlin Symbol Processing; faster annotation processing (used by Room).

---

## 11. Pre-Interview Checklist

- [ ] Can recite the **30-second pitch** cold.
- [ ] Can **redraw the layer diagram** + the parallel playback service from memory.
- [ ] Can narrate **Trace A** (tap → resume playback) end-to-end out loud.
- [ ] Can explain **why a service** for playback in two sentences.
- [ ] Can state the **manual-DI trade-off** and when I'd switch to Hilt.
- [ ] Can name **3 weak spots** and their production fixes unprompted.
- [ ] Have one sentence on **Compose recomposition + StateFlow**.
- [ ] Know my **Room schema** (3 entities, version 4) and the migration caveat.

---

*Generated as interview-prep study material for the Premium Audiobook app. File references point
to `app/src/main/java/com/audiobook/app/…`.*

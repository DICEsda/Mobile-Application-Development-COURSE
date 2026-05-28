# Architecture Decisions

A short log of the non-obvious design decisions in this project and the reasoning behind each. Decisions are written so future me (or a reviewer) can understand *why* a choice was made — not just *what* was built.

Format: short ADRs. Each entry covers context, the decision, alternatives considered, and consequences.

---

## 1. Manual Dependency Injection over Hilt/Koin

**Context.** The app has a small number of long-lived dependencies (database, repositories, player, Firebase clients) wired together at application start.

**Decision.** A hand-written `AppContainer` class holds singletons as `by lazy` properties and is reached via an extension on `Context`.

**Alternatives considered.**
- **Hilt** — compile-time validated graph, standard in production Android.
- **Koin** — runtime DSL, less ceremony than Hilt.

**Why manual.** With one team member and ~7 repositories, a DI framework adds annotation processing overhead and build-time cost without solving a problem that exists yet. Writing the container by hand also forces explicit thinking about lifetimes and dependencies, which is easier to reason about while learning.

**When to revisit.** Move to **Hilt** when any of these become true:
- Multiple feature modules (need scoped components)
- More than one developer (compile-time graph validation pays off)
- More than ~15 injected types (manual wiring becomes noise)

**Consequences.** No compile-time validation; mistakes surface as runtime crashes. Tests can't inject fakes via a framework — they must construct the graph manually.

---

## 2. MVVM with Compose state holders

**Context.** UI needs to survive configuration changes, expose async data, and be testable in isolation from Android framework classes.

**Decision.** Screens are `@Composable` functions. State is owned by `ViewModel` classes (`LibraryViewModel`, `PlayerViewModel`), exposed as `StateFlow`, collected with `collectAsStateWithLifecycle`. Repositories are passed into ViewModels via `viewModelFactory`.

**Alternatives considered.**
- **MVI / strict UDF** — a single immutable state per screen, intents flowing one way. More principled, but more boilerplate for a project this size.
- **State in `@Composable` only** — fine for trivial screens, but loses configuration-change survival.

**Why MVVM.** Standard Android pattern with first-class Jetpack support, low boilerplate, easy to grow into MVI later if state complexity demands it.

**Consequences.** Screen state isn't enforced to be a single object — `PlayerViewModel` exposes several flows. That's fine here but would benefit from consolidation if the screen grew.

---

## 3. Room for local persistence

**Context.** Need to store the audiobook library, chapter metadata, and per-book playback progress. Schema will evolve.

**Decision.** Room database with two DAOs (`AudiobookDao`, `ProgressDao`) and schema export enabled (`schemas/` directory).

**Alternatives considered.**
- **SQLDelight** — type-safe SQL, KMP-friendly.
- **DataStore Proto** — fine for prefs, not for relational data.
- **Raw SQLite** — too much boilerplate.

**Why Room.** Tight Jetpack integration, KSP-driven (no kapt), schema export gives me a paper trail for migrations, `Flow` return types compose naturally with the rest of the app.

**Consequences.** Locked into Android. If this codebase ever went KMP, SQLDelight would be the migration target.

---

## 4. Media3 (ExoPlayer) inside a `MediaSessionService`

**Context.** Audio must play in the background, survive task removal, integrate with the system media controls, and respond to audio focus / becoming-noisy events.

**Decision.** A foreground `PlaybackService` extending `MediaSessionService` hosts an ExoPlayer instance. The UI talks to it via `MediaController` (wrapped by `AudiobookPlayer` for Compose-friendly state).

**Alternatives considered.**
- **`MediaPlayer`** — does not handle the metadata, gapless playback, or chapter seeking I needed.
- **Plain ExoPlayer in the Activity** — dies when the Activity dies. Unacceptable for an audiobook app.

**Why Media3.** It's the supported, modern path for media on Android. `MediaSessionService` gives Android Auto / lock screen / Bluetooth controls for free.

**Consequences.** Lifecycle complexity is non-trivial — must call `startForeground` within 5s of `startService`, must release the player in `onDestroy`, must not leak the controller to Composables.

---

## 5. Offline-first sync with Firestore

**Context.** A user listens on the phone, then later on a tablet. Progress must follow them. Network may be unavailable.

**Decision.** All writes go to **Room first**, then sync to Firestore in the background:
1. `PlayerViewModel` writes progress to `ProgressDao` immediately (UI never waits on the network).
2. `ProgressSyncRepository.syncUnsyncedProgress()` pushes pending changes when authenticated.
3. `ProgressSyncRepository.pullCloudProgress()` pulls remote state on app start and post-login.
4. Conflicts are resolved by `lastUpdated` timestamp (last-write-wins).

**Alternatives considered.**
- **Firestore as primary store** with offline cache — simpler, but ties UI responsiveness to Firestore's local cache behavior and makes offline-only usage harder.
- **CRDT or operational transform** — overkill; conflicts here are rare and benign.

**Why offline-first with timestamp resolution.** Audiobook progress is intrinsically last-write-wins (you can only be at one position at a time). The added value of CRDTs would be invisible to users.

**Consequences.** A device that's been offline for a long time and resumes can lose progress to a more recent write from another device. That matches user expectation for this domain.

---

## 6. Custom `.m2b` bookmark file format

**Context.** Users may want to back up or move their listening progress between installations (or share a position with a friend).

**Decision.** A custom JSON-based format (`M2BFileFormat`, `M2BExporter`, `M2BImporter`) that captures: book identity, chapter list, playback position, optional bookmarks. Import resolves the matching local audiobook via the scanner.

**Alternatives considered.**
- **Use Firestore export only** — requires the cloud account; defeats the use case of moving across accounts.
- **Reuse an existing format** (M3U, CUE) — these describe playlists, not playback state.

**Why custom.** No existing format captures "where I am in this book" portably. Designing a small format was the right scope.

**Consequences.** I own the format forever. Versioning matters from day one — `M2BFileFormat` includes a version field.

---

## 7. Storage Access Framework (SAF) over legacy storage permissions

**Context.** Users keep their `.m4b` files in arbitrary locations (Downloads, SD card, app folders).

**Decision.** Use `ACTION_OPEN_DOCUMENT_TREE` to let the user grant access to a folder. The app reads files via `DocumentFile`. Legacy `READ_EXTERNAL_STORAGE` is declared with `maxSdkVersion="32"` only.

**Alternatives considered.**
- **`READ_MEDIA_AUDIO` only** — works for Android 13+ but doesn't give folder-level access.
- **All-files-access** — policy violation on Play Store without strong justification.

**Why SAF.** Scoped, user-controlled, future-proof, and explicit. The user knows exactly what folder the app can read.

**Consequences.** Slightly more UX friction (the folder picker). Some `DocumentFile` operations are slower than direct file I/O. Acceptable for the use case.

---

## 8. Firebase Auth + Biometric library lock

**Context.** A persistent identity is needed for cloud sync. The library on-device also needs gating against casual access (shared phone, etc.).

**Decision.** Two layers, intentionally separate:
- **Firebase Auth** for identity and authorization to Firestore. Email/password.
- **AndroidX Biometric** as a *local UI gate* — does not authenticate to the server, only unlocks the library screen.

**Alternatives considered.**
- **Biometric tied to Keystore** — would let me encrypt local data with a biometric-bound key. Heavier; not strictly needed for the threat model (the device is the trust boundary).
- **Google Sign-In only** — better UX but ties identity to a Google account, which I wanted to avoid for portability.

**Consequences.** Biometric unlock is convenience, not security; the underlying data on disk is not encrypted. Production hardening would add an encrypted DataStore for any sensitive prefs and consider a biometric-bound Keystore key for high-value data.

---

## 9. Single-Activity + Compose Navigation

**Context.** Multiple screens, deep links potentially needed later, want to share ViewModels and theming across screens.

**Decision.** One `MainActivity`, all screens are Composables routed by `androidx.navigation.compose.NavHost`. Routes are defined in a sealed `Screen` class.

**Alternatives considered.**
- **Multiple Activities** — predates modern Android; loses shared state easily.
- **Voyager or Decompose** — fine libraries, but Compose Navigation is the standard path and good enough here.

**Consequences.** Type-safe navigation arguments are a little awkward (string-based routes). The new type-safe Navigation Compose API would be the next step.

---

## What I'd do differently

Honest list, in priority order:
1. **Tests.** The M2B parser and `ProgressSyncRepository` are pure-logic seams that deserve unit tests. I'd add those before anything else.
2. **Modularize by feature** once the app grows past ~10 screens — `:feature:library`, `:feature:player`, `:core:data`, etc.
3. **Encrypt the local Room database** if any sensitive data lived in it (it doesn't today).
4. **CI** — GitHub Actions running lint + tests + a signed release build on every PR.
5. **Baseline profiles** for startup performance once the app stabilizes.
6. **Switch to Hilt** if a second developer joins or the dependency graph crosses ~15 types.

# Architecture Decisions

A short log of the non-obvious design decisions in this project and the reasoning behind each.
Decisions are written so a reviewer (or future me) can understand *why* a choice was made — not
just *what* was built.

Format: short ADRs. Each entry covers context, the decision, alternatives considered, and
consequences. The case is small on purpose; the value is in the reasoning, not the feature count.

> **Context for the reader:** this is a take-home case (the "Random Dog App"). The brief mandated
> Jetpack Compose, Kotlin, **Google's recommended app architecture**, coroutines, and **Hilt**.
> Several decisions below are framed against a sister project of mine (a local-only audiobook player)
> that made the *opposite* call — the contrast is deliberate, and shows the choices are driven by
> context, not habit.

---

## 1. Hilt for dependency injection

**Context.** The brief requires Hilt. The app has a handful of long-lived dependencies (Retrofit
API, Room database, repository, dispatcher) wired together at startup.

**Decision.** Hilt with `SingletonComponent` modules (`NetworkModule`, `DatabaseModule`,
`DispatcherModule`) and a `@Binds` `RepositoryModule` mapping `DogRepository → DogRepositoryImpl`.
ViewModels are `@HiltViewModel` and resolved with `hiltViewModel()`.

**Alternatives considered.**
- **Manual DI (a hand-written `AppContainer`)** — what my audiobook project does.
- **Koin** — runtime DSL, less ceremony than Hilt but no compile-time validation.

**Why Hilt here — and why the trade-off flips vs. the audiobook project.** In the audiobook
project I argued *against* a DI framework: one developer, ~7 dependencies, and the annotation-
processing build cost wasn't worth it. Two things flip that here. First, the brief mandates Hilt —
aligning with the requirement is the job. Second, Hilt earns its keep precisely on the integration
this app has: it gives **compile-time graph validation** (a missing binding fails `assembleDebug`,
not at runtime — which actually caught an ordering issue during the build), and it's the
production-standard every Android team expects to see. The cost I avoided in the audiobook app
(annotation processing for a tiny graph) is exactly the cost the brief is asking me to pay to
demonstrate the standard tool.

**When manual DI would still win.** A library module with no Android entry points, or a graph so
small that the KSP round-trip dominates the build — then a hand-written container is leaner.

**Consequences.** Slightly slower builds (KSP). The graph is implicit (read the modules to see it);
the [dependency diagram](ARCHITECTURE.md#3-dependency-graph-who-provides-whom--hilt) documents it explicitly.

---

## 2. MVI for the presentation layer

**Context.** The UI needs to survive config changes, model loading/success/error cleanly, and stay
testable. Google's recommended architecture mandates *UDF + a single immutable UI state* but is
unopinionated between MVVM and MVI.

**Decision.** **MVI.** Each screen has a single immutable `UiState`, a sealed `Intent` set (the only
entry point, via `onIntent`), a sealed `Effect` set for one-off events, and a reducer. A reusable
`MviViewModel<S, I, E>` base centralises the state/effect plumbing so both screens are consistent.

**Alternatives considered.**
- **Plain MVVM with event functions** (`onNewDog()`, `onToggle()`) and possibly several state fields —
  what my audiobook project uses.
- **Full MVI with a formal reducer library** (e.g. Orbit, MVIKotlin) — more structure than this app needs.

**Why MVI — and why this diverges from the audiobook project on purpose.** The audiobook project
chose MVVM and explicitly noted "easy to grow into MVI later." This case is where I show that growth.
MVI is **still fully aligned with the brief**: it's a *stricter* implementation of the exact UDF +
single-immutable-state principles Google recommends, not a departure from them. Choosing it here (and
MVVM there) is the point — the pattern should fit the goal, and I can argue both sides.

**Consequences.** More boilerplate than MVVM (a contract file per screen). Accepted deliberately: it's
a showcase, the `MviViewModel` base keeps the boilerplate small, and the structure scales cleanly if
the screens grew. For a truly trivial screen, plain MVVM would be less code.

---

## 3. Room for favourites (not DataStore)

**Context.** Favourites are a growing collection that the gallery must render reactively, and that the
home screen's heart icon must reflect live.

**Decision.** A Room `favourites` table keyed by image URL, with a DAO exposing `observeAll()` and
`observeIsFavourite(url)` as `Flow`s. Room is the **single source of truth** for favourites.

**Alternatives considered.**
- **Preferences DataStore** — store a serialized set/JSON. Lighter, but it's a key-value store, not
  meant for a queryable, growing collection; "is this URL a favourite?" becomes a manual scan.
- **Proto DataStore** — typed, but heavier setup for what is fundamentally a list of rows.

**Why Room.** Relational data wants a relational store. Room's `Flow` reads compose directly with the
rest of the reactive stack — toggling a favourite writes one row and the gallery updates with **zero
manual refresh**. Schema export (`app/schemas/`) gives a migration paper trail from day one.

**Consequences.** A KSP annotation processor and a schema to migrate if the shape changes. Worth it for
the reactive, queryable single source of truth.

---

## 4. A domain layer of use cases

**Context.** Google's recommended architecture treats the domain layer as **optional**. This app's logic
is thin (fetch, toggle, observe).

**Decision.** Include it: four single-responsibility use cases between the ViewModels and the repository.

**Alternatives considered.**
- **ViewModels call the repository directly** — fewer files; defensible for an app this small.
- **One "interactor" class** grouping all actions — fewer files, but muddier responsibilities.

**Why include it (honest both ways).** The counter-argument is real: for four one-line delegations this
is arguably more structure than the feature strictly needs, and a reviewer might call it ceremony. I
included it deliberately because (a) the brief asks to demonstrate Google's *full* recommended
architecture, (b) it keeps ViewModels free of repository orchestration, and (c) the use cases are the
cleanest possible unit-test seam. I'd happily defend dropping it on a project where the domain logic
genuinely never grows.

**Consequences.** Four extra small classes. If logic stayed this thin forever, collapsing them into the
ViewModels would be a reasonable simplification.

---

## 5. Google's recommended architecture vs. strict Clean Architecture

**Context.** "Clean Architecture" and Google's recommended architecture overlap heavily (layers, UDF,
use cases, a repository behind an interface), but diverge in two specific places. The brief says
*Google's recommended app architecture*, so where they conflict, the brief wins.

**Decision.** Follow Google in the two divergent spots:
- The `DogRepository` **interface lives in the data layer** (Google treats the repository as the data
  layer's public API), **not** in the domain layer as strict Clean Architecture's dependency-inversion
  would put it.
- **Single module, package-by-layer** (`ui/ domain/ data/ di/`), **not** a pure-Kotlin `:domain` module
  that would compiler-enforce the dependency rule.

**Alternatives considered.**
- **Strict Clean Architecture** — repository interface in domain, a framework-free `:domain` module so
  the dependency rule is enforced by the compiler. Canonical, but multi-module is over-engineering for a
  two-screen app and isn't what the brief asked for.

**Why align with the brief.** Picking the requested architecture over a "purer" one I happen to like is
the senior move. The Clean ideas that *don't* conflict (clear layers, depending on a repository
abstraction, domain models separate from DTO/Entity) are all kept.

**Consequences.** The dependency rule is convention here, not compiler-enforced. The note in this entry
is the honest "I know the difference and chose deliberately" — and the multi-module split is a documented
"when I'd revisit" (see #9), not a gap.

---

## 6. Retrofit + kotlinx.serialization

**Context.** One JSON GET endpoint (`dog.ceo`).

**Decision.** Retrofit with the kotlinx.serialization converter; `DogImageDto` is `@Serializable`.

**Alternatives considered.**
- **Ktor client** — great for KMP/coroutine-first stacks, but Retrofit is the Android default and lighter
  to reach for here.
- **Moshi / Gson converter** — fine, but kotlinx.serialization is the Kotlin-first, reflection-free choice
  and pairs cleanly with the Kotlin plugin already in the build.

**Why.** Retrofit declares the API as an interface (clean, testable); kotlinx.serialization avoids
reflection and needs no extra annotations beyond `@Serializable`.

**Consequences.** Locked to Retrofit's model; trivially swappable behind the repository if ever needed.

---

## 7. `Result`-based error handling (errors as state)

**Context.** The network call can fail (offline, timeout, bad payload). The UI must degrade gracefully.

**Decision.** `DogRepositoryImpl.getRandomDog()` wraps the call in `runCatching` and returns `Result<Dog>`.
The ViewModel reduces a failure into `HomeUiState.errorMessage` and shows an `ErrorState` with a **Retry**
intent. Exceptions never reach Compose.

**Alternatives considered.**
- **Throw and catch in the ViewModel** — works, but scatters try/catch and makes "error" an exceptional
  path rather than a normal state.
- **A custom sealed `Resource`/`UiError` type** — richer error taxonomy; overkill for one endpoint with one
  user-facing message.

**Why `Result`.** It models "this can fail" as a value at the layer boundary, which maps directly onto the
single immutable UI state. One generic, friendly message is enough for this app.

**Consequences.** Error granularity is coarse (one message). A real app would map exception types to specific
messages — a small, isolated change in one place.

---

## 8. Coil for image loading

**Context.** Every screen renders a remote dog image (single image on Home, a grid on Favourites).

**Decision.** Coil's `AsyncImage`.

**Alternatives considered.**
- **Glide / Picasso** — mature, but View-first; needs an accompanist/interop shim for Compose.
- **Hand-rolled loading** — pointless; caching and lifecycle are solved problems.

**Why Coil.** Compose-native, coroutine-based, memory/disk caching out of the box, minimal API.

**Consequences.** One more dependency; standard and well-maintained.

---

## 9. Single module, package-by-layer

**Context.** Two screens, one API, one table.

**Decision.** A single `:app` module organised by layer (`ui/ domain/ data/ di/`), matching how Google's
recommended-architecture guide presents an app.

**Alternatives considered.**
- **Package-by-feature** (`feature/home`, `feature/favourites`, `core/`) — better at scale, but scatters
  the layer story a reviewer wants to see in a two-feature app.
- **Multi-module** (`:app :data :domain`) — the canonical Clean Architecture enforcement (see #5); genuine
  over-engineering here.

**Why.** It's the simplest structure that still tells the layered story clearly, and it keeps the two
showcase projects consistent.

**When to revisit.** Package-by-feature past ~5 features; multi-module once a `:domain` purity boundary or
independent build/test of layers becomes worth the Gradle overhead (or to compiler-enforce the dependency rule).

**Consequences.** No compiler-enforced layer boundaries — discipline is by convention and review.

---

## 10. Single-Activity + Compose Navigation

**Context.** Two destinations (Home, Favourites), shared theme, room to grow.

**Decision.** One `MainActivity` hosting a `NavHost`; a `NavigationBar` switches between sealed `Screen`
routes.

**Alternatives considered.**
- **Multiple Activities** — loses shared state/theme easily; not the modern path.
- **A nav library (Voyager/Decompose)** — fine, but Compose Navigation is the standard and sufficient here.

**Consequences.** String-based routes (no typed args needed yet). The type-safe Navigation Compose API would
be the next step if arguments appeared.

---

## What I'd do differently

Honest list, in priority order:
1. **Compose UI tests** — the screens have branching (loading/error/empty/content) that deserves a
   `createComposeRule` test each. The instrumented Room test is in place but needs a device to run.
2. **Screenshot tests** (Paparazzi/Roborazzi) for the Home and Favourites layouts.
3. **Richer error mapping** — turn exception types into specific messages (offline vs. server vs. parse).
4. **Offline image strategy** — favourited images are remote URLs; a real app might cache the bytes so the
   gallery survives going offline.
5. **CI** — GitHub Actions running lint + unit tests + a debug build on every PR.
6. **Type-safe navigation** and, if the app grew, **package-by-feature or a `:domain` module** (see #5, #9).

# Trifork Interview — Diagram Set + Q&A Prep (Design)

**Date:** 2026-06-16
**Goal:** A single interview-prep artifact that ties together two Android apps — the
"GIVEN CASE" Random Dog app and the audiobook Final Submission Project — for a **new-grad
Android developer** interview at Trifork (with a light nod toward their AI/ML role).

## Audience & framing

- **Role:** New-grad Android developer (primary), slight appeal to the GenAI/ML role.
- **Format:** 45+ min deep dive, **diagram-led**, with dog-app code shown as evidence of
  Kotlin fundamentals.
- **The spine:** *"My audiobook app is where I learned Android deeply by doing things by
  hand (manual DI, MVVM); the dog app is me proving I also build to today's idiomatic
  standard (Hilt, MVI, use cases). Depth + currency."*
- **The narrative resolves the apparent contrast** (heavyweight patterns on the small app,
  lighter patterns on the large one) as a deliberate learning-vs-currency story:
  - Audiobook = **learning turned production**. Started as a course project; DI done by hand
    on purpose to understand the mechanics Hilt hides; later refactored toward production
    (AudiobookRepository split, cloud → local migration).
  - Dog = **greenfield, to today's standard**. Clean-slate idiomatic stack.

## Deliverable

- **`TRIFORK/INTERVIEW_DIAGRAMS.md`** — Mermaid source + all prose (curated index + Q&A).
- **`TRIFORK/diagrams/`** — pre-rendered PNGs (dark theme, `-b "#1e1e2e" --scale 3`).
- Style matches the existing `Final Submission Project/docs/diagrams/*.png` bar: dark
  background, high contrast, an "About" card per diagram, colored subgraphs via `classDef`.

### A. New diagrams (the missing pieces — no duplication of existing ~16 diagrams)

1. **Two-app journey map** (design) — the two arcs side by side; states "depth + currency".
2. **Side-by-side architecture contrast** (architectural) — DI · presentation pattern ·
   domain layer · data sources · external actors · scope. *The spine.*
3. **DI contrast: Hilt vs manual `AppContainer`** (architectural) — same composition-root
   concept drawn both ways.
4. **Pattern contrast: MVI loop vs MVVM flow** (design).
5. **Decision cards** (design) — per key decision: decision → why → what I'd do differently.
6. **Skills coverage map** (design, optional) — what each app demonstrates, across both.

### B. Curated index of existing diagrams

Table: presentation order → existing diagram (file path in dog/audiobook `ARCHITECTURE.md`
or `docs/diagrams/*.png`) → what it shows → the one key line to say.

### C. Q&A prep (~12–15, grouped)

- **The contrast:** manual DI vs Hilt; why MVVM not MVI on audiobook; why use-cases in the
  dog app but not the audiobook.
- **Kotlin/Compose fundamentals:** coroutines & structured concurrency; Flow vs StateFlow;
  recomposition; sealed classes & null safety.
- **Audiobook depth:** Media3/`MediaSessionService` (why not `MediaPlayer`); audio focus;
  cloud → local trade-off; "what would you clean up next?"; Room migrations; SAF.
- **AI/ML nod:** grounding; SSE streaming; on-device vs cloud vs self-hosted; `LlmProvider` seam.
- **New-grad/cross-tech:** Big-O/performance (main-thread + memory, not O(n)); KMP;
  Ktor vs Retrofit; "what was hardest / what would you do differently?".

## Validation gate (explicit user request)

Every diagram is rendered to PNG and **visually inspected** for readability — text overflow,
contrast, overlapping nodes — then fixed and re-rendered until clean. PNGs are best-effort
(require `mermaid-cli` via `npx`); the Mermaid source is the guaranteed-usable fallback.

## Out of scope

- Rebuilding the existing per-app diagrams.
- Changing any application code.

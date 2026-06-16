# Trifork Interview — My Notes

Quick reminder of what they actually care about: native Android, **Kotlin + Jetpack Compose**, and that I have *opinions* about how mobile apps should be built. They're less into algorithm puzzles, more into "why did you build it that way." Danish company, builds big national apps (MobilePay, MitID, Min Læge), really into AI right now.

**The one trick that matters:** for everything below, don't just explain what it is — say *"in my audiobook app I did X instead of Y because…"*. That's the whole game. They want to hear me make decisions.

---

## My story — lead with this

This is the hook I open with, because it's true and it ties everything together:

> "I'm really into self-hosting and tinkering. I run a homelab at home and I'm currently building it out into a proper NAS. This app actually grew out of that hobby — it's an audiobook player built for people like me who like to host their own media instead of renting it from a streaming service. Think Plex, but for audiobooks. So it's my own books, on my own storage, and I even self-hosted my own LLM for the AI companion so nothing leaves my network."

Why this works:
- It's **genuine passion**, not a class assignment — that's instantly more compelling.
- It hits exactly what their posting asks for: someone **self-driven** who **actively keeps learning** and tinkers with new tech off their own back.
- It naturally sets up *every* technical topic — the app, the self-hosted AI, data ownership — as things I did because I *wanted* to, not because I was told to.
- It lines up with Trifork's own current direction (self-hosting / sovereign AI / owning your data instead of renting from the US cloud giants).

The whole app is basically "self-hosting, but make it an Android app." Everything else below is just the how.

### Homelab quick facts (fill these in so I have crisp answers if they dig)

They *will* get curious and ask about the homelab — that's a good thing. Have casual one-liners ready so it feels lived-in, not rehearsed:

- **Hardware:** _(what's the box? old PC / mini PC / Raspberry Pi / dedicated server?)_
- **OS / hypervisor:** _(Proxmox? bare Docker? TrueNAS? Unraid?)_
- **What I run on it:** _(Plex/Jellyfin? *arr stack? Pi-hole? etc.)_
- **The NAS plan:** _(what drives, what for — backups? media storage? RAID setup?)_
- **The LLM:** _(Ollama / llama.cpp / LM Studio? which model — Llama, Mistral, Qwen?)_
- **How the app reaches it:** _(local network endpoint? Tailscale/VPN? reverse proxy?)_
- **Honest trade-off line:** "Self-hosting gets me privacy and zero API cost, but I pay for it in hardware, slower inference, and being my own sysadmin." (Saying the downside makes it credible.)

---

## Stuff I built (and how I'd talk about it)

- **Jetpack Compose** — It's the modern way to build Android UI in pure Kotlin. Instead of editing XML and poking at views, I just describe what the screen should look like for a given state, and Compose redraws when the state changes.
- **MVVM** — That's how my app is structured. I've got three ViewModels (Library, Player, BookCompanion) that hold the state and hand it to the screens. Data flows one direction: state down, events up.
- **MVI (if they ask "what else could you use")** — It's basically MVVM tightened up: one single state object per screen and you fire "events" at it. Fits Compose really nicely, just more code to write. Good thing to mention to show I know the options.
- **Coroutines & Flow** — How I keep the app smooth. Heavy stuff like scanning files or hitting the database runs in the background so the UI never freezes. I talk about this in my architecture diagrams.
- **Room** — My local database. I know about migrations (I even export the schema), and I can talk about adding an index when a lookup gets slow.
- **Media3 / ExoPlayer** — This is my favorite one to bring up. It's the actual audio engine — plays in the background, shows controls on the lock screen, handles a phone call interrupting playback. Not many people have built real media playback, so I lead with this.
- **Dependency Injection** — Heads up: I did this **by hand**, NOT with Hilt. My ViewModels get their dependencies through the constructor and I wire them up with a Factory. I'd say I did it manually on purpose because the app's small — and I'd reach for Hilt if it got bigger. (Good answer because it shows I get the *idea*, not just the library.)
- **Biometric login** — Fingerprint/face to unlock the library. Worth noting it only protects local access, it's not the same as logging into a server.
- **File access (SAF)** — How the app gets permission to read the user's audiobook folder. It's the modern Android way since you can't just grab file paths anymore.
- **Networking** — Retrofit + OkHttp, calling the OpenLibrary API to grab book covers and info.
- **The big refactor** — I had one giant `AudiobookRepository` doing too much, and I split it into a scanner + an M2B repository. Perfect example of "one class, one job."
- **The AI book companion** — My LLM feature, and (true to the homelab theme) I **self-hosted my own LLM** for it instead of calling a paid API. Runs on my own box, my own books, nothing leaves my network, no per-call cost. I can talk about on-device vs cloud vs self-hosted trade-offs, why you'd route AI calls through your own backend, and the privacy side. Strong one to bring up because **self-hosting + data sovereignty is exactly what Trifork just launched a product around** — "I self-hosted it because I didn't want my data going to a US cloud" is basically their pitch, coming from genuine hobby interest.
- **Smaller bits**: DataStore (better than SharedPreferences), Coil (loads cover images), Palette (pulls colors from the cover for theming), R8 (shrinks the app for release).

---

## The performance thing (good answer to have ready)

If they ask about Big O or performance: honestly, for a single-user local app the math doesn't matter much — there just aren't enough items for it to bite. The *real* performance stuff on mobile is (1) keeping work off the main thread so the UI doesn't stutter, and (2) memory — cover images and audio buffers eat way more RAM than any data list ever would. That's a more grown-up answer than reciting O(n) charts. I'll still know the Big O basics just in case, but I won't pretend it's the bottleneck.

---

## If they go "have you heard of…" (cross-tech chat)

- **Kotlin Multiplatform** — Share your logic (data, networking, business rules) between Android and iOS, but keep the UI native on each. My app doesn't use it — it's Android-only — but I get the idea.
- **Ktor vs Retrofit** — I used Retrofit, which is Android-only. If I wanted to share networking across iOS too, I'd swap to Ktor.
- **Compose vs MAUI/XAML** — They look similar but work differently under the hood. Compose redraws from state; XAML keeps a live UI tree and pokes at it. Compose is really in the same family as SwiftUI and React.
- **Kotlin vs C#** — Super similar languages honestly. Big differences: Kotlin forces you to handle null (C# makes it optional), Kotlin uses coroutines while C# uses async/await, and C# has more low-level/performance control.

---

## Know-the-company stuff (always lands well)

- Danish company, ~1,200 people, big in Denmark. They build *huge* apps: **MobilePay, MitID, Min Læge** (almost half of Denmark uses it), DSB, the Corona passport.
- They're product + consultancy, growing fast in **health and fintech**.
- Really into **AI** right now — they just launched a "Danish sovereign AI" thing as an alternative to the US cloud giants. They run the GOTO conferences too.
- **My go-to line:** "I'd love to work on apps like Min Læge or MobilePay where the architecture decisions actually affect millions of people" — then tie in my homelab/self-hosting hobby + the self-hosted AI, since their sovereign-AI push is the exact same instinct: own your data instead of renting it from the US cloud.

---

## Videos to watch (in order)

**Architecture (most important):**
- Clean Architecture (Android) — https://www.youtube.com/watch?v=4ZkEeygRECQ
- MVVM vs Clean Architecture — https://www.youtube.com/watch?v=ZL8QZdo4bPY
- Why MVI — https://www.youtube.com/watch?v=tIPxSWx5qpk

**Compose:**
- Recomposition Explained Simply — https://www.youtube.com/watch?v=48a6OE_D3lk
- derivedStateOf — https://www.youtube.com/watch?v=uNaEUcMwBVo

**Coroutines & Flow:**
- Flow Crash Course — https://www.youtube.com/watch?v=3GFOWrlrivA
- Coroutines Best Practices — https://www.youtube.com/watch?v=tVDCpjqQ1Ro

**Big O (quick refresher, don't overdo it):**
- Big O in 3 min — https://www.youtube.com/shorts/TRX-Me68YCw

**Best channel overall:** Philipp Lackner — https://www.youtube.com/@PhilippLackner/videos

---

## If I'm short on time, prep in this order
1. Architecture — why MVVM, what MVI is, my big refactor
2. The Media3 audio player — my standout
3. Compose + coroutines basics
4. The AI feature + data sovereignty (their thing right now)
5. Everything else (Room, networking, biometric)

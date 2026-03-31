# Revised Prompt: Advanced Audiobook System (Media3 Edition)

## Part 1 — The Actual Project (Audiobook Player)

You are an expert Android developer, software architect, and academic evaluator. Your task is to create, analyze, and reason about a mobile application project with the following concept and requirements.

---

## 1. App Concept and Vision

The project is a premium **Audiobook Player** application with native support for the **M4B/M4A** format. The goal is to demonstrate mastery of the Android Media3 ecosystem and modern UI patterns.

---

## 2. Functional Requirements

The app must include at least four main screens:

- **Library Screen:** (Requires BiometricPrompt for access). Displays local/cloud audiobooks.
    
- **Player Screen:** High-end playback controls, speed slider (0.5x - 2.0x), and chapter jump.
    
- **Book Details Screen:** Metadata, chapter list, and "Last Listened" timestamps.
    
- **Profile/Settings Screen:** Theme switching and playback persistence settings.
    

**Advanced Features:**

- **Firebase Integration:** Auth for users and Firestore for cross-device progress syncing.
    
- **Biometric Security:** Optional "Locked Library" feature using `BiometricManager`.
    

---

## 3. Technical Requirements (The "Media3" Stack)

The app must strictly adhere to the following architectural standards:

### A. Media & Playback Engine

- **Jetpack Media3 (ExoPlayer):** Use the `Media3` library exclusively for playback logic.
    
- **MediaSessionService:** Implement a `MediaSessionService` to decouple playback from the UI, ensuring the book keeps playing when the app is backgrounded.
    
- **Foreground Service:** Manage a persistent notification with `MediaStyle` for lock-screen controls.
    
- **M4B Metadata Parsing:** Utilize `MediaMetadataRetriever` or ExoPlayer’s `Metadata` listeners to extract embedded chapter markers and cover art.
    

### B. Language & UI

- **Language:** 100% Kotlin with Coroutines/Flow for state management.
    
- **UI:** Jetpack Compose with a **Lunar/Uber minimalist aesthetic** (deep blacks, subtle grays, high-end typography).
    
- **Architecture:** Clean MVVM or MVI.
    

---

## 4. Risks and Challenges

The evaluation must specifically address:

- **MediaSession Lifecycle:** Handling "Audio Focus" (pausing when a call comes in) and service ducking.
    
- **Persistence:** Solving the "1-second gap" (ensuring the user resumes exactly where they left off in a 20-hour M4B file).
    
- **Resource Management:** Preventing memory leaks within the `MediaSession` when the service is destroyed.
    

---

# Part 2 — Delivery and Academic Submission

---

## 5. Required Deliverables

- **Zipped Project:** Cleaned (no `/build` folders).
    
- **Synopsis & Report:** 5–10 pages detailing the **Media3 architecture choice**.
    

---

## 6. Synopsis & Report Specifics

The "Technologies List" in the documentation must highlight:

1. **Mandatory:** Firebase (Auth/Firestore), External API.
    
2. **Advanced (The Differentiator):** * **Media3 ExoPlayer Implementation** for M4B support.
    
    - **MediaSession API** for system-level integration (Bluetooth, Android Auto).
        
    - **Biometric API** for secure library access.
        

---

## 7. Evaluation Criteria

- **Technical Maturity:** Does the app correctly use a `MediaController` to talk to the `MediaSession`?
    
- **UI/UX:** Does the interface feel "Lunar-inspired" (minimalist) or like a standard template?
    
- **Reliability:** Does the progress sync to Firestore in real-time without lagging the UI thread?
  


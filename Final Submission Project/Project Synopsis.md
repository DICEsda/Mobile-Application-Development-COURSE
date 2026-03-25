# Project Synopsis

## Project Title
**Lunar Audio: Smart Audiobook Player**

**Group Members:**  
- John Nguyen (202209849)  
- Khaled Rami Omar (202307853)  
- Jahye Ali (202309135)  

---

## App Vision
Lunar Audio is a premium mobile audiobook app focused on smooth long-form listening, especially for M4B/M4A files.  

The app solves common audiobook pain points: poor chapter support, weak resume accuracy, and inconsistent playback across sessions/devices. It offers secure user accounts, cloud-synced progress, chapter-aware playback, and a minimalist high-contrast interface designed for distraction-free listening.

---

## Project Scope
This project delivers a complete end-to-end audiobook playback experience for Android users.

**In Scope:**  
- User authentication with Firebase Authentication.  
- Cloud data management using Firestore (progress, bookmarks, and preferences).  
- Core screens: Library, Player, Profile, and Settings.  
- Chapter-aware playback with speed control and resume support.  
- HTTP API integration (manual REST calls) to enrich metadata and artwork when needed.  
- Notification-based playback controls and real-time progress updates.  

**Out of Scope (for this phase):**  
- Multi-platform clients (iOS/Web).  
- Marketplace/store functionality for buying books.  
- Social/community features (reviews, sharing, comments).  
- Full offline-first sync conflict resolution engine beyond baseline handling.

---

## Screen Layouts
The following Figma mockups represent the main screens (excluding sign-in/sign-out):

### 1) Library Screen
Purpose: Displays audiobook collections, quick search, and “continue listening” access.

![Library Screen](../Figma%20pictures/HomeScreen%20-%20Library.png)

### 2) Player Screen
Purpose: Focused playback UI with chapter control, progress tracking, and listening controls.

![Player Screen](../Figma%20pictures/Player%20Page.png)

### 3) Profile Screen
Purpose: Shows user profile and account-level personalization options.

![Profile Screen](../Figma%20pictures/Profile%20Page.png)

### 4) Settings Screen
Purpose: Lets users manage theme, playback defaults, and application behavior.

![Settings Screen](../Figma%20pictures/Settings%20Page.png)

### Optional Interaction Mockups
- Chapter selection popup:

![Chapter Card Popup](../Figma%20pictures/Chapter%20Card%20popup.png)

- Playback speed selector popup:

![Playback Speed Popup](../Figma%20pictures/Playback%20speed%20Card%20popup.png)

- Sleep timer popup:

![Sleep Timer Popup](../Figma%20pictures/Sleep%20Timer.png)

---

## Functional Analysis (Firebase-Centric)

### Playback Page Functionality
- The Player page is driven by an activity-scoped `PlayerViewModel`, so playback state survives screen navigation.
- It supports chapter-aware playback: M4B chapters are parsed (if missing), current chapter is auto-detected from playback position, and the seek bar switches to chapter-relative mode when chapters exist.
- Playback controls include play/pause, skip back **15s**, skip forward **30s**, seek, chapter jump, speed control (**0.5x–2.0x**), and sleep timer modes (5–30 min or end-of-chapter).
- Resume behavior is precise: when a book is opened, the player checks saved playback position and resumes from that timestamp.
- Progress is persisted very frequently (debounced at ~500ms) with position, percent, and chapter data, then reflected in library/continue-reading state.
- Firebase-linked behavior is used for user activity stats and engagement workflows: completion/session events update Firestore-backed user metrics and trigger milestone/streak notifications.
- A dedicated `ProgressSyncRepository` exists for cloud progress sync under `users/{uid}/progress/{bookId}` (save, pull, and observe real-time changes), supporting cross-device continuity.

### Library Search Functionality
- Search is real-time and reactive: typing updates `searchQuery`, and results are recomputed immediately.
- Filtering is case-insensitive and matches both **title** and **author**.
- UX behavior adapts to query state: clear button appears when text exists, section title switches to **Search Results**, and empty-state messaging changes to search-specific guidance.
- The screen uses `filteredAudiobooks` from `LibraryViewModel` (built from the library flow + search query), so search updates automatically as the local library changes.
- Metadata enrichment (covers/details) uses manual HTTP API calls, while search itself is local-library focused for fast interaction.

---

## User Stories
- As a user, I want to sign in securely so that my progress is saved per account.
- As a user, I want to browse my audiobook library so that I can quickly choose what to listen to.
- As a user, I want precise resume playback so that I continue exactly where I stopped.
- As a user, I want chapter navigation so that I can jump to specific parts of a book.
- As a user, I want playback speed control so that I can listen at my preferred pace.
- As a user, I want cloud sync of progress so that I can continue smoothly across app restarts and devices.
- As a user, I want notifications with playback controls so that I can control audio without opening the app.
- As a user, I want clean, distraction-free UI so that long listening sessions stay comfortable.

---

## Technologies
**Mandatory:**  
- **Firebase Authentication:** Email/password sign-in for account-based personalization and user-scoped cloud data.  
- **Firestore Database:** Stores user progress/events in user-specific paths (e.g., `users/{uid}/progress/{bookId}`) and supports cross-device continuity scenarios.  
- **HTTP API (manual calls, not SDK):** Direct REST calls (e.g., Open Library or Google Books endpoints) via Retrofit/OkHttp for metadata and cover fallback when local tags are incomplete.  

**Additional (selected):**  
- **Notifications:** Persistent media controls plus milestone/streak flows connected to Firebase-backed user activity.  
- **Firebase Storage:** Optional cloud storage for user-uploaded covers or assets.  
- **Real-time updates:** Firestore listeners for near real-time sync of progress and bookmarks.  

---

## Main Risks
- **Playback state accuracy** – In long audiobooks, saving/restoring an exact timestamp can drift if state writes are delayed or interrupted.  
- **Sync conflicts across sessions** – Progress updates from multiple devices may overwrite each other without conflict strategy.  
- **External API reliability** – HTTP metadata endpoints may have latency/rate limits, affecting cover/title enrichment performance.  



## Phase 1: Foundation & Infrastructure

- **Project Setup:** Initialize the Android Studio project with **Jetpack Compose**.
    
- **Firebase Integration:** Connect to Firebase Console. Set up **Auth** (Email/Password) and **Firestore**.
    
- **Architecture Scaffolding:** Create your package structure (`data`, `domain`, `ui`, `service`, `di`).
    
- **DI Setup:** Implement **Hilt** or **Koin** for Dependency Injection (highly recommended for Media3 projects).
    

##  Phase 2: The Core Media Engine (The "Heart")

- **MediaSessionService:** Create your `PlaybackService` extending `MediaSessionService`.
    
- **ExoPlayer Configuration:** Initialize the player to handle M4B/AAC.
    
- **Audio Focus:** Implement handling for "Ducking" (lowering volume during a notification) and pausing on phone calls.
    
- **Chapter Extraction:** Write the logic to parse M4B metadata to get chapter timestamps.
    

##  Phase 3: Data & State Persistence

- **Firestore Sync:** Create a "Progress Repository" that saves the user's `currentPosition` and `lastPlayedBookId` to the cloud.
    
- **Local Caching:** Use **Room** or **DataStore** for instant offline resume, then sync to Firestore in the background.
    
- **External API:** Integrate a book API (like Google Books or OpenLibrary) to pull high-res covers if the M4B file lacks them.
    

##  Phase 4: UI/UX "Lunar" Design Implementation

- **Theming:** Define a `LunarColorPalette` (Deep Blacks `#000000`, Slate Grays, and crisp White text).
    
- **Library Screen:** Implement a card-based layout with a search bar.
    
- **Player Screen:** Create the "Now Playing" view with a sleek progress bar and a **Speed Selector** (0.5x – 2.0x).
    
- **Navigation:** Set up `Compose Navigation` with smooth slide-in/out transitions.
    

##  Phase 5: Advanced Features & Security

- **Biometrics:** Wrap the Library Screen in a `BiometricPrompt` check (with a "Remember Me" toggle in Settings).
    
- **Notifications:** Customize the `MediaNotification` to show audiobook-specific actions (e.g., "Skip 30s" instead of "Next Track").
    
- **State Restoration:** Ensure that if Android kills the app for memory, the `MediaSession` can restore the queue perfectly.
    

##  Phase 6: Academic Wrap-up & Delivery

- **Testing:** Verify the app works on different screen sizes and handles "Airplane Mode" gracefully.
    
- **Documentation:** Draft the **Synopsis** and the **10-page Report**.
    
- **Visuals:** Create the Component and Use Case diagrams (UML).
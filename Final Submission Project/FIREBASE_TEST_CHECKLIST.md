# 🧪 Firebase Features - Quick Test Checklist

Use this checklist to verify Firebase Authentication and Firestore Sync are working.

---

## 🔧 **PRE-TESTING SETUP** (One-time)

### Step 1: Firebase Console Setup

- [ ] Go to [Firebase Console](https://console.firebase.google.com/)
- [ ] Create/Select project: **"Smart Audiobook Player"**
- [ ] Add Android app with package: `com.audiobook.app`
- [ ] Download `google-services.json` → Place in `app/` folder
- [ ] Enable **Authentication** → Sign-in method → **Email/Password** ✓
- [ ] Enable **Firestore Database** → Create database → **Test mode** ✓

### Step 2: Build & Install

- [ ] Run: `.\gradlew clean assembleDebug installDebug`
- [ ] Or double-click: `test_firebase.bat`
- [ ] App installed on device/emulator

---

## ✅ **TEST 1: Firebase Authentication**

### A. Sign Up New User

| Step | Action | Expected Result | ✓ |
|------|--------|-----------------|---|
| 1 | Open app → Navigate to Sign Up screen | Sign up form appears | ☐ |
| 2 | Email: `test1@example.com` | Email field accepts input | ☐ |
| 3 | Password: `test123456` | Password field accepts input (hidden) | ☐ |
| 4 | Confirm Password: `test123456` | Confirm field matches | ☐ |
| 5 | Tap "Sign Up" button | Loading spinner shows | ☐ |
| 6 | Wait 2-3 seconds | Navigates to Library screen | ☐ |
| 7 | Open Firebase Console → Authentication → Users | `test1@example.com` appears in list | ☐ |

**✓ PASS** if all steps succeed | **✗ FAIL** if any step fails

---

### B. Sign In Existing User

| Step | Action | Expected Result | ✓ |
|------|--------|-----------------|---|
| 1 | Sign out (if signed in) | Returns to auth screen | ☐ |
| 2 | Navigate to Sign In screen | Sign in form appears | ☐ |
| 3 | Email: `test1@example.com` | Email field accepts input | ☐ |
| 4 | Password: `test123456` | Password field accepts input | ☐ |
| 5 | Tap "Sign In" button | Loading spinner shows | ☐ |
| 6 | Wait 2-3 seconds | Navigates to Library screen | ☐ |
| 7 | Force close app → Reopen | Still signed in (no need to sign in again) | ☐ |

**✓ PASS** if all steps succeed | **✗ FAIL** if any step fails

---

### C. Error Handling

| Test | Action | Expected Error Message | ✓ |
|------|--------|----------------------|---|
| Empty fields | Leave fields blank → Tap Sign In | "Please enter email and password" | ☐ |
| Wrong password | Email: `test1@example.com`, Password: `wrongpass` | "The password is invalid..." | ☐ |
| Non-existent user | Email: `fake@example.com`, Password: `test123` | "There is no user record..." | ☐ |
| Short password (Sign Up) | Password: `test` (< 6 chars) | "Password must be at least 6 characters" | ☐ |
| Password mismatch | Password ≠ Confirm Password | "Passwords do not match" | ☐ |

**✓ PASS** if all errors display correctly | **✗ FAIL** if errors don't show

---

## ✅ **TEST 2: Firestore Progress Sync**

### A. Progress Save to Cloud

| Step | Action | Expected Result | ✓ |
|------|--------|-----------------|---|
| 1 | Ensure signed in as `test1@example.com` | Signed in state confirmed | ☐ |
| 2 | Library → Select any audiobook | Book detail/player opens | ☐ |
| 3 | Tap Play button | Audiobook starts playing | ☐ |
| 4 | Let play for **60 seconds** | Progress bar moves, time updates | ☐ |
| 5 | Open Firebase Console → Firestore Database → Data | - | ☐ |
| 6 | Look for collection: `progress` | Collection exists | ☐ |
| 7 | Open document (format: `{userId}_{bookId}`) | Document contains fields: | ☐ |
|   | - `bookId` (string) | Matches current book | ☐ |
|   | - `bookTitle` (string) | Matches current book title | ☐ |
|   | - `positionMs` (number) | ~60000 (60 seconds in ms) | ☐ |
|   | - `totalDurationMs` (number) | Total book duration | ☐ |
|   | - `lastChapter` (number) | Current chapter number | ☐ |
|   | - `playbackSpeed` (number) | 1.0 (or current speed) | ☐ |
|   | - `timestamp` (timestamp) | Recent timestamp | ☐ |

**✓ PASS** if Firestore document exists with correct data | **✗ FAIL** if missing

---

### B. Cross-Device Sync (Simulated)

| Step | Action | Expected Result | ✓ |
|------|--------|-----------------|---|
| 1 | Device 1: Play book for 2 minutes | Position: ~02:00 | ☐ |
| 2 | Device 1: Pause playback | Progress saved to Firestore | ☐ |
| 3 | Device 1: Note exact position (e.g., 02:15) | Position recorded | ☐ |
| 4 | Device 2: Install app (or uninstall/reinstall Device 1) | Fresh install | ☐ |
| 5 | Device 2: Sign in as SAME user (`test1@example.com`) | Signed in | ☐ |
| 6 | Device 2: Open SAME audiobook | Book opens | ☐ |
| 7 | Device 2: Check progress position | **Should be ~02:15** (synced) | ☐ |
| 8 | Device 2: Tap Play | Resumes from synced position | ☐ |

**✓ PASS** if position syncs across devices | **✗ FAIL** if starts from beginning

---

### C. Real-Time Sync (Advanced)

| Step | Action | Expected Result | ✓ |
|------|--------|-----------------|---|
| 1 | Device 1: Play audiobook | Playing | ☐ |
| 2 | Device 2: Open Firestore Console (keep refreshing) | `positionMs` updates every ~1 second | ☐ |
| 3 | Device 1: Seek to 05:00 | Position jumps | ☐ |
| 4 | Device 2: Firestore Console → Refresh | `positionMs` = ~300000 (5 min in ms) | ☐ |
| 5 | Device 1: Change playback speed to 1.5x | Speed changed | ☐ |
| 6 | Device 2: Firestore Console → Refresh | `playbackSpeed` = 1.5 | ☐ |

**✓ PASS** if Firestore updates in real-time | **✗ FAIL** if stale data

---

## 🐛 **TEST 3: Error Recovery**

### A. Offline Behavior

| Step | Action | Expected Result | ✓ |
|------|--------|-----------------|---|
| 1 | Play audiobook (online) | Playing & syncing | ☐ |
| 2 | Enable Airplane Mode | Internet disconnected | ☐ |
| 3 | Continue playing for 1 minute | Still plays (local playback) | ☐ |
| 4 | Check Logcat: `adb logcat \| grep ProgressSync` | Error logged: "Failed to sync progress" | ☐ |
| 5 | Disable Airplane Mode | Internet restored | ☐ |
| 6 | Continue playing | Sync resumes successfully | ☐ |
| 7 | Check Firestore Console | Latest position updated | ☐ |

**✓ PASS** if app handles offline gracefully | **✗ FAIL** if crashes

---

### B. Auth Persistence

| Step | Action | Expected Result | ✓ |
|------|--------|-----------------|---|
| 1 | Sign in as `test1@example.com` | Signed in | ☐ |
| 2 | Force close app (swipe from recents) | App closed | ☐ |
| 3 | Reopen app | Still signed in (Library shows, not Sign In) | ☐ |
| 4 | Restart device | Device rebooted | ☐ |
| 5 | Open app | Still signed in | ☐ |

**✓ PASS** if auth persists | **✗ FAIL** if requires re-login

---

## 📊 **Test Results Summary**

Fill this out after completing all tests:

| Feature | Status | Notes |
|---------|--------|-------|
| Sign Up | ☐ Pass ☐ Fail | |
| Sign In | ☐ Pass ☐ Fail | |
| Error Messages | ☐ Pass ☐ Fail | |
| Progress Save | ☐ Pass ☐ Fail | |
| Cross-Device Sync | ☐ Pass ☐ Fail | |
| Real-Time Updates | ☐ Pass ☐ Fail | |
| Offline Handling | ☐ Pass ☐ Fail | |
| Auth Persistence | ☐ Pass ☐ Fail | |

---

## 🔍 **Debugging Commands**

If something fails, use these commands:

```bash
# View all app logs
adb logcat | grep "com.audiobook.app"

# View Firebase Auth logs
adb logcat | grep -E "AUTH|SignIn|SignUp|AuthRepository"

# View Firestore Sync logs
adb logcat | grep -E "Firestore|ProgressSync|PlayerViewModel"

# View error logs only
adb logcat *:E | grep "com.audiobook.app"

# Clear logs and start fresh
adb logcat -c
adb logcat | grep -E "firebase|auth|progress"
```

---

## ✅ **All Tests Passed?**

If all tests pass:
- ✓ Firebase Authentication is working
- ✓ Firestore Progress Sync is working
- ✓ Cross-device sync is functional
- ✓ Error handling is robust

**Your app is production-ready for Firebase features! 🎉**

---

## ❌ **Tests Failed?**

See troubleshooting in `FIREBASE_TESTING_GUIDE.md` or check:
1. `google-services.json` is in correct location
2. Firebase services are enabled in Console
3. Internet connection is active
4. Check Logcat for error messages

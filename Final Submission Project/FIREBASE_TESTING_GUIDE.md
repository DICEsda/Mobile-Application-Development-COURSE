# Firebase Testing Guide - Smart Audiobook Player

## 🔥 Firebase Setup & Testing Instructions

This guide explains how to test Firebase Authentication and Firestore sync features in the Smart Audiobook Player app.

---

## Prerequisites

Before testing, you need to set up Firebase for your project:

### 1. Create Firebase Project (If Not Already Done)

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click "Add project" or select existing project
3. Name it: "Smart Audiobook Player" (or your preferred name)
4. Follow the setup wizard

### 2. Add Android App to Firebase

1. In Firebase Console, click "Add app" → Android icon
2. Enter your Android package name: `com.audiobook.app`
3. Download the `google-services.json` file
4. Place it in: `Final Submission Project/app/google-services.json`
5. The build system will automatically use it

### 3. Enable Firebase Services

#### **Enable Authentication:**
1. In Firebase Console → Authentication
2. Click "Get Started"
3. Go to "Sign-in method" tab
4. Enable "Email/Password" provider
5. Save

#### **Enable Firestore Database:**
1. In Firebase Console → Firestore Database
2. Click "Create database"
3. **Start in TEST mode** (for development)
4. Choose location (e.g., us-central1)
5. Click "Enable"

⚠️ **Security Note:** Test mode allows read/write without authentication. For production, you need proper security rules.

#### **Enable Cloud Messaging (Optional for push notifications):**
1. In Firebase Console → Cloud Messaging
2. Should be enabled by default
3. Note down the Server Key if needed

---

## 📝 Testing Firebase Authentication

### **Test 1: Sign Up New User**

1. **Build and run the app** on emulator/device:
   ```bash
   cd "C:\Users\YahyaAli\Desktop\Mobile-Application-Development-COURSE\Final Submission Project"
   .\gradlew installDebug
   ```

2. **Navigate to Sign Up:**
   - If you haven't disabled biometric lock, you'll need to unlock first
   - Look for navigation to "Sign Up" screen (may need to add button in UI)
   - Or modify `Navigation.kt` to start at `Screen.SignUp.route` temporarily

3. **Fill in the form:**
   - Email: `test@example.com`
   - Password: `password123` (min 6 chars)
   - Confirm Password: `password123`
   - Tap "Sign Up"

4. **Expected Behavior:**
   - Loading spinner appears
   - On success: Navigates to Library screen
   - On error: Shows error message below fields

5. **Verify in Firebase Console:**
   - Go to Authentication → Users
   - You should see `test@example.com` listed

### **Test 2: Sign In Existing User**

1. **Navigate to Sign In screen**
2. **Enter credentials:**
   - Email: `test@example.com`
   - Password: `password123`
   - Tap "Sign In"

3. **Expected Behavior:**
   - Loading spinner appears
   - On success: Navigates to Library screen
   - User stays signed in even after app restart

### **Test 3: Error Handling**

Try these to test validation:
- Empty fields → "Please enter email and password"
- Wrong password → "The password is invalid or the user does not have a password."
- Non-existent email → "There is no user record corresponding to this identifier."
- Weak password (< 6 chars) → "Password should be at least 6 characters"

### **Test 4: Sign Out**

1. **Go to Settings screen**
2. **Look for Sign Out button** (if implemented)
3. **Or manually test via code:**
   ```kotlin
   container.authRepository.signOut()
   ```

---

## 📊 Testing Firestore Progress Sync

### **Test 1: Progress Save to Firestore**

1. **Sign in with a user** (must be authenticated for Firestore)
2. **Play an audiobook:**
   - Library → Select book → Play
3. **Let it play for 30+ seconds**
4. **Verify in Firebase Console:**
   - Go to Firestore Database → Data tab
   - Look for collection: `progress` or `playback_progress`
   - Document ID should be: `{userId}_{bookId}`
   - Fields should include:
     - `bookId`: string
     - `bookTitle`: string
     - `positionMs`: number (milliseconds)
     - `totalDurationMs`: number
     - `lastChapter`: number
     - `playbackSpeed`: number (e.g., 1.0)
     - `timestamp`: timestamp

5. **Check Logcat for confirmation:**
   ```bash
   adb logcat | grep -i "progress"
   ```
   Should see: "Saving progress to Firestore" logs

### **Test 2: Cross-Device Sync (Simulated)**

1. **Play audiobook on Device/Emulator 1:**
   - Sign in as `test@example.com`
   - Play book for 2 minutes
   - Note the progress position

2. **Install on Device/Emulator 2:**
   - Install same APK
   - Sign in as SAME user (`test@example.com`)
   - Open the same audiobook
   - **Expected:** Should resume from the 2-minute position

3. **Verify sync timing:**
   - Progress saves every 500ms during playback (see PlayerViewModel line 162)
   - Firestore should reflect the latest position within 1-2 seconds

### **Test 3: Error Handling**

1. **Disable internet connection**
2. **Play audiobook**
3. **Check Logcat:**
   ```bash
   adb logcat | grep -E "PlayerViewModel|ProgressSync"
   ```
   Should see: "Failed to sync progress to Firestore" with error details

4. **Re-enable internet**
5. **Continue playing**
6. **Verify:** Sync resumes successfully

---

## 🛠️ Debugging Tips

### **Check Authentication State**

Add this to your code temporarily:
```kotlin
LaunchedEffect(Unit) {
    container.authRepository.authState.collect { user ->
        Log.d("AUTH_DEBUG", "Current user: ${user?.email ?: "Not signed in"}")
    }
}
```

### **Check Firestore Connection**

```kotlin
val firestore = FirebaseFirestore.getInstance()
firestore.collection("test").add(mapOf("timestamp" to System.currentTimeMillis()))
    .addOnSuccessListener { Log.d("FIRESTORE_DEBUG", "Connected successfully") }
    .addOnFailureListener { e -> Log.e("FIRESTORE_DEBUG", "Connection failed", e) }
```

### **View All Logs**

```bash
# Filter Firebase logs
adb logcat | grep -i firebase

# Filter auth logs
adb logcat | grep -E "AUTH|SignIn|SignUp"

# Filter Firestore logs  
adb logcat | grep -E "Firestore|ProgressSync"

# Filter everything related to your app
adb logcat | grep "com.audiobook.app"
```

### **Common Issues**

| Issue | Solution |
|-------|----------|
| "google-services.json not found" | Download from Firebase Console and place in `app/` folder |
| "Default FirebaseApp not initialized" | Add `google-services` plugin to build.gradle |
| "Permission denied" errors in Firestore | Update Firestore security rules or use test mode |
| Auth not working | Check internet connection, verify Firebase config |
| Progress not syncing | Check user is signed in, verify userId in Firestore |

---

## 📱 Quick Test Scenario

**Complete end-to-end test (5 minutes):**

1. ✅ **Setup Firebase** (if not done):
   - Create project
   - Add Android app
   - Download google-services.json
   - Enable Auth (Email/Password)
   - Enable Firestore (Test mode)

2. ✅ **Build & Install:**
   ```bash
   .\gradlew clean installDebug
   ```

3. ✅ **Test Auth:**
   - Sign up: `user1@test.com` / `test123`
   - Verify in Firebase Console → Authentication

4. ✅ **Test Progress Sync:**
   - Play audiobook for 1 minute
   - Check Firestore Database for progress document
   - Verify fields: position, chapter, speed

5. ✅ **Test Cross-Device:**
   - Note progress position
   - Uninstall and reinstall (or use second device)
   - Sign in with same account
   - Play same book → Should resume from saved position

---

## 🔐 Production Security Rules

For production, replace test mode rules with:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    // Users can only read/write their own progress
    match /progress/{userId}_{bookId} {
      allow read, write: if request.auth != null 
                         && request.auth.uid == userId;
    }
    
    // Users can only read/write their own stats
    match /users/{userId} {
      allow read, write: if request.auth != null 
                         && request.auth.uid == userId;
    }
  }
}
```

Apply in: Firebase Console → Firestore Database → Rules tab

---

## 📞 Need Help?

- **Firebase Docs:** https://firebase.google.com/docs
- **Check Logcat:** `adb logcat | grep -i firebase`
- **Firebase Console:** https://console.firebase.google.com/
- **Stack Overflow:** Tag your questions with `firebase-authentication` and `google-cloud-firestore`

---

**Your app is now ready for Firebase testing! 🚀**

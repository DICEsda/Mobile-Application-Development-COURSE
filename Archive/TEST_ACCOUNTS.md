# Test Accounts for Firebase Testing

Use these pre-configured test accounts for Firebase Authentication testing.

## Quick Test Accounts

### Primary Test Account
```
Email: test1@example.com
Password: test123456
Purpose: Main testing account for auth and sync
```

### Secondary Test Account (for multi-device sync testing)
```
Email: test2@example.com
Password: test123456
Purpose: Test cross-device sync with different user
```

### Error Testing Account (Wrong Password)
```
Email: test1@example.com
Password: wrongpassword
Expected: "The password is invalid..."
```

### Non-Existent Account (For error testing)
```
Email: nouser@example.com
Password: test123456
Expected: "There is no user record..."
```

---

## Creating Test Accounts in Firebase Console

If you prefer to create accounts directly in Firebase Console:

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Select your project
3. Click **Authentication** → **Users** tab
4. Click **"Add user"** button
5. Enter:
   - **Email:** test1@example.com
   - **Password:** test123456
6. Click **"Add user"**

---

## Testing Workflow

### Scenario 1: First-Time User
1. Open app → Navigate to **Sign Up**
2. Use: `test1@example.com` / `test123456`
3. Should create account and navigate to Library

### Scenario 2: Returning User
1. Open app → Navigate to **Sign In**
2. Use: `test1@example.com` / `test123456`
3. Should sign in and navigate to Library

### Scenario 3: Cross-Device Sync
1. **Device 1:** Sign in as `test1@example.com`, play book for 2 min
2. **Device 2:** Sign in as `test1@example.com`, same book
3. Should resume from same position (~2 min)

### Scenario 4: Multiple Users (Different Libraries)
1. **Device 1:** Sign in as `test1@example.com`, play Book A
2. **Device 2:** Sign in as `test2@example.com`, play Book B
3. Each user should have separate progress (not mixed)

---

## Password Requirements

Firebase requires:
- ✓ Minimum **6 characters**
- ✓ Any combination (letters, numbers, symbols)

Examples:
- ✓ `test123` - Valid (6 chars)
- ✓ `password` - Valid (8 chars)
- ✓ `pass123!` - Valid (8 chars with symbol)
- ✗ `test` - Invalid (< 6 chars)
- ✗ `abc12` - Invalid (< 6 chars)

---

## Verifying Test Accounts in Firebase

After signing up via the app, verify in Firebase Console:

1. **Authentication** → **Users** tab
2. You should see:
   ```
   User UID                          Email                      Created
   ───────────────────────────────── ────────────────────────── ─────────────
   abc123def456...                   test1@example.com          Just now
   ```

3. Click on a user to see:
   - User UID
   - Email
   - Created date
   - Sign-in method (Email/Password)
   - Last sign-in time

---

## Firestore Data Structure

After playing audiobooks, check Firestore:

### Collection: `progress`
```
Document: {userId}_{bookId}
Fields:
  - bookId: "book123"
  - bookTitle: "The Creative Act"
  - positionMs: 120000 (2 minutes)
  - totalDurationMs: 3600000 (1 hour)
  - lastChapter: 2
  - playbackSpeed: 1.0
  - timestamp: <Firestore Timestamp>
```

### Collection: `users` (if using notification features)
```
Document: {userId}
Fields:
  - booksCompleted: 5
  - totalListeningMinutes: 240
  - streak: 3
  - lastActiveDate: "2026-03-26"
```

---

## Quick Commands

### Check if user is signed in (via adb):
```bash
adb shell am broadcast -a com.audiobook.app.CHECK_AUTH
```

### View auth state in logs:
```bash
adb logcat | grep "AuthRepository"
```

### Force sign out (for testing):
```bash
# In code, call:
container.authRepository.signOut()
```

---

## Cleanup After Testing

To remove test accounts:

1. **Firebase Console** → **Authentication** → **Users**
2. Click ⋮ (three dots) next to user
3. Select **"Delete account"**
4. Confirm deletion

Or delete all test data in Firestore:
1. **Firestore Database** → **Data**
2. Select collection (e.g., `progress`)
3. Click ⋮ → **"Delete collection"**
4. Confirm

---

**Ready to test Firebase! Use these accounts and follow the checklist.** ✅

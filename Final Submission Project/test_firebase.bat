@echo off
REM Firebase Testing Quick Start Script
REM Run this to check if Firebase is properly configured

echo ============================================
echo Smart Audiobook Player - Firebase Test
echo ============================================
echo.

echo [1/4] Checking for google-services.json...
if exist "app\google-services.json" (
    echo ✓ google-services.json found!
) else (
    echo ✗ google-services.json NOT FOUND!
    echo.
    echo ACTION REQUIRED:
    echo 1. Go to https://console.firebase.google.com/
    echo 2. Select your project or create new one
    echo 3. Add Android app with package: com.audiobook.app
    echo 4. Download google-services.json
    echo 5. Place it in: app\google-services.json
    echo.
    pause
    exit /b 1
)

echo.
echo [2/4] Cleaning previous build...
call gradlew clean >nul 2>&1

echo.
echo [3/4] Building APK...
call gradlew assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo ✗ Build failed! Check errors above.
    pause
    exit /b 1
)

echo.
echo [4/4] Installing on connected device...
call gradlew installDebug
if %ERRORLEVEL% NEQ 0 (
    echo ✗ Installation failed! Is device connected?
    pause
    exit /b 1
)

echo.
echo ============================================
echo ✓ App installed successfully!
echo ============================================
echo.
echo TESTING CHECKLIST:
echo.
echo [ ] 1. Enable Firebase Auth (Email/Password)
echo        https://console.firebase.google.com/ ^> Authentication
echo.
echo [ ] 2. Enable Firestore Database (Test mode)
echo        https://console.firebase.google.com/ ^> Firestore Database
echo.
echo [ ] 3. Test Sign Up:
echo        - Email: test@example.com
echo        - Password: password123
echo.
echo [ ] 4. Test Progress Sync:
echo        - Play audiobook for 1+ minute
echo        - Check Firestore Database for progress document
echo.
echo [ ] 5. View logs:
echo        adb logcat ^| findstr "firebase auth progress"
echo.
echo See FIREBASE_TESTING_GUIDE.md for detailed instructions.
echo.
pause

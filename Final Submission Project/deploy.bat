@echo off
echo ================================
echo Building and Deploying App
echo ================================

echo.
echo [1/4] Building APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo Build failed!
    pause
    exit /b 1
)

echo.
echo [2/4] Uninstalling old version...
"C:\Users\yahya\AppData\Local\Android\Sdk\platform-tools\adb.exe" uninstall com.audiobook.app

echo.
echo [3/4] Installing new version...
"C:\Users\yahya\AppData\Local\Android\Sdk\platform-tools\adb.exe" install "app\build\outputs\apk\debug\app-debug.apk"
if %ERRORLEVEL% NEQ 0 (
    echo Installation failed!
    pause
    exit /b 1
)

echo.
echo [4/4] Launching app...
"C:\Users\yahya\AppData\Local\Android\Sdk\platform-tools\adb.exe" shell am start -n com.audiobook.app/.MainActivity

echo.
echo ================================
echo Deployment Complete!
echo ================================
pause

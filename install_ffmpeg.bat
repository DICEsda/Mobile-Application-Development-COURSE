@echo off
REM FFmpeg Installer for Windows
REM This script downloads FFmpeg and adds it to PATH

echo ========================================
echo FFmpeg Installer for Windows
echo ========================================
echo.

REM Check if running as administrator
net session >nul 2>&1
if %errorLevel% neq 0 (
    echo WARNING: Not running as administrator.
    echo Some features may not work properly.
    echo.
    pause
)

REM Set installation directory
set INSTALL_DIR=C:\ffmpeg
set BIN_DIR=%INSTALL_DIR%\bin
set DOWNLOAD_URL=https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip
set ZIP_FILE=%TEMP%\ffmpeg.zip

echo Installation directory: %INSTALL_DIR%
echo.

REM Check if FFmpeg already exists
if exist "%BIN_DIR%\ffmpeg.exe" (
    echo FFmpeg is already installed at %INSTALL_DIR%
    echo.
    choice /C YN /M "Do you want to reinstall"
    if errorlevel 2 goto :check_path
    if errorlevel 1 goto :download
) else (
    goto :download
)

:download
echo Step 1: Downloading FFmpeg...
echo This may take a few minutes depending on your connection.
echo.

REM Download using PowerShell
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri '%DOWNLOAD_URL%' -OutFile '%ZIP_FILE%'}"

if %errorlevel% neq 0 (
    echo ERROR: Failed to download FFmpeg
    echo Please check your internet connection and try again.
    pause
    exit /b 1
)

echo Download complete!
echo.

:extract
echo Step 2: Extracting FFmpeg...
echo.

REM Remove old installation if exists
if exist "%INSTALL_DIR%" (
    echo Removing old installation...
    rmdir /s /q "%INSTALL_DIR%" 2>nul
)

REM Create installation directory
mkdir "%INSTALL_DIR%" 2>nul

REM Extract using PowerShell
powershell -Command "& {Expand-Archive -Path '%ZIP_FILE%' -DestinationPath '%TEMP%\ffmpeg_temp' -Force}"

if %errorlevel% neq 0 (
    echo ERROR: Failed to extract FFmpeg
    pause
    exit /b 1
)

REM Move files from extracted folder to installation directory
for /d %%i in ("%TEMP%\ffmpeg_temp\ffmpeg-*") do (
    xcopy "%%i\*" "%INSTALL_DIR%\" /E /I /Y >nul
)

REM Clean up
rmdir /s /q "%TEMP%\ffmpeg_temp" 2>nul
del "%ZIP_FILE%" 2>nul

echo Extraction complete!
echo.

:check_path
echo Step 3: Checking PATH...
echo.

REM Check if already in PATH
echo %PATH% | findstr /C:"%BIN_DIR%" >nul
if %errorlevel% equ 0 (
    echo FFmpeg is already in PATH
    goto :verify
)

:add_path
echo Step 4: Adding FFmpeg to PATH...
echo.

REM Add to system PATH using PowerShell
powershell -Command "& {[Environment]::SetEnvironmentVariable('Path', [Environment]::GetEnvironmentVariable('Path', 'Machine') + ';%BIN_DIR%', 'Machine')}"

if %errorlevel% neq 0 (
    echo ERROR: Failed to add FFmpeg to PATH
    echo You may need to run this script as Administrator.
    echo.
    echo Manual steps:
    echo 1. Press Win + X and select "System"
    echo 2. Click "Advanced system settings"
    echo 3. Click "Environment Variables"
    echo 4. Under "System variables", select "Path" and click "Edit"
    echo 5. Click "New" and add: %BIN_DIR%
    echo 6. Click OK on all dialogs
    pause
    goto :verify
)

echo PATH updated successfully!
echo NOTE: You need to restart your command prompt for changes to take effect.
echo.

:verify
echo Step 5: Verifying installation...
echo.

if exist "%BIN_DIR%\ffmpeg.exe" (
    echo [OK] ffmpeg.exe found at %BIN_DIR%\ffmpeg.exe
    
    REM Try to run ffmpeg
    "%BIN_DIR%\ffmpeg.exe" -version >nul 2>&1
    if %errorlevel% equ 0 (
        echo [OK] FFmpeg is working correctly
        echo.
        echo ========================================
        echo Installation Complete!
        echo ========================================
        echo.
        echo FFmpeg has been installed to: %INSTALL_DIR%
        echo FFmpeg binaries are in: %BIN_DIR%
        echo.
        echo IMPORTANT: 
        echo - Close and reopen your command prompt or terminal
        echo - Then test by typing: ffmpeg -version
        echo.
        echo You can now use the M4B creator tool!
        echo.
    ) else (
        echo [WARNING] FFmpeg.exe exists but failed to run
    )
) else (
    echo [ERROR] FFmpeg installation failed
    echo Please try installing manually from: https://ffmpeg.org/download.html
)

pause
exit /b 0

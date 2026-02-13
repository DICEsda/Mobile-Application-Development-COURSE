@echo off
REM Installation script for MP3 to M4B Generator (Windows)

echo =========================================
echo MP3 to M4B Audiobook Generator - Setup
echo =========================================
echo.

REM Check Python
echo Checking Python installation...
python --version >nul 2>&1
if %errorlevel% neq 0 (
    echo X Python not found. Please install Python 3.8 or later.
    echo   Download from: https://www.python.org/downloads/
    pause
    exit /b 1
)

for /f "tokens=2" %%i in ('python --version 2^>^&1') do set PYTHON_VERSION=%%i
echo √ Found Python %PYTHON_VERSION%
echo.

REM Check FFmpeg
echo Checking FFmpeg installation...
ffmpeg -version >nul 2>&1
if %errorlevel% neq 0 (
    echo X FFmpeg not found.
    echo.
    echo Please install FFmpeg:
    echo   1. Download from: https://ffmpeg.org/download.html
    echo   2. Extract the archive
    echo   3. Add the bin folder to your PATH
    echo.
    pause
    exit /b 1
)

echo √ Found FFmpeg
echo.

REM Install Python dependencies
echo Installing Python dependencies...
python -m pip install --upgrade pip
python -m pip install -r requirements.txt

if %errorlevel% neq 0 (
    echo X Failed to install Python dependencies
    pause
    exit /b 1
)

echo.
echo √ Python dependencies installed successfully
echo.

REM Create cache directories
echo Creating cache directories...
if not exist "%USERPROFILE%\.m4b_generator\cache" mkdir "%USERPROFILE%\.m4b_generator\cache"
if not exist "%USERPROFILE%\.m4b_generator\covers" mkdir "%USERPROFILE%\.m4b_generator\covers"
echo √ Cache directories created
echo.

echo =========================================
echo √ Installation complete!
echo =========================================
echo.
echo To run the application:
echo   python m4b_generator.py
echo.
echo For help, see README.md
echo.
pause

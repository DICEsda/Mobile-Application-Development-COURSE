@echo off
REM Quick launcher for MP3 to M4B Generator

echo Starting MP3 to M4B Audiobook Generator...
echo.

python m4b_generator.py

if %errorlevel% neq 0 (
    echo.
    echo Error running the application!
    echo Make sure Python and all dependencies are installed.
    echo Run install.bat if you haven't already.
    pause
)

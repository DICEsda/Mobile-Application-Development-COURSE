# FFmpeg Simple Installer (No Admin Required)
# This installs FFmpeg to user directory instead of C:\ffmpeg

$ErrorActionPreference = "Stop"

# Install to user profile instead of C:\
$InstallDir = "$env:USERPROFILE\ffmpeg"
$BinDir = "$InstallDir\bin"
$DownloadUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
$ZipFile = "$env:TEMP\ffmpeg.zip"

Write-Host ""
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host "   FFmpeg Installer (User Mode)"  -ForegroundColor Cyan
Write-Host "========================================"  -ForegroundColor Cyan
Write-Host ""

# Check if already exists
if (Test-Path "$BinDir\ffmpeg.exe") {
    Write-Host "FFmpeg is already installed at: $InstallDir" -ForegroundColor Yellow
    Write-Host ""
    $response = Read-Host "Reinstall? (y/N)"
    if ($response -ne "y") {
        Write-Host "Checking if in PATH..." -ForegroundColor Cyan
        goto CheckPath
    }
}

# Download
Write-Host "Step 1: Downloading FFmpeg..." -ForegroundColor Cyan
Write-Host "This will take 2-5 minutes..." -ForegroundColor Yellow
Write-Host ""

try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipFile -UseBasicParsing
    Write-Host "[OK] Download complete!" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host "[ERROR] Download failed: $_" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please download manually from:" -ForegroundColor Yellow
    Write-Host "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip" -ForegroundColor Cyan
    pause
    exit 1
}

# Extract
Write-Host "Step 2: Extracting..." -ForegroundColor Cyan

try {
    if (Test-Path $InstallDir) {
        Remove-Item $InstallDir -Recurse -Force
    }
    
    $TempExtract = "$env:TEMP\ffmpeg_extract"
    if (Test-Path $TempExtract) {
        Remove-Item $TempExtract -Recurse -Force
    }
    
    Expand-Archive -Path $ZipFile -DestinationPath $TempExtract -Force
    $ExtractedFolder = Get-ChildItem $TempExtract -Directory | Select-Object -First 1
    Move-Item $ExtractedFolder.FullName $InstallDir -Force
    
    Remove-Item $TempExtract -Recurse -Force
    Remove-Item $ZipFile -Force
    
    Write-Host "[OK] Extraction complete!" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host "[ERROR] Extraction failed: $_" -ForegroundColor Red
    pause
    exit 1
}

# Add to User PATH
:CheckPath
Write-Host "Step 3: Adding to PATH..." -ForegroundColor Cyan

$UserPath = [Environment]::GetEnvironmentVariable("Path", "User")

if ($UserPath -like "*$BinDir*") {
    Write-Host "[OK] Already in PATH" -ForegroundColor Green
} else {
    try {
        $NewPath = if ($UserPath) { "$UserPath;$BinDir" } else { $BinDir }
        [Environment]::SetEnvironmentVariable("Path", $NewPath, "User")
        $env:Path = "$env:Path;$BinDir"
        Write-Host "[OK] Added to PATH" -ForegroundColor Green
        Write-Host ""
        Write-Host "NOTE: Restart your terminal for changes to take effect" -ForegroundColor Yellow
    } catch {
        Write-Host "[ERROR] Failed to add to PATH: $_" -ForegroundColor Red
        Write-Host ""
        Write-Host "Manual steps:" -ForegroundColor Yellow
        Write-Host "1. Press Win + R" -ForegroundColor Cyan
        Write-Host "2. Type: sysdm.cpl" -ForegroundColor Cyan
        Write-Host "3. Go to Advanced tab -> Environment Variables" -ForegroundColor Cyan
        Write-Host "4. Under User variables, select Path -> Edit" -ForegroundColor Cyan
        Write-Host "5. Click New and add: $BinDir" -ForegroundColor Cyan
    }
}

Write-Host ""

# Verify
Write-Host "Step 4: Verifying..." -ForegroundColor Cyan

if (Test-Path "$BinDir\ffmpeg.exe") {
    Write-Host "[OK] ffmpeg.exe found!" -ForegroundColor Green
    
    $version = & "$BinDir\ffmpeg.exe" -version 2>&1 | Select-Object -First 1
    Write-Host "[OK] FFmpeg is working!" -ForegroundColor Green
    Write-Host ""
    Write-Host $version -ForegroundColor Gray
    Write-Host ""
} else {
    Write-Host "[ERROR] Installation failed" -ForegroundColor Red
    pause
    exit 1
}

# Success
Write-Host ""
Write-Host "========================================"  -ForegroundColor Green
Write-Host "     Installation Complete! "  -ForegroundColor Green
Write-Host "========================================"  -ForegroundColor Green
Write-Host ""
Write-Host "Installed to: $InstallDir" -ForegroundColor Cyan
Write-Host ""
Write-Host "NEXT STEPS:" -ForegroundColor Yellow
Write-Host "1. Close this window" -ForegroundColor White
Write-Host "2. Open a NEW command prompt or PowerShell" -ForegroundColor White
Write-Host "3. Test: ffmpeg -version" -ForegroundColor Cyan
Write-Host "4. Run: python m4b_creator.py" -ForegroundColor Cyan
Write-Host ""

pause

# FFmpeg Installer for Windows (PowerShell)
# Run this script with: powershell -ExecutionPolicy Bypass -File install_ffmpeg.ps1

# Require Administrator privileges
#Requires -RunAsAdministrator

$ErrorActionPreference = "Stop"

# Configuration
$InstallDir = "C:\ffmpeg"
$BinDir = "$InstallDir\bin"
$DownloadUrl = "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip"
$ZipFile = "$env:TEMP\ffmpeg.zip"

# Colors
function Write-ColorOutput($ForegroundColor, $Message) {
    $fc = $host.UI.RawUI.ForegroundColor
    $host.UI.RawUI.ForegroundColor = $ForegroundColor
    Write-Output $Message
    $host.UI.RawUI.ForegroundColor = $fc
}

Write-Host ""
Write-ColorOutput Cyan "========================================"
Write-ColorOutput Cyan "   FFmpeg Installer for Windows"
Write-ColorOutput Cyan "========================================"
Write-Host ""

# Check if FFmpeg already exists
if (Test-Path "$BinDir\ffmpeg.exe") {
    Write-ColorOutput Yellow "FFmpeg is already installed at: $InstallDir"
    Write-Host ""
    $response = Read-Host "Do you want to reinstall? (y/N)"
    if ($response -ne "y" -and $response -ne "Y") {
        Write-ColorOutput Green "Skipping download and extraction..."
        goto CheckPath
    }
}

# Step 1: Download FFmpeg
Write-Host ""
Write-ColorOutput Cyan "Step 1: Downloading FFmpeg..."
Write-Host "Source: $DownloadUrl"
Write-Host "This may take a few minutes depending on your connection..."
Write-Host ""

try {
    # Enable TLS 1.2
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    
    # Download with progress
    $ProgressPreference = 'Continue'
    Invoke-WebRequest -Uri $DownloadUrl -OutFile $ZipFile -UseBasicParsing
    
    Write-ColorOutput Green "[OK] Download complete!"
    Write-Host ""
} catch {
    Write-ColorOutput Red "[ERROR] Failed to download FFmpeg"
    Write-Host "Error: $_"
    Write-Host ""
    Write-Host "Please check your internet connection and try again."
    Write-Host "Or download manually from: https://ffmpeg.org/download.html"
    pause
    exit 1
}

# Step 2: Extract FFmpeg
Write-ColorOutput Cyan "Step 2: Extracting FFmpeg..."
Write-Host ""

try {
    # Remove old installation
    if (Test-Path $InstallDir) {
        Write-Host "Removing old installation..."
        Remove-Item -Path $InstallDir -Recurse -Force
    }
    
    # Create temporary extraction directory
    $TempExtract = "$env:TEMP\ffmpeg_temp"
    if (Test-Path $TempExtract) {
        Remove-Item -Path $TempExtract -Recurse -Force
    }
    New-Item -ItemType Directory -Path $TempExtract -Force | Out-Null
    
    # Extract ZIP
    Write-Host "Extracting archive..."
    Expand-Archive -Path $ZipFile -DestinationPath $TempExtract -Force
    
    # Find the extracted folder (it has a version number)
    $ExtractedFolder = Get-ChildItem -Path $TempExtract -Directory | Select-Object -First 1
    
    # Move to installation directory
    Write-Host "Moving files to $InstallDir..."
    Move-Item -Path $ExtractedFolder.FullName -Destination $InstallDir -Force
    
    # Clean up
    Remove-Item -Path $TempExtract -Recurse -Force
    Remove-Item -Path $ZipFile -Force
    
    Write-ColorOutput Green "[OK] Extraction complete!"
    Write-Host ""
} catch {
    Write-ColorOutput Red "[ERROR] Failed to extract FFmpeg"
    Write-Host "Error: $_"
    pause
    exit 1
}

# Step 3: Add to PATH
:CheckPath
Write-ColorOutput Cyan "Step 3: Checking PATH..."
Write-Host ""

$CurrentPath = [Environment]::GetEnvironmentVariable("Path", "Machine")

if ($CurrentPath -like "*$BinDir*") {
    Write-ColorOutput Yellow "FFmpeg is already in PATH"
    Write-Host ""
} else {
    Write-ColorOutput Cyan "Step 4: Adding FFmpeg to PATH..."
    Write-Host ""
    
    try {
        # Add to system PATH
        $NewPath = $CurrentPath + ";" + $BinDir
        [Environment]::SetEnvironmentVariable("Path", $NewPath, "Machine")
        
        # Also add to current session
        $env:Path = $env:Path + ";" + $BinDir
        
        Write-ColorOutput Green "[OK] PATH updated successfully!"
        Write-Host ""
        Write-ColorOutput Yellow "NOTE: Other command prompts need to be restarted for changes to take effect."
        Write-Host ""
    } catch {
        Write-ColorOutput Red "[ERROR] Failed to add FFmpeg to PATH"
        Write-Host "Error: $_"
        Write-Host ""
        Write-ColorOutput Yellow "Manual steps to add to PATH:"
        Write-Host "1. Press Win + X and select 'System'"
        Write-Host "2. Click 'Advanced system settings'"
        Write-Host "3. Click 'Environment Variables'"
        Write-Host "4. Under 'System variables', select 'Path' and click 'Edit'"
        Write-Host "5. Click 'New' and add: $BinDir"
        Write-Host "6. Click OK on all dialogs"
        Write-Host ""
    }
}

# Step 5: Verify installation
Write-ColorOutput Cyan "Step 5: Verifying installation..."
Write-Host ""

if (Test-Path "$BinDir\ffmpeg.exe") {
    Write-ColorOutput Green "[OK] ffmpeg.exe found at: $BinDir\ffmpeg.exe"
    
    # Test FFmpeg
    try {
        $version = & "$BinDir\ffmpeg.exe" -version 2>&1 | Select-Object -First 1
        Write-ColorOutput Green "[OK] FFmpeg is working correctly"
        Write-Host ""
        Write-Host $version
        Write-Host ""
    } catch {
        Write-ColorOutput Yellow "[WARNING] FFmpeg.exe exists but failed to run"
        Write-Host "Error: $_"
    }
} else {
    Write-ColorOutput Red "[ERROR] FFmpeg installation failed"
    Write-Host "Please try installing manually from: https://ffmpeg.org/download.html"
    Write-Host ""
    pause
    exit 1
}

# Success!
Write-Host ""
Write-ColorOutput Green "========================================"
Write-ColorOutput Green "   Installation Complete!"
Write-ColorOutput Green "========================================"
Write-Host ""
Write-Host "FFmpeg has been installed to: $InstallDir"
Write-Host "FFmpeg binaries are in: $BinDir"
Write-Host ""
Write-ColorOutput Yellow "IMPORTANT:"
Write-Host "- Close and reopen your command prompt or terminal"
Write-Host "- Then test by typing: ffmpeg -version"
Write-Host ""
Write-ColorOutput Cyan "You can now use the M4B creator tool!"
Write-Host "Run: python m4b_creator.py"
Write-Host ""

pause

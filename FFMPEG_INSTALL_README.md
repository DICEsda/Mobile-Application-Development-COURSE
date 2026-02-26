# FFmpeg Installer for Windows

Automated installer scripts to download and install FFmpeg on Windows with PATH configuration.

## Quick Install

### Option 1: PowerShell (Recommended)

1. **Right-click** on `install_ffmpeg.ps1`
2. Select **"Run with PowerShell"**
3. If prompted, allow administrator access
4. Wait for installation to complete
5. **Restart your command prompt/terminal**

### Option 2: Command Prompt

1. **Right-click** on `install_ffmpeg.bat`
2. Select **"Run as administrator"**
3. Wait for installation to complete
4. **Restart your command prompt/terminal**

### Option 3: Command Line

```powershell
# PowerShell (run as Administrator)
powershell -ExecutionPolicy Bypass -File install_ffmpeg.ps1
```

```cmd
# Command Prompt (run as Administrator)
install_ffmpeg.bat
```

## What It Does

1. ✅ Downloads FFmpeg from official source (gyan.dev)
2. ✅ Extracts to `C:\ffmpeg`
3. ✅ Adds `C:\ffmpeg\bin` to system PATH
4. ✅ Verifies installation
5. ✅ Tests that FFmpeg works

## After Installation

1. **Close all command prompts and terminals**
2. **Open a new command prompt**
3. Test FFmpeg:
   ```cmd
   ffmpeg -version
   ```

You should see FFmpeg version information!

## Verify Installation

```cmd
# Check if FFmpeg is installed
ffmpeg -version

# Check if it's in PATH
where ffmpeg

# Should show: C:\ffmpeg\bin\ffmpeg.exe
```

## Manual Installation (If Scripts Fail)

If the automated scripts don't work, follow these steps:

### 1. Download FFmpeg
- Go to: https://www.gyan.dev/ffmpeg/builds/
- Download: **ffmpeg-release-essentials.zip**
- Save to your Downloads folder

### 2. Extract
- Right-click the ZIP file
- Select "Extract All..."
- Extract to `C:\ffmpeg`

### 3. Add to PATH
1. Press `Win + X` and select **"System"**
2. Click **"Advanced system settings"**
3. Click **"Environment Variables"**
4. Under **"System variables"**, find and select **"Path"**
5. Click **"Edit"**
6. Click **"New"**
7. Add: `C:\ffmpeg\bin`
8. Click **OK** on all dialogs
9. **Restart your terminal**

### 4. Verify
```cmd
ffmpeg -version
```

## Troubleshooting

### "FFmpeg is not recognized"
- Make sure you **restarted your command prompt** after installation
- Check PATH by typing: `echo %PATH%`
- Verify file exists: `dir C:\ffmpeg\bin\ffmpeg.exe`

### "Access Denied" or "Permission Error"
- Run the script **as Administrator**
- Right-click → "Run as administrator"

### "Cannot download"
- Check your internet connection
- Try downloading manually from: https://ffmpeg.org/download.html
- Some corporate networks block downloads - try on a different network

### "Script execution disabled"
For PowerShell script, you may need to allow script execution:
```powershell
# Run as Administrator
Set-ExecutionPolicy -ExecutionPolicy RemoteSigned -Scope CurrentUser
```

Then run the installer again.

## Uninstall

To remove FFmpeg:

1. Delete the folder: `C:\ffmpeg`
2. Remove from PATH:
   - Win + X → System → Advanced system settings
   - Environment Variables → Path → Edit
   - Remove the `C:\ffmpeg\bin` entry
   - Click OK

## For M4B Creator Tool

After installing FFmpeg, you can use the M4B creator:

```cmd
# Test that FFmpeg is working
ffmpeg -version

# Run the M4B creator
python m4b_creator.py
```

## System Requirements

- Windows 10 or 11
- Administrator access
- Internet connection (for download)
- ~100 MB free disk space

## What Gets Installed

- **Location**: `C:\ffmpeg`
- **Binaries**: `C:\ffmpeg\bin\`
  - ffmpeg.exe (main tool)
  - ffplay.exe (player)
  - ffprobe.exe (analyzer)

## Security

- Downloads from official FFmpeg builds by Gyan Doshi (trusted source)
- No telemetry or additional software
- Open source scripts you can review

## Need Help?

If you encounter issues:

1. Check the troubleshooting section above
2. Make sure you're running as Administrator
3. Try the manual installation method
4. Check FFmpeg's official documentation: https://ffmpeg.org/

## License

FFmpeg is licensed under LGPL 2.1 or later.
These installer scripts are provided as-is for convenience.

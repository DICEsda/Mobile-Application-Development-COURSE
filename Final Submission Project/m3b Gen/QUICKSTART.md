# Quick Start Guide

Get started with MP3 to M4B Generator in 5 minutes!

## Prerequisites

1. **Python 3.8+** installed
2. **FFmpeg** installed and in PATH

## Installation

### Windows
```cmd
install.bat
```

### macOS/Linux
```bash
chmod +x install.sh
./install.sh
```

### Manual Installation
```bash
pip install -r requirements.txt
```

## Running the App

```bash
python m4b_generator.py
```

## Your First Audiobook

### 1. Prepare Your Files
- Create a folder with your MP3 files
- Name them sequentially (e.g., `01.mp3`, `02.mp3`, etc.)
- Example structure:
  ```
  atomic_habits/
  ├── 01 - Introduction.mp3
  ├── 02 - Chapter 1.mp3
  ├── 03 - Chapter 2.mp3
  └── ...
  ```

### 2. Launch the App
```bash
python m4b_generator.py
```

### 3. Select Your Directory
- Press Enter on "Select MP3 Directory"
- Navigate to your audiobook folder
- Press Enter to select

### 4. Search for Metadata
- Type the book title: `Atomic Habits`
- Optional: Add author: `James Clear`
- Press Enter on "Search"
- Select the correct book from results

### 5. Review Metadata
- Check title and author
- Add narrator name (optional)
- Cover will download automatically
- Press "Continue"

### 6. Configure Chapters
- Default: One chapter per file
- Edit chapter names if needed
- Press "Generate M4B"

### 7. Generate Audiobook
- Enter output filename: `Atomic Habits`
- Press "Generate"
- Wait for completion
- Find your `.m4b` file on Desktop!

## Common Issues

### FFmpeg Not Found
```bash
# Windows: Download from https://ffmpeg.org
# macOS:
brew install ffmpeg

# Linux:
sudo apt install ffmpeg
```

### No Search Results
- Try simpler title (e.g., "Atomic Habits" instead of "Atomic Habits: An Easy & Proven Way...")
- Add author name
- Or skip metadata and enter manually

### Files Won't Open
- Ensure all MP3 files are valid
- Check file permissions
- Try moving files to a folder without special characters

## Tips for Best Results

1. **File Naming**: Use sequential numbers
   - Good: `01.mp3`, `02.mp3`, `03.mp3`
   - Bad: `chapter1.mp3`, `chapterone.mp3`, `ch_1.mp3`

2. **Audio Quality**: Use consistent bitrate
   - All files should be same format (e.g., all 128kbps or all 192kbps)

3. **Metadata**: The more accurate, the better
   - Use ISBNs when available
   - Include full author names

4. **Chapters**: Clean up auto-generated names
   - Replace "01 Introduction" with "Introduction"
   - Remove track numbers if not needed

## Example Workflow (30 seconds)

```
1. python m4b_generator.py
2. [Navigate to folder] → [Press Enter]
3. Type "Atomic Habits" → [Press Enter on Search]
4. [Select result #1]
5. [Press Continue]
6. [Press Generate M4B]
7. [Enter filename] → [Press Generate]
8. ✓ Done! File on Desktop
```

## Next Steps

- Read full [README.md](README.md) for advanced features
- Try timestamp-based chapters for single-file audiobooks
- Customize chapter names before generating
- Upload custom cover images

## Getting Help

- Check [README.md](README.md) for detailed documentation
- Review error messages in the log panel
- Ensure FFmpeg is working: `ffmpeg -version`

---

**Ready to create your first audiobook? Run `python m4b_generator.py` now!**

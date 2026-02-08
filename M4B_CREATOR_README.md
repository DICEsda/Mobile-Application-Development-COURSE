# M4B Audiobook Creator

A Windows CLI tool for creating M4B audiobook files from MP3/audio files with automatic metadata fetching and chapter generation.

## Features

- ✨ **Automatic Metadata**: Fetches book metadata (title, author, cover art) from Open Library
- 📖 **Chapter Generation**: Creates chapters from individual audio files
- 🎨 **Cover Art**: Embeds cover images into the M4B file
- ✏️ **Interactive Editing**: Edit metadata before creating the audiobook
- 🎧 **Optimized Output**: Creates properly formatted M4B files compatible with audiobook players
- 🖥️ **Windows Compatible**: Full Windows support with colored console output

## Requirements

### System Requirements
- **Windows 10/11**
- **Python 3.7+**
- **FFmpeg** - Must be installed and available in PATH

### Python Dependencies
```bash
pip install -r requirements.txt
```

Required packages:
- `mutagen` - Audio metadata handling
- `requests` - API calls for metadata
- `colorama` - Colored console output

## Installation

1. **Install Python**
   - Download from [python.org](https://www.python.org/downloads/)
   - Make sure to check "Add Python to PATH" during installation

2. **Install FFmpeg**
   - Download from [ffmpeg.org](https://ffmpeg.org/download.html)
   - Extract to a folder (e.g., `C:\ffmpeg`)
   - Add FFmpeg bin folder to PATH:
     - Open System Properties → Environment Variables
     - Edit "Path" variable
     - Add `C:\ffmpeg\bin` (or your FFmpeg location)
   
3. **Install Python dependencies**
   ```bash
   pip install -r requirements.txt
   ```

## Usage

### Basic Usage

1. Place your audio files (MP3, M4A, WAV, FLAC, OGG) in the `input` folder
2. Run the script:
   ```bash
   python m4b_creator.py
   ```
3. Enter the book title when prompted
4. Edit metadata if needed
5. Find your M4B file in the `output` folder

### Command Line Options

```bash
# Specify custom input and output directories
python m4b_creator.py --input "C:\Audio\MyBook" --output "C:\Audiobooks"

# Provide title upfront (skip prompt)
python m4b_creator.py --title "The Hobbit"

# Custom output filename
python m4b_creator.py --name "my_audiobook.m4b"

# Skip metadata editing
python m4b_creator.py --no-edit

# Skip Open Library search
python m4b_creator.py --no-search

# Adjust audio quality
python m4b_creator.py --bitrate 32k --sample-rate 22050

# Combine options
python m4b_creator.py -i input -t "The Hobbit" --no-edit -b 64k
```

### Full Options

```
-i, --input        Input directory with audio files (default: input)
-o, --output       Output directory for M4B file (default: output)
-t, --title        Book title for metadata search
-n, --name         Output filename (default: audiobook.m4b)
-b, --bitrate      Audio bitrate (default: 64k)
-s, --sample-rate  Sample rate (default: 44100)
--no-edit          Skip metadata editing step
--no-search        Skip Open Library search
```

## File Organization

### Input Files
Organize your audio files with numbers for proper ordering:
```
input/
├── 01 - Chapter 1.mp3
├── 02 - Chapter 2.mp3
├── 03 - Chapter 3.mp3
└── ...
```

The script will:
- Sort files naturally (1, 2, 10 instead of 1, 10, 2)
- Use filenames as chapter titles (removes numbers and "Chapter" prefix)
- Create chapter markers at the start of each file

### Output Structure
```
output/
├── audiobook.m4b      # Final M4B file
├── chapters.txt       # Generated chapter metadata
├── files.txt          # File list for FFmpeg
└── cover.jpg          # Downloaded cover art (if found)
```

## Examples

### Example 1: Basic Conversion
```bash
# Put MP3s in input folder
python m4b_creator.py
# Enter "The Hobbit" when prompted
# Edit metadata if desired
# Done!
```

### Example 2: Batch Processing
```bash
# Process multiple books
python m4b_creator.py -i "C:\Books\Book1" -t "Book One" --no-edit
python m4b_creator.py -i "C:\Books\Book2" -t "Book Two" --no-edit
python m4b_creator.py -i "C:\Books\Book3" -t "Book Three" --no-edit
```

### Example 3: Custom Quality
```bash
# High quality for music-rich audiobooks
python m4b_creator.py --bitrate 128k --sample-rate 48000

# Low quality for voice-only (smaller file size)
python m4b_creator.py --bitrate 32k --sample-rate 22050
```

## Metadata

The tool automatically fetches metadata from Open Library:
- **Title**: Book title
- **Author**: Author name(s)
- **Publisher**: Publisher name
- **Year**: Publication year
- **Description**: Book subjects/categories
- **Cover Art**: High-resolution cover image

You can edit all metadata before creating the M4B file.

## Chapter Naming

Chapters are automatically named from file names:
- `01 - Introduction.mp3` → "Introduction"
- `Chapter 1 - The Beginning.mp3` → "The Beginning"
- `02.mp3` → "Chapter 2"

## Troubleshooting

### FFmpeg Not Found
```
✗ FFmpeg not found in PATH
```
**Solution**: Install FFmpeg and add it to your system PATH

### No Audio Files Found
```
✗ No audio files found in input
```
**Solution**: Make sure your audio files are in the correct directory and have supported extensions (mp3, m4a, wav, flac, ogg)

### Metadata Not Found
```
✗ No results found
```
**Solution**: 
- Try a different search term
- Use `--no-search` and manually enter metadata
- Check your internet connection

### FFmpeg Error
```
✗ FFmpeg error: [error message]
```
**Solution**: Check that:
- All audio files are valid and not corrupted
- You have enough disk space
- FFmpeg is properly installed

## Audio Quality Guidelines

| Use Case | Bitrate | Sample Rate | File Size (per hour) |
|----------|---------|-------------|---------------------|
| Voice only, minimum quality | 32k | 22050 | ~15 MB |
| Voice only, standard | 64k | 44100 | ~30 MB |
| Voice + music, good quality | 96k | 44100 | ~45 MB |
| High quality | 128k | 48000 | ~60 MB |

## Technical Details

### M4B Format
M4B (MPEG-4 Audio Book) is a container format that:
- Uses AAC audio codec
- Supports chapter markers
- Allows embedded cover art
- Remembers playback position
- Is compatible with iOS, Android, and desktop audiobook players

### Chapter Format
Chapters are stored using FFmpeg metadata format:
```
;FFMETADATA1
[CHAPTER]
TIMEBASE=1/1000
START=0
END=180000
title=Introduction
```

## License

This tool is provided as-is for personal use.

## Credits

- Metadata from [Open Library](https://openlibrary.org/)
- Audio processing by [FFmpeg](https://ffmpeg.org/)
- Metadata handling by [Mutagen](https://mutagen.readthedocs.io/)

## Support

For issues or questions:
1. Check FFmpeg is installed: `ffmpeg -version`
2. Verify Python dependencies: `pip list`
3. Check audio file formats are supported
4. Review error messages in the console

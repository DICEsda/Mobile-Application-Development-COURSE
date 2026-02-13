# MP3 to M4B Audiobook Generator

A powerful Terminal User Interface (TUI) application for converting MP3 files into M4B audiobooks with embedded chapters, metadata, and cover art.

## Features

- **Smart Audio Handling**
  - Automatic MP3 file detection and sorting
  - Audio format validation (sample rate, channels, bitrate)
  - Multiple sorting options (filename, track number, duration)

- **Rich Metadata Integration**
  - Search book metadata from Google Books API
  - Fallback to Open Library API
  - Automatic cover art download
  - **Live cover image preview in terminal** 🆕
  - Edit all metadata fields before generation

- **Flexible Chapter Management**
  - File-based chapters (one chapter per MP3)
  - Timestamp-based chapters (for single long files)
  - Manual chapter editing and reordering
  - Auto-naming functionality

- **Professional M4B Output**
  - No audio re-encoding (fast & lossless)
  - Embedded chapter markers
  - Cover art integration
  - Complete metadata tagging

- **Modern TUI Interface**
  - Clean, intuitive interface using Textual
  - Keyboard shortcuts for power users
  - Real-time progress tracking
  - Comprehensive validation and warnings

## Requirements

### System Dependencies

1. **Python 3.8+**
   - Download from [python.org](https://www.python.org/downloads/)

2. **FFmpeg**
   - **Windows**: Download from [ffmpeg.org](https://ffmpeg.org/download.html) and add to PATH
   - **macOS**: `brew install ffmpeg`
   - **Linux**: `sudo apt install ffmpeg` (Ubuntu/Debian) or `sudo yum install ffmpeg` (CentOS/RHEL)

3. **mp4chaps** (Optional but HIGHLY recommended for Android compatibility)
   - Required for chapters to work properly on Android devices (Media3)
   - **Windows**: Download from [mp4v2 releases](https://github.com/enzo1982/mp4v2/releases) or `choco install mp4v2`
   - **macOS**: `brew install mp4v2`
   - **Linux**: `sudo apt install mp4v2-utils` (Ubuntu/Debian)
   - Without this, chapters may show as `_0_00_00` in MediaInfo and won't work on Android
   - See **ANDROID_CHAPTERS_FIX.md** for detailed setup

### Python Dependencies

All Python dependencies are listed in `requirements.txt`:
- `textual>=0.47.0` - TUI framework
- `mutagen>=1.47.0` - Audio metadata handling
- `requests>=2.31.0` - API calls
- `Pillow>=10.0.0` - Image processing
- `term-image>=0.7.0` - Terminal image preview (for cover display)

## Installation

1. **Clone or download this repository**
   ```bash
   git clone https://github.com/yourusername/m4b-generator.git
   cd m4b-generator
   ```

2. **Install Python dependencies**
   ```bash
   pip install -r requirements.txt
   ```

3. **Verify FFmpeg installation**
   ```bash
   ffmpeg -version
   ```

## Usage

### Quick Start

1. **Run the application**
   ```bash
   python m4b_generator.py
   ```

2. **Follow the TUI workflow**:
   - Select your MP3 directory
   - Search for book metadata
   - Review and edit metadata
   - Configure chapters
   - Generate M4B audiobook

### Detailed Workflow

#### Step 1: Select MP3 Directory
- Navigate to the directory containing your MP3 files
- The app will automatically scan and validate files
- View file count, total duration, and format warnings

#### Step 2: Search Book Metadata
- Enter book title (required)
- Optionally add author name or ISBN for better results
- Browse search results from Google Books and Open Library
- Select the correct edition

**Tip**: You can skip metadata search and use defaults based on directory name

#### Step 3: Review Metadata
- Edit title, author(s), narrator
- Modify publication year and language
- Edit or write a custom description
- Preview and download cover art
- Upload custom cover (optional)

#### Step 4: Configure Chapters
**File-Based Mode** (default):
- Each MP3 file becomes one chapter
- Chapter names auto-generated from filenames
- Edit individual chapter titles
- Reorder chapters as needed

**Timestamp-Based Mode**:
- For single long MP3 files
- Add chapters at specific timestamps
- Format: HH:MM:SS or MM:SS

**Chapter Editing**:
- Press `E` to edit selected chapter
- Press `D` to delete chapter
- Press `A` to add new chapter
- Use "Auto-name" to rename all chapters sequentially

#### Step 5: Generate M4B
- Review final summary
- Enter output filename
- Watch real-time progress
- Find your audiobook on Desktop when complete

## Keyboard Shortcuts

| Key | Action |
|-----|--------|
| `Q` | Quit application |
| `ESC` | Go back / Cancel |
| `ENTER` | Select / Confirm |
| `A` | Add chapter |
| `E` | Edit selected chapter |
| `D` | Delete selected chapter |
| `↑` `↓` | Navigate lists |
| `TAB` | Switch focus |

## Configuration

The app stores configuration in `~/.m4b_generator/`:
- `config.json` - User preferences
- `cache/` - Cached metadata
- `covers/` - Downloaded cover images

### Default Settings
```json
{
  "default_output_dir": "~/Desktop",
  "default_sort": "filename",
  "default_language": "en",
  "preserve_original_bitrate": true,
  "auto_download_covers": true
}
```

## Project Structure

```
m4b-generator/
├── m4b_generator.py          # Main entry point
├── requirements.txt           # Python dependencies
├── README.md                  # This file
├── core/                      # Core functionality
│   ├── __init__.py
│   ├── audio_handler.py       # Audio file operations
│   ├── metadata_provider.py   # Metadata API integration
│   ├── chapter_manager.py     # Chapter management
│   └── m4b_creator.py         # M4B generation (ffmpeg)
├── ui/                        # TUI screens
│   ├── __init__.py
│   ├── main_screen.py         # Main menu
│   ├── directory_selector.py # Directory browser
│   ├── metadata_search.py     # Metadata search UI
│   ├── metadata_review.py     # Metadata editor
│   ├── chapter_editor.py      # Chapter configuration
│   └── generator_screen.py    # Generation progress
└── utils/                     # Utilities
    ├── __init__.py
    └── config.py              # Configuration management
```

## Technical Details

### Audio Processing
- Uses `mutagen` for MP3 metadata extraction
- Validates sample rate, channels, and bitrate consistency
- Supports variable bitrate (VBR) files
- No audio re-encoding (preserves original quality)

### M4B Creation
The app uses FFmpeg to:
1. Concatenate multiple MP3 files
2. Embed chapter markers in FFMETADATA format
3. Attach cover art as embedded image
4. Add comprehensive metadata tags
5. Output single M4B (MPEG-4 audiobook) file

**FFmpeg Command Structure**:
```bash
ffmpeg -f concat -safe 0 -i filelist.txt \
       -i cover.jpg \
       -i metadata.txt \
       -map 0:a -map 1:v \
       -c:a copy -c:v copy \
       -disposition:v:0 attached_pic \
       -map_metadata 2 \
       output.m4b
```

### Metadata APIs

**Google Books API**:
- Primary metadata source
- No API key required
- High-quality cover images
- Comprehensive book information

**Open Library API**:
- Fallback source
- Great for older or niche books
- ISBN-based search support

## Troubleshooting

### FFmpeg Not Found
**Error**: `RuntimeError: ffmpeg not found`

**Solution**:
1. Install FFmpeg (see Requirements)
2. Verify installation: `ffmpeg -version`
3. Ensure FFmpeg is in your system PATH

### Mixed Audio Formats Warning
**Warning**: `Mixed sample rates detected`

**Solution**:
- Use consistent audio files (same format)
- Or accept the warning (usually works fine)
- Consider re-encoding files to match

### No Metadata Results
**Issue**: Search returns no results

**Solutions**:
1. Try alternative spelling
2. Add author name for better results
3. Use ISBN if available
4. Skip metadata and enter manually

### Generation Failed
**Error**: M4B creation fails

**Check**:
1. Enough disk space available
2. Output directory is writable
3. All MP3 files are readable
4. FFmpeg is working: `ffmpeg -version`

### Cover Image Issues
**Issue**: Cover not appearing in output

**Solutions**:
1. Ensure cover downloaded successfully
2. Try uploading custom cover (JPG/PNG)
3. Check image file isn't corrupted

## Advanced Usage

### Custom Chapter Timestamps
For a single MP3 file with known chapters:
1. Select "Timestamp-based" mode
2. Add chapters manually:
   ```
   00:00:00 - Introduction
   00:05:30 - Chapter 1
   01:15:45 - Chapter 2
   ```

### Batch Processing
Currently the app processes one audiobook at a time. For batch processing:
1. Create separate directories for each audiobook
2. Run the app multiple times
3. Or contribute a batch mode feature!

### Custom Metadata
Skip the search and enter everything manually:
1. Click "Skip Metadata" in search screen
2. Edit all fields in review screen
3. Upload custom cover image

## Contributing

Contributions are welcome! Areas for improvement:
- [ ] Batch processing support
- [ ] Custom cover upload UI
- [ ] M4A input support
- [ ] Advanced timestamp parser
- [ ] Configurable output directory picker
- [ ] Export/import chapter files
- [ ] Preview audio playback

## License

MIT License - See LICENSE file for details

## Credits

Built with:
- [Textual](https://textual.textualize.io/) - Modern TUI framework
- [Mutagen](https://mutagen.readthedocs.io/) - Audio metadata
- [FFmpeg](https://ffmpeg.org/) - Audio processing
- [Google Books API](https://developers.google.com/books)
- [Open Library API](https://openlibrary.org/developers/api)

## Support

For issues, questions, or suggestions:
- Open an issue on GitHub
- Check existing issues for solutions
- Read the documentation

---

**Happy audiobook creating!**

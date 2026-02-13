# 📷 Cover Image Preview Feature

## ✨ What's New

The metadata review screen now shows a **live preview** of your audiobook cover image directly in the terminal!

## 🎯 How It Works

### Automatic Preview

When you reach the "Review Metadata" screen:

1. **Cover is automatically downloaded** from Google Books/Open Library
2. **Live preview appears** in the right panel
3. **Image info displayed**: dimensions, format, file size
4. **Ready to embed** indicator shows when cover is ready

### What You'll See

```
┌─────────────────────────────────────────┐
│ 📷 Cover Image Preview                  │
│                                         │
│ 500×750px | JPEG | 125.3KB             │
│                                         │
│ [Beautiful ASCII/color preview of       │
│  your book cover displayed here!]       │
│                                         │
│ ✅ Ready to embed                       │
└─────────────────────────────────────────┘
```

## 🚀 Setup

### Install Required Library

The cover preview uses `term-image` for high-quality terminal image rendering:

```bash
# Already included in requirements.txt
pip install term-image
```

Or reinstall all dependencies:

```bash
pip install -r requirements.txt
```

### Verify Installation

```python
python -c "from term_image.image import AutoImage; print('✓ term-image installed!')"
```

## 📋 Features

### Smart Rendering

- **Automatic format detection**: Uses best rendering method for your terminal
- **Color support**: Full color preview if your terminal supports it
- **Fallback ASCII**: Simple ASCII art if color isn't available
- **Aspect ratio preserved**: Images scale correctly

### Image Information

Each preview shows:
- 📐 **Dimensions**: Width × Height in pixels
- 📄 **Format**: JPEG, PNG, WEBP, etc.
- 💾 **File Size**: In kilobytes
- ✅ **Status**: Ready to embed indicator

### Interactive Controls

- **Download Cover**: Re-download if needed
- **Upload Custom**: (Coming soon) Upload your own cover
- **Auto-download**: Happens automatically when metadata loads

## 🖼️ Supported Image Formats

- ✅ JPEG/JPG
- ✅ PNG
- ✅ WEBP
- ✅ GIF (first frame)
- ✅ BMP

## 🎨 Terminal Compatibility

### Best Experience

For full-color previews, use modern terminals:

**Windows:**
- ✅ Windows Terminal (Recommended)
- ✅ Windows 11 Console
- ⚠️  CMD (basic support)
- ⚠️  PowerShell (basic support)

**Linux:**
- ✅ Kitty (Best)
- ✅ iTerm2 (macOS)
- ✅ Alacritty
- ✅ GNOME Terminal
- ✅ Konsole

**Fallback:**
- ASCII art mode works everywhere

## 🔧 Troubleshooting

### "Preview unavailable"

**Causes:**
1. Cover image failed to download
2. Image file is corrupted
3. Unsupported image format
4. term-image not installed

**Solutions:**
```bash
# Reinstall term-image
pip install --upgrade term-image

# Check if cover downloaded
ls ~/.m4b_generator/covers/

# Try re-downloading
# Click "Download Cover" button in the app
```

### "term_image import error"

```bash
# Install term-image
pip install term-image

# If that fails, try:
pip install term-image --no-cache-dir

# Verify installation
python -c "import term_image; print('OK')"
```

### Preview looks distorted

- **Problem**: Terminal font or size issues
- **Solution**: 
  - Use a monospace font (Consolas, Fira Code, etc.)
  - Resize terminal window
  - Try Windows Terminal on Windows

### Black screen or no display

- **Problem**: Terminal doesn't support ANSI colors
- **Solution**: 
  - Upgrade to Windows Terminal (Windows)
  - Enable color support in terminal settings
  - Falls back to ASCII automatically

## 💡 Pro Tips

### Tip 1: Best Terminal Setup

For the best cover preview experience:

**Windows:**
```cmd
# Install Windows Terminal from Microsoft Store
# Set font: Cascadia Code or Fira Code
# Enable "Use acrylic" for modern look
```

**Linux/Mac:**
```bash
# Use Kitty or iTerm2 for best results
# They have excellent image rendering
```

### Tip 2: Custom Covers

Want to use your own cover?

1. Download your cover image
2. Place it in: `~/.m4b_generator/covers/`
3. Name it: `{BookTitle}_cover.jpg`
4. Reload the metadata screen

### Tip 3: High-Resolution Covers

The app automatically downloads high-quality covers:
- Google Books: Up to 800×1200px
- Open Library: Up to 500×750px

These work perfectly for audiobook apps!

### Tip 4: Preview Before Generate

Use the cover preview to:
- ✅ Verify correct book cover downloaded
- ✅ Check image quality
- ✅ Ensure it's not corrupted
- ✅ Confirm it matches your book

## 📚 Technical Details

### How term-image Works

`term-image` provides several rendering methods:

1. **Kitty Graphics Protocol**: Best quality (Kitty terminal)
2. **ITerm2 Inline Images**: High quality (iTerm2)
3. **Sixel Graphics**: Good quality (many terminals)
4. **Block Elements**: Unicode blocks with ANSI colors
5. **ASCII**: Simple fallback

The library automatically selects the best method for your terminal!

### Performance

- **Fast**: Images render in <100ms
- **Cached**: Downloaded covers are cached
- **Efficient**: Only loads when needed
- **Memory**: Low memory footprint

### Image Processing

1. Cover downloaded from API
2. Saved to cache directory
3. term-image loads and scales
4. Rendered with best method for terminal
5. Displayed in preview panel

## 🎬 Example Workflow

1. **Launch app**: `python m4b_generator.py`
2. **Select folder**: Choose MP3 directory
3. **Search metadata**: Find your book
4. **Review screen**: 
   - See book info on left
   - **See cover preview on right!** ← NEW!
5. **Verify cover**: Make sure it's correct
6. **Continue**: Move to chapter editor
7. **Generate**: Create M4B with embedded cover

## ❓ FAQ

**Q: Does this work without internet?**
A: Yes! Once the cover is downloaded and cached, previews work offline.

**Q: Can I disable cover preview?**
A: The app still works if term-image isn't installed - it just shows image info without preview.

**Q: Does this slow down the app?**
A: No! Previews are generated asynchronously and cached.

**Q: What if I don't like the downloaded cover?**
A: Click "Upload Custom" (coming soon) or manually replace the file in the cache directory.

**Q: Will the M4B include the cover?**
A: Yes! The cover is embedded in the final M4B file regardless of preview.

## 🆕 Future Enhancements

Coming soon:
- 📤 Upload custom cover images
- 🔍 Search for alternative covers
- ✂️  Crop and edit covers
- 🎨 Apply filters and adjustments

## 📖 Related Documentation

- **README.md**: Main documentation
- **QUICKSTART.md**: Getting started guide
- **ID3_TITLES_INFO.md**: Understanding metadata

---

**Enjoy previewing your audiobook covers before generation!** 📚✨

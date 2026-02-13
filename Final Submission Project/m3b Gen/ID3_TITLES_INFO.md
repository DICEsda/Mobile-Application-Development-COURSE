# ID3 Title Auto-Discovery

## How It Works

The app **automatically reads ID3 title tags** from your MP3 files and uses them as chapter names!

### Priority System

1. **First Choice**: ID3 Title Tag (embedded in MP3)
2. **Fallback**: Filename (if no ID3 title exists)

## What This Means for You

If your MP3 files have embedded titles like this:

```
File: 01.mp3
  ID3 Title: "Introduction to Atomic Habits"
  
File: 02.mp3
  ID3 Title: "Chapter 1 - The Surprising Power of Tiny Changes"
  
File: 03.mp3
  ID3 Title: "Chapter 2 - How Your Habits Shape Your Identity"
```

The app will automatically use those exact titles as chapter names! ✨

## Testing Your MP3 Files

Before running the main app, test your files to see what titles will be used:

```bash
python test_titles.py "path/to/your/mp3/folder"
```

### Example Output:

```
======================================================================
MP3 Title Extraction Test
======================================================================

Scanning: C:\Users\YourName\Music\Audiobook
✓ Found 10 MP3 file(s)

----------------------------------------------------------------------

[1] 01.mp3
    ID3 Title Tag: Introduction to Atomic Habits
    Duration: 5m 30s
    ✓ Will use: 'Introduction to Atomic Habits' as chapter name

[2] 02.mp3
    ID3 Title Tag: Chapter 1 - The Surprising Power...
    Duration: 12m 15s
    ✓ Will use: 'Chapter 1 - The Surprising Power...' as chapter name

----------------------------------------------------------------------

Summary:
  Files with ID3 titles: 10
  Files without ID3 titles: 0

✓ All files have ID3 titles! Chapter names will be perfect.
```

## Smart Title Cleaning

The app intelligently cleans titles:

### Original ID3 Titles (Preserved)
- ✓ "Introduction" → "Introduction"
- ✓ "Chapter 1 - The Basics" → "Chapter 1 - The Basics"
- ✓ "Part 2: Advanced Concepts" → "Part 2: Advanced Concepts"

### Filenames (Cleaned)
- "01 - Introduction.mp3" → "Introduction"
- "Track 05 - Conclusion.mp3" → "Conclusion"
- "03_chapter_three.mp3" → "chapter three"

## Workflow

### Step 1: Prepare Your MP3s (Optional but Recommended)

Set proper ID3 titles using a tool like:
- **Windows**: Mp3tag (free)
- **macOS**: Kid3, Music app
- **Linux**: EasyTAG, Kid3

### Step 2: Test (Optional)

```bash
python test_titles.py "C:\path\to\your\mp3s"
```

### Step 3: Run the App

```bash
python m4b_generator.py
```

Or double-click `run.bat`

### Step 4: Review Chapter Names

When you reach the Chapter Configuration screen:

1. You'll see all chapters with their ID3 titles
2. A notification shows how many titles came from ID3 vs filenames
3. Edit any chapter name by clicking it and typing a new name
4. Click "Update Name" or press Ctrl+S

### Step 5: Generate!

Click "Generate M4B →" and you're done!

## Editing Chapter Names

Even with perfect ID3 titles, you can still edit them:

1. **Click any row** in the chapter table
2. **Edit the name** in the text box below
3. **Press Ctrl+S** or click "✓ Update Name"

### Quick Edits
- `Ctrl+S` - Update selected chapter
- `Delete` - Delete selected chapter  
- `F2` - Auto-name all (Chapter 1, 2, 3...)

## Troubleshooting

### My files have titles but the app uses filenames

**Problem**: MP3 files might not have ID3v2 tags

**Solution**: 
1. Run the test script to verify: `python test_titles.py "your/folder"`
2. Use Mp3tag (Windows) or Kid3 to add proper ID3 tags
3. Make sure you're using ID3v2 (not ID3v1)

### Some chapters show weird names

**Problem**: ID3 titles might have unusual characters

**Solution**: 
Just edit them in the Chapter Configuration screen!

### I want to use filenames instead

**Problem**: ID3 titles are wrong, but filenames are good

**Solution**: 
1. Clear ID3 tags using Mp3tag
2. OR: Name your files well and let the app clean them
3. OR: Just manually edit the chapter names in the app

## Example: Perfect Setup

### Your MP3 Files:
```
audiobook/
├── 01.mp3 (Title: "Introduction")
├── 02.mp3 (Title: "Chapter 1 - The Fundamentals")
├── 03.mp3 (Title: "Chapter 2 - Building Better Habits")
├── 04.mp3 (Title: "Chapter 3 - Breaking Bad Habits")
└── 05.mp3 (Title: "Conclusion")
```

### Result in M4B:
```
Audiobook Title
├─ Introduction
├─ Chapter 1 - The Fundamentals
├─ Chapter 2 - Building Better Habits
├─ Chapter 3 - Breaking Bad Habits
└─ Conclusion
```

Perfect! 🎉

## Benefits

✓ **No manual typing** - If your ID3 tags are set, chapter names are automatic
✓ **Clean names** - Smart cleaning removes junk but preserves good titles
✓ **Always editable** - Don't like a name? Change it before generating
✓ **Fallback** - Files without ID3 tags use filenames (cleaned up nicely)

## Quick Reference

| Source | Priority | Example | Result |
|--------|----------|---------|--------|
| ID3 Title | **1st** | "Introduction" | "Introduction" |
| Filename (if no ID3) | 2nd | "01-intro.mp3" | "intro" |
| Manual Edit | Override | User types "Intro Chapter" | "Intro Chapter" |

---

**Bottom Line**: Your MP3 files already have chapter names! The app reads them automatically. 🎵→📚

Run `python test_titles.py "your/folder"` to see what the app will use!

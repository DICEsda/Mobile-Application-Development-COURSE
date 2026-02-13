# Chapter Naming Guide

## How It Works

**Each MP3 file = One Chapter**

When you select a folder with MP3 files, the app automatically creates one chapter per file.

## Step-by-Step: Naming Your Chapters

### 1. Start the App
```bash
python m4b_generator.py
```
Or double-click `run.bat`

### 2. Navigate Through Setup
- Select your MP3 directory
- Search for book metadata (or skip)
- Review metadata

### 3. Chapter Naming Screen

You'll see a table with all your chapters:

```
┌───┬─────────────────────────┬──────────┬──────────┐
│ # │ Title                   │ Start    │ Duration │
├───┼─────────────────────────┼──────────┼──────────┤
│ 1 │ 01 Introduction.mp3     │ 00:00:00 │ 5m 30s   │
│ 2 │ 02 Chapter 1.mp3        │ 00:05:30 │ 12m 15s  │
│ 3 │ 03 Chapter 2.mp3        │ 00:17:45 │ 18m 42s  │
└───┴─────────────────────────┴──────────┴──────────┘
```

### 4. Edit Chapter Names

**Method 1: Click and Edit (Recommended)**
1. Click on any row in the table
2. The chapter name appears in the text box below
3. Type your new name
4. Click "✓ Update Name" or press `Ctrl+S`

**Method 2: Keyboard Shortcuts**
- `Ctrl+S` - Update the selected chapter
- `Delete` - Delete the selected chapter
- `F2` - Auto-name all chapters (Chapter 1, Chapter 2, etc.)
- `Escape` - Go back

### 5. Example Workflow

Let's rename chapters for "Atomic Habits":

1. Click row 1: "01 Introduction.mp3"
2. Edit to: "Introduction"
3. Press `Ctrl+S`

4. Click row 2: "02 Chapter 1.mp3"
5. Edit to: "Chapter 1 - The Surprising Power of Atomic Habits"
6. Press `Ctrl+S`

7. Continue for all chapters...

### 6. Quick Auto-Naming

If you just want simple numbered chapters:
- Click "🔄 Auto-name" button (or press F2)
- All chapters become: "Chapter 1", "Chapter 2", "Chapter 3", etc.

### 7. Generate Your Audiobook

Once all chapters are named:
- Click "Generate M4B →"
- Enter output filename
- Click "🚀 Generate"
- Done!

## Tips

### Good Chapter Names
✓ "Introduction"
✓ "Chapter 1 - The Basics"
✓ "Part 2: Advanced Concepts"
✓ "Epilogue"

### Names to Avoid
✗ "01.mp3" (file extensions)
✗ "track_001" (technical names)
✗ "" (empty names)

### Time-Saving Tricks

1. **Use Your Filenames**: Name your MP3 files properly before starting:
   ```
   Introduction.mp3
   Chapter 1 - The Basics.mp3
   Chapter 2 - Advanced Topics.mp3
   ```
   The app will use these as chapter names automatically!

2. **Bulk Rename Files**: Use Windows Explorer or a bulk rename tool to rename all MP3s before running the app

3. **Auto-name First, Then Edit**: 
   - Press F2 to auto-name all (Chapter 1, 2, 3...)
   - Then edit just the ones you want to customize

## Common Questions

**Q: Can I reorder chapters?**
A: Yes! Just rename your MP3 files with numbers (01, 02, 03) and the app will sort them automatically.

**Q: What if I make a mistake?**
A: Just click the row again and update it. You can change names as many times as you want before generating.

**Q: Can I delete chapters?**
A: Yes! Select a row and click "❌ Delete Chapter" or press Delete key. (You must have at least 1 chapter)

**Q: How do I see all my chapters?**
A: The table shows all chapters. Scroll if you have many files.

## Example Output

Your final M4B file will have chapters like this in your audiobook player:

```
Atomic Habits by James Clear

├─ Introduction (0:00 - 5:30)
├─ Chapter 1 - The Surprising Power... (5:30 - 17:45)
├─ Chapter 2 - How Your Habits Shape... (17:45 - 36:27)
├─ Chapter 3 - The Four Laws... (36:27 - 51:02)
└─ Conclusion (5:10:15 - 5:35:00)
```

## Need Help?

- Read QUICKSTART.md for full workflow
- Read README.md for complete documentation
- Check that FFmpeg is installed for generation step

---

**Remember**: The app automatically creates one chapter per MP3 file. Your job is just to give them nice names!

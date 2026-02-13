# Chapter Preview with Timeframes

## ✨ What You'll See

The **Chapter Configuration** screen shows a **full preview** of all your chapters with:

```
📋 CHAPTER PREVIEW - 18 Chapters with Timeframes

┌────┬─────────────────────────────────────────────┬───────────┬──────────┐
│  # │ Title                                       │ Start     │ Duration │
├────┼─────────────────────────────────────────────┼───────────┼──────────┤
│  1 │ Introduction                                │ 00:00:00  │ 5m 30s   │
│  2 │ Chapter 1 - The Surprising Power of Habits │ 00:05:30  │ 12m 15s  │
│  3 │ Chapter 2 - How Your Habits Shape Identity │ 00:17:45  │ 18m 42s  │
│  4 │ Chapter 3 - Building Better Habits         │ 00:36:27  │ 14m 35s  │
│  5 │ Chapter 4 - The Laws of Habit              │ 00:51:02  │ 22m 18s  │
│  ... (more chapters)                            │           │          │
│ 18 │ Conclusion                                  │ 5:10:15   │ 24m 45s  │
└────┴─────────────────────────────────────────────┴───────────┴──────────┘
```

## 📊 What Each Column Means

| Column | Description |
|--------|-------------|
| **#** | Chapter number (1, 2, 3...) |
| **Title** | Chapter name (from ID3 tags or filename) |
| **Start** | When this chapter begins (HH:MM:SS) |
| **Duration** | How long this chapter runs |

## 🎯 How to Use It

### 1. **View All Chapters**
- The table shows **every chapter** with complete timeframes
- Scroll if you have many chapters
- This is your **exact preview** of what will be in the M4B!

### 2. **Edit a Chapter Name**
1. **Click any row** in the table
2. The chapter name appears in the text box below
3. Type your new name
4. Click **"✓ Update Name"** or press **Ctrl+S**

### 3. **Delete a Chapter**
1. Click the row you want to remove
2. Click **"❌ Delete Chapter"** or press **Delete key**
3. Timeframes automatically adjust!

### 4. **Auto-name All**
- Click **"🔄 Auto-name"** or press **F2**
- All chapters become: "Chapter 1", "Chapter 2", etc.
- Then edit individual ones if needed

## 📝 Example Workflow

### Step 1: Review the Preview
```
You see:
1. Introduction (0:00 - 5:30)
2. 01-chapter-one (5:30 - 17:45)  ← Needs renaming
3. 02-chapter-two (17:45 - 36:27) ← Needs renaming
4. Conclusion (36:27 - 51:02)
```

### Step 2: Fix Chapter 2
1. Click row 2
2. Change "01-chapter-one" to "Chapter 1 - The Fundamentals"
3. Press Ctrl+S
4. ✓ Chapter updated!

### Step 3: Fix Chapter 3
1. Click row 3
2. Change "02-chapter-two" to "Chapter 2 - Advanced Topics"
3. Press Ctrl+S
4. ✓ Chapter updated!

### Step 4: Verify and Generate
```
Final preview:
1. Introduction (0:00 - 5:30)         ← Perfect!
2. Chapter 1 - The Fundamentals (5:30 - 17:45)  ← Fixed!
3. Chapter 2 - Advanced Topics (17:45 - 36:27)  ← Fixed!
4. Conclusion (36:27 - 51:02)         ← Perfect!
```

Click **"Generate M4B →"** ✨

## 🔍 Understanding the Timeframes

### Start Times
- **Cumulative**: Each chapter starts where the previous one ended
- **Format**: HH:MM:SS (hours:minutes:seconds)
- **Example**: 
  - Chapter 1: 00:00:00 (starts at beginning)
  - Chapter 2: 00:12:30 (starts 12m 30s in)
  - Chapter 3: 00:25:15 (starts 25m 15s in)

### Durations
- **Individual**: How long each chapter plays
- **Format**: Xh Ym or Ym Zs
- **Examples**:
  - "5m 30s" = 5 minutes 30 seconds
  - "1h 15m" = 1 hour 15 minutes
  - "45s" = 45 seconds

### Total Duration
Add up all durations to get your total audiobook length!

## ⚡ Keyboard Shortcuts

| Key | Action |
|-----|--------|
| **↑ ↓** | Navigate through chapters |
| **Click** | Select a chapter to edit |
| **Ctrl+S** | Save/update current chapter |
| **Delete** | Remove current chapter |
| **F2** | Auto-name all chapters |
| **Tab** | Switch between table and input |
| **Escape** | Go back (without generating) |

## 🎨 Visual Highlights

The table features:
- **Zebra striping**: Alternating row colors for easy reading
- **Cursor highlight**: Currently selected chapter is highlighted
- **Headers**: Bold column names at the top
- **Thick border**: Prominent display
- **Fixed height**: Shows ~15-20 chapters at once
- **Scrollable**: Scroll to see all chapters if you have many

## ✅ Before You Generate

The preview lets you verify:
- ✓ **All chapters are present**
- ✓ **Names are correct and clean**
- ✓ **Timeframes are accurate**
- ✓ **Order is correct**
- ✓ **No duplicates or gaps**

If something looks wrong, fix it now! Once generated, you'd have to regenerate to fix it.

## 💡 Pro Tips

### Tip 1: Check Start Times
If a chapter starts at 00:00:00 but it shouldn't be first, your MP3 files might be out of order. Rename files with numbers (01, 02, 03) before starting the app.

### Tip 2: Verify Durations
If a duration seems too short or too long, the MP3 file might be corrupted or wrong. Check your source files.

### Tip 3: Clean Names Now
It's much easier to fix chapter names here than in your audiobook player later!

### Tip 4: Use ID3 Titles
If your preview shows messy filenames, add proper ID3 titles to your MP3s first:
```bash
python test_titles.py "your/folder"  # Check current state
# Then edit with Mp3tag or similar tool
# Then run the app again
```

## 🚀 Example: Perfect Preview

```
📋 CHAPTER PREVIEW - 5 Chapters with Timeframes

#   Title                              Start      Duration
─────────────────────────────────────────────────────────
1   Introduction                       00:00:00   5m 30s
2   Chapter 1 - The Fundamentals       00:05:30   18m 15s
3   Chapter 2 - Advanced Techniques    00:23:45   22m 40s
4   Chapter 3 - Real World Examples    00:46:25   16m 55s
5   Conclusion                         01:03:20   8m 40s

Total: 1h 12m audiobook
```

**This is exactly what you'll get in your M4B file!** 🎉

## 🎧 What This Means in Your Player

When you open the M4B in your audiobook app:
- Swipe/tap to jump between chapters
- See chapter names and times
- Bookmark specific chapters
- Skip intro/outro easily

All thanks to this preview ensuring everything is perfect!

## ❓ Troubleshooting

### "I don't see the table"
- Make sure you reached the "Chapter Configuration" screen
- It's after: Directory → Metadata Search → Metadata Review → **Here!**

### "Table is empty"
- Your folder has no MP3 files
- Go back and select the correct folder

### "Timeframes look wrong"
- Check that MP3 files aren't corrupted
- Verify files with: `python test_titles.py "folder"`

### "Can't click rows"
- Table needs focus - click inside it first
- Use arrow keys to navigate
- Selected row is highlighted

## 📚 Related Docs

- **ID3_TITLES_INFO.md** - How titles are discovered
- **CHAPTER_NAMING_GUIDE.md** - Detailed naming guide
- **READY_TO_USE.md** - Complete quick start
- **README.md** - Full documentation

---

**Remember**: The preview is exactly what you'll get! Make it perfect before clicking Generate. 📖✨

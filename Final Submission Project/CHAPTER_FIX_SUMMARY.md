# Chapter Metadata Fix Summary

## Problem Identified

The Art of Seduction M4B file contains chapters in **QuickTime text track format** (stream index 1), which Android's Media3 library cannot properly read as ChapterFrame objects. The Android app's ChapterParser expects chapters in one of these formats:

1. **ChapterFrame** (ID3 chapters)
2. **MdtaMetadataEntry** with specific string format
3. **TextInformationFrame** with chapter info

## Root Cause

**From m4b Gen side (m4b_creator.py):**
- ffmpeg creates chapters using FFMETADATA1 format
- These are stored as a QuickTime text track
- The text track is visible to ffprobe but NOT to Media3's MetadataRetriever

**From Android App side (ChapterParser.kt):**
- Media3's MetadataRetriever doesn't expose QuickTime text track chapters
- The parser only finds chapters if they're in specific metadata atom formats
- Without mp4chaps, there are no Nero chapter atoms (chpl)

## Solutions Implemented

### Solution 1: Add MDTA Metadata Entries (m4b Gen)

**Files Modified:**
- `m3b Gen/core/m4b_creator.py`

**Changes:**

1. **Added MDTA format generator** (m4b_creator.py:337-357)
   ```python
   def _format_chapter_mdta(self, chapter_num: int, chapter) -> str:
       """Format: Menu <number># _<HH>_<MM>_<SS>_<mmm>: <title>"""
       # Converts chapter data to Android-parseable format
   ```

2. **Modified _run_ffmpeg to add chapter metadata** (m4b_creator.py:303-315)
   ```python
   for i, chapter in enumerate(chapter_manager.chapters, start=1):
       chapter_str = self._format_chapter_mdta(i, chapter)
       cmd.extend(["-metadata", f"chapter{i}={chapter_str}"])
       cmd.extend(["-metadata", f"©chp{i}={chapter_str}"])
   ```

**Format Examples:**
```
chapter1=Menu 1# _00_00_00_000: Preface
chapter2=Menu 2# _00_33_30_687: Part One - the Seductive Character
©chp1=Menu 1# _00_00_00_000: Preface
©chp2=Menu 2# _00_33_30_687: Part One - the Seductive Character
```

### Solution 2: Improved Android Parser

**Files Modified:**
- `app/src/main/java/com/audiobook/app/data/parser/ChapterParser.kt`

**Changes:**

1. **Added fallback chapter extraction** (ChapterParser.kt:78-95)
   - If Media3's MetadataRetriever finds no chapters, tries alternative method
   - Provides graceful degradation

2. **Enhanced MDTA parsing with better logging** (ChapterParser.kt:246-268)
   - More robust string parsing
   - Better error handling
   - Detailed logging to help debug issues
   - Checks for keys: "chapter", "©chp", "chp"

3. **Improved parseChapterString patterns** (already existed, now better documented)
   - Pattern 1: `"Menu <number># _<HH>_<MM>_<SS>_<mmm>: <title>"` ← **We generate this**
   - Pattern 2: `"_<HH>_<MM>_<SS>_<mmm>: <title>"`
   - Pattern 3: `"<HH>-<MM>-<SS>-<mmm>: <title>"`

## Testing

### Test 1: Verify MDTA Format Generation

```bash
cd "m3b Gen"
python test_mdta_format.py
```

**Expected Output:**
```
Chapter 1:
  Input: title='Preface', start_ms=0
  MDTA:  Menu 1# _00_00_00_000: Preface
  [OK] Format is CORRECT
```

### Test 2: Regenerate Art of Seduction M4B

1. Run the m4b generator with the Art of Seduction source files
2. The generator will now add MDTA entries automatically

### Test 3: Verify MDTA Entries in M4B

```bash
ffprobe -v quiet -show_format -of json "your_new_book.m4b"
```

Look for entries like:
```json
"tags": {
    "chapter1": "Menu 1# _00_00_00_000: Preface",
    "chapter2": "Menu 2# _00_33_30_687: Part One...",
    "©chp1": "Menu 1# _00_00_00_000: Preface"
}
```

### Test 4: Test on Android

1. Build and install the updated Android app
2. Copy the new M4B file to your device
3. Open the audiobook in the app
4. Check logcat for chapter parsing messages:

```bash
adb logcat | grep ChapterParser
```

**Look for:**
```
Found MdtaMetadataEntry: key='chapter1', value='Menu 1# _00_00_00_000: Preface'
Parsing chapter MDTA string: 'Menu 1# _00_00_00_000: Preface'
Successfully parsed chapter: Preface @ 0ms
```

### Test 5: Verify Chapters Appear in UI

- Open the audiobook detail screen
- Check if chapters are listed
- Try navigating between chapters
- Verify timestamps are correct

## Troubleshooting

### Issue: Chapters still don't appear

**Check 1: Are MDTA entries in the M4B?**
```bash
ffprobe -v quiet -show_format -of json "your_book.m4b" | grep chapter
```

- If YES → MDTA entries were written successfully
- If NO → ffmpeg didn't write the metadata (try mp4chaps instead)

**Check 2: Is Android parsing them?**
```bash
adb logcat | grep "ChapterParser"
```

- Look for "Found MdtaMetadataEntry" messages
- Check for parsing errors or warnings

**Check 3: Format verification**
- The MDTA value must exactly match: `Menu <num># _HH_MM_SS_mmm: <title>`
- Check for encoding issues (must be UTF-8)
- Verify timestamps are correct

### Issue: MDTA entries not written by ffmpeg

**Cause:** ffmpeg's `-metadata` flag might not write custom keys to MDTA atoms on some versions.

**Solution:** Use mp4chaps (see `ANDROID_CHAPTERS_FIX.md`):
```bash
# Windows
choco install mp4v2

# Linux
sudo apt install mp4v2-utils

# Mac
brew install mp4v2
```

The generator will automatically detect and use mp4chaps to add Nero chapters.

### Issue: Chapters have wrong timestamps

**Cause:** Source MP3 files might have incorrect durations or be in wrong order.

**Solution:** 
```bash
cd "m3b Gen"
python test_titles.py "Robert Greene - The Art of Seduction - mp3"
```

Check if files are in correct order and have proper ID3 tags.

## File Changes Summary

### Modified Files

1. **m3b Gen/core/m4b_creator.py**
   - Added `_format_chapter_mdta()` method
   - Modified `_run_ffmpeg()` to add MDTA metadata entries
   - Updated method signature to pass `chapter_manager`

2. **app/src/main/java/com/audiobook/app/data/parser/ChapterParser.kt**
   - Added `extractChaptersFallback()` method
   - Enhanced MDTA parsing with better logging
   - Improved error handling and debugging

### New Files

3. **m3b Gen/test_mdta_format.py**
   - Test script to verify MDTA format generation
   - Validates timestamp calculations
   - Confirms format matches Android parser expectations

4. **m3b Gen/MDTA_CHAPTERS_INFO.md**
   - Detailed documentation of MDTA chapter format
   - How it works end-to-end
   - Troubleshooting guide

5. **CHAPTER_FIX_SUMMARY.md** (this file)
   - Complete summary of all changes
   - Testing procedures
   - Troubleshooting guide

## Next Steps

1. **Test the MDTA format generator:**
   ```bash
   cd "m3b Gen"
   python test_mdta_format.py
   ```

2. **Regenerate Art of Seduction M4B:**
   - Run the m4b generator
   - It will automatically add MDTA entries

3. **Build and install updated Android app:**
   ```bash
   cd app
   ./gradlew installDebug
   ```

4. **Test on Android device:**
   - Copy new M4B to device
   - Open in app
   - Verify chapters appear
   - Check logcat for parsing messages

5. **If MDTA doesn't work:**
   - Install mp4chaps
   - Regenerate M4B (will use Nero chapters instead)
   - Test again

## Technical Details

### Why Two Keys? (chapter1 AND ©chp1)

MP4 metadata standards have variations:
- `chapter1`, `chapter2` - Generic custom metadata
- `©chp1`, `©chp2` - Specialized chapter keys (© prefix used in some MP4 metadata)

We add both to maximize compatibility. The Android parser checks for either format.

### Why This Format? `Menu 2# _00_00_00_000: Title`

This format was reverse-engineered from the Android app's `parseChapterString()` method (ChapterParser.kt:288-341). It expects:
- `Menu <number>#` - Chapter number indicator
- `_HH_MM_SS_mmm` - Timestamp with underscores
- `: ` - Separator
- `<title>` - Chapter title

This is a custom format, likely from a specific audiobook creation tool.

### Alternative: Nero Chapters (mp4chaps)

If MDTA doesn't work, mp4chaps creates **Nero chapter atoms** (chpl) which are the industry standard:
- Used by iTunes, Audible, most audiobook players
- Natively supported by iOS, macOS, most Android players
- More reliable than custom MDTA entries

The m4b_creator.py already has code to use mp4chaps if available (lines 359-419).

## Comparison: MDTA vs Nero vs Text Track

| Format | Created By | Android Support | Reliability |
|--------|-----------|-----------------|-------------|
| Text Track (current) | ffmpeg FFMETADATA1 | ❌ No | Low |
| MDTA entries (new) | ffmpeg -metadata | ⚠️ Maybe | Medium |
| Nero chapters (best) | mp4chaps | ✅ Yes | High |

**Recommendation:** Use mp4chaps for production audiobooks. Use MDTA as a fallback if mp4chaps isn't available.

---

## Summary

✅ **m4b Gen:** Now adds MDTA metadata entries in Android-compatible format  
✅ **Android App:** Enhanced parsing with fallback methods and better logging  
✅ **Testing:** Scripts and procedures to verify everything works  
✅ **Documentation:** Complete guides for troubleshooting  

The chapters should now work! If MDTA entries aren't sufficient, install mp4chaps for guaranteed compatibility.

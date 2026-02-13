# MDTA Chapter Metadata for Android Compatibility

## What Changed

Modified `m4b_creator.py` to add **MDTA metadata entries** in the format that the Android app's `ChapterParser.kt` expects.

## Format Details

The Android app's ChapterParser (lines 196-209, 239-292 in ChapterParser.kt) looks for MDTA metadata entries with:
- Keys containing: `"chapter"` or `"©chp"`
- Value format: `"Menu <number># _<HH>_<MM>_<SS>_<mmm>: <title>"`

### Example MDTA Entries

```
chapter1=Menu 1# _00_00_00_000: Preface
chapter2=Menu 2# _00_33_30_687: Part One - the Seductive Character
chapter3=Menu 3# _09_26_10_954: The Seducer's Victim
©chp1=Menu 1# _00_00_00_000: Preface
©chp2=Menu 2# _00_33_30_687: Part One - the Seductive Character
©chp3=Menu 3# _09_26_10_954: The Seducer's Victim
```

## How It Works

### 1. Generation (m4b_creator.py:303-315)
```python
# Add MDTA chapter metadata entries for Android compatibility
for i, chapter in enumerate(chapter_manager.chapters, start=1):
    chapter_str = self._format_chapter_mdta(i, chapter)
    cmd.extend(["-metadata", f"chapter{i}={chapter_str}"])
    cmd.extend(["-metadata", f"©chp{i}={chapter_str}"])
```

### 2. Format Function (m4b_creator.py:337-357)
Converts chapter data to MDTA string:
- `start_ms` → `_HH_MM_SS_mmm` timestamp format
- Adds chapter number and title

### 3. Android Parsing (ChapterParser.kt:196-209)
```kotlin
is MdtaMetadataEntry -> {
    if (entry.key.contains("chapter", ignoreCase = true) || 
        entry.key.contains("©chp", ignoreCase = true)) {
        entry.value?.let { valueBytes ->
            val valueStr = String(valueBytes, Charsets.UTF_8)
            parseChapterString(valueStr)?.let { rawChapterData.add(it) }
        }
    }
}
```

## Testing

### 1. Test Format Generation
```bash
cd "m3b Gen"
python test_mdta_format.py
```

Expected output:
```
Chapter 1:
  Input: title='Preface', start_ms=0
  MDTA:  Menu 1# _00_00_00_000: Preface
  [OK] Format is CORRECT
```

### 2. Regenerate M4B File
Run the m4b generator to create a new audiobook with MDTA chapters.

### 3. Verify Metadata in M4B
```bash
ffprobe -v quiet -show_format -of json "your_book.m4b" | grep -A5 "tags"
```

Look for `chapter1`, `chapter2`, etc. in the tags section.

### 4. Test on Android
1. Copy the new M4B to your Android device
2. Open in the audiobook app
3. Check if chapters now appear correctly

## Alternative: mp4chaps

If MDTA entries don't work, you can still use **mp4chaps** (the recommended method):
- Windows: Download from https://github.com/enzo1982/mp4v2/releases
- Linux: `sudo apt install mp4v2-utils`
- Mac: `brew install mp4v2`

The generator will automatically detect and use mp4chaps if available (see `_add_mp4_chapters` method).

## Troubleshooting

### Chapters still don't show on Android

1. **Check if MDTA entries were written:**
   ```bash
   ffprobe -v quiet -show_format -of json "your_book.m4b"
   ```
   Look for `chapter1`, `chapter2`, etc.

2. **Check Android logs:**
   The ChapterParser logs what it finds:
   ```
   Found MdtaMetadataEntry: key=chapter1, value=...
   ```

3. **Fallback to mp4chaps:**
   If ffmpeg's `-metadata` doesn't create proper MDTA atoms, install mp4chaps.

### Why both chapter1 AND ©chp1?

- `chapter1`, `chapter2`, etc. - Standard custom metadata
- `©chp1`, `©chp2`, etc. - Alternative format (© prefix is used in some MP4 metadata standards)
- We add both to maximize compatibility

The Android app checks for either format, so this gives us two chances for it to work.

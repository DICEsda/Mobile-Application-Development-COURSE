# Local Storage Clarification - Updates Complete

## ✅ What Was Fixed

The diagrams and report text now **clearly show and explain** that audiobook files are stored locally on the device's filesystem, not in the cloud or database.

---

## 📊 Diagram Updates

### 1. Component Diagram
**Added:**
- 🟠 **"Device Storage (M4B/M4A Files)"** component
- Clear arrows showing:
  - `AudiobookScanner` → Device Storage (Scans M4B/M4A files)
  - `ExoPlayer` → Device Storage (Streams audio)
- Updated legend to include "Storage" layer

**Visual changes:**
- Device Storage shown as orange storage icon
- Distinct from Room DB (metadata) and DataStore (preferences)
- Clear data flow: Scanner reads files, ExoPlayer streams them

### 2. Sequence Diagram
**Added:**
- 🟠 **"Device Storage\n(M4B/M4A)"** participant
- Notes clarifying:
  - "Metadata only (file path, title, etc.)" when querying Room
  - "M4B/M4A file from /storage/emulated/0/Audiobooks/" when loading
  - "Sets file URI from device storage path" when preparing media
- Explicit `ExoPlayer` → `Device Storage` interaction: "Load audio file"

**Visual changes:**
- Device Storage color-coded orange (matching component diagram)
- Shows that Room DB returns file path, not audio data
- ExoPlayer explicitly requests audio stream from storage

---

## 📝 Report Text Updates

### LaTeX Report (`Project_Rapport.tex`)

#### Added to "Implemented Feature Overview":
```
Important: All audiobook files (M4B/M4A format) are stored locally 
on the device's filesystem. The app scans device storage directories 
(e.g., /storage/emulated/0/Audiobooks/) to discover available audiobooks. 
Room database stores only metadata (title, author, file path, cover art path) 
and playback progress—not the audio files themselves. ExoPlayer streams 
audio directly from local file URIs during playback.
```

#### Added to "Component Diagram" description:
```
Device Storage Integration: The AudiobookScanner component scans the 
device filesystem to discover M4B/M4A audiobook files stored locally. 
ExoPlayer streams audio directly from these local file paths during playback. 
Only metadata and progress are persisted in Room database—the actual audio 
files remain in device storage and are never copied or moved.
```

#### Enhanced "Sequence Diagram" explanation:
- Phase 1: "Room stores only metadata; the actual M4B/M4A file remains in device storage"
- Phase 2: "ExoPlayer loads the audio file directly from the device filesystem using the local file URI"
- Phase 5: "All chapter metadata is extracted from the M4B file itself during initial scanning"

### Markdown Report (`Project Rapport.md`)
Same clarifications added to the markdown version.

---

## 🎯 Key Clarifications Now Visible

### What's Stored WHERE:

| Component | Stores | Type |
|-----------|--------|------|
| **Device Storage** | M4B/M4A audio files | 🟠 Filesystem |
| **Room Database** | Metadata (title, author, file path, chapters) | 🟡 SQLite |
| **Room Database** | Playback progress (position, bookmarks) | 🟡 SQLite |
| **DataStore** | User preferences (speed, theme, etc.) | 🟡 Key-value |
| **Firestore** | Cloud sync progress (optional) | 🔷 Cloud |

### Data Flow Visualization:

```
1. App Launch
   ↓
2. AudiobookScanner scans → Device Storage (/Audiobooks/*.m4b)
   ↓
3. Metadata extracted and saved → Room Database
   ↓
4. User taps Play
   ↓
5. Room DB provides: file path + saved position
   ↓
6. ExoPlayer streams audio FROM → Device Storage (file://...)
   ↓
7. Progress updates saved TO → Room Database
```

---

## 📈 Updated Diagram Sizes

| Diagram | Old Size | New Size | Change |
|---------|----------|----------|--------|
| component_diagram.png | 90.7 KB | 106.4 KB | +15.7 KB (added storage) |
| sequence_playback.png | 198.6 KB | 240.5 KB | +41.9 KB (added storage interactions) |

---

## ✅ Verification

### Architecture is now clear:
- ✅ Audio files stored locally on device filesystem
- ✅ Room DB stores ONLY metadata and progress
- ✅ ExoPlayer streams directly from local files
- ✅ No audio data uploaded or downloaded
- ✅ AudiobookScanner discovers files via filesystem scan
- ✅ File paths (URIs) link metadata to actual files

### User journey is accurate:
1. User copies M4B files to device storage (via USB/download)
2. App scans and indexes these files
3. Metadata stored in Room
4. Playback streams from original file location
5. Progress saved to Room (and optionally synced to Firestore)

---

## 🎨 Visual Design

**Color coding:**
- 🔵 Blue = UI screens
- 🟠 Orange = ViewModel
- 🟣 Purple = Data repositories
- 🟢 Green = Playback services
- 🟡 Yellow = Local databases (Room, DataStore)
- 🟠 Orange = Device filesystem storage
- 🔷 Cyan = Cloud services

Device Storage uses **orange** to visually distinguish it as the source of audio content, separate from structured databases.

---

## 📤 For Overleaf

1. **Re-upload diagrams:**
   - `component_diagram.png` (106.4 KB - now shows Device Storage)
   - `sequence_playback.png` (240.5 KB - now shows file loading)

2. **Update LaTeX:**
   - Copy entire `Project_Rapport.tex` to Overleaf
   - New text clarifies local storage architecture

3. **Recompile:**
   - Diagrams will show Device Storage component
   - Text will explain file storage clearly

---

## 🎯 Summary

The documentation now **accurately reflects** that:
- Audiobook files live on device storage
- Only metadata lives in Room database
- ExoPlayer reads audio from local files
- No cloud storage of audio files (only progress sync)

This matches the actual Android implementation! ✅

---

**Updated:** March 25, 2026 @ 14:50 UTC

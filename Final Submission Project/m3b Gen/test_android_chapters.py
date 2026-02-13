#!/usr/bin/env python3
"""Test if M4B file has Android-compatible chapters."""

import subprocess
import sys
import json
from pathlib import Path


def check_ffmpeg_chapters(m4b_file: Path):
    """Check chapters using ffprobe."""
    print("🔍 Testing with ffprobe (shows what's in the file)...\n")

    cmd = [
        "ffprobe",
        "-v",
        "quiet",
        "-print_format",
        "json",
        "-show_chapters",
        "-show_format",
        str(m4b_file),
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        data = json.loads(result.stdout)

        # Check chapters
        chapters = data.get("chapters", [])
        if not chapters:
            print("❌ NO CHAPTERS FOUND by ffprobe!")
            print("   This means chapters weren't written to the MP4 file properly.\n")
            return False

        print(f"✅ Found {len(chapters)} chapters:\n")
        for i, chapter in enumerate(chapters[:5], 1):
            title = chapter.get("tags", {}).get("title", f"Chapter {i}")
            start = float(chapter.get("start_time", 0))
            print(f"  {i}. {title} @ {format_time(start)}")

        if len(chapters) > 5:
            print(f"  ... and {len(chapters) - 5} more")

        print("\n✅ Chapters exist in MP4 container")

        # Check format metadata
        fmt = data.get("format", {})
        tags = fmt.get("tags", {})

        print(f"\n📋 File format: {fmt.get('format_name', 'unknown')}")
        print(f"   Duration: {format_time(float(fmt.get('duration', 0)))}")

        return True

    except Exception as e:
        print(f"❌ Error: {e}")
        return False


def check_mp4_atoms(m4b_file: Path):
    """Check MP4 atoms structure."""
    print("\n🔬 Checking MP4 atoms (Android compatibility)...\n")

    # Try mp4dump if available
    try:
        result = subprocess.run(
            ["mp4dump", str(m4b_file)], capture_output=True, text=True, timeout=10
        )

        if result.returncode == 0:
            output = result.stdout.lower()

            # Check for chapter-related atoms
            has_chpl = "chpl" in output  # Nero chapters
            has_text = "text" in output  # QuickTime text track
            has_tref = "tref" in output  # Track references

            print("Chapter atom types found:")
            if has_chpl:
                print("  ✅ chpl (Nero chapters) - Android compatible!")
            else:
                print("  ❌ chpl (Nero chapters) - NOT FOUND")

            if has_text:
                print("  ✅ text track - QuickTime chapters")
            else:
                print("  ⚠️  text track - not found")

            if not has_chpl and not has_text:
                print("\n❌ NO Android-compatible chapter atoms found!")
                print("   This is why MediaInfo shows '_0_00_00' format.\n")
                return False

            return True

    except FileNotFoundError:
        print("⚠️  mp4dump not found (install mp4v2-utils for detailed atom inspection)")
    except Exception as e:
        print(f"⚠️  Could not check atoms: {e}")

    return None


def suggest_fixes():
    """Suggest fixes for chapter issues."""
    print("\n" + "=" * 60)
    print("🔧 HOW TO FIX ANDROID CHAPTER COMPATIBILITY")
    print("=" * 60)

    print("""
The issue is that ffmpeg's FFMETADATA1 format doesn't always create
MP4 chapter atoms that Android's Media3 can read.

📦 Install mp4chaps (RECOMMENDED):
   
   Windows:
   1. Download MP4v2 tools from: https://github.com/enzo1982/mp4v2/releases
   2. Extract mp4chaps.exe to your ffmpeg folder
   3. Or install via: choco install mp4v2
   
   Linux/Mac:
   sudo apt install mp4v2-utils     # Ubuntu/Debian
   brew install mp4v2               # macOS

🔄 After installing, re-run your M4B generator.
   The code will automatically use mp4chaps to add Android-compatible chapters.

✅ Test your M4B:
   python test_android_chapters.py "your_audiobook.m4b"

📱 Test on Android:
   1. Transfer M4B to your Android device
   2. Open in: Voice Audiobook Player, Smart AudioBook Player, etc.
   3. Chapters should now appear!
""")


def format_time(seconds: float) -> str:
    """Format seconds as HH:MM:SS."""
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = int(seconds % 60)
    return f"{hours:02d}:{minutes:02d}:{secs:02d}"


def main():
    """Main entry point."""
    if len(sys.argv) < 2:
        print("📚 Android Chapter Compatibility Tester\n")
        print("Usage: python test_android_chapters.py <path_to_m4b_file>\n")
        print("This script checks if your M4B has Android-compatible chapters.")
        print('Example: python test_android_chapters.py "output/My Book.m4b"')
        sys.exit(1)

    m4b_path = Path(sys.argv[1])

    if not m4b_path.exists():
        print(f"❌ File not found: {m4b_path}")
        sys.exit(1)

    print(f"📖 Testing: {m4b_path.name}\n")
    print("=" * 60)

    # Check with ffprobe
    has_chapters = check_ffmpeg_chapters(m4b_path)

    # Check atom structure
    is_android_compatible = check_mp4_atoms(m4b_path)

    # Summary
    print("\n" + "=" * 60)
    print("📊 SUMMARY")
    print("=" * 60)

    if not has_chapters:
        print("❌ File has NO chapters at all")
        print("   Problem: ffmpeg didn't write chapter metadata")
        suggest_fixes()
    elif is_android_compatible is False:
        print("⚠️  File has chapters, but NOT in Android-compatible format")
        print("   Problem: Chapters use wrong MP4 atom type")
        suggest_fixes()
    elif is_android_compatible is True:
        print("✅ File has proper Android-compatible chapters!")
        print("   Should work in Android audiobook players")
    else:
        print("⚠️  Could not verify Android compatibility")
        print("   Install mp4v2-utils for detailed testing")
        suggest_fixes()


if __name__ == "__main__":
    main()

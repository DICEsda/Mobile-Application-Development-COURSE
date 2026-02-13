#!/usr/bin/env python3
"""Verify chapters in generated M4B file."""

import subprocess
import sys
import json
from pathlib import Path


def check_chapters_ffprobe(m4b_file: Path):
    """Use ffprobe to inspect chapters in M4B file."""
    print(f"🔍 Inspecting: {m4b_file.name}\n")

    # Get chapters using ffprobe
    cmd = [
        "ffprobe",
        "-v",
        "quiet",
        "-print_format",
        "json",
        "-show_chapters",
        str(m4b_file),
    ]

    try:
        result = subprocess.run(cmd, capture_output=True, text=True, check=True)
        data = json.loads(result.stdout)

        if "chapters" not in data or not data["chapters"]:
            print("❌ No chapters found in file!")
            return

        chapters = data["chapters"]
        print(f"✅ Found {len(chapters)} chapters:\n")

        for i, chapter in enumerate(chapters, 1):
            # Get chapter info
            start_time = float(chapter.get("start_time", 0))
            end_time = float(chapter.get("end_time", 0))
            duration = end_time - start_time

            # Get title from tags
            tags = chapter.get("tags", {})
            title = tags.get("title", f"Chapter {i}")

            # Format times
            start_str = format_time(start_time)
            duration_str = format_duration(duration)

            print(f"  {i:2d}. {title}")
            print(f"      Start: {start_str} | Duration: {duration_str}")
            print(
                f"      (Raw: start={chapter.get('start_time')}s, end={chapter.get('end_time')}s)"
            )
            print()

        # Show total duration
        total_duration = float(chapters[-1].get("end_time", 0))
        print(f"📚 Total audiobook length: {format_duration(total_duration)}")

    except subprocess.CalledProcessError as e:
        print(f"❌ Error running ffprobe: {e}")
        print(f"   Make sure ffmpeg is installed and in PATH")
    except json.JSONDecodeError:
        print(f"❌ Error parsing ffprobe output")
    except Exception as e:
        print(f"❌ Unexpected error: {e}")


def format_time(seconds: float) -> str:
    """Format seconds as HH:MM:SS."""
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = int(seconds % 60)
    return f"{hours:02d}:{minutes:02d}:{secs:02d}"


def format_duration(seconds: float) -> str:
    """Format duration in human-readable format."""
    hours = int(seconds // 3600)
    minutes = int((seconds % 3600) // 60)
    secs = int(seconds % 60)

    parts = []
    if hours > 0:
        parts.append(f"{hours}h")
    if minutes > 0:
        parts.append(f"{minutes}m")
    if secs > 0 or not parts:
        parts.append(f"{secs}s")

    return " ".join(parts)


def main():
    """Main entry point."""
    if len(sys.argv) < 2:
        print("Usage: python verify_chapters.py <path_to_m4b_file>")
        print("\nExample:")
        print('  python verify_chapters.py "output/My Audiobook.m4b"')
        sys.exit(1)

    m4b_path = Path(sys.argv[1])

    if not m4b_path.exists():
        print(f"❌ File not found: {m4b_path}")
        sys.exit(1)

    if not m4b_path.suffix.lower() in [".m4b", ".m4a", ".mp4"]:
        print(f"⚠️  Warning: File doesn't have .m4b/.m4a/.mp4 extension")

    check_chapters_ffprobe(m4b_path)


if __name__ == "__main__":
    main()

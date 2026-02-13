#!/usr/bin/env python3
"""Test script to verify ID3 title extraction from MP3 files."""

import sys
from pathlib import Path
from core.audio_handler import AudioHandler


def test_mp3_titles(directory):
    """Test reading titles from MP3 files in a directory.

    Args:
        directory: Path to directory containing MP3 files
    """
    print("=" * 70)
    print("MP3 Title Extraction Test")
    print("=" * 70)
    print()

    # Create audio handler
    handler = AudioHandler(directory)

    # Scan directory
    print(f"Scanning: {directory}")
    audio_files = handler.scan_directory()

    if not audio_files:
        print("❌ No MP3 files found!")
        return

    print(f"✓ Found {len(audio_files)} MP3 file(s)")
    print()
    print("-" * 70)

    # Show details for each file
    for i, audio_file in enumerate(audio_files, 1):
        print(f"\n[{i}] {audio_file.filename}")
        print(f"    ID3 Title Tag: {audio_file.title or '(none)'}")
        print(f"    Duration: {handler.format_duration(audio_file.duration_seconds)}")

        if audio_file.title:
            print(f"    ✓ Will use: '{audio_file.title}' as chapter name")
        else:
            print(
                f"    ⚠ Will use: '{audio_file.filename}' as chapter name (no ID3 title)"
            )

    print()
    print("-" * 70)

    # Summary
    with_titles = sum(1 for f in audio_files if f.title)
    without_titles = len(audio_files) - with_titles

    print("\nSummary:")
    print(f"  Files with ID3 titles: {with_titles}")
    print(f"  Files without ID3 titles: {without_titles}")

    if with_titles == len(audio_files):
        print("\n✓ All files have ID3 titles! Chapter names will be perfect.")
    elif with_titles > 0:
        print(
            f"\n⚠ {without_titles} file(s) missing ID3 titles - will use filenames instead."
        )
    else:
        print("\n⚠ No ID3 titles found - all chapter names will come from filenames.")

    print()
    print("=" * 70)


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: python test_titles.py <directory>")
        print()
        print("Example:")
        print('  python test_titles.py "C:\\Users\\YourName\\Music\\Audiobook"')
        sys.exit(1)

    directory = Path(sys.argv[1])

    if not directory.exists():
        print(f"❌ Error: Directory not found: {directory}")
        sys.exit(1)

    if not directory.is_dir():
        print(f"❌ Error: Not a directory: {directory}")
        sys.exit(1)

    test_mp3_titles(directory)

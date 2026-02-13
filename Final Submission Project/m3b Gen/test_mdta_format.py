#!/usr/bin/env python3
"""Test MDTA format generation for Android compatibility."""

from core.chapter_manager import ChapterManager, Chapter
from pathlib import Path


def test_mdta_format():
    """Test that MDTA format matches what Android app expects."""

    # Create test chapters
    chapters = [
        Chapter(title="Preface", start_ms=0, duration_ms=2010687),
        Chapter(
            title="Part One - the Seductive Character",
            start_ms=2010687,
            duration_ms=31960267,
        ),
        Chapter(title="The Seducer's Victim", start_ms=33970954, duration_ms=2513893),
    ]

    # Test the format function
    from core.m4b_creator import M4BCreator

    creator = M4BCreator()

    print("Testing MDTA format generation:\n")
    print("=" * 70)

    for i, chapter in enumerate(chapters, start=1):
        mdta_str = creator._format_chapter_mdta(i, chapter)
        print(f"Chapter {i}:")
        print(f"  Input: title='{chapter.title}', start_ms={chapter.start_ms}")
        print(f"  MDTA:  {mdta_str}")

        # Verify format matches expected pattern
        # Pattern: "Menu <number># _<HH>_<MM>_<SS>_<mmm>: <title>"
        import re

        pattern = r"Menu (\d+)# _(\d{2})_(\d{2})_(\d{2})_(\d{3}): (.+)"
        match = re.match(pattern, mdta_str)

        if match:
            num, h, m, s, ms, title = match.groups()
            # Verify timestamp calculation
            calc_ms = (int(h) * 3600000) + (int(m) * 60000) + (int(s) * 1000) + int(ms)
            if calc_ms == chapter.start_ms and title == chapter.title:
                print(f"  [OK] Format is CORRECT")
            else:
                print(
                    f"  [ERROR] Format is WRONG - timestamp mismatch: {calc_ms} != {chapter.start_ms}"
                )
        else:
            print(f"  [ERROR] Format is WRONG - doesn't match pattern")

        print()

    print("=" * 70)
    print("\nExpected Android ChapterParser patterns:")
    print("1. Menu <number># _<HH>_<MM>_<SS>_<mmm>: <title>")
    print("2. _<HH>_<MM>_<SS>_<mmm>: <title>")
    print("3. <HH>-<MM>-<SS>-<mmm>: <title>")
    print("\nOur format uses pattern #1 (most specific)")


if __name__ == "__main__":
    test_mdta_format()

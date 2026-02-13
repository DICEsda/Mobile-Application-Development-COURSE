#!/usr/bin/env python3
"""Test cover preview functionality."""

import sys
from pathlib import Path
from typing import Optional
from utils.image_preview import ImagePreview, create_cover_display


def test_cover_preview(image_path: Optional[str] = None):
    """Test cover image preview."""
    print("Cover Preview Test\n")
    print("=" * 60)

    # Test 1: No image path
    print("\n1. Testing with no image:")
    result = create_cover_display(None)
    # Remove emojis for Windows console compatibility
    result_clean = result.encode("ascii", "ignore").decode("ascii")
    print(result_clean)

    # Test 2: Non-existent image
    print("\n2. Testing with non-existent image:")
    fake_path = Path("nonexistent.jpg")
    result = create_cover_display(fake_path)
    result_clean = result.encode("ascii", "ignore").decode("ascii")
    print(result_clean)

    # Test 3: Real image if provided
    if image_path:
        img_path = Path(image_path)
        if img_path.exists():
            print(f"\n3. Testing with real image: {img_path.name}")

            # Get image info
            info = ImagePreview.get_image_info(img_path)
            if info:
                print(f"\nImage Info:")
                print(f"  Dimensions: {info['width']}x{info['height']}px")
                print(f"  Format: {info['format']}")
                print(f"  Size: {info['size_kb']:.1f} KB")

            # Create preview
            print("\nGenerating preview...\n")
            result = create_cover_display(img_path, preview_width=35)
            # Keep the ASCII art but remove emojis
            print(result)
        else:
            print(f"\n3. Image not found: {image_path}")
    else:
        print("\n3. No image path provided - skipping real image test")
        print("   Usage: python test_cover_preview.py <path_to_image.jpg>")

    print("\n" + "=" * 60)
    print("Test complete!")


if __name__ == "__main__":
    image_path = sys.argv[1] if len(sys.argv) > 1 else None
    test_cover_preview(image_path)

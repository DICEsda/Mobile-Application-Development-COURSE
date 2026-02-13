"""Image preview utilities for terminal display."""

from pathlib import Path
from typing import Optional
from PIL import Image

# Optional import - graceful fallback if not installed
try:
    from term_image.image import AutoImage

    TERM_IMAGE_AVAILABLE = True
except ImportError:
    TERM_IMAGE_AVAILABLE = False


class ImagePreview:
    """Handle image preview generation for terminal display."""

    @staticmethod
    def get_image_info(image_path: Path) -> Optional[dict]:
        """Get image information.

        Args:
            image_path: Path to image file

        Returns:
            Dict with image info or None if error
        """
        try:
            with Image.open(image_path) as img:
                return {
                    "width": img.width,
                    "height": img.height,
                    "format": img.format,
                    "mode": img.mode,
                    "size_kb": image_path.stat().st_size / 1024,
                }
        except Exception:
            return None

    @staticmethod
    def create_terminal_preview(
        image_path: Path, width: int = 35, height: int = 18
    ) -> Optional[str]:
        """Create terminal image preview using term-image.

        Args:
            image_path: Path to image file
            width: Target width in characters
            height: Target height in characters

        Returns:
            Rendered image string or None if error
        """
        # Try term-image if available
        if TERM_IMAGE_AVAILABLE:
            try:
                # Create AutoImage which automatically selects best rendering method
                img = AutoImage.from_file(str(image_path))

                # Set size (term-image uses cells, not pixels)
                img.set_size(width=width, height=height)

                # Render to string
                return str(img)

            except Exception:
                pass  # Fall through to ASCII fallback

        # Fallback to simple ASCII
        return ImagePreview._create_simple_ascii(image_path, width, height)

    @staticmethod
    def _create_simple_ascii(
        image_path: Path, width: int = 30, height: int = 15
    ) -> Optional[str]:
        """Fallback simple ASCII preview.

        Args:
            image_path: Path to image file
            width: Target width
            height: Target height

        Returns:
            ASCII art or None
        """
        try:
            with Image.open(image_path) as img:
                # Convert to grayscale and resize
                img = img.convert("L")
                aspect = img.height / img.width
                new_height = int(width * aspect * 0.5)
                img = img.resize((width, new_height), Image.Resampling.LANCZOS)

                # Character ramp
                chars = " .:-=+*#%@"

                # Build ASCII
                lines = []
                for y in range(img.height):
                    row = []
                    for x in range(img.width):
                        pixel = img.getpixel((x, y))
                        brightness = pixel if isinstance(pixel, int) else 0
                        idx = int((brightness / 255) * (len(chars) - 1))
                        row.append(chars[idx])
                    lines.append("".join(row))

                return "\n".join(lines)
        except Exception:
            return None


def format_image_dimensions(info: dict) -> str:
    """Format image dimensions for display.

    Args:
        info: Image info dict

    Returns:
        Formatted dimension string
    """
    width = info["width"]
    height = info["height"]
    size_kb = info["size_kb"]
    format_str = info.get("format", "Unknown")

    return f"{width}×{height}px | {format_str} | {size_kb:.1f}KB"


def create_cover_display(image_path: Optional[Path], preview_width: int = 35) -> str:
    """Create a formatted cover display with preview and info.

    Args:
        image_path: Path to cover image or None
        preview_width: Width of preview in characters

    Returns:
        Formatted string for display
    """
    if not image_path or not image_path.exists():
        return "📷 No Cover Image\n\n[dim]Cover will be embedded if downloaded[/dim]"

    # Get image info
    info = ImagePreview.get_image_info(image_path)
    if not info:
        return "📷 Cover Image (Unable to load)\n\n[dim]File may be corrupted[/dim]"

    # Try to create terminal preview
    preview = ImagePreview.create_terminal_preview(
        image_path, width=preview_width, height=18
    )

    if not preview:
        # Fallback to just showing info
        preview = "🖼️  [Preview unavailable]"

    # Format output
    dim_str = format_image_dimensions(info)

    result = "📷 Cover Image Preview\n\n"
    result += f"[dim]{dim_str}[/dim]\n\n"
    result += preview
    result += "\n\n✅ Ready to embed"

    return result

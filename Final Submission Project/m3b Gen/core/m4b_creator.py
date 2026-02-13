"""M4B audiobook creation using ffmpeg."""

import subprocess
import tempfile
from pathlib import Path
from typing import List, Optional
from PIL import Image
import io

from .chapter_manager import ChapterManager
from .metadata_provider import BookMetadata
from .audio_handler import AudioFile


class M4BCreator:
    """Creates M4B audiobook files using ffmpeg."""

    def __init__(self):
        """Initialize M4B creator."""
        self._check_ffmpeg()

    def _check_ffmpeg(self) -> bool:
        """Check if ffmpeg is available.

        Returns:
            True if ffmpeg is found

        Raises:
            RuntimeError if ffmpeg is not found
        """
        try:
            result = subprocess.run(
                ["ffmpeg", "-version"], capture_output=True, text=True
            )
            return result.returncode == 0
        except FileNotFoundError:
            raise RuntimeError(
                "ffmpeg not found. Please install ffmpeg and ensure it's in your PATH."
            )

    def create_audiobook(
        self,
        audio_files: List[AudioFile],
        output_path: Path,
        metadata: BookMetadata,
        chapter_manager: ChapterManager,
        cover_image_path: Optional[Path] = None,
        narrator: Optional[str] = None,
        progress_callback: Optional[callable] = None,
    ) -> bool:
        """Create M4B audiobook file.

        Args:
            audio_files: List of audio files to concatenate
            output_path: Output path for M4B file
            metadata: Book metadata
            chapter_manager: Chapter manager with chapter information
            cover_image_path: Optional path to cover image
            narrator: Optional narrator name
            progress_callback: Optional callback for progress updates

        Returns:
            True if successful, False otherwise
        """
        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                temp_path = Path(temp_dir)

                # Step 1: Create concat file list
                if progress_callback:
                    progress_callback("Creating file list...")
                concat_file = self._create_concat_file(audio_files, temp_path)

                # Step 2: Create chapter metadata file
                if progress_callback:
                    progress_callback("Creating chapter metadata...")
                metadata_file = self._create_metadata_file(
                    chapter_manager, metadata, narrator, temp_path
                )

                # Step 3: Prepare cover image
                cover_file = None
                if cover_image_path and cover_image_path.exists():
                    if progress_callback:
                        progress_callback("Processing cover image...")
                    cover_file = self._prepare_cover_image(cover_image_path, temp_path)

                # Step 4: Build ffmpeg command
                if progress_callback:
                    progress_callback("Building audiobook...")

                success = self._run_ffmpeg(
                    concat_file,
                    metadata_file,
                    cover_file,
                    output_path,
                    chapter_manager,
                    progress_callback,
                )

                if not success:
                    return False

                # Step 5: Add MP4 native chapters for Android/Media3 compatibility
                if progress_callback:
                    progress_callback("Adding Android-compatible chapters...")

                self._add_mp4_chapters(chapter_manager, output_path, temp_path)

                if progress_callback:
                    progress_callback("Audiobook created successfully!")

                return True

        except Exception as e:
            if progress_callback:
                progress_callback(f"Error: {str(e)}")
            return False

    def _create_concat_file(
        self, audio_files: List[AudioFile], temp_path: Path
    ) -> Path:
        """Create ffmpeg concat file for multiple audio files.

        Args:
            audio_files: List of audio files
            temp_path: Temporary directory path

        Returns:
            Path to concat file
        """
        concat_file = temp_path / "concat.txt"

        with open(concat_file, "w", encoding="utf-8") as f:
            for audio_file in audio_files:
                # Use absolute path and escape special characters
                file_path = str(audio_file.path.absolute()).replace("\\", "/")
                f.write(f"file '{file_path}'\n")

        return concat_file

    def _create_metadata_file(
        self,
        chapter_manager: ChapterManager,
        metadata: BookMetadata,
        narrator: Optional[str],
        temp_path: Path,
    ) -> Path:
        """Create ffmpeg metadata file with chapters and book info.

        Args:
            chapter_manager: Chapter manager
            metadata: Book metadata
            narrator: Optional narrator name
            temp_path: Temporary directory path

        Returns:
            Path to metadata file
        """
        metadata_file = temp_path / "metadata.txt"

        # Start with ffmpeg metadata format
        content = ";FFMETADATA1\n"

        # Add book metadata
        content += f"title={metadata.title}\n"
        if metadata.authors:
            content += f"artist={', '.join(metadata.authors)}\n"
            content += f"album_artist={', '.join(metadata.authors)}\n"

        content += f"album={metadata.title}\n"

        if narrator:
            content += f"composer={narrator}\n"

        if metadata.publication_year:
            content += f"date={metadata.publication_year}\n"

        if metadata.description:
            # Escape special characters in description
            desc = metadata.description.replace("\n", " ").replace(";", ",")
            content += f"comment={desc}\n"

        content += "genre=Audiobook\n"

        # Add chapters
        content += "\n"
        for chapter in chapter_manager.chapters:
            content += chapter.to_ffmpeg_format()
            content += "\n"

        with open(metadata_file, "w", encoding="utf-8") as f:
            f.write(content)

        return metadata_file

    def _prepare_cover_image(self, cover_path: Path, temp_path: Path) -> Path:
        """Prepare and optimize cover image.

        Args:
            cover_path: Path to original cover image
            temp_path: Temporary directory path

        Returns:
            Path to prepared cover image
        """
        output_path = temp_path / "cover.jpg"

        try:
            # Open and resize image if needed
            with Image.open(cover_path) as img:
                # Convert to RGB if needed
                if img.mode in ("RGBA", "LA", "P"):
                    # Create white background
                    background = Image.new("RGB", img.size, (255, 255, 255))
                    if img.mode == "P":
                        img = img.convert("RGBA")
                    background.paste(
                        img,
                        mask=img.split()[-1] if img.mode in ("RGBA", "LA") else None,
                    )
                    img = background
                elif img.mode != "RGB":
                    img = img.convert("RGB")

                # Resize if too large (max 1400x1400 is good for audiobooks)
                max_size = 1400
                if img.width > max_size or img.height > max_size:
                    img.thumbnail((max_size, max_size), Image.Resampling.LANCZOS)

                # Save as JPEG with good quality
                img.save(output_path, "JPEG", quality=90, optimize=True)

            return output_path

        except Exception as e:
            print(f"Warning: Could not process cover image: {e}")
            # Return original if processing fails
            return cover_path

    def _run_ffmpeg(
        self,
        concat_file: Path,
        metadata_file: Path,
        cover_file: Optional[Path],
        output_path: Path,
        chapter_manager: ChapterManager,
        progress_callback: Optional[callable] = None,
    ) -> bool:
        """Run ffmpeg to create M4B file.

        Args:
            concat_file: Path to concat file
            metadata_file: Path to metadata file
            cover_file: Optional path to cover image
            output_path: Output path for M4B
            chapter_manager: Chapter manager with chapter information
            progress_callback: Optional callback for progress

        Returns:
            True if successful
        """
        # Ensure output directory exists
        output_path.parent.mkdir(parents=True, exist_ok=True)

        # Build ffmpeg command
        cmd = [
            "ffmpeg",
            "-f",
            "concat",
            "-safe",
            "0",
            "-i",
            str(concat_file),
        ]

        # Add cover image if provided
        if cover_file:
            cmd.extend(["-i", str(cover_file)])

        cmd.extend(
            [
                "-i",
                str(metadata_file),
                "-map",
                "0:a",  # Map audio from concat
            ]
        )

        if cover_file:
            cmd.extend(
                [
                    "-map",
                    "1:v",  # Map video (cover) from second input
                    "-disposition:v:0",
                    "attached_pic",
                ]
            )

        cmd.extend(
            [
                "-map_metadata",
                "2",  # Map metadata from third input
            ]
        )

        # Add MDTA chapter metadata entries for Android compatibility
        # Format: "Menu <number># _<HH>_<MM>_<SS>_<mmm>: <title>"
        for i, chapter in enumerate(chapter_manager.chapters, start=1):
            chapter_str = self._format_chapter_mdta(i, chapter)
            cmd.extend(["-metadata", f"chapter{i}={chapter_str}"])
            # Also add with ©chp prefix for additional compatibility
            cmd.extend(["-metadata", f"©chp{i}={chapter_str}"])

        cmd.extend(
            [
                "-c:a",
                "copy",  # Copy audio codec (no re-encoding)
            ]
        )

        if cover_file:
            cmd.extend(["-c:v", "copy"])  # Copy video codec

        cmd.extend(
            [
                "-f",
                "mp4",  # Output format
                "-y",  # Overwrite output file
                str(output_path),
            ]
        )

        try:
            # Run ffmpeg
            result = subprocess.run(cmd, capture_output=True, text=True)

            if result.returncode != 0:
                error_msg = result.stderr
                if progress_callback:
                    progress_callback(f"ffmpeg error: {error_msg}")
                return False

            return True

        except Exception as e:
            if progress_callback:
                progress_callback(f"Error running ffmpeg: {str(e)}")
            return False

    def _format_chapter_mdta(self, chapter_num: int, chapter) -> str:
        """Format chapter in MDTA format for Android compatibility.

        Format: "Menu <number># _<HH>_<MM>_<SS>_<mmm>: <title>"

        Args:
            chapter_num: Chapter number (1-indexed)
            chapter: Chapter object

        Returns:
            Formatted chapter string
        """
        # Convert milliseconds to hours, minutes, seconds, milliseconds
        total_ms = chapter.start_ms
        milliseconds = total_ms % 1000
        total_seconds = total_ms // 1000
        hours = total_seconds // 3600
        minutes = (total_seconds % 3600) // 60
        seconds = total_seconds % 60

        # Format: "Menu 2# _00_00_00_000: Preface"
        return f"Menu {chapter_num}# _{hours:02d}_{minutes:02d}_{seconds:02d}_{milliseconds:03d}: {chapter.title}"

    def _add_mp4_chapters(
        self, chapter_manager: ChapterManager, output_path: Path, temp_path: Path
    ) -> None:
        """Add MP4 native chapters using mp4chaps for Android/Media3 compatibility.

        Args:
            chapter_manager: Chapter manager with chapter information
            output_path: Path to the M4B file
            temp_path: Temporary directory path
        """
        try:
            # Create a chapters.txt file in mp4chaps format
            # Format: HH:MM:SS.mmm Chapter Title
            chapters_file = temp_path / "chapters.txt"

            with open(chapters_file, "w", encoding="utf-8") as f:
                for chapter in chapter_manager.chapters:
                    timestamp = ChapterManager.format_timestamp(chapter.start_ms)
                    # mp4chaps format: HH:MM:SS.mmm Title (space-separated, trim milliseconds format)
                    # Remove trailing zeros from milliseconds for cleaner format
                    time_parts = timestamp.rsplit(".", 1)
                    if len(time_parts) == 2:
                        base_time, ms = time_parts
                        ms = ms.rstrip("0").rstrip(".")  # Remove trailing zeros
                        if ms:
                            timestamp = f"{base_time}.{ms}"
                        else:
                            timestamp = base_time
                    f.write(f"{timestamp} {chapter.title}\n")

            # Try to use mp4chaps (creates Nero chapters - best Android compatibility)
            try:
                # Import chapters using mp4chaps
                # -i = import, -z = use specific file
                result = subprocess.run(
                    ["mp4chaps", "--import", str(output_path)],
                    capture_output=True,
                    text=True,
                    timeout=30,
                    cwd=str(temp_path),  # Run in temp dir where chapters.txt is
                )

                if result.returncode == 0:
                    print("✅ Added Nero chapters using mp4chaps (Android compatible)")
                    return  # Success with mp4chaps
                else:
                    print(f"⚠️  mp4chaps warning: {result.stderr}")

            except FileNotFoundError:
                print("⚠️  mp4chaps not found - chapters may not work on Android")
                print("   Install mp4v2-utils for Android compatibility")
                print("   Windows: https://github.com/enzo1982/mp4v2/releases")
                print("   Linux: sudo apt install mp4v2-utils")
            except subprocess.TimeoutExpired:
                print("⚠️  mp4chaps timed out")

        except Exception as e:
            # Don't fail the whole process if chapter addition fails
            # The file will still work, just without Android-visible chapters
            print(f"⚠️  Warning: Could not add Android-compatible chapters: {e}")
            print("   File will work but chapters may not show on Android devices")

    @staticmethod
    def generate_output_filename(metadata: BookMetadata) -> str:
        """Generate a clean output filename from metadata.

        Args:
            metadata: Book metadata

        Returns:
            Suggested filename (without extension)
        """
        title = metadata.title
        authors = ", ".join(metadata.authors) if metadata.authors else "Unknown"

        # Clean up filename (remove invalid characters)
        filename = f"{title} ({authors})"

        # Remove or replace invalid filename characters
        invalid_chars = '<>:"/\\|?*'
        for char in invalid_chars:
            filename = filename.replace(char, "")

        # Replace multiple spaces with single space
        filename = " ".join(filename.split())

        return filename

"""Chapter management for audiobooks."""

from typing import List, Optional
from dataclasses import dataclass, field
from pathlib import Path
import re


@dataclass
class Chapter:
    """Represents a chapter in an audiobook."""

    title: str
    start_ms: int  # Start time in milliseconds
    duration_ms: Optional[int] = None  # Duration in milliseconds (optional)
    file_path: Optional[Path] = None  # Source file for file-based chapters

    def __post_init__(self):
        """Clean up chapter title."""
        self.title = self._clean_title(self.title)

    @staticmethod
    def _clean_title(title: str) -> str:
        """Clean up chapter title by removing common prefixes and extensions.

        Only cleans titles that look like filenames. Preserves proper ID3 titles.

        Args:
            title: Original title

        Returns:
            Cleaned title
        """
        original = title

        # Remove file extension (only if present - indicates filename)
        has_extension = bool(re.search(r"\.(mp3|m4a|m4b)$", title, flags=re.IGNORECASE))
        title = re.sub(r"\.(mp3|m4a|m4b)$", "", title, flags=re.IGNORECASE)

        # Only do aggressive cleaning if it looks like a filename
        # (has extension or starts with track numbers like "01 - " or "Track 01")
        if has_extension or re.match(
            r"^(track\s*)?\d+[\s\-_\.]+", title, flags=re.IGNORECASE
        ):
            # Remove leading track numbers and common separators (e.g., "01 - ", "Track 01 - ")
            title = re.sub(r"^(track\s*)?\d+[\s\-_\.]+", "", title, flags=re.IGNORECASE)

        # Always do light cleaning: replace underscores and multiple spaces
        title = re.sub(r"[_]+", " ", title)
        title = re.sub(r"\s+", " ", title)

        result = title.strip()

        # If cleaning resulted in empty string, return original
        return result if result else original

    def to_ffmpeg_format(self) -> str:
        """Convert chapter to ffmpeg metadata format.

        Returns:
            Formatted string for ffmpeg chapter metadata
        """
        end_ms = self.start_ms + self.duration_ms if self.duration_ms else self.start_ms
        return f"""[CHAPTER]
TIMEBASE=1/1000
START={self.start_ms}
END={end_ms}
title={self.title}
"""


@dataclass
class ChapterManager:
    """Manages chapters for an audiobook."""

    chapters: List[Chapter] = field(default_factory=list)
    last_create_stats: dict = field(default_factory=dict)

    def add_chapter(
        self,
        title: str,
        start_ms: int,
        duration_ms: Optional[int] = None,
        file_path: Optional[Path] = None,
    ) -> Chapter:
        """Add a new chapter.

        Args:
            title: Chapter title
            start_ms: Start time in milliseconds
            duration_ms: Optional duration in milliseconds
            file_path: Optional source file path

        Returns:
            Created Chapter object
        """
        chapter = Chapter(
            title=title, start_ms=start_ms, duration_ms=duration_ms, file_path=file_path
        )
        self.chapters.append(chapter)
        return chapter

    def remove_chapter(self, index: int) -> None:
        """Remove a chapter by index.

        Args:
            index: Chapter index to remove
        """
        if 0 <= index < len(self.chapters):
            self.chapters.pop(index)

    def update_chapter(
        self,
        index: int,
        title: Optional[str] = None,
        start_ms: Optional[int] = None,
        duration_ms: Optional[int] = None,
    ) -> None:
        """Update a chapter's properties.

        Args:
            index: Chapter index
            title: Optional new title
            start_ms: Optional new start time
            duration_ms: Optional new duration
        """
        if 0 <= index < len(self.chapters):
            chapter = self.chapters[index]
            if title is not None:
                chapter.title = Chapter._clean_title(title)
            if start_ms is not None:
                chapter.start_ms = start_ms
            if duration_ms is not None:
                chapter.duration_ms = duration_ms

    def reorder_chapter(self, from_index: int, to_index: int) -> None:
        """Move a chapter from one position to another.

        Args:
            from_index: Current chapter index
            to_index: Target chapter index
        """
        if 0 <= from_index < len(self.chapters) and 0 <= to_index < len(self.chapters):
            chapter = self.chapters.pop(from_index)
            self.chapters.insert(to_index, chapter)

    def sort_by_start_time(self) -> None:
        """Sort chapters by start time."""
        self.chapters.sort(key=lambda c: c.start_ms)

    def create_from_files(
        self, audio_files: List, prefer_id3_titles: bool = True
    ) -> None:
        """Create chapters from audio files (one chapter per file).

        Args:
            audio_files: List of AudioFile objects
            prefer_id3_titles: If True, use ID3 title tags when available (default: True)
        """
        self.chapters.clear()
        current_time_ms = 0

        titles_from_id3 = 0
        titles_from_filename = 0

        for audio_file in audio_files:
            duration_ms = int(audio_file.duration_seconds * 1000)

            # Prefer ID3 title tag over filename
            if prefer_id3_titles and audio_file.title:
                title = audio_file.title
                titles_from_id3 += 1
            else:
                title = audio_file.filename
                titles_from_filename += 1

            self.add_chapter(
                title=title,
                start_ms=current_time_ms,
                duration_ms=duration_ms,
                file_path=audio_file.path,
            )

            current_time_ms += duration_ms

        # Store stats for user feedback
        self.last_create_stats = {
            "from_id3": titles_from_id3,
            "from_filename": titles_from_filename,
            "total": len(audio_files),
        }

    def create_from_timestamps(self, timestamps: List[tuple]) -> None:
        """Create chapters from timestamp list.

        Args:
            timestamps: List of (time_str, title) tuples
                       e.g., [("00:00:00", "Introduction"), ("00:05:30", "Chapter 1")]
        """
        self.chapters.clear()

        parsed_chapters = []
        for time_str, title in timestamps:
            start_ms = self._parse_timestamp(time_str)
            parsed_chapters.append((start_ms, title))

        # Sort by time
        parsed_chapters.sort(key=lambda x: x[0])

        # Create chapters with durations
        for i, (start_ms, title) in enumerate(parsed_chapters):
            # Calculate duration (until next chapter or end)
            if i < len(parsed_chapters) - 1:
                duration_ms = parsed_chapters[i + 1][0] - start_ms
            else:
                duration_ms = None  # Last chapter, duration unknown

            self.add_chapter(title=title, start_ms=start_ms, duration_ms=duration_ms)

    def auto_name_chapters(self, pattern: str = "Chapter {num}") -> None:
        """Automatically name chapters using a pattern.

        Args:
            pattern: Naming pattern with {num} placeholder
        """
        for i, chapter in enumerate(self.chapters, start=1):
            chapter.title = pattern.format(num=i)

    def to_ffmpeg_metadata(self) -> str:
        """Generate ffmpeg metadata file content.

        Returns:
            Formatted metadata string for ffmpeg
        """
        if not self.chapters:
            return ""

        metadata = ";FFMETADATA1\n"
        for chapter in self.chapters:
            metadata += chapter.to_ffmpeg_format()

        return metadata

    @staticmethod
    def _parse_timestamp(time_str: str) -> int:
        """Parse timestamp string to milliseconds.

        Args:
            time_str: Time string in format HH:MM:SS or MM:SS or HH:MM:SS.mmm

        Returns:
            Time in milliseconds
        """
        # Handle fractional seconds
        if "." in time_str:
            time_part, ms_part = time_str.split(".")
            ms = int(ms_part.ljust(3, "0")[:3])  # Pad or truncate to 3 digits
        else:
            time_part = time_str
            ms = 0

        # Parse time components
        parts = time_part.split(":")

        if len(parts) == 3:  # HH:MM:SS
            hours, minutes, seconds = map(int, parts)
        elif len(parts) == 2:  # MM:SS
            hours = 0
            minutes, seconds = map(int, parts)
        elif len(parts) == 1:  # SS
            hours = 0
            minutes = 0
            seconds = int(parts[0])
        else:
            raise ValueError(f"Invalid timestamp format: {time_str}")

        total_ms = (hours * 3600 + minutes * 60 + seconds) * 1000 + ms
        return total_ms

    @staticmethod
    def format_timestamp(milliseconds: int) -> str:
        """Format milliseconds to HH:MM:SS.mmm string.

        Args:
            milliseconds: Time in milliseconds

        Returns:
            Formatted timestamp string
        """
        ms = milliseconds % 1000
        total_seconds = milliseconds // 1000

        hours = total_seconds // 3600
        minutes = (total_seconds % 3600) // 60
        seconds = total_seconds % 60

        return f"{hours:02d}:{minutes:02d}:{seconds:02d}.{ms:03d}"

    def get_chapter_count(self) -> int:
        """Get total number of chapters.

        Returns:
            Chapter count
        """
        return len(self.chapters)

    def validate(self) -> List[str]:
        """Validate chapter configuration.

        Returns:
            List of validation warnings/errors
        """
        warnings = []

        if not self.chapters:
            warnings.append("No chapters defined")
            return warnings

        # Check for empty titles
        for i, chapter in enumerate(self.chapters, start=1):
            if not chapter.title.strip():
                warnings.append(f"Chapter {i} has empty title")

        # Check for negative or invalid times
        for i, chapter in enumerate(self.chapters, start=1):
            if chapter.start_ms < 0:
                warnings.append(f"Chapter {i} has negative start time")
            if chapter.duration_ms is not None and chapter.duration_ms < 0:
                warnings.append(f"Chapter {i} has negative duration")

        # Check for overlapping chapters
        sorted_chapters = sorted(self.chapters, key=lambda c: c.start_ms)
        for i in range(len(sorted_chapters) - 1):
            current = sorted_chapters[i]
            next_chapter = sorted_chapters[i + 1]

            if current.duration_ms:
                current_end = current.start_ms + current.duration_ms
                if current_end > next_chapter.start_ms:
                    warnings.append(
                        f"Chapters '{current.title}' and '{next_chapter.title}' overlap"
                    )

        return warnings

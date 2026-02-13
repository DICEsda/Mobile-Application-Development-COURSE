"""Audio file handling: validation, sorting, and metadata extraction."""

import os
from pathlib import Path
from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass
from mutagen.mp3 import MP3
from mutagen.id3 import ID3


@dataclass
class AudioFile:
    """Represents an audio file with its metadata."""

    path: Path
    filename: str
    duration_seconds: float
    sample_rate: int
    channels: int
    bitrate: Optional[int]
    track_number: Optional[int] = None
    title: Optional[str] = None


class AudioHandler:
    """Handles audio file operations."""

    SUPPORTED_FORMATS = [".mp3"]

    def __init__(self, directory: Path):
        """Initialize the audio handler.

        Args:
            directory: Path to directory containing audio files
        """
        self.directory = Path(directory)
        self.audio_files: List[AudioFile] = []

    def scan_directory(self) -> List[AudioFile]:
        """Scan directory for audio files and extract metadata.

        Returns:
            List of AudioFile objects
        """
        audio_files = []

        for file_path in sorted(self.directory.iterdir()):
            if file_path.suffix.lower() in self.SUPPORTED_FORMATS:
                try:
                    audio_file = self._extract_metadata(file_path)
                    audio_files.append(audio_file)
                except Exception as e:
                    # Skip files that can't be read
                    print(f"Warning: Could not read {file_path}: {e}")
                    continue

        self.audio_files = audio_files
        return audio_files

    def _extract_metadata(self, file_path: Path) -> AudioFile:
        """Extract metadata from an audio file.

        Args:
            file_path: Path to audio file

        Returns:
            AudioFile object with extracted metadata
        """
        audio = MP3(file_path)

        # Extract basic audio properties
        duration = audio.info.length
        sample_rate = audio.info.sample_rate
        channels = audio.info.channels
        bitrate = audio.info.bitrate

        # Try to extract ID3 tags
        track_number = None
        title = None

        try:
            tags = ID3(file_path)
            if "TRCK" in tags:
                # Track number might be "1/10" format
                track_str = str(tags["TRCK"])
                track_number = int(track_str.split("/")[0])
            if "TIT2" in tags:
                title = str(tags["TIT2"])
        except:
            pass

        return AudioFile(
            path=file_path,
            filename=file_path.name,
            duration_seconds=duration,
            sample_rate=sample_rate,
            channels=channels,
            bitrate=bitrate,
            track_number=track_number,
            title=title,
        )

    def sort_files(self, sort_by: str = "filename") -> List[AudioFile]:
        """Sort audio files by specified criterion.

        Args:
            sort_by: Sorting criterion ('filename', 'track', 'duration')

        Returns:
            Sorted list of AudioFile objects
        """
        if sort_by == "filename":
            self.audio_files.sort(key=lambda x: x.filename)
        elif sort_by == "track":
            self.audio_files.sort(
                key=lambda x: (
                    x.track_number if x.track_number is not None else 999,
                    x.filename,
                )
            )
        elif sort_by == "duration":
            self.audio_files.sort(key=lambda x: x.duration_seconds)

        return self.audio_files

    def validate_consistency(self) -> Dict[str, any]:
        """Validate that audio files are consistent in format.

        Returns:
            Dictionary with validation results and warnings
        """
        if not self.audio_files:
            return {"valid": False, "warnings": ["No audio files found"]}

        warnings = []

        # Check sample rate consistency
        sample_rates = set(f.sample_rate for f in self.audio_files)
        if len(sample_rates) > 1:
            warnings.append(
                f"Mixed sample rates detected: {', '.join(map(str, sample_rates))} Hz"
            )

        # Check channel consistency
        channels = set(f.channels for f in self.audio_files)
        if len(channels) > 1:
            warnings.append(
                f"Mixed channel counts detected: {', '.join(map(str, channels))}"
            )

        # Check bitrate variance (optional warning)
        bitrates = [f.bitrate for f in self.audio_files if f.bitrate]
        if bitrates:
            avg_bitrate = sum(bitrates) / len(bitrates)
            variance = max(abs(br - avg_bitrate) for br in bitrates)
            if variance > avg_bitrate * 0.5:  # 50% variance
                warnings.append(
                    f"High bitrate variance detected (avg: {int(avg_bitrate / 1000)}kbps)"
                )

        return {
            "valid": True,
            "warnings": warnings,
            "sample_rate": list(sample_rates)[0] if len(sample_rates) == 1 else None,
            "channels": list(channels)[0] if len(channels) == 1 else None,
            "total_duration": sum(f.duration_seconds for f in self.audio_files),
            "file_count": len(self.audio_files),
        }

    def get_total_duration(self) -> float:
        """Get total duration of all audio files in seconds.

        Returns:
            Total duration in seconds
        """
        return sum(f.duration_seconds for f in self.audio_files)

    @staticmethod
    def format_duration(seconds: float) -> str:
        """Format duration in seconds to human-readable string.

        Args:
            seconds: Duration in seconds

        Returns:
            Formatted string (e.g., "5h 35m")
        """
        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        secs = int(seconds % 60)

        if hours > 0:
            return f"{hours}h {minutes}m"
        elif minutes > 0:
            return f"{minutes}m {secs}s"
        else:
            return f"{secs}s"

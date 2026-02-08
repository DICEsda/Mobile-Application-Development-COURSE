#!/usr/bin/env python3
"""
M4B Audiobook Creator - Windows CLI Tool
=========================================

Creates M4B audiobook files from MP3/audio files with:
- Automatic metadata fetching from Open Library
- Chapter generation from file names
- Cover art embedding
- Interactive metadata editing

Requirements:
    pip install mutagen requests colorama

System Requirements:
    - FFmpeg must be installed and available in PATH
    - Windows 10/11

Usage:
    python m4b_creator.py
    python m4b_creator.py --input "C:\Audio\MyBook" --title "The Hobbit"
"""

import os
import sys
import subprocess
import argparse
import json
from pathlib import Path
from typing import List, Dict, Optional
import requests
from mutagen.mp3 import MP3
from mutagen.mp4 import MP4, MP4Cover
from mutagen.id3 import ID3
from colorama import init, Fore, Style

# Initialize colorama for Windows color support
init()

# Default configuration
DEFAULT_INPUT_DIR = "input"
DEFAULT_OUTPUT_DIR = "output"
DEFAULT_BITRATE = "64k"  # Lower bitrate for audiobooks
DEFAULT_SAMPLE_RATE = "44100"


class M4BCreator:
    """Main class for creating M4B audiobook files."""
    
    def __init__(self, input_dir: str, output_dir: str):
        self.input_dir = Path(input_dir)
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        
    def log(self, message: str, color=Fore.WHITE):
        """Print colored log message."""
        print(f"{color}{message}{Style.RESET_ALL}")
        
    def check_ffmpeg(self) -> bool:
        """Check if FFmpeg is installed and accessible."""
        try:
            result = subprocess.run(
                ["ffmpeg", "-version"],
                capture_output=True,
                text=True,
                creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0
            )
            if result.returncode == 0:
                self.log("✓ FFmpeg found", Fore.GREEN)
                return True
        except FileNotFoundError:
            pass
        
        self.log("✗ FFmpeg not found in PATH", Fore.RED)
        self.log("  Please install FFmpeg from: https://ffmpeg.org/download.html", Fore.YELLOW)
        return False
    
    def get_audio_files(self) -> List[Path]:
        """Get all audio files from input directory, sorted naturally."""
        extensions = ['.mp3', '.m4a', '.wav', '.flac', '.ogg']
        files = []
        
        for ext in extensions:
            files.extend(self.input_dir.glob(f'*{ext}'))
            
        # Sort naturally (1, 2, 10 instead of 1, 10, 2)
        def natural_sort_key(path):
            import re
            return [int(text) if text.isdigit() else text.lower()
                    for text in re.split('([0-9]+)', path.name)]
        
        files = sorted(files, key=natural_sort_key)
        
        self.log(f"✓ Found {len(files)} audio file(s)", Fore.GREEN)
        for i, f in enumerate(files, 1):
            self.log(f"  {i}. {f.name}", Fore.CYAN)
            
        return files
    
    def get_duration_ms(self, file: Path) -> int:
        """Get audio file duration in milliseconds."""
        try:
            audio = MP3(str(file))
            return int(audio.info.length * 1000)
        except Exception as e:
            self.log(f"Warning: Could not read duration for {file.name}: {e}", Fore.YELLOW)
            return 0
    
    def fetch_open_library_metadata(self, title: str) -> Dict:
        """Fetch book metadata from Open Library API."""
        self.log(f"\n🔍 Searching Open Library for: {title}", Fore.CYAN)
        
        try:
            url = f"https://openlibrary.org/search.json?title={title.replace(' ', '+')}"
            response = requests.get(url, timeout=10)
            response.raise_for_status()
            data = response.json()
            
            if "docs" not in data or len(data["docs"]) == 0:
                self.log("✗ No results found", Fore.YELLOW)
                return {}
            
            book = data["docs"][0]
            metadata = {
                "title": book.get("title", "Unknown"),
                "author": ", ".join(book.get("author_name", ["Unknown Author"])),
                "publisher": ", ".join(book.get("publisher", ["Unknown"])[:1]),
                "year": str(book.get("first_publish_year", "")),
                "description": ", ".join(book.get("subject", [""])[:3]),  # First 3 subjects
                "cover_id": book.get("cover_i")
            }
            
            if metadata["cover_id"]:
                metadata["cover_url"] = f"https://covers.openlibrary.org/b/id/{metadata['cover_id']}-L.jpg"
            
            self.log(f"✓ Found: {metadata['title']} by {metadata['author']}", Fore.GREEN)
            return metadata
            
        except Exception as e:
            self.log(f"✗ Error fetching metadata: {e}", Fore.RED)
            return {}
    
    def download_cover(self, cover_url: str) -> Optional[Path]:
        """Download cover image from URL."""
        if not cover_url:
            return None
            
        self.log("📥 Downloading cover art...", Fore.CYAN)
        cover_path = self.output_dir / "cover.jpg"
        
        try:
            response = requests.get(cover_url, timeout=10)
            response.raise_for_status()
            
            with open(cover_path, "wb") as f:
                f.write(response.content)
            
            self.log(f"✓ Cover saved to {cover_path}", Fore.GREEN)
            return cover_path
            
        except Exception as e:
            self.log(f"✗ Could not download cover: {e}", Fore.YELLOW)
            return None
    
    def generate_chapters(self, files: List[Path]) -> Path:
        """Generate FFmpeg metadata file with chapter markers."""
        self.log("\n📖 Generating chapters...", Fore.CYAN)
        
        chapters_file = self.output_dir / "chapters.txt"
        start_ms = 0
        lines = [";FFMETADATA1"]
        
        for i, file in enumerate(files, 1):
            duration_ms = self.get_duration_ms(file)
            end_ms = start_ms + duration_ms
            
            # Use filename without extension as chapter title
            chapter_title = file.stem
            # Clean up common patterns like "01 - " or "Chapter 1 - "
            import re
            chapter_title = re.sub(r'^\d+[\s\-\.]*', '', chapter_title)
            chapter_title = re.sub(r'^Chapter\s+\d+[\s\-\.]*', '', chapter_title, flags=re.IGNORECASE)
            
            if not chapter_title.strip():
                chapter_title = f"Chapter {i}"
            
            lines.append("\n[CHAPTER]")
            lines.append("TIMEBASE=1/1000")
            lines.append(f"START={start_ms}")
            lines.append(f"END={end_ms}")
            lines.append(f"title={chapter_title}")
            
            self.log(f"  Chapter {i}: {chapter_title} ({duration_ms/1000:.1f}s)", Fore.CYAN)
            start_ms = end_ms
        
        with open(chapters_file, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))
        
        self.log(f"✓ Created {len(files)} chapters", Fore.GREEN)
        return chapters_file
    
    def create_file_list(self, files: List[Path]) -> Path:
        """Create FFmpeg concat file list."""
        list_file = self.output_dir / "files.txt"
        
        with open(list_file, "w", encoding="utf-8") as f:
            for file in files:
                # Use absolute path and escape properly for Windows
                abs_path = str(file.absolute()).replace('\\', '/')
                f.write(f"file '{abs_path}'\n")
        
        return list_file
    
    def edit_metadata(self, metadata: Dict) -> Dict:
        """Interactive metadata editor."""
        self.log("\n✏️  Edit Metadata (press Enter to keep current value):", Fore.YELLOW)
        
        editable_fields = ["title", "author", "publisher", "year", "description"]
        
        for field in editable_fields:
            current = metadata.get(field, "")
            self.log(f"\n{field.capitalize()}: {Fore.CYAN}{current}{Style.RESET_ALL}")
            new_value = input(f"New value (or Enter to keep): ").strip()
            
            if new_value:
                metadata[field] = new_value
        
        # Ask about cover
        if "cover_url" in metadata:
            self.log(f"\nCover URL: {Fore.CYAN}{metadata['cover_url']}{Style.RESET_ALL}")
            use_cover = input("Download this cover? (Y/n): ").strip().lower()
            if use_cover == 'n':
                metadata.pop("cover_url", None)
        else:
            cover_url = input("\nEnter cover image URL (or press Enter to skip): ").strip()
            if cover_url:
                metadata["cover_url"] = cover_url
        
        return metadata
    
    def create_m4b(
        self,
        file_list: Path,
        chapters_file: Path,
        metadata: Dict,
        cover_path: Optional[Path],
        output_filename: str,
        bitrate: str = DEFAULT_BITRATE,
        sample_rate: str = DEFAULT_SAMPLE_RATE
    ) -> Path:
        """Create M4B file using FFmpeg."""
        output_file = self.output_dir / output_filename
        
        self.log(f"\n🎧 Creating M4B audiobook...", Fore.MAGENTA)
        self.log(f"Output: {output_file}", Fore.CYAN)
        
        # Build FFmpeg command
        cmd = [
            "ffmpeg",
            "-f", "concat",
            "-safe", "0",
            "-i", str(file_list),
            "-i", str(chapters_file),
            "-map_metadata", "1",  # Use chapter metadata
            "-c:a", "aac",
            "-b:a", bitrate,
            "-ar", sample_rate,
            "-movflags", "+faststart",  # Optimize for streaming
        ]
        
        # Add cover if available
        if cover_path and cover_path.exists():
            cmd.extend([
                "-i", str(cover_path),
                "-map", "0:a",
                "-map", "2:v",
                "-c:v", "copy",
                "-disposition:v:0", "attached_pic"
            ])
        else:
            cmd.extend(["-map", "0:a"])
        
        # Add metadata tags
        metadata_map = {
            "title": "©nam",
            "author": "©ART",
            "publisher": "©pub",
            "year": "©day",
            "description": "©cmt"
        }
        
        for key, tag in metadata_map.items():
            if metadata.get(key):
                cmd.extend(["-metadata", f"{tag}={metadata[key]}"])
        
        # Add genre for audiobooks
        cmd.extend(["-metadata", "genre=Audiobook"])
        
        # Output file
        cmd.append(str(output_file))
        
        # Run FFmpeg
        try:
            self.log("\n⏳ Processing (this may take a while)...", Fore.YELLOW)
            
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0
            )
            
            if result.returncode != 0:
                self.log(f"\n✗ FFmpeg error:", Fore.RED)
                self.log(result.stderr, Fore.RED)
                raise Exception("FFmpeg conversion failed")
            
            file_size_mb = output_file.stat().st_size / (1024 * 1024)
            self.log(f"\n✅ M4B created successfully!", Fore.GREEN)
            self.log(f"   Location: {output_file}", Fore.GREEN)
            self.log(f"   Size: {file_size_mb:.2f} MB", Fore.GREEN)
            
            return output_file
            
        except Exception as e:
            self.log(f"\n✗ Error creating M4B: {e}", Fore.RED)
            raise


def main():
    """Main CLI entry point."""
    parser = argparse.ArgumentParser(
        description="Create M4B audiobook files from audio files",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  python m4b_creator.py
  python m4b_creator.py --input "C:\\Audio\\MyBook" --title "The Hobbit"
  python m4b_creator.py -i input -o output --bitrate 32k --no-edit
        """
    )
    
    parser.add_argument(
        "-i", "--input",
        default=DEFAULT_INPUT_DIR,
        help=f"Input directory with audio files (default: {DEFAULT_INPUT_DIR})"
    )
    parser.add_argument(
        "-o", "--output",
        default=DEFAULT_OUTPUT_DIR,
        help=f"Output directory for M4B file (default: {DEFAULT_OUTPUT_DIR})"
    )
    parser.add_argument(
        "-t", "--title",
        help="Book title for metadata search (will prompt if not provided)"
    )
    parser.add_argument(
        "-n", "--name",
        help="Output filename (default: audiobook.m4b)"
    )
    parser.add_argument(
        "-b", "--bitrate",
        default=DEFAULT_BITRATE,
        help=f"Audio bitrate (default: {DEFAULT_BITRATE})"
    )
    parser.add_argument(
        "-s", "--sample-rate",
        default=DEFAULT_SAMPLE_RATE,
        help=f"Sample rate (default: {DEFAULT_SAMPLE_RATE})"
    )
    parser.add_argument(
        "--no-edit",
        action="store_true",
        help="Skip metadata editing step"
    )
    parser.add_argument(
        "--no-search",
        action="store_true",
        help="Skip Open Library search"
    )
    
    args = parser.parse_args()
    
    # Print header
    print(f"\n{Fore.CYAN}{'='*60}")
    print(f"{'M4B Audiobook Creator':^60}")
    print(f"{'='*60}{Style.RESET_ALL}\n")
    
    # Create M4B creator instance
    creator = M4BCreator(args.input, args.output)
    
    # Check FFmpeg
    if not creator.check_ffmpeg():
        return 1
    
    # Get audio files
    files = creator.get_audio_files()
    if not files:
        creator.log(f"✗ No audio files found in {args.input}", Fore.RED)
        return 1
    
    # Get book title
    title = args.title
    if not title and not args.no_search:
        title = input(f"\n{Fore.YELLOW}Enter book title for metadata search: {Style.RESET_ALL}").strip()
    
    # Fetch metadata from Open Library
    metadata = {}
    if title and not args.no_search:
        metadata = creator.fetch_open_library_metadata(title)
    
    # Fallback metadata
    if not metadata.get("title"):
        metadata["title"] = title or "Unknown Audiobook"
    if not metadata.get("author"):
        metadata["author"] = "Unknown Author"
    
    # Edit metadata if requested
    if not args.no_edit:
        metadata = creator.edit_metadata(metadata)
    
    # Download cover
    cover_path = None
    if metadata.get("cover_url"):
        cover_path = creator.download_cover(metadata["cover_url"])
    
    # Generate chapters
    chapters_file = creator.generate_chapters(files)
    
    # Create file list
    file_list = creator.create_file_list(files)
    
    # Determine output filename
    output_filename = args.name or f"{metadata['title']}.m4b"
    # Clean filename
    output_filename = "".join(c for c in output_filename if c.isalnum() or c in (' ', '-', '_', '.')).strip()
    if not output_filename.endswith('.m4b'):
        output_filename += '.m4b'
    
    # Create M4B
    try:
        output_file = creator.create_m4b(
            file_list=file_list,
            chapters_file=chapters_file,
            metadata=metadata,
            cover_path=cover_path,
            output_filename=output_filename,
            bitrate=args.bitrate,
            sample_rate=args.sample_rate
        )
        
        creator.log(f"\n{'='*60}", Fore.GREEN)
        creator.log("✅ Conversion complete!", Fore.GREEN)
        creator.log(f"{'='*60}\n", Fore.GREEN)
        
        return 0
        
    except Exception as e:
        creator.log(f"\n✗ Fatal error: {e}", Fore.RED)
        return 1


if __name__ == "__main__":
    sys.exit(main())

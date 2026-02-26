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
    python m4b_creator.py --input "C:\\Audio\\MyBook" --title "The Hobbit"
"""

import os
import sys
import subprocess
import argparse
import json
import shutil
from pathlib import Path
from typing import List, Dict, Optional
import requests
from mutagen.mp3 import MP3
from mutagen.mp4 import MP4, MP4Cover
from mutagen.m4a import M4A
from mutagen.id3 import ID3
from mutagen.flac import FLAC
from mutagen.oggvorbis import OggVorbis
from mutagen.wave import WAVE
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
        self.ffmpeg_cmd: Optional[str] = None
        
    def log(self, message: str, color=Fore.WHITE):
        """Print colored log message."""
        print(f"{color}{message}{Style.RESET_ALL}")
        
    def _find_ffmpeg(self) -> Optional[str]:
        """Locate FFmpeg executable from PATH, env var, or common install paths."""
        if self.ffmpeg_cmd and Path(self.ffmpeg_cmd).exists():
            return self.ffmpeg_cmd

        env_path = os.environ.get("FFMPEG_PATH") or os.environ.get("FFMPEG")
        if env_path:
            env_candidate = Path(env_path)
            if env_candidate.is_dir():
                env_candidate = env_candidate / "ffmpeg.exe"
            if env_candidate.exists():
                self.ffmpeg_cmd = str(env_candidate)
                return self.ffmpeg_cmd

        which_path = shutil.which("ffmpeg")
        if which_path:
            self.ffmpeg_cmd = which_path
            return self.ffmpeg_cmd

        common_paths = [
            Path("C:/ffmpeg/bin/ffmpeg.exe"),
            Path("C:/Program Files/ffmpeg/bin/ffmpeg.exe"),
            Path("C:/Program Files (x86)/ffmpeg/bin/ffmpeg.exe"),
            Path.home() / "ffmpeg" / "bin" / "ffmpeg.exe",
        ]

        for candidate in common_paths:
            if candidate.exists():
                self.ffmpeg_cmd = str(candidate)
                return self.ffmpeg_cmd

        return None

    def check_ffmpeg(self) -> bool:
        """Check if FFmpeg is installed and accessible."""
        ffmpeg_cmd = self._find_ffmpeg()
        if not ffmpeg_cmd:
            self.log("✗ FFmpeg not found in PATH", Fore.RED)
            self.log("  Set FFMPEG_PATH to ffmpeg.exe or add FFmpeg to PATH", Fore.YELLOW)
            self.log("  Example (PowerShell): $env:FFMPEG_PATH='C:\\ffmpeg\\bin\\ffmpeg.exe'", Fore.YELLOW)
            self.log("  Download: https://ffmpeg.org/download.html", Fore.YELLOW)
            return False

        try:
            result = subprocess.run(
                [ffmpeg_cmd, "-version"],
                capture_output=True,
                text=True,
                creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0
            )
            if result.returncode == 0:
                self.log(f"✓ FFmpeg found ({ffmpeg_cmd})", Fore.GREEN)
                return True
        except FileNotFoundError:
            pass

        self.log("✗ FFmpeg not found in PATH", Fore.RED)
        self.log("  Please install FFmpeg from: https://ffmpeg.org/download.html", Fore.YELLOW)
        return False
    
    def find_audiobook_folders(self) -> List[Path]:
        """Find folders containing audio files in the input directory."""
        folders = []
        
        for item in self.input_dir.iterdir():
            if item.is_dir():
                # Check if folder contains audio files
                audio_files = self.get_audio_files_from_folder(item)
                if audio_files:
                    folders.append(item)
        
        return sorted(folders)
    
    def get_audio_files_from_folder(self, folder: Path) -> List[Path]:
        """Get all audio files from a specific folder, sorted naturally."""
        extensions = ['.mp3', '.m4a', '.m4b', '.mp4', '.wav', '.flac', '.ogg']
        files = []
        
        for ext in extensions:
            files.extend(folder.glob(f'*{ext}'))
            
        # Sort naturally (1, 2, 10 instead of 1, 10, 2)
        def natural_sort_key(path):
            import re
            return [int(text) if text.isdigit() else text.lower()
                    for text in re.split('([0-9]+)', path.name)]
        
        files = sorted(files, key=natural_sort_key)
        return files
    
    def get_audio_files(self) -> List[Path]:
        """Get all audio files from input directory, sorted naturally."""
        extensions = ['.mp3', '.m4a', '.m4b', '.mp4', '.wav', '.flac', '.ogg']
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
            ext = file.suffix.lower()
            if ext == '.mp3':
                audio = MP3(str(file))
            elif ext in ['.m4a', '.m4b', '.mp4']:
                audio = M4A(str(file))
            elif ext == '.flac':
                audio = FLAC(str(file))
            elif ext == '.ogg':
                audio = OggVorbis(str(file))
            elif ext == '.wav':
                audio = WAVE(str(file))
            else:
                audio = MP3(str(file))
            
            return int(audio.info.length * 1000)
        except Exception as e:
            self.log(f"Warning: Could not read duration for {file.name}: {e}", Fore.YELLOW)
            return 0
    
    def extract_metadata_from_files(self, files: List[Path]) -> Dict:
        """Extract metadata from audio files themselves."""
        self.log("\n📝 Extracting metadata from audio files...", Fore.CYAN)
        
        metadata = {
            "title": "",
            "author": "",
            "publisher": "",
            "year": "",
            "description": "",
            "narrator": ""
        }
        
        # Try to get metadata from the first file
        if not files:
            return metadata
        
        first_file = files[0]
        ext = first_file.suffix.lower()
        
        try:
            if ext == '.mp3':
                audio = MP3(str(first_file))
                tags = audio.tags
                if tags:
                    metadata["title"] = str(tags.get("TIT2", "")) or str(tags.get("TALB", ""))
                    metadata["author"] = str(tags.get("TPE1", "")) or str(tags.get("TPE2", ""))
                    metadata["publisher"] = str(tags.get("TPUB", ""))
                    metadata["year"] = str(tags.get("TDRC", ""))
                    metadata["description"] = str(tags.get("COMM::eng", ""))
                    
            elif ext in ['.m4a', '.m4b', '.mp4']:
                audio = M4A(str(first_file))
                tags = audio.tags
                if tags:
                    metadata["title"] = tags.get("©nam", [""])[0] or tags.get("©alb", [""])[0]
                    metadata["author"] = tags.get("©ART", [""])[0] or tags.get("aART", [""])[0]
                    metadata["publisher"] = tags.get("©pub", [""])[0]
                    metadata["year"] = tags.get("©day", [""])[0]
                    metadata["description"] = tags.get("©cmt", [""])[0] or tags.get("desc", [""])[0]
                    metadata["narrator"] = tags.get("©wrt", [""])[0]
            
            # Clean up metadata
            metadata = {k: v.strip() if isinstance(v, str) else v for k, v in metadata.items()}
            
            if metadata["title"]:
                self.log(f"✓ Found embedded metadata: {metadata['title']}", Fore.GREEN)
            else:
                self.log("✗ No embedded metadata found", Fore.YELLOW)
                # Use folder name as fallback
                metadata["title"] = first_file.parent.name
                self.log(f"  Using folder name: {metadata['title']}", Fore.CYAN)
            
        except Exception as e:
            self.log(f"✗ Could not extract metadata: {e}", Fore.YELLOW)
            # Use folder name as fallback
            metadata["title"] = first_file.parent.name
        
        return metadata
    
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
        """Download cover image from URL or copy from local path."""
        if not cover_url:
            return None
        
        # Check if it's a local file path
        local_path = Path(cover_url)
        if local_path.exists() and local_path.is_file():
            self.log(f"📁 Using local cover: {cover_url}", Fore.CYAN)
            cover_path = self.output_dir / "cover.jpg"
            try:
                import shutil
                shutil.copy(str(local_path), str(cover_path))
                self.log(f"✓ Cover copied to {cover_path}", Fore.GREEN)
                return cover_path
            except Exception as e:
                self.log(f"✗ Could not copy cover: {e}", Fore.YELLOW)
                return None
        
        # Download from URL
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
    
    def preview_chapters(self, files: List[Path]) -> List[Dict]:
        """Preview chapters that will be created."""
        self.log("\n📖 Chapter Preview:", Fore.CYAN)
        self.log("="*60, Fore.CYAN)
        
        chapters = []
        total_duration = 0
        
        for i, file in enumerate(files, 1):
            duration_ms = self.get_duration_ms(file)
            duration_sec = duration_ms / 1000
            total_duration += duration_sec
            
            # Use filename without extension as chapter title
            chapter_title = file.stem
            # Clean up common patterns like "01 - " or "Chapter 1 - "
            import re
            chapter_title = re.sub(r'^\d+[\s\-\.]*', '', chapter_title)
            chapter_title = re.sub(r'^Chapter\s+\d+[\s\-\.]*', '', chapter_title, flags=re.IGNORECASE)
            
            if not chapter_title.strip():
                chapter_title = f"Chapter {i}"
            
            chapters.append({
                'number': i,
                'title': chapter_title,
                'duration_ms': duration_ms,
                'file': file
            })
            
            # Format duration as HH:MM:SS
            hours = int(duration_sec // 3600)
            minutes = int((duration_sec % 3600) // 60)
            seconds = int(duration_sec % 60)
            duration_str = f"{hours:02d}:{minutes:02d}:{seconds:02d}"
            
            self.log(f"  {i:2d}. {chapter_title:<45} [{duration_str}]", Fore.CYAN)
        
        # Show total duration
        hours = int(total_duration // 3600)
        minutes = int((total_duration % 3600) // 60)
        seconds = int(total_duration % 60)
        total_str = f"{hours:02d}:{minutes:02d}:{seconds:02d}"
        
        self.log("="*60, Fore.CYAN)
        self.log(f"Total: {len(chapters)} chapters, Duration: {total_str}", Fore.GREEN)
        
        return chapters
    
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
    
    def edit_metadata(self, metadata: Dict, allow_empty: bool = False) -> Dict:
        """Interactive metadata editor."""
        self.log("\n✏️  Edit Metadata (press Enter to keep current value):", Fore.YELLOW)
        
        editable_fields = ["title", "author", "narrator", "publisher", "year", "description"]
        
        for field in editable_fields:
            current = metadata.get(field, "")
            
            # Highlight empty fields
            if not current:
                self.log(f"\n{field.capitalize()}: {Fore.RED}(empty){Style.RESET_ALL}")
            else:
                self.log(f"\n{field.capitalize()}: {Fore.CYAN}{current}{Style.RESET_ALL}")
            
            new_value = input(f"New value (or Enter to keep): ").strip()
            
            if new_value:
                metadata[field] = new_value
            elif not current and not allow_empty:
                # Prompt for required fields
                if field in ["title", "author"]:
                    while not new_value:
                        new_value = input(f"{Fore.YELLOW}⚠ {field.capitalize()} is required. Enter value: {Style.RESET_ALL}").strip()
                    metadata[field] = new_value
        
        # Ask about cover
        if "cover_url" in metadata:
            self.log(f"\nCover URL: {Fore.CYAN}{metadata['cover_url']}{Style.RESET_ALL}")
            use_cover = input("Download this cover? (Y/n): ").strip().lower()
            if use_cover == 'n':
                metadata.pop("cover_url", None)
        else:
            self.log(f"\nCover URL: {Fore.RED}(empty){Style.RESET_ALL}")
            cover_url = input("Enter cover image URL or local path (or press Enter to skip): ").strip()
            if cover_url:
                # Check if it's a local file
                if Path(cover_url).exists():
                    self.log(f"✓ Using local cover: {cover_url}", Fore.GREEN)
                    metadata["cover_path"] = cover_url
                else:
                    metadata["cover_url"] = cover_url
        
        return metadata
    
    def prompt_for_missing_metadata(self, metadata: Dict) -> Dict:
        """Prompt user to fill in missing critical metadata."""
        self.log("\n⚠️  Some metadata is missing. Please provide the following:", Fore.YELLOW)
        
        # Required fields
        if not metadata.get("title"):
            metadata["title"] = input(f"{Fore.YELLOW}Book Title (required): {Style.RESET_ALL}").strip()
            while not metadata["title"]:
                metadata["title"] = input(f"{Fore.RED}Title cannot be empty. Please enter: {Style.RESET_ALL}").strip()
        
        if not metadata.get("author"):
            metadata["author"] = input(f"{Fore.YELLOW}Author (required): {Style.RESET_ALL}").strip()
            while not metadata["author"]:
                metadata["author"] = input(f"{Fore.RED}Author cannot be empty. Please enter: {Style.RESET_ALL}").strip()
        
        # Optional fields
        if not metadata.get("narrator"):
            narrator = input(f"Narrator (optional, press Enter to skip): ").strip()
            if narrator:
                metadata["narrator"] = narrator
        
        if not metadata.get("description"):
            description = input(f"Description (optional, press Enter to skip): ").strip()
            if description:
                metadata["description"] = description
        
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
        ffmpeg_cmd = self._find_ffmpeg() or "ffmpeg"
        cmd = [
            ffmpeg_cmd,
            "-f", "concat",
            "-safe", "0",
            "-i", str(file_list),
            "-i", str(chapters_file),
        ]
        
        # Add cover if available
        if cover_path and cover_path.exists():
            cmd.extend([
                "-i", str(cover_path),
            ])
        
        # Add mapping and encoding options
        cmd.extend([
            "-map_metadata", "1",  # Use chapter metadata from second input
        ])
        
        if cover_path and cover_path.exists():
            cmd.extend([
                "-map", "0:a",
                "-map", "2:v",
                "-c:v", "copy",
                "-disposition:v:0", "attached_pic"
            ])
        else:
            cmd.extend(["-map", "0:a"])
        
        # Add encoding options
        cmd.extend([
            "-c:a", "aac",
            "-b:a", bitrate,
            "-ar", sample_rate,
            "-movflags", "+faststart",  # Optimize for streaming
        ])
        
        # Add metadata tags
        metadata_map = {
            "title": "©nam",
            "author": "©ART",
            "narrator": "©wrt",  # Writer field for narrator
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
            self.log("📊 Progress will be shown below:\n", Fore.CYAN)
            
            # Run with progress output
            process = subprocess.Popen(
                cmd,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                creationflags=subprocess.CREATE_NO_WINDOW if sys.platform == 'win32' else 0,
                bufsize=1,
                universal_newlines=True
            )
            
            # Monitor progress
            for line in process.stdout:
                if "time=" in line:
                    # Extract and display progress
                    import re
                    time_match = re.search(r'time=(\d{2}:\d{2}:\d{2}\.\d{2})', line)
                    speed_match = re.search(r'speed=([\d.]+)x', line)
                    if time_match:
                        time_str = time_match.group(1)
                        speed_str = speed_match.group(1) if speed_match else "N/A"
                        print(f"\r⏱️  Time: {time_str} | Speed: {speed_str}x", end='', flush=True)
            
            process.wait()
            print()  # New line after progress
            
            if process.returncode != 0:
                self.log(f"\n✗ FFmpeg conversion failed", Fore.RED)
                raise Exception("FFmpeg conversion failed")
            
            file_size_mb = output_file.stat().st_size / (1024 * 1024)
            self.log(f"\n✅ M4B created successfully!", Fore.GREEN)
            self.log(f"   Location: {output_file}", Fore.GREEN)
            self.log(f"   Size: {file_size_mb:.2f} MB", Fore.GREEN)
            
            # Clean up temporary files
            self.log("\n🧹 Cleaning up temporary files...", Fore.CYAN)
            try:
                if file_list.exists():
                    file_list.unlink()
                    self.log("   ✓ Removed files.txt", Fore.GREEN)
                if chapters_file.exists():
                    chapters_file.unlink()
                    self.log("   ✓ Removed chapters.txt", Fore.GREEN)
                if cover_path and cover_path.exists():
                    cover_path.unlink()
                    self.log("   ✓ Removed cover.jpg", Fore.GREEN)
            except Exception as cleanup_error:
                self.log(f"   ⚠️ Cleanup warning: {cleanup_error}", Fore.YELLOW)
            
            return output_file
            
        except Exception as e:
            self.log(f"\n✗ Error creating M4B: {e}", Fore.RED)
            raise


def process_audiobook(creator: 'M4BCreator', folder: Path, args) -> bool:
    """Process a single audiobook folder."""
    creator.log(f"\n{'='*60}", Fore.MAGENTA)
    creator.log(f"Processing: {folder.name}", Fore.MAGENTA)
    creator.log(f"{'='*60}", Fore.MAGENTA)
    
    # Get audio files from this folder
    files = creator.get_audio_files_from_folder(folder)
    if not files:
        creator.log(f"✗ No audio files found in {folder.name}", Fore.RED)
        return False
    
    creator.log(f"\n✓ Found {len(files)} audio file(s)", Fore.GREEN)
    
    # Preview chapters
    chapters = creator.preview_chapters(files)
    
    # Ask for approval
    creator.log(f"\n{Fore.YELLOW}Review the chapters above.", Fore.YELLOW)
    approval = input(f"{Fore.YELLOW}Do you want to proceed with creating this audiobook? (Y/n): {Style.RESET_ALL}").strip().lower()
    
    if approval == 'n':
        creator.log("⏭️  Skipped by user", Fore.YELLOW)
        return False
    
    # Step 1: Extract metadata
    creator.log(f"\n{'='*60}", Fore.CYAN)
    creator.log("Step 1: Extracting metadata from audio files", Fore.CYAN)
    creator.log(f"{'='*60}", Fore.CYAN)
    
    file_metadata = creator.extract_metadata_from_files(files)
    
    # Use folder name as default title if not found
    if not file_metadata.get('title'):
        file_metadata['title'] = folder.name
    
    # Step 2: Search Open Library
    metadata = file_metadata.copy()
    
    if not args.no_search:
        creator.log(f"\n{'='*60}", Fore.CYAN)
        creator.log("Step 2: Searching Open Library for additional metadata", Fore.CYAN)
        creator.log(f"{'='*60}", Fore.CYAN)
        
        search_title = args.title or file_metadata.get("title") or folder.name
        
        if search_title:
            library_metadata = creator.fetch_open_library_metadata(search_title)
            
            # Merge metadata
            for key in ["title", "author", "publisher", "year", "description", "cover_url"]:
                if library_metadata.get(key):
                    metadata[key] = library_metadata[key]
    
    # Step 3: Validate metadata
    creator.log(f"\n{'='*60}", Fore.CYAN)
    creator.log("Step 3: Validating metadata", Fore.CYAN)
    creator.log(f"{'='*60}", Fore.CYAN)
    
    if not metadata.get("title"):
        metadata["title"] = folder.name
    if not metadata.get("author"):
        metadata["author"] = "Unknown Author"
    
    creator.log("✓ Metadata validated", Fore.GREEN)
    
    # Display metadata summary
    creator.log(f"\n{'='*60}", Fore.CYAN)
    creator.log("Current Metadata Summary", Fore.CYAN)
    creator.log(f"{'='*60}", Fore.CYAN)
    creator.log(f"Title:       {Fore.GREEN}{metadata.get('title', 'N/A')}{Style.RESET_ALL}")
    creator.log(f"Author:      {Fore.GREEN}{metadata.get('author', 'N/A')}{Style.RESET_ALL}")
    creator.log(f"Narrator:    {Fore.CYAN}{metadata.get('narrator', 'N/A')}{Style.RESET_ALL}")
    creator.log(f"Publisher:   {Fore.CYAN}{metadata.get('publisher', 'N/A')}{Style.RESET_ALL}")
    creator.log(f"Year:        {Fore.CYAN}{metadata.get('year', 'N/A')}{Style.RESET_ALL}")
    creator.log(f"Description: {Fore.CYAN}{metadata.get('description', 'N/A')[:50]}...{Style.RESET_ALL}" if metadata.get('description') else f"Description: {Fore.CYAN}N/A{Style.RESET_ALL}")
    creator.log(f"Cover:       {Fore.GREEN}Yes{Style.RESET_ALL}" if metadata.get('cover_url') or metadata.get('cover_path') else f"Cover:       {Fore.YELLOW}No{Style.RESET_ALL}")
    
    # Step 4: Edit metadata
    if not args.no_edit:
        creator.log(f"\n{'='*60}", Fore.CYAN)
        creator.log("Step 4: Review and edit metadata", Fore.CYAN)
        creator.log(f"{'='*60}", Fore.CYAN)
        
        edit_choice = input(f"\n{Fore.YELLOW}Do you want to edit the metadata? (y/N): {Style.RESET_ALL}").strip().lower()
        if edit_choice == 'y':
            metadata = creator.edit_metadata(metadata, allow_empty=True)
    
    # Step 5: Handle cover art
    creator.log(f"\n{'='*60}", Fore.CYAN)
    creator.log("Step 5: Processing cover art", Fore.CYAN)
    creator.log(f"{'='*60}", Fore.CYAN)
    
    cover_path = None
    if metadata.get("cover_path"):
        cover_path = creator.download_cover(metadata["cover_path"])
    elif metadata.get("cover_url"):
        cover_path = creator.download_cover(metadata["cover_url"])
    
    if not cover_path:
        creator.log("⚠️  No cover art will be embedded", Fore.YELLOW)
    
    # Step 6: Generate chapters
    creator.log(f"\n{'='*60}", Fore.CYAN)
    creator.log("Step 6: Generating chapter metadata", Fore.CYAN)
    creator.log(f"{'='*60}", Fore.CYAN)
    chapters_file = creator.generate_chapters(files)
    
    # Create file list
    file_list = creator.create_file_list(files)
    
    # Step 7: Create M4B
    creator.log(f"\n{'='*60}", Fore.MAGENTA)
    creator.log("Step 7: Creating M4B audiobook file", Fore.MAGENTA)
    creator.log(f"{'='*60}", Fore.MAGENTA)
    
    # Determine output filename
    output_filename = args.name or f"{metadata['title']}.m4b"
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
        return True
        
    except Exception as e:
        creator.log(f"\n✗ Error creating audiobook: {e}", Fore.RED)
        return False


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
    
    # Find audiobook folders
    creator.log("\n🔍 Scanning input directory...", Fore.CYAN)
    folders = creator.find_audiobook_folders()
    
    if not folders:
        creator.log(f"✗ No audiobook folders found in {args.input}", Fore.RED)
        creator.log(f"  Looking for folders containing audio files (mp3, m4a, etc.)", Fore.YELLOW)
        return 1
    
    creator.log(f"\n✓ Found {len(folders)} audiobook folder(s):", Fore.GREEN)
    for i, folder in enumerate(folders, 1):
        file_count = len(creator.get_audio_files_from_folder(folder))
        creator.log(f"  {i}. {folder.name} ({file_count} files)", Fore.CYAN)
    
    # Process each audiobook
    successful = 0
    failed = 0
    
    for folder in folders:
        if process_audiobook(creator, folder, args):
            successful += 1
        else:
            failed += 1
    
    # Final summary
    creator.log(f"\n{'='*60}", Fore.CYAN)
    creator.log("Processing Summary", Fore.CYAN)
    creator.log(f"{'='*60}", Fore.CYAN)
    creator.log(f"✅ Successful: {successful}", Fore.GREEN if successful > 0 else Fore.YELLOW)
    creator.log(f"❌ Failed/Skipped: {failed}", Fore.RED if failed > 0 else Fore.GREEN)
    
    return 0 if successful > 0 else 1



if __name__ == "__main__":
    sys.exit(main())

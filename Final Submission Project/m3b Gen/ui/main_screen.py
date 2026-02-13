"""Main screen for the M4B Generator application."""

from textual.screen import Screen
from textual.widgets import Header, Footer, Button, Static, DirectoryTree
from textual.containers import Container, Vertical, Horizontal
from textual.binding import Binding
from pathlib import Path

from .directory_selector import DirectorySelector


class MainScreen(Screen):
    """Main menu screen."""

    BINDINGS = [
        Binding("q", "quit", "Quit"),
    ]

    CSS = """
    MainScreen {
        align: center middle;
    }
    
    #main-container {
        width: 60;
        height: auto;
        border: thick $primary;
        background: $surface;
        padding: 2 4;
    }
    
    #title {
        width: 100%;
        content-align: center middle;
        text-style: bold;
        color: $accent;
        margin-bottom: 2;
    }
    
    #subtitle {
        width: 100%;
        content-align: center middle;
        color: $text-muted;
        margin-bottom: 3;
    }
    
    Button {
        width: 100%;
        margin: 1 0;
    }
    """

    def compose(self):
        """Compose the main screen."""
        yield Header()

        with Container(id="main-container"):
            yield Static("MP3 to M4B Audiobook Generator", id="title")
            yield Static("Convert your MP3 files to M4B audiobooks", id="subtitle")

            yield Button("📁 Select MP3 Directory", id="select-dir", variant="primary")
            yield Button("❓ Help", id="help")
            yield Button("⚙️  Settings", id="settings")
            yield Button("❌ Quit", id="quit", variant="error")

        yield Footer()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        """Handle button presses."""
        if event.button.id == "select-dir":
            self.app.push_screen(DirectorySelector())
        elif event.button.id == "help":
            self.app.push_screen(HelpScreen())
        elif event.button.id == "settings":
            self.notify("Settings coming soon!", severity="information")
        elif event.button.id == "quit":
            self.app.exit()


class HelpScreen(Screen):
    """Help screen with instructions."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back"),
    ]

    CSS = """
    HelpScreen {
        align: center middle;
    }
    
    #help-container {
        width: 80;
        height: auto;
        border: thick $primary;
        background: $surface;
        padding: 2 4;
    }
    
    #help-title {
        width: 100%;
        content-align: center middle;
        text-style: bold;
        color: $accent;
        margin-bottom: 2;
    }
    
    #help-content {
        width: 100%;
        height: auto;
        color: $text;
    }
    """

    def compose(self):
        """Compose the help screen."""
        yield Header()

        with Container(id="help-container"):
            yield Static("Help", id="help-title")
            yield Static(self._get_help_text(), id="help-content")

        yield Footer()

    def _get_help_text(self) -> str:
        """Get help text."""
        return """
How to use MP3 to M4B Generator:

1. Select Directory
   Choose a folder containing your MP3 audiobook files.
   Files will be automatically sorted by filename.

2. Enter Book Information
   Search for your book's metadata by entering:
   - Book title (required)
   - Author name (optional)
   - ISBN (optional)

3. Review Metadata
   Review and edit:
   - Title, author, narrator
   - Book description
   - Cover image

4. Set Up Chapters
   Two modes available:
   - File-based: One chapter per MP3 file
   - Timestamp-based: For single long MP3s

5. Generate M4B
   The tool will create your audiobook with:
   - Embedded chapters
   - Cover art
   - Complete metadata

Requirements:
- FFmpeg must be installed and in your PATH
- MP3 files should have consistent format

Press ESC to go back
"""

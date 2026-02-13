"""Directory selector screen."""

from pathlib import Path
from textual.screen import Screen
from textual.widgets import Header, Footer, Button, Static, DirectoryTree, Label
from textual.containers import Container, Vertical, Horizontal, ScrollableContainer
from textual.binding import Binding

from core.audio_handler import AudioHandler
from .metadata_search import MetadataSearchScreen


class DirectorySelector(Screen):
    """Screen for selecting MP3 directory."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back"),
        Binding("enter", "select_directory", "Select"),
    ]

    CSS = """
    DirectorySelector {
        align: center top;
    }
    
    #dir-container {
        width: 90;
        height: 90%;
        border: thick $primary;
        background: $surface;
        margin-top: 1;
    }
    
    #dir-title {
        width: 100%;
        content-align: center middle;
        text-style: bold;
        color: $accent;
        padding: 1;
        background: $primary-darken-2;
    }
    
    #tree-container {
        width: 100%;
        height: 1fr;
        border: solid $primary;
        margin: 1;
    }
    
    #info-panel {
        width: 100%;
        height: auto;
        border: solid $accent;
        padding: 1;
        margin: 1;
        background: $primary-darken-3;
    }
    
    #button-panel {
        width: 100%;
        height: auto;
        align: center middle;
        padding: 1;
    }
    
    Button {
        margin: 0 1;
    }
    """

    def __init__(self):
        """Initialize directory selector."""
        super().__init__()
        self.selected_path: Path = Path.home()
        self.audio_handler: AudioHandler = None

    def compose(self):
        """Compose the directory selector screen."""
        yield Header()

        with Container(id="dir-container"):
            yield Static("Select MP3 Directory", id="dir-title")

            with ScrollableContainer(id="tree-container"):
                yield DirectoryTree(str(Path.home()))

            with Container(id="info-panel"):
                yield Label("Select a directory containing MP3 files", id="info-text")

            with Horizontal(id="button-panel"):
                yield Button("Select", id="select-btn", variant="primary")
                yield Button("Cancel", id="cancel-btn")

        yield Footer()

    def on_directory_tree_directory_selected(
        self, event: DirectoryTree.DirectorySelected
    ) -> None:
        """Handle directory selection in tree."""
        self.selected_path = Path(event.path)
        self._scan_directory()

    def _scan_directory(self) -> None:
        """Scan selected directory for audio files."""
        info_label = self.query_one("#info-text", Label)

        try:
            self.audio_handler = AudioHandler(self.selected_path)
            audio_files = self.audio_handler.scan_directory()

            if not audio_files:
                info_label.update(
                    f"📁 {self.selected_path.name}\n"
                    "⚠️  No MP3 files found in this directory"
                )
                return

            # Validate files
            validation = self.audio_handler.validate_consistency()
            total_duration = self.audio_handler.format_duration(
                validation["total_duration"]
            )

            # Build info text
            info_text = f"📁 {self.selected_path.name}\n"
            info_text += f"📄 Found {validation['file_count']} MP3 files\n"
            info_text += f"⏱️  Total duration: {total_duration}\n"

            if validation["warnings"]:
                info_text += "\n⚠️  Warnings:\n"
                for warning in validation["warnings"]:
                    info_text += f"  • {warning}\n"
            else:
                info_text += "\n✅ All files are consistent"

            info_label.update(info_text)

        except Exception as e:
            info_label.update(f"📁 {self.selected_path.name}\n❌ Error: {str(e)}")

    def on_button_pressed(self, event: Button.Pressed) -> None:
        """Handle button presses."""
        if event.button.id == "select-btn":
            self.action_select_directory()
        elif event.button.id == "cancel-btn":
            self.app.pop_screen()

    def action_select_directory(self) -> None:
        """Select the current directory and proceed."""
        if not self.audio_handler or not self.audio_handler.audio_files:
            self.notify("Please select a directory with MP3 files", severity="warning")
            return

        # Sort files by filename (default)
        self.audio_handler.sort_files("filename")

        # Move to metadata search screen
        self.app.pop_screen()
        self.app.push_screen(MetadataSearchScreen(self.audio_handler))

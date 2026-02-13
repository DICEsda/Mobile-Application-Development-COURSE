"""Chapter editor screen."""

from pathlib import Path
from typing import Optional
from textual.screen import Screen
from textual.widgets import (
    Header,
    Footer,
    Button,
    Static,
    Input,
    Label,
    ListView,
    ListItem,
    DataTable,
)
from textual.containers import Container, Vertical, Horizontal, ScrollableContainer
from textual.binding import Binding

from core.audio_handler import AudioHandler
from core.metadata_provider import BookMetadata
from core.chapter_manager import ChapterManager
from .generator_screen import GeneratorScreen


class ChapterEditorScreen(Screen):
    """Screen for editing audiobook chapters."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back"),
        Binding("ctrl+s", "update_chapter", "Update"),
        Binding("delete", "delete_chapter", "Delete"),
        Binding("f2", "auto_name", "Auto-name"),
    ]

    CSS = """
    ChapterEditorScreen {
        align: center top;
    }
    
    #chapter-container {
        width: 95;
        height: 95%;
        border: thick $primary;
        background: $surface;
        margin-top: 1;
    }
    
    #chapter-title {
        width: 100%;
        content-align: center middle;
        text-style: bold;
        color: $accent;
        padding: 1;
        background: $primary-darken-2;
    }
    
    #chapter-content {
        width: 100%;
        height: 1fr;
        padding: 1 2;
    }
    
    #instructions {
        width: 100%;
        content-align: center middle;
        background: $accent-darken-2;
        color: $text;
        padding: 1;
        margin-bottom: 1;
        text-style: bold;
    }
    
    #mode-panel {
        width: 100%;
        height: auto;
        padding: 1;
        margin-bottom: 1;
        align: center middle;
    }
    
    #table-header {
        width: 100%;
        content-align: center middle;
        background: $primary-darken-1;
        color: $accent;
        padding: 1;
        text-style: bold;
        border: solid $primary;
    }
    
    #chapter-table-container {
        width: 100%;
        height: 30;
        border: thick $accent;
        margin: 1 0;
        background: $surface-darken-1;
    }
    
    DataTable {
        width: 100%;
        height: 100%;
        border: solid $primary;
    }
    
    DataTable > .datatable--header {
        background: $accent-darken-2;
        color: $text;
        text-style: bold;
    }
    
    DataTable > .datatable--cursor {
        background: $accent;
    }
    
    #edit-panel {
        width: 100%;
        height: auto;
        border: thick $accent;
        padding: 2;
        margin: 1 0;
        background: $boost;
    }
    
    #edit-label {
        text-style: bold;
        color: $text;
        margin-bottom: 1;
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

    def __init__(
        self,
        audio_handler: AudioHandler,
        metadata: BookMetadata,
        narrator: Optional[str],
        cover_path: Optional[Path],
    ):
        """Initialize chapter editor screen.

        Args:
            audio_handler: AudioHandler with loaded files
            metadata: BookMetadata
            narrator: Optional narrator name
            cover_path: Optional path to cover image
        """
        super().__init__()
        self.audio_handler = audio_handler
        self.metadata = metadata
        self.narrator = narrator
        self.cover_path = cover_path

        # Initialize chapter manager
        self.chapter_manager = ChapterManager()
        self.chapter_manager.create_from_files(audio_handler.audio_files)

        self.mode = "file-based"  # or "timestamp-based"
        self.selected_row = 0

    def compose(self):
        """Compose the chapter editor screen."""
        yield Header()

        with Container(id="chapter-container"):
            yield Static("Chapter Configuration", id="chapter-title")

            with ScrollableContainer(id="chapter-content"):
                # Instructions
                yield Static(
                    "📖 Each MP3 = One Chapter | Using ID3 titles from your files | Click any row to rename",
                    id="instructions",
                )

                # Mode selection
                with Horizontal(id="mode-panel"):
                    yield Button(
                        "🔄 Auto-name (Chapter 1, 2, 3...)", id="auto-name-btn"
                    )

                # Chapter list header
                yield Static(
                    f"📋 CHAPTER PREVIEW - {len(self.chapter_manager.chapters)} Chapters with Timeframes",
                    id="table-header",
                )

                # Chapter list
                with Container(id="chapter-table-container"):
                    yield DataTable(
                        id="chapter-table",
                        cursor_type="row",
                        show_cursor=True,
                        zebra_stripes=True,
                    )

                # Edit panel
                with Container(id="edit-panel"):
                    yield Label("✏️ Edit selected chapter name:", id="edit-label")
                    yield Input(
                        placeholder="Enter chapter title here...", id="edit-title-input"
                    )

                    with Horizontal():
                        yield Button(
                            "✓ Update Name", id="update-btn", variant="success"
                        )
                        yield Button(
                            "❌ Delete Chapter", id="delete-btn", variant="error"
                        )

            with Horizontal(id="button-panel"):
                yield Button("Generate M4B →", id="generate-btn", variant="primary")
                yield Button("Back", id="back-btn")

        yield Footer()

    def on_mount(self) -> None:
        """Handle screen mount."""
        self._populate_table()

        # Show feedback about title sources
        if hasattr(self.chapter_manager, "last_create_stats"):
            stats = self.chapter_manager.last_create_stats
            id3_count = stats.get("from_id3", 0)
            filename_count = stats.get("from_filename", 0)

            if id3_count > 0:
                self.notify(
                    f"✓ Using ID3 titles from {id3_count} MP3 file(s)",
                    severity="information",
                    timeout=5,
                )
            if filename_count > 0:
                self.notify(
                    f"Note: {filename_count} file(s) have no ID3 title, using filenames",
                    severity="information",
                    timeout=5,
                )

    def _populate_table(self) -> None:
        """Populate chapter table."""
        table = self.query_one("#chapter-table", DataTable)
        table.clear(columns=True)

        # Add columns
        table.add_columns("#", "Title", "Start", "Duration")

        # Add rows
        for i, chapter in enumerate(self.chapter_manager.chapters, start=1):
            start_time = ChapterManager.format_timestamp(chapter.start_ms)
            duration = ""
            if chapter.duration_ms:
                duration = self.audio_handler.format_duration(
                    chapter.duration_ms / 1000
                )

            table.add_row(str(i), chapter.title, start_time, duration)

    def on_data_table_row_selected(self, event: DataTable.RowSelected) -> None:
        """Handle row selection."""
        self.selected_row = event.cursor_row

        if 0 <= self.selected_row < len(self.chapter_manager.chapters):
            chapter = self.chapter_manager.chapters[self.selected_row]

            # Update edit panel
            title_input = self.query_one("#edit-title-input", Input)
            title_input.value = chapter.title
            title_input.focus()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        """Handle button presses."""
        if event.button.id == "auto-name-btn":
            self._auto_name_chapters()
        elif event.button.id == "update-btn":
            self._update_chapter()
        elif event.button.id == "delete-btn":
            self._delete_chapter()
        elif event.button.id == "generate-btn":
            self._generate_audiobook()
        elif event.button.id == "back-btn":
            self.app.pop_screen()

    def action_update_chapter(self) -> None:
        """Action to update chapter (Ctrl+S)."""
        self._update_chapter()

    def action_delete_chapter(self) -> None:
        """Action to delete chapter (Delete key)."""
        self._delete_chapter()

    def action_auto_name(self) -> None:
        """Action to auto-name chapters (F2)."""
        self._auto_name_chapters()

    def _auto_name_chapters(self) -> None:
        """Auto-name chapters."""
        self.chapter_manager.auto_name_chapters("Chapter {num}")
        self._populate_table()
        self.notify("Chapters auto-named", severity="information")

    def _update_chapter(self) -> None:
        """Update selected chapter."""
        if not (0 <= self.selected_row < len(self.chapter_manager.chapters)):
            self.notify("No chapter selected", severity="warning")
            return

        title_input = self.query_one("#edit-title-input", Input)
        new_title = title_input.value.strip()

        if not new_title:
            self.notify("Chapter title cannot be empty", severity="error")
            return

        self.chapter_manager.update_chapter(self.selected_row, title=new_title)
        self._populate_table()
        self.notify("Chapter updated", severity="information")

    def _delete_chapter(self) -> None:
        """Delete selected chapter."""
        if not (0 <= self.selected_row < len(self.chapter_manager.chapters)):
            self.notify("No chapter selected", severity="warning")
            return

        if len(self.chapter_manager.chapters) == 1:
            self.notify("Cannot delete the only chapter", severity="error")
            return

        self.chapter_manager.remove_chapter(self.selected_row)
        self._populate_table()
        self.notify("Chapter deleted", severity="information")

    def _generate_audiobook(self) -> None:
        """Proceed to audiobook generation."""
        # Validate chapters
        warnings = self.chapter_manager.validate()

        if warnings:
            warning_text = "\n".join(warnings)
            self.notify(
                f"Chapter warnings:\n{warning_text}", severity="warning", timeout=5
            )

        # Move to generator screen
        self.app.pop_screen()
        self.app.push_screen(
            GeneratorScreen(
                self.audio_handler,
                self.metadata,
                self.chapter_manager,
                self.narrator,
                self.cover_path,
            )
        )

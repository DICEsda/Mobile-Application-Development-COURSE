"""Metadata review and editing screen."""

from pathlib import Path
from typing import Optional
from textual.screen import Screen
from textual.widgets import Header, Footer, Button, Static, Input, Label, TextArea
from textual.containers import Container, Vertical, Horizontal, ScrollableContainer
from textual.binding import Binding

from core.audio_handler import AudioHandler
from core.metadata_provider import MetadataProvider, BookMetadata
from utils.image_preview import create_cover_display
from .chapter_editor import ChapterEditorScreen


class MetadataReviewScreen(Screen):
    """Screen for reviewing and editing metadata."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back"),
    ]

    CSS = """
    MetadataReviewScreen {
        align: center top;
    }
    
    #review-container {
        width: 90;
        height: 90%;
        border: thick $primary;
        background: $surface;
        margin-top: 1;
    }
    
    #review-title {
        width: 100%;
        content-align: center middle;
        text-style: bold;
        color: $accent;
        padding: 1;
        background: $primary-darken-2;
    }
    
    #review-content {
        width: 100%;
        height: 1fr;
        padding: 2;
    }
    
    #left-panel {
        width: 2fr;
        height: 100%;
    }
    
    #right-panel {
        width: 1fr;
        height: 100%;
        align: center top;
        padding: 1;
    }
    
    .field-label {
        width: 100%;
        margin-top: 1;
        color: $accent;
        text-style: bold;
    }
    
    Input {
        width: 100%;
        margin-bottom: 1;
    }
    
    TextArea {
        width: 100%;
        height: 10;
        margin-bottom: 1;
    }
    
    #cover-preview {
        width: 100%;
        min-height: 25;
        max-height: 35;
        border: solid $primary;
        padding: 1;
        content-align: center top;
        overflow-y: auto;
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
        metadata_provider: MetadataProvider,
    ):
        """Initialize metadata review screen.

        Args:
            audio_handler: AudioHandler with loaded files
            metadata: BookMetadata to review
            metadata_provider: MetadataProvider instance
        """
        super().__init__()
        self.audio_handler = audio_handler
        self.metadata = metadata
        self.metadata_provider = metadata_provider
        self.cover_path: Optional[Path] = None

    def compose(self):
        """Compose the metadata review screen."""
        yield Header()

        with Container(id="review-container"):
            yield Static("Review Metadata", id="review-title")

            with Horizontal(id="review-content"):
                with ScrollableContainer(id="left-panel"):
                    yield Label("Title", classes="field-label")
                    yield Input(value=self.metadata.title, id="title-field")

                    yield Label("Author(s)", classes="field-label")
                    yield Input(
                        value=", ".join(self.metadata.authors), id="authors-field"
                    )

                    yield Label("Narrator (optional)", classes="field-label")
                    yield Input(placeholder="Enter narrator name", id="narrator-field")

                    yield Label("Publication Year", classes="field-label")
                    yield Input(
                        value=str(self.metadata.publication_year)
                        if self.metadata.publication_year
                        else "",
                        placeholder="YYYY",
                        id="year-field",
                    )

                    yield Label("Language", classes="field-label")
                    yield Input(
                        value=self.metadata.language or "en", id="language-field"
                    )

                    yield Label("Description", classes="field-label")
                    yield TextArea(
                        text=self.metadata.description or "", id="description-field"
                    )

                with Vertical(id="right-panel"):
                    yield Static("Cover Image", classes="field-label")
                    yield Static(
                        "📷\n\n"
                        + (
                            "Cover available"
                            if self.metadata.cover_url
                            else "No cover image"
                        ),
                        id="cover-preview",
                    )
                    yield Button("Download Cover", id="download-cover-btn")
                    yield Button("Upload Custom", id="upload-cover-btn")

            # Audio info summary
            validation = self.audio_handler.validate_consistency()
            duration_str = self.audio_handler.format_duration(
                validation["total_duration"]
            )

            with Container(id="summary-panel-container"):
                yield Static(
                    f"📄 {validation['file_count']} files  |  ⏱️  {duration_str}",
                    id="audio-summary",
                )

            with Horizontal(id="button-panel"):
                yield Button("Continue →", id="continue-btn", variant="primary")
                yield Button("Back", id="back-btn")

        yield Footer()

    def on_mount(self) -> None:
        """Handle screen mount."""
        # Download cover if available
        if self.metadata.cover_url:
            self._download_cover()
        else:
            self._update_cover_preview()

    def _update_cover_preview(self) -> None:
        """Update cover preview display."""
        cover_preview = self.query_one("#cover-preview", Static)
        preview_content = create_cover_display(self.cover_path, preview_width=30)
        cover_preview.update(preview_content)

    def on_button_pressed(self, event: Button.Pressed) -> None:
        """Handle button presses."""
        if event.button.id == "continue-btn":
            self._continue_to_chapters()
        elif event.button.id == "back-btn":
            self.app.pop_screen()
        elif event.button.id == "download-cover-btn":
            self._download_cover()
        elif event.button.id == "upload-cover-btn":
            self.notify("Custom cover upload coming soon!", severity="information")

    def _download_cover(self) -> None:
        """Download cover image."""
        if not self.metadata.cover_url:
            self.notify("No cover URL available", severity="warning")
            return

        try:
            # Create temp directory for cover
            cache_dir = Path.home() / ".m4b_generator" / "covers"
            cache_dir.mkdir(parents=True, exist_ok=True)

            # Download cover
            cover_filename = f"{self.metadata.title.replace(' ', '_')}_cover.jpg"
            self.cover_path = cache_dir / cover_filename

            if not self.cover_path.exists():
                self.notify("Downloading cover...", severity="information")
                success = self.metadata_provider.download_cover(
                    self.metadata.cover_url, self.cover_path
                )

                if success:
                    self.notify("✓ Cover downloaded!", severity="information")
                    self._update_cover_preview()
                else:
                    self.notify("Failed to download cover", severity="error")

        except Exception as e:
            self.notify(f"Error downloading cover: {str(e)}", severity="error")

    def _continue_to_chapters(self) -> None:
        """Continue to chapter editing."""
        # Update metadata from form
        title_field = self.query_one("#title-field", Input)
        authors_field = self.query_one("#authors-field", Input)
        narrator_field = self.query_one("#narrator-field", Input)
        year_field = self.query_one("#year-field", Input)
        language_field = self.query_one("#language-field", Input)
        description_field = self.query_one("#description-field", TextArea)

        self.metadata.title = title_field.value.strip()
        self.metadata.authors = [
            a.strip() for a in authors_field.value.split(",") if a.strip()
        ]

        narrator = narrator_field.value.strip() or None

        try:
            year_str = year_field.value.strip()
            self.metadata.publication_year = int(year_str) if year_str else None
        except ValueError:
            pass

        self.metadata.language = language_field.value.strip() or "en"
        self.metadata.description = description_field.text.strip() or None

        # Validate
        if not self.metadata.title:
            self.notify("Title is required", severity="error")
            return

        if not self.metadata.authors:
            self.notify("At least one author is required", severity="error")
            return

        # Move to chapter editor
        self.app.pop_screen()
        self.app.push_screen(
            ChapterEditorScreen(
                self.audio_handler, self.metadata, narrator, self.cover_path
            )
        )

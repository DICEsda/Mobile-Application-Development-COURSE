"""M4B generation screen with progress tracking."""

from pathlib import Path
from typing import Optional
from textual.screen import Screen
from textual.widgets import Header, Footer, Button, Static, Input, Label, ProgressBar
from textual.containers import Container, Vertical, Horizontal, ScrollableContainer
from textual.binding import Binding
import threading

from core.audio_handler import AudioHandler
from core.metadata_provider import BookMetadata
from core.chapter_manager import ChapterManager
from core.m4b_creator import M4BCreator


class GeneratorScreen(Screen):
    """Screen for generating M4B audiobook."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back"),
    ]

    CSS = """
    GeneratorScreen {
        align: center middle;
    }
    
    #gen-container {
        width: 80;
        height: auto;
        border: thick $primary;
        background: $surface;
        padding: 2;
    }
    
    #gen-title {
        width: 100%;
        content-align: center middle;
        text-style: bold;
        color: $accent;
        padding: 1;
        background: $primary-darken-2;
        margin-bottom: 2;
    }
    
    #output-panel {
        width: 100%;
        height: auto;
        padding: 1;
        margin-bottom: 2;
    }
    
    #summary-panel {
        width: 100%;
        height: auto;
        border: solid $primary;
        padding: 1;
        margin: 1 0;
        background: $primary-darken-3;
    }
    
    #progress-panel {
        width: 100%;
        height: auto;
        padding: 1;
        margin: 1 0;
    }
    
    #status-text {
        width: 100%;
        content-align: center middle;
        padding: 1;
        color: $accent;
    }
    
    ProgressBar {
        width: 100%;
        margin: 1 0;
    }
    
    #log-panel {
        width: 100%;
        height: 15;
        border: solid $primary;
        padding: 1;
        background: $surface-darken-1;
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
    
    Input {
        width: 100%;
        margin: 1 0;
    }
    """

    def __init__(
        self,
        audio_handler: AudioHandler,
        metadata: BookMetadata,
        chapter_manager: ChapterManager,
        narrator: Optional[str],
        cover_path: Optional[Path],
    ):
        """Initialize generator screen.

        Args:
            audio_handler: AudioHandler with loaded files
            metadata: BookMetadata
            chapter_manager: ChapterManager with chapters
            narrator: Optional narrator name
            cover_path: Optional path to cover image
        """
        super().__init__()
        self.audio_handler = audio_handler
        self.metadata = metadata
        self.chapter_manager = chapter_manager
        self.narrator = narrator
        self.cover_path = cover_path

        self.m4b_creator = M4BCreator()
        self.output_path: Optional[Path] = None
        self.is_generating = False
        self.log_messages = []

    def compose(self):
        """Compose the generator screen."""
        yield Header()

        with Container(id="gen-container"):
            yield Static("Generate M4B Audiobook", id="gen-title")

            # Output file selection
            with Container(id="output-panel"):
                yield Label("Output file name:")

                # Generate suggested filename
                suggested_name = M4BCreator.generate_output_filename(self.metadata)

                yield Input(
                    value=suggested_name,
                    placeholder="Output filename (without extension)",
                    id="output-name-input",
                )

                yield Label("Output will be saved to: Desktop", id="output-location")

            # Summary
            with Container(id="summary-panel"):
                validation = self.audio_handler.validate_consistency()
                duration_str = self.audio_handler.format_duration(
                    validation["total_duration"]
                )

                summary_text = f"""
📖 Title: {self.metadata.title}
✍️  Author: {", ".join(self.metadata.authors)}
🎙️  Narrator: {self.narrator or "Not specified"}
📄 Files: {validation["file_count"]}
📑 Chapters: {self.chapter_manager.get_chapter_count()}
⏱️  Duration: {duration_str}
🖼️  Cover: {"Yes" if self.cover_path else "No"}
"""
                yield Static(summary_text.strip(), id="summary-text")

            # Progress
            with Container(id="progress-panel"):
                yield Static("Ready to generate", id="status-text")
                yield ProgressBar(total=100, show_eta=False, id="progress-bar")

            # Log
            with ScrollableContainer(id="log-panel"):
                yield Static("", id="log-text")

            # Buttons
            with Horizontal(id="button-panel"):
                yield Button("🚀 Generate", id="generate-btn", variant="primary")
                yield Button("Back", id="back-btn")

        yield Footer()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        """Handle button presses."""
        if event.button.id == "generate-btn":
            if not self.is_generating:
                self._start_generation()
        elif event.button.id == "back-btn":
            if not self.is_generating:
                self.app.pop_screen()
            else:
                self.notify("Cannot go back while generating", severity="warning")

    def _start_generation(self) -> None:
        """Start M4B generation."""
        # Get output filename
        output_input = self.query_one("#output-name-input", Input)
        output_name = output_input.value.strip()

        if not output_name:
            self.notify("Please enter an output filename", severity="error")
            return

        # Set output path (Desktop for now)
        desktop = Path.home() / "Desktop"
        self.output_path = desktop / f"{output_name}.m4b"

        # Check if file exists
        if self.output_path.exists():
            self.notify(
                "File already exists! Choose a different name.", severity="error"
            )
            return

        # Update UI
        self.is_generating = True
        generate_btn = self.query_one("#generate-btn", Button)
        generate_btn.disabled = True
        generate_btn.label = "Generating..."

        back_btn = self.query_one("#back-btn", Button)
        back_btn.disabled = True

        # Start generation in background thread
        thread = threading.Thread(target=self._generate_audiobook, daemon=True)
        thread.start()

    def _generate_audiobook(self) -> None:
        """Generate the audiobook (runs in background thread)."""

        def progress_callback(message: str):
            """Callback for progress updates."""
            self.app.call_from_thread(self._update_progress, message)

        try:
            success = self.m4b_creator.create_audiobook(
                audio_files=self.audio_handler.audio_files,
                output_path=self.output_path,
                metadata=self.metadata,
                chapter_manager=self.chapter_manager,
                cover_image_path=self.cover_path,
                narrator=self.narrator,
                progress_callback=progress_callback,
            )

            self.app.call_from_thread(self._generation_complete, success)

        except Exception as e:
            self.app.call_from_thread(self._generation_error, str(e))

    def _update_progress(self, message: str) -> None:
        """Update progress display.

        Args:
            message: Progress message
        """
        # Update status
        status_text = self.query_one("#status-text", Static)
        status_text.update(message)

        # Add to log
        self.log_messages.append(f"• {message}")
        log_text = self.query_one("#log-text", Static)
        log_text.update("\n".join(self.log_messages[-20:]))  # Keep last 20 messages

        # Scroll to bottom
        log_panel = self.query_one("#log-panel")
        log_panel.scroll_end(animate=False)

    def _generation_complete(self, success: bool) -> None:
        """Handle generation completion.

        Args:
            success: Whether generation was successful
        """
        self.is_generating = False

        generate_btn = self.query_one("#generate-btn", Button)
        back_btn = self.query_one("#back-btn", Button)
        status_text = self.query_one("#status-text", Static)
        progress_bar = self.query_one("#progress-bar", ProgressBar)

        if success:
            status_text.update("✅ Audiobook created successfully!")
            progress_bar.update(total=100, progress=100)

            self.notify(
                f"Audiobook saved to:\n{self.output_path}",
                severity="success",
                timeout=10,
            )

            # Update button
            generate_btn.label = "✓ Complete"
            generate_btn.variant = "success"
        else:
            status_text.update("❌ Generation failed")

            self.notify(
                "Failed to generate audiobook. Check the log for details.",
                severity="error",
            )

            # Re-enable button
            generate_btn.disabled = False
            generate_btn.label = "🔄 Retry"

        back_btn.disabled = False

    def _generation_error(self, error: str) -> None:
        """Handle generation error.

        Args:
            error: Error message
        """
        self._update_progress(f"Error: {error}")
        self._generation_complete(False)

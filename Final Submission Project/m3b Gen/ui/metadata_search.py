"""Metadata search and selection screen."""

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
)
from textual.containers import Container, Vertical, Horizontal, ScrollableContainer
from textual.binding import Binding

from core.audio_handler import AudioHandler
from core.metadata_provider import MetadataProvider, BookMetadata
from .metadata_review import MetadataReviewScreen


class MetadataSearchScreen(Screen):
    """Screen for searching and selecting book metadata."""

    BINDINGS = [
        Binding("escape", "pop_screen", "Back"),
    ]

    CSS = """
    MetadataSearchScreen {
        align: center top;
    }
    
    #meta-container {
        width: 80;
        height: 90%;
        border: thick $primary;
        background: $surface;
        margin-top: 1;
    }
    
    #meta-title {
        width: 100%;
        content-align: center middle;
        text-style: bold;
        color: $accent;
        padding: 1;
        background: $primary-darken-2;
    }
    
    #search-panel {
        width: 100%;
        height: auto;
        padding: 1 2;
    }
    
    Input {
        width: 100%;
        margin: 1 0;
    }
    
    #results-container {
        width: 100%;
        height: 1fr;
        border: solid $primary;
        margin: 1 2;
    }
    
    #results-title {
        width: 100%;
        padding: 1;
        text-style: bold;
        color: $accent;
    }
    
    ListView {
        width: 100%;
        height: 1fr;
    }
    
    ListItem {
        padding: 1;
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

    def __init__(self, audio_handler: AudioHandler):
        """Initialize metadata search screen.

        Args:
            audio_handler: AudioHandler with loaded files
        """
        super().__init__()
        self.audio_handler = audio_handler
        self.metadata_provider = MetadataProvider()
        self.search_results = []
        self.selected_metadata: BookMetadata = None

    def compose(self):
        """Compose the metadata search screen."""
        yield Header()

        with Container(id="meta-container"):
            yield Static("Search Book Metadata", id="meta-title")

            with Vertical(id="search-panel"):
                yield Label("Enter book information:")
                yield Input(placeholder="Book title (required)", id="title-input")
                yield Input(placeholder="Author (optional)", id="author-input")
                yield Input(placeholder="ISBN (optional)", id="isbn-input")
                yield Button("🔍 Search", id="search-btn", variant="primary")

            with Container(id="results-container"):
                yield Static("Search results will appear here", id="results-title")
                yield ListView(id="results-list")

            with Horizontal(id="button-panel"):
                yield Button(
                    "Select", id="select-btn", variant="success", disabled=True
                )
                yield Button("Skip Metadata", id="skip-btn", variant="warning")
                yield Button("Back", id="back-btn")

        yield Footer()

    def on_button_pressed(self, event: Button.Pressed) -> None:
        """Handle button presses."""
        if event.button.id == "search-btn":
            self._perform_search()
        elif event.button.id == "select-btn":
            self._select_metadata()
        elif event.button.id == "skip-btn":
            self._skip_metadata()
        elif event.button.id == "back-btn":
            self.app.pop_screen()

    def _perform_search(self) -> None:
        """Perform metadata search."""
        title_input = self.query_one("#title-input", Input)
        author_input = self.query_one("#author-input", Input)
        isbn_input = self.query_one("#isbn-input", Input)

        title = title_input.value.strip()
        author = author_input.value.strip() or None
        isbn = isbn_input.value.strip() or None

        if not title and not isbn:
            self.notify("Please enter at least a title or ISBN", severity="warning")
            return

        # Update UI
        results_title = self.query_one("#results-title", Static)
        results_title.update("🔍 Searching...")

        # Perform search
        self.search_results = self.metadata_provider.search(title, author, isbn)

        # Update results list
        results_list = self.query_one("#results-list", ListView)
        results_list.clear()

        if not self.search_results:
            results_title.update("No results found")
            return

        results_title.update(f"Found {len(self.search_results)} results:")

        for i, metadata in enumerate(self.search_results):
            authors_str = ", ".join(metadata.authors)
            year_str = (
                f" ({metadata.publication_year})" if metadata.publication_year else ""
            )

            # Add icon based on source
            if metadata.source == "Google Books":
                source_icon = "📗"
            elif metadata.source == "Open Library":
                source_icon = "📚"
            else:
                source_icon = "📖"

            source_str = f" {source_icon} {metadata.source}" if metadata.source else ""

            item_text = (
                f"{i + 1}. {metadata.title} – {authors_str}{year_str}{source_str}"
            )
            results_list.append(ListItem(Label(item_text)))

    def on_list_view_selected(self, event: ListView.Selected) -> None:
        """Handle result selection."""
        index = event.list_view.index
        if 0 <= index < len(self.search_results):
            self.selected_metadata = self.search_results[index]

            # Enable select button
            select_btn = self.query_one("#select-btn", Button)
            select_btn.disabled = False

    def _select_metadata(self) -> None:
        """Proceed with selected metadata."""
        if not self.selected_metadata:
            self.notify("Please select a result", severity="warning")
            return

        # Move to metadata review screen
        self.app.pop_screen()
        self.app.push_screen(
            MetadataReviewScreen(
                self.audio_handler, self.selected_metadata, self.metadata_provider
            )
        )

    def _skip_metadata(self) -> None:
        """Skip metadata search and use defaults."""
        # Create basic metadata from directory name
        dir_name = self.audio_handler.directory.name

        default_metadata = BookMetadata(
            title=dir_name,
            authors=["Unknown"],
            description=None,
            cover_url=None,
            source="Manual",
        )

        # Move to metadata review screen
        self.app.pop_screen()
        self.app.push_screen(
            MetadataReviewScreen(
                self.audio_handler, default_metadata, self.metadata_provider
            )
        )

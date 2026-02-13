#!/usr/bin/env python3
"""
MP3 to M4B Audiobook Converter
A TUI application for converting MP3 files to M4B audiobooks with metadata and chapters.
"""

from textual.app import App
from ui.main_screen import MainScreen


class M4BGeneratorApp(App):
    """Main application class for the M4B Generator."""

    CSS = """
    Screen {
        background: $surface;
    }
    """

    TITLE = "MP3 to M4B Audiobook Generator"

    def on_mount(self) -> None:
        """Initialize the application."""
        self.push_screen(MainScreen())


def main():
    """Entry point for the application."""
    app = M4BGeneratorApp()
    app.run()


if __name__ == "__main__":
    main()

"""Configuration management for M4B Generator."""

import json
from pathlib import Path
from typing import Dict, Any, Optional


class Config:
    """Configuration manager."""

    DEFAULT_CONFIG = {
        "default_output_dir": str(Path.home() / "Desktop"),
        "cache_dir": str(Path.home() / ".m4b_generator" / "cache"),
        "cover_dir": str(Path.home() / ".m4b_generator" / "covers"),
        "default_sort": "filename",  # filename, track, duration
        "default_language": "en",
        "preserve_original_bitrate": True,
        "max_cover_size": 1400,
        "auto_download_covers": True,
    }

    def __init__(self, config_path: Optional[Path] = None):
        """Initialize configuration.

        Args:
            config_path: Optional path to config file
        """
        self.config_path = config_path or (
            Path.home() / ".m4b_generator" / "config.json"
        )
        self.config: Dict[str, Any] = self.DEFAULT_CONFIG.copy()
        self.load()

    def load(self) -> None:
        """Load configuration from file."""
        if self.config_path.exists():
            try:
                with open(self.config_path, "r", encoding="utf-8") as f:
                    user_config = json.load(f)
                self.config.update(user_config)
            except Exception as e:
                print(f"Warning: Could not load config: {e}")

    def save(self) -> None:
        """Save configuration to file."""
        try:
            self.config_path.parent.mkdir(parents=True, exist_ok=True)
            with open(self.config_path, "w", encoding="utf-8") as f:
                json.dump(self.config, f, indent=2, ensure_ascii=False)
        except Exception as e:
            print(f"Warning: Could not save config: {e}")

    def get(self, key: str, default: Any = None) -> Any:
        """Get configuration value.

        Args:
            key: Configuration key
            default: Default value if key not found

        Returns:
            Configuration value
        """
        return self.config.get(key, default)

    def set(self, key: str, value: Any) -> None:
        """Set configuration value.

        Args:
            key: Configuration key
            value: Configuration value
        """
        self.config[key] = value
        self.save()

    def reset(self) -> None:
        """Reset configuration to defaults."""
        self.config = self.DEFAULT_CONFIG.copy()
        self.save()

"""Metadata provider for fetching book information from APIs."""

import requests
from typing import List, Dict, Optional
from dataclasses import dataclass
from pathlib import Path
import json


@dataclass
class BookMetadata:
    """Represents book metadata."""

    title: str
    authors: List[str]
    description: Optional[str] = None
    cover_url: Optional[str] = None
    publication_year: Optional[int] = None
    language: Optional[str] = None
    isbn: Optional[str] = None
    publisher: Optional[str] = None
    source: Optional[str] = None  # API source

    def to_dict(self) -> Dict:
        """Convert to dictionary for caching."""
        return {
            "title": self.title,
            "authors": self.authors,
            "description": self.description,
            "cover_url": self.cover_url,
            "publication_year": self.publication_year,
            "language": self.language,
            "isbn": self.isbn,
            "publisher": self.publisher,
            "source": self.source,
        }

    @classmethod
    def from_dict(cls, data: Dict) -> "BookMetadata":
        """Create from dictionary."""
        return cls(**data)


class MetadataProvider:
    """Fetches book metadata from various APIs."""

    GOOGLE_BOOKS_API = "https://www.googleapis.com/books/v1/volumes"
    OPEN_LIBRARY_API = "https://openlibrary.org/search.json"

    def __init__(self, cache_dir: Optional[Path] = None):
        """Initialize metadata provider.

        Args:
            cache_dir: Directory for caching metadata
        """
        self.cache_dir = cache_dir or Path.home() / ".m4b_generator" / "cache"
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def search(
        self, title: str, author: Optional[str] = None, isbn: Optional[str] = None
    ) -> List[BookMetadata]:
        """Search for book metadata.

        Args:
            title: Book title
            author: Optional author name
            isbn: Optional ISBN

        Returns:
            List of matching BookMetadata objects
        """
        results = []

        # Try Google Books first
        google_results = self._search_google_books(title, author, isbn)
        results.extend(google_results)

        # Try Open Library if Google Books didn't return enough results
        if len(results) < 5:
            open_library_results = self._search_open_library(title, author, isbn)
            results.extend(open_library_results)

        # Remove duplicates based on title and author
        seen = set()
        unique_results = []
        for result in results:
            key = (
                result.title.lower(),
                tuple(sorted(a.lower() for a in result.authors)),
            )
            if key not in seen:
                seen.add(key)
                unique_results.append(result)

        return unique_results[:10]  # Return top 10 results

    def _search_google_books(
        self, title: str, author: Optional[str] = None, isbn: Optional[str] = None
    ) -> List[BookMetadata]:
        """Search Google Books API.

        Args:
            title: Book title
            author: Optional author name
            isbn: Optional ISBN

        Returns:
            List of BookMetadata objects
        """
        try:
            # Build query
            if isbn:
                query = f"isbn:{isbn}"
            else:
                query = f"intitle:{title}"
                if author:
                    query += f"+inauthor:{author}"

            params = {"q": query, "maxResults": 10, "printType": "books"}

            response = requests.get(self.GOOGLE_BOOKS_API, params=params, timeout=10)
            response.raise_for_status()
            data = response.json()

            results = []
            for item in data.get("items", []):
                volume_info = item.get("volumeInfo", {})

                # Extract metadata
                title = volume_info.get("title", "Unknown")
                authors = volume_info.get("authors", ["Unknown"])
                description = volume_info.get("description")

                # Get highest resolution cover
                image_links = volume_info.get("imageLinks", {})
                cover_url = (
                    image_links.get("extraLarge")
                    or image_links.get("large")
                    or image_links.get("medium")
                    or image_links.get("thumbnail")
                )

                # Publication info
                published_date = volume_info.get("publishedDate", "")
                pub_year = None
                if published_date:
                    try:
                        pub_year = int(published_date[:4])
                    except:
                        pass

                # ISBN
                identifiers = volume_info.get("industryIdentifiers", [])
                isbn = None
                for identifier in identifiers:
                    if identifier.get("type") in ["ISBN_13", "ISBN_10"]:
                        isbn = identifier.get("identifier")
                        break

                metadata = BookMetadata(
                    title=title,
                    authors=authors,
                    description=description,
                    cover_url=cover_url,
                    publication_year=pub_year,
                    language=volume_info.get("language"),
                    isbn=isbn,
                    publisher=volume_info.get("publisher"),
                    source="Google Books",
                )
                results.append(metadata)

            return results

        except Exception as e:
            print(f"Error searching Google Books: {e}")
            return []

    def _search_open_library(
        self, title: str, author: Optional[str] = None, isbn: Optional[str] = None
    ) -> List[BookMetadata]:
        """Search Open Library API.

        Args:
            title: Book title
            author: Optional author name
            isbn: Optional ISBN

        Returns:
            List of BookMetadata objects
        """
        try:
            # Build query
            params = {}
            if isbn:
                params["isbn"] = isbn
            else:
                params["title"] = title
                if author:
                    params["author"] = author

            params["limit"] = 10

            response = requests.get(self.OPEN_LIBRARY_API, params=params, timeout=10)
            response.raise_for_status()
            data = response.json()

            results = []
            for doc in data.get("docs", []):
                # Extract metadata
                title = doc.get("title", "Unknown")
                authors = doc.get("author_name", ["Unknown"])

                # Description (first sentence or none)
                description = doc.get("first_sentence")
                if isinstance(description, list):
                    description = " ".join(description)

                # Cover URL
                cover_id = doc.get("cover_i")
                cover_url = None
                if cover_id:
                    cover_url = f"https://covers.openlibrary.org/b/id/{cover_id}-L.jpg"

                # Publication year
                pub_year = doc.get("first_publish_year")

                # ISBN
                isbn_list = doc.get("isbn", [])
                isbn = isbn_list[0] if isbn_list else None

                metadata = BookMetadata(
                    title=title,
                    authors=authors,
                    description=description,
                    cover_url=cover_url,
                    publication_year=pub_year,
                    language=doc.get("language", [None])[0]
                    if doc.get("language")
                    else None,
                    isbn=isbn,
                    publisher=doc.get("publisher", [None])[0]
                    if doc.get("publisher")
                    else None,
                    source="Open Library",
                )
                results.append(metadata)

            return results

        except Exception as e:
            print(f"Error searching Open Library: {e}")
            return []

    def download_cover(self, cover_url: str, output_path: Path) -> bool:
        """Download cover image.

        Args:
            cover_url: URL of cover image
            output_path: Path to save image

        Returns:
            True if successful, False otherwise
        """
        try:
            response = requests.get(cover_url, timeout=10)
            response.raise_for_status()

            output_path.parent.mkdir(parents=True, exist_ok=True)
            with open(output_path, "wb") as f:
                f.write(response.content)

            return True

        except Exception as e:
            print(f"Error downloading cover: {e}")
            return False

    def cache_metadata(self, metadata: BookMetadata, cache_key: str) -> None:
        """Cache metadata to disk.

        Args:
            metadata: BookMetadata to cache
            cache_key: Unique key for this metadata
        """
        try:
            cache_file = self.cache_dir / f"{cache_key}.json"
            with open(cache_file, "w", encoding="utf-8") as f:
                json.dump(metadata.to_dict(), f, indent=2, ensure_ascii=False)
        except Exception as e:
            print(f"Error caching metadata: {e}")

    def load_cached_metadata(self, cache_key: str) -> Optional[BookMetadata]:
        """Load cached metadata from disk.

        Args:
            cache_key: Unique key for metadata

        Returns:
            BookMetadata if found, None otherwise
        """
        try:
            cache_file = self.cache_dir / f"{cache_key}.json"
            if cache_file.exists():
                with open(cache_file, "r", encoding="utf-8") as f:
                    data = json.load(f)
                return BookMetadata.from_dict(data)
        except Exception as e:
            print(f"Error loading cached metadata: {e}")

        return None

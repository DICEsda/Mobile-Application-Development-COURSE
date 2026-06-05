package com.audiobook.app.data.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the M2B bookmark file format — the pure serialization seam
 * called out in DECISIONS.md as a high-value place to add tests.
 *
 * These exercise the model, the Audiobook <-> M2BFile conversions, and a full
 * JSON round-trip via kotlinx.serialization. No Android framework involved.
 */
class M2BFileFormatTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun sampleBook() = Audiobook(
        id = "book-1",
        title = "Atomic Habits",
        author = "James Clear",
        coverUrl = "",
        duration = "5h",
        totalDurationMinutes = 300,
        progress = 0.25f,
        currentChapter = 2,
        chapters = listOf(
            Chapter(id = 1, number = 1, title = "Introduction", startTimeMs = 0L, endTimeMs = 1_800_000L),
            Chapter(id = 2, number = 2, title = "The Fundamentals", startTimeMs = 1_800_000L, endTimeMs = 3_600_000L)
        ),
        filePath = "/storage/emulated/0/Books/atomic_habits.m4b",
        contentUri = null,
        description = "Tiny changes, remarkable results.",
        narrator = "James Clear"
    )

    @Test
    fun `toM2BFile captures bookmark metadata and chapters`() {
        val m2b = sampleBook().toM2BFile(currentPositionMs = 1_234_567L, playbackSpeed = 1.5f)

        assertEquals("1.0", m2b.version)
        assertEquals("Atomic Habits", m2b.metadata.title)
        assertEquals("James Clear", m2b.metadata.author)
        assertEquals(300, m2b.metadata.totalDurationMinutes)
        // durationMs is derived from totalDurationMinutes (300 min -> ms)
        assertEquals(300L * 60 * 1000, m2b.metadata.durationMs)

        assertEquals(1_234_567L, m2b.bookmark.positionMs)
        assertEquals(1.5f, m2b.bookmark.playbackSpeed, 0.0f)
        assertEquals(2, m2b.bookmark.chapter)
        assertEquals(0.25f, m2b.bookmark.progress, 0.0f)

        assertEquals(2, m2b.chapters.size)
        assertEquals("Introduction", m2b.chapters[0].title)
        assertEquals(0L, m2b.chapters[0].startMs)
        assertEquals(1_800_000L, m2b.chapters[0].endMs)
        assertEquals(1_800_000L, m2b.chapters[0].durationMs)

        assertEquals("book-1", m2b.source.bookId)
        assertEquals("/storage/emulated/0/Books/atomic_habits.m4b", m2b.source.filePath)
    }

    @Test
    fun `json round-trip preserves the entire structure`() {
        val original = sampleBook().toM2BFile(currentPositionMs = 999L, playbackSpeed = 2.0f)

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<M2BFile>(encoded)

        // Data-class structural equality covers every field.
        assertEquals(original, decoded)
    }

    @Test
    fun `toAudiobook reconstructs the core fields from an M2BFile`() {
        val m2b = sampleBook().toM2BFile(currentPositionMs = 500L, playbackSpeed = 1.0f)

        val restored = m2b.toAudiobook()

        assertEquals("book-1", restored.id)
        assertEquals("Atomic Habits", restored.title)
        assertEquals("James Clear", restored.author)
        assertEquals(300, restored.totalDurationMinutes)
        assertEquals(2, restored.currentChapter)
        assertEquals(0.25f, restored.progress, 0.0f)
        assertEquals(2, restored.chapters.size)
        assertEquals("The Fundamentals", restored.chapters[1].title)
        assertEquals(1_800_000L, restored.chapters[1].startTimeMs)
    }

    @Test
    fun `M2BChapter conversion is lossless for timing fields`() {
        val chapter = Chapter(id = 7, number = 7, title = "Chapter Seven", startTimeMs = 10_000L, endTimeMs = 70_000L)

        val m2bChapter = M2BChapter.fromChapter(chapter)
        assertEquals(7, m2bChapter.number)
        assertEquals("Chapter Seven", m2bChapter.title)
        assertEquals(10_000L, m2bChapter.startMs)
        assertEquals(70_000L, m2bChapter.endMs)
        assertEquals(60_000L, m2bChapter.durationMs)

        val back = m2bChapter.toChapter()
        assertEquals(7, back.number)
        assertEquals("Chapter Seven", back.title)
        assertEquals(10_000L, back.startTimeMs)
        assertEquals(70_000L, back.endTimeMs)
        assertEquals(1, back.durationMinutes) // 60_000 ms -> 1 min
    }

    @Test
    fun `decoding json without optional fields applies defaults`() {
        // Minimal document omitting every field that has a default (version,
        // narrator, description, coverArtBase64, playbackSpeed, source paths…).
        val minimal = """
            {
              "metadata": { "title": "T", "author": "A", "durationMs": 0, "totalDurationMinutes": 0 },
              "chapters": [],
              "bookmark": { "positionMs": 0, "chapter": 1, "progress": 0.0, "timestamp": 0 },
              "source": { "bookId": "b-1" }
            }
        """.trimIndent()

        val decoded = json.decodeFromString<M2BFile>(minimal)

        assertEquals("1.0", decoded.version)
        assertEquals(1.0f, decoded.bookmark.playbackSpeed, 0.0f)
        assertNull(decoded.metadata.narrator)
        assertNull(decoded.source.filePath)
        assertEquals("b-1", decoded.source.bookId)
        assertTrue(decoded.chapters.isEmpty())
    }
}

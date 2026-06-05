package com.audiobook.app.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [CueParser]. CUE parsing is pure string/regex logic (the only
 * Android dependency is a Log.d call, neutralised by
 * testOptions.unitTests.isReturnDefaultValues), so it's a clean unit-test target.
 */
class CueParserTest {

    private val parser = CueParser()

    @Test
    fun `single-file cue chains chapter end times and reads album metadata`() {
        // INDEX timestamps are MM:SS:FF (frames are 1/75s). Minutes are unbounded.
        val cue = """
            PERFORMER "James Clear"
            TITLE "Atomic Habits"
            FILE "atomic_habits.m4b" MP3
              TRACK 01 AUDIO
                TITLE "Introduction"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "The Fundamentals"
                INDEX 01 30:00:00
              TRACK 03 AUDIO
                TITLE "The 1st Law"
                INDEX 01 60:00:00
        """.trimIndent()

        val totalMs = 90L * 60 * 1000 // 90 minutes
        val result = parser.parse(cue, totalDurationMs = totalMs)

        assertEquals("Atomic Habits", result.title)
        assertEquals("James Clear", result.performer)
        assertFalse(result.isMultiFile)
        assertEquals(3, result.chapters.size)

        val (c1, c2, c3) = result.chapters
        assertEquals("Introduction", c1.title)
        assertEquals(0L, c1.startTimeMs)
        assertEquals(1_800_000L, c1.endTimeMs) // ends where ch2 starts (30 min)

        assertEquals(1_800_000L, c2.startTimeMs)
        assertEquals(3_600_000L, c2.endTimeMs) // ends where ch3 starts (60 min)

        assertEquals(3_600_000L, c3.startTimeMs)
        assertEquals(totalMs, c3.endTimeMs) // last chapter ends at total duration
    }

    @Test
    fun `index frames are converted to milliseconds`() {
        val cue = """
            FILE "x.mp3" MP3
              TRACK 01 AUDIO
                TITLE "A"
                INDEX 01 00:00:00
              TRACK 02 AUDIO
                TITLE "B"
                INDEX 01 00:10:30
        """.trimIndent()

        val result = parser.parse(cue, totalDurationMs = 20_000L)

        // 00:10:30 = 10s + 30 frames -> 10_000 + (30 * 1000 / 75) = 10_400 ms
        assertEquals(10_400L, result.chapters[1].startTimeMs)
        assertEquals(10_400L, result.chapters[0].endTimeMs)
    }

    @Test
    fun `multi-file cue computes cumulative offsets from file durations`() {
        val cue = """
            PERFORMER "Author"
            TITLE "Book"
            FILE "ch01.mp3" MP3
              TRACK 01 AUDIO
                TITLE "Chapter One"
                INDEX 01 00:00:00
            FILE "ch02.mp3" MP3
              TRACK 02 AUDIO
                TITLE "Chapter Two"
                INDEX 01 00:00:00
        """.trimIndent()

        val fileDurations = mapOf("ch01.mp3" to 600_000L, "ch02.mp3" to 400_000L)
        val result = parser.parse(cue, fileDurations = fileDurations)

        assertTrue(result.isMultiFile)
        assertEquals(2, result.chapters.size)

        val (c1, c2) = result.chapters
        assertEquals(0L, c1.startTimeMs)
        assertEquals(600_000L, c1.endTimeMs)
        assertEquals(600_000L, c2.startTimeMs) // starts after ch01's duration
        assertEquals(1_000_000L, c2.endTimeMs)

        assertEquals(mapOf(1 to "ch01.mp3", 2 to "ch02.mp3"), result.fileReferences)
    }

    @Test
    fun `tracks without an explicit title fall back to a track label`() {
        val cue = """
            FILE "x.mp3" MP3
              TRACK 01 AUDIO
                INDEX 01 00:00:00
        """.trimIndent()

        val result = parser.parse(cue, totalDurationMs = 1_000L)

        assertEquals(1, result.chapters.size)
        assertEquals("Track 1", result.chapters[0].title)
    }

    @Test
    fun `empty content yields no chapters`() {
        val result = parser.parse("", totalDurationMs = 1_000L)

        assertTrue(result.chapters.isEmpty())
        assertFalse(result.isMultiFile)
    }
}

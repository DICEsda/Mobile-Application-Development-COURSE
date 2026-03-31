package com.audiobook.app.data.parser

import android.util.Log
import com.audiobook.app.data.model.Chapter
import java.util.concurrent.TimeUnit

/**
 * Parser for CUE sheet files (.cue).
 *
 * CUE sheets define track/chapter information for audio files and are commonly
 * found alongside MP3 audiobook folders. They contain:
 * - FILE references (the audio file(s) the cue sheet describes)
 * - TRACK entries with INDEX timestamps
 * - TITLE and PERFORMER metadata per track
 *
 * This parser handles two scenarios:
 * 1. Single-file CUE: One large audio file split into chapters via timestamps
 *    (similar to M4B chapter markers — uses time-range clipping)
 * 2. Multi-file CUE: Multiple FILE entries, each referencing a separate audio file
 *    (each file becomes a chapter with its own fileUri)
 *
 * Example CUE format:
 * ```
 * PERFORMER "Author Name"
 * TITLE "Book Title"
 * FILE "chapter01.mp3" MP3
 *   TRACK 01 AUDIO
 *     TITLE "Chapter 1"
 *     INDEX 01 00:00:00
 * FILE "chapter02.mp3" MP3
 *   TRACK 02 AUDIO
 *     TITLE "Chapter 2"
 *     INDEX 01 00:00:00
 * ```
 */
class CueParser {

    companion object {
        private const val TAG = "CueParser"
        private val QUOTED_VALUE_REGEX = Regex("""\"([^\"]*)\"""")
        private val TRACK_REGEX = Regex("""TRACK\s+(\d+)""", RegexOption.IGNORE_CASE)
        private val INDEX_REGEX = Regex("""INDEX\s+\d+\s+(\d+):(\d+):(\d+)""", RegexOption.IGNORE_CASE)
    }

    /**
     * Result of parsing a CUE sheet.
     *
     * @param title Album/book title from the CUE sheet (null if not present)
     * @param performer Author/performer from the CUE sheet (null if not present)
     * @param chapters Parsed chapter list with timestamps
     * @param isMultiFile True if the CUE sheet references multiple FILE entries
     * @param fileReferences Map of chapter number to the FILE name referenced in the CUE sheet
     */
    data class CueResult(
        val title: String?,
        val performer: String?,
        val chapters: List<Chapter>,
        val isMultiFile: Boolean,
        val fileReferences: Map<Int, String>
    )

    /**
     * Parse CUE sheet content into structured chapter data.
     *
     * @param cueContent The raw text content of the .cue file
     * @param fileDurations Optional map of filename -> duration in ms, used to calculate
     *                      end times for multi-file CUE sheets
     * @param totalDurationMs Total duration of the audio in ms (used for single-file end time)
     * @return CueResult with parsed chapters and metadata
     */
    fun parse(
        cueContent: String,
        fileDurations: Map<String, Long> = emptyMap(),
        totalDurationMs: Long = 0L
    ): CueResult {
        val lines = cueContent.lines()

        var albumTitle: String? = null
        var albumPerformer: String? = null

        // Raw parsed tracks before calculating end times
        val rawTracks = mutableListOf<RawCueTrack>()
        val fileRefs = mutableMapOf<Int, String>()

        var currentFile: String? = null
        var currentTrackNumber: Int? = null
        var currentTrackTitle: String? = null
        var currentTrackPerformer: String? = null
        var currentIndexMs: Long? = null
        var fileCount = 0

        for (line in lines) {
            val trimmed = line.trim()

            when {
                // Global metadata (before any TRACK)
                trimmed.startsWith("TITLE ", ignoreCase = true) && currentTrackNumber == null -> {
                    albumTitle = extractQuotedValue(trimmed)
                }
                trimmed.startsWith("PERFORMER ", ignoreCase = true) && currentTrackNumber == null -> {
                    albumPerformer = extractQuotedValue(trimmed)
                }

                // FILE entry
                trimmed.startsWith("FILE ", ignoreCase = true) -> {
                    // Flush previous track if pending
                    flushTrack(rawTracks, fileRefs, currentTrackNumber, currentTrackTitle,
                        currentTrackPerformer, currentIndexMs, currentFile)

                    currentFile = extractQuotedValue(trimmed)
                    fileCount++
                    // Reset track state for new file
                    currentTrackNumber = null
                    currentTrackTitle = null
                    currentTrackPerformer = null
                    currentIndexMs = null
                }

                // TRACK entry
                trimmed.startsWith("TRACK ", ignoreCase = true) -> {
                    // Flush previous track if pending
                    flushTrack(rawTracks, fileRefs, currentTrackNumber, currentTrackTitle,
                        currentTrackPerformer, currentIndexMs, currentFile)

                    currentTrackNumber = TRACK_REGEX.find(trimmed)
                        ?.groupValues?.get(1)?.toIntOrNull()
                    currentTrackTitle = null
                    currentTrackPerformer = null
                    currentIndexMs = null
                }

                // Track-level TITLE
                trimmed.startsWith("TITLE ", ignoreCase = true) && currentTrackNumber != null -> {
                    currentTrackTitle = extractQuotedValue(trimmed)
                }

                // Track-level PERFORMER
                trimmed.startsWith("PERFORMER ", ignoreCase = true) && currentTrackNumber != null -> {
                    currentTrackPerformer = extractQuotedValue(trimmed)
                }

                // INDEX 01 — the main start timestamp
                trimmed.startsWith("INDEX 01", ignoreCase = true) -> {
                    currentIndexMs = parseIndex(trimmed)
                }
            }
        }

        // Flush the last track
        flushTrack(rawTracks, fileRefs, currentTrackNumber, currentTrackTitle,
            currentTrackPerformer, currentIndexMs, currentFile)

        val isMultiFile = fileCount > 1

        // Build Chapter objects with calculated end times
        val chapters = buildChapters(rawTracks, fileRefs, fileDurations, totalDurationMs, isMultiFile)

        Log.d(TAG, "Parsed CUE sheet: title=$albumTitle, performer=$albumPerformer, " +
                "tracks=${chapters.size}, multiFile=$isMultiFile")

        return CueResult(
            title = albumTitle,
            performer = albumPerformer,
            chapters = chapters,
            isMultiFile = isMultiFile,
            fileReferences = fileRefs
        )
    }

    // ──────────────────── internal helpers ────────────────────

    private data class RawCueTrack(
        val number: Int,
        val title: String,
        val performer: String?,
        val startTimeMs: Long,
        val fileName: String?
    )

    private fun flushTrack(
        tracks: MutableList<RawCueTrack>,
        fileRefs: MutableMap<Int, String>,
        trackNumber: Int?,
        title: String?,
        performer: String?,
        indexMs: Long?,
        currentFile: String?
    ) {
        if (trackNumber == null || indexMs == null) return
        val trackTitle = title ?: "Track $trackNumber"
        tracks.add(RawCueTrack(trackNumber, trackTitle, performer, indexMs, currentFile))
        if (currentFile != null) {
            fileRefs[trackNumber] = currentFile
        }
    }

    private fun buildChapters(
        rawTracks: List<RawCueTrack>,
        fileRefs: Map<Int, String>,
        fileDurations: Map<String, Long>,
        totalDurationMs: Long,
        isMultiFile: Boolean
    ): List<Chapter> {
        if (rawTracks.isEmpty()) return emptyList()

        val sorted = rawTracks.sortedBy { it.number }

        return sorted.mapIndexed { index, track ->
            val endTimeMs: Long
            val startTimeMs: Long

            if (isMultiFile) {
                // Multi-file: each track starts at a cumulative offset
                // startTimeMs = sum of durations of all previous files
                startTimeMs = calculateCumulativeOffset(sorted, index, fileDurations)
                val fileDuration = track.fileName?.let { fileDurations[it] } ?: 0L
                endTimeMs = startTimeMs + fileDuration
            } else {
                // Single-file: timestamps from the CUE INDEX entries
                startTimeMs = track.startTimeMs
                endTimeMs = if (index < sorted.size - 1) {
                    sorted[index + 1].startTimeMs
                } else {
                    totalDurationMs
                }
            }

            val durationMs = (endTimeMs - startTimeMs).coerceAtLeast(0)

            Chapter(
                id = index + 1,
                number = track.number,
                title = track.title,
                duration = formatDuration(durationMs),
                durationMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs).toInt(),
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )
        }
    }

    /**
     * Calculate the cumulative start offset for a track in a multi-file CUE sheet.
     * The offset is the sum of durations of all files for tracks before [index].
     */
    private fun calculateCumulativeOffset(
        sorted: List<RawCueTrack>,
        index: Int,
        fileDurations: Map<String, Long>
    ): Long {
        var offset = 0L
        for (i in 0 until index) {
            val fileName = sorted[i].fileName
            offset += fileName?.let { fileDurations[it] } ?: 0L
        }
        return offset
    }

    /**
     * Extract a quoted string value from a CUE directive line.
     * e.g., `TITLE "My Book"` -> `My Book`
     */
    private fun extractQuotedValue(line: String): String? {
        val match = QUOTED_VALUE_REGEX.find(line)
        return match?.groupValues?.get(1)
    }

    /**
     * Parse an INDEX line timestamp in MM:SS:FF format (frames are 1/75th of a second).
     * e.g., `INDEX 01 03:45:37` -> milliseconds
     */
    private fun parseIndex(line: String): Long? {
        val match = INDEX_REGEX.find(line) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val frames = match.groupValues[3].toLongOrNull() ?: 0L

        // CUE frames are 1/75th of a second
        return (minutes * 60_000) + (seconds * 1000) + (frames * 1000 / 75)
    }

    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(durationMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

}

package com.audiobook.app.data.model

import kotlinx.serialization.Serializable

/**
 * M2B (MPEG-2 Bookmark) File Format
 * 
 * M2B files are JSON-based bookmark files for audiobooks that store:
 * - Complete audiobook metadata
 * - Chapter markers with timestamps
 * - Current playback position and progress
 * - Playback settings (speed, etc.)
 * 
 * This format allows users to:
 * - Share their listening progress with others
 * - Restore their position across devices
 * - Back up their audiobook library with bookmarks
 */
@Serializable
data class M2BFile(
    val version: String = "1.0",
    val metadata: M2BMetadata,
    val chapters: List<M2BChapter>,
    val bookmark: M2BBookmark,
    val source: M2BSource
)

/**
 * Audiobook metadata section
 */
@Serializable
data class M2BMetadata(
    val title: String,
    val author: String,
    val narrator: String? = null,
    val description: String? = null,
    val durationMs: Long,
    val totalDurationMinutes: Int,
    val coverArtBase64: String? = null  // Base64-encoded cover art (optional)
)

/**
 * Chapter information
 */
@Serializable
data class M2BChapter(
    val number: Int,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long
) {
    companion object {
        fun fromChapter(chapter: Chapter): M2BChapter {
            return M2BChapter(
                number = chapter.number,
                title = chapter.title,
                startMs = chapter.startTimeMs,
                endMs = chapter.endTimeMs,
                durationMs = chapter.endTimeMs - chapter.startTimeMs
            )
        }
    }
    
    fun toChapter(): Chapter {
        return Chapter(
            id = number,
            number = number,
            title = title,
            startTimeMs = startMs,
            endTimeMs = endMs,
            duration = formatDuration(durationMs),
            durationMinutes = (durationMs / 60000).toInt()
        )
    }
    
    private fun formatDuration(durationMs: Long): String {
        val totalSeconds = durationMs / 1000
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

/**
 * Bookmark/progress information
 */
@Serializable
data class M2BBookmark(
    val positionMs: Long,
    val chapter: Int,
    val progress: Float,  // 0.0 to 1.0
    val playbackSpeed: Float = 1.0f,
    val timestamp: Long  // Unix timestamp when bookmark was created
)

/**
 * Source file information
 */
@Serializable
data class M2BSource(
    val filePath: String? = null,
    val contentUri: String? = null,
    val fileHash: String? = null,  // SHA-256 hash for integrity verification
    val bookId: String  // Unique identifier for the audiobook
)

// Extension functions for easy conversion

/**
 * Convert an Audiobook with progress to M2BFile
 */
fun Audiobook.toM2BFile(
    currentPositionMs: Long,
    playbackSpeed: Float = 1.0f,
    coverArtBase64: String? = null
): M2BFile {
    return M2BFile(
        version = "1.0",
        metadata = M2BMetadata(
            title = title,
            author = author,
            narrator = narrator,
            description = description,
            durationMs = (totalDurationMinutes * 60 * 1000).toLong(),
            totalDurationMinutes = totalDurationMinutes,
            coverArtBase64 = coverArtBase64
        ),
        chapters = chapters.map { M2BChapter.fromChapter(it) },
        bookmark = M2BBookmark(
            positionMs = currentPositionMs,
            chapter = currentChapter,
            progress = progress,
            playbackSpeed = playbackSpeed,
            timestamp = System.currentTimeMillis()
        ),
        source = M2BSource(
            filePath = filePath,
            contentUri = contentUri,
            fileHash = null,  // TODO: Calculate hash if needed
            bookId = id
        )
    )
}

/**
 * Extract audiobook data from M2BFile
 */
fun M2BFile.toAudiobook(): Audiobook {
    return Audiobook(
        id = source.bookId,
        title = metadata.title,
        author = metadata.author,
        narrator = metadata.narrator,
        description = metadata.description,
        coverUrl = "",  // Will need to be set separately if cover art is provided
        duration = formatDuration(metadata.durationMs),
        totalDurationMinutes = metadata.totalDurationMinutes,
        progress = bookmark.progress,
        currentChapter = bookmark.chapter,
        chapters = chapters.map { it.toChapter() },
        filePath = source.filePath,
        contentUri = source.contentUri
    )
}

/**
 * Format duration in milliseconds to human-readable string
 */
private fun formatDuration(durationMs: Long): String {
    val totalMinutes = durationMs / 60000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

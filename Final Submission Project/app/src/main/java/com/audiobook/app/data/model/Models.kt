package com.audiobook.app.data.model

data class Audiobook(
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val duration: String,
    val totalDurationMinutes: Int = 0,
    val progress: Float = 0f, // 0.0 to 1.0
    val currentChapter: Int = 1,
    val chapters: List<Chapter> = emptyList(),
    val filePath: String? = null, // Local file path for M4B files
    val contentUri: String? = null, // Content URI for MediaStore access
    val description: String? = null, // Book synopsis/description
    val narrator: String? = null // Narrator name (if available)
)

data class Chapter(
    val id: Int = 0, // Chapter ID (auto-generated in database)
    val number: Int = 0, // Chapter number in sequence
    val title: String,
    val duration: String = "",
    val durationMinutes: Int = 0,
    val isPlaying: Boolean = false,
    val progress: Float = 0f, // 0.0 to 1.0
    val startTimeMs: Long = 0L, // Chapter start time in milliseconds
    val endTimeMs: Long = 0L // Chapter end time in milliseconds
) {
    /**
     * Calculate duration string from start/end times if not provided.
     */
    val calculatedDuration: String
        get() {
            if (duration.isNotEmpty()) return duration
            val durationMs = endTimeMs - startTimeMs
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "${minutes}:${seconds.toString().padStart(2, '0')}"
        }
}

data class UserStats(
    val booksCompleted: Int,
    val hoursListened: Int,
    val currentStreak: Int
)

data class UserProfile(
    val name: String,
    val email: String,
    val stats: UserStats
) {
    companion object {
        /** Default profile for unauthenticated users */
        val default = UserProfile(
            name = "Guest User",
            email = "Not signed in",
            stats = UserStats(
                booksCompleted = 0,
                hoursListened = 0,
                currentStreak = 0
            )
        )
    }
}

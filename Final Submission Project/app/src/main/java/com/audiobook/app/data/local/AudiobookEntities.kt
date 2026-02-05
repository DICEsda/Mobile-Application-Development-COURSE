package com.audiobook.app.data.local

import androidx.room.*
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.Chapter

/**
 * Room Entity representing an audiobook stored locally.
 * Maps to the Audiobook domain model.
 */
@Entity(tableName = "audiobooks")
data class AudiobookEntity(
    @PrimaryKey
    val id: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val duration: String,
    val totalDurationMinutes: Int,
    val filePath: String?,
    val contentUri: String?,
    val description: String? = null,
    val narrator: String? = null,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastPlayed: Long? = null
)

/**
 * Room Entity for chapter information.
 * Each chapter is linked to an audiobook via bookId.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val bookId: String,
    val chapterNumber: Int,
    val title: String,
    val startTimeMs: Long,
    val endTimeMs: Long
)

/**
 * Room Entity for tracking playback progress.
 * Stores the user's progress for each audiobook.
 */
@Entity(
    tableName = "progress",
    foreignKeys = [
        ForeignKey(
            entity = AudiobookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ProgressEntity(
    @PrimaryKey
    val bookId: String,
    val currentPositionMs: Long = 0,
    val currentChapter: Int = 1,
    val progress: Float = 0f,
    val playbackSpeed: Float = 1.0f,
    val lastUpdated: Long = System.currentTimeMillis(),
    val isSyncedToCloud: Boolean = false
)

/**
 * Combined data class for audiobook with its chapters.
 * Used for Room's @Relation queries.
 */
data class AudiobookWithChapters(
    @Embedded
    val audiobook: AudiobookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val chapters: List<ChapterEntity>
)

/**
 * Combined data class for audiobook with progress.
 */
data class AudiobookWithProgress(
    @Embedded
    val audiobook: AudiobookEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "bookId"
    )
    val progress: ProgressEntity?
)

// Extension functions for mapping between entities and domain models

fun AudiobookEntity.toDomainModel(chapters: List<Chapter> = emptyList(), progress: Float = 0f, currentChapter: Int = 1): Audiobook {
    return Audiobook(
        id = id,
        title = title,
        author = author,
        coverUrl = coverUrl,
        duration = duration,
        totalDurationMinutes = totalDurationMinutes,
        progress = progress,
        currentChapter = currentChapter,
        chapters = chapters,
        filePath = filePath,
        contentUri = contentUri,
        description = description,
        narrator = narrator
    )
}

fun Audiobook.toEntity(): AudiobookEntity {
    return AudiobookEntity(
        id = id,
        title = title,
        author = author,
        coverUrl = coverUrl,
        duration = duration,
        totalDurationMinutes = totalDurationMinutes,
        filePath = filePath,
        contentUri = contentUri,
        description = description,
        narrator = narrator
    )
}

fun ChapterEntity.toDomainModel(): Chapter {
    return Chapter(
        id = id.toInt(),
        number = chapterNumber,
        title = title,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs
    )
}

fun Chapter.toEntity(bookId: String, chapterNumber: Int): ChapterEntity {
    return ChapterEntity(
        bookId = bookId,
        chapterNumber = chapterNumber,
        title = title,
        startTimeMs = startTimeMs,
        endTimeMs = endTimeMs
    )
}

fun AudiobookWithChapters.toDomainModel(progress: ProgressEntity? = null): Audiobook {
    return audiobook.toDomainModel(
        chapters = chapters.sortedBy { it.chapterNumber }.map { it.toDomainModel() },
        progress = progress?.progress ?: 0f,
        currentChapter = progress?.currentChapter ?: 1
    )
}

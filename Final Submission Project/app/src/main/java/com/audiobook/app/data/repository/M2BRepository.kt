package com.audiobook.app.data.repository

import android.net.Uri
import android.util.Log
import com.audiobook.app.data.local.ProgressDao
import com.audiobook.app.data.local.ProgressEntity
import com.audiobook.app.data.local.AudiobookDao
import com.audiobook.app.data.local.toEntity
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.parser.ImportResult
import com.audiobook.app.data.parser.M2BExporter
import com.audiobook.app.data.parser.M2BImporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Repository for M2B bookmark file import/export operations.
 *
 * Extracted from AudiobookRepository to isolate the M2B serialization
 * concern from core library management.
 */
class M2BRepository(
    private val m2bExporter: M2BExporter,
    private val m2bImporter: M2BImporter,
    private val audiobookDao: AudiobookDao? = null,
    private val progressDao: ProgressDao? = null,
    private val audiobookScanner: AudiobookScanner,
    private val audiobookRepository: AudiobookRepository
) {

    companion object {
        private const val TAG = "M2BRepository"
    }

    /**
     * Export an audiobook with its current progress to an M2B bookmark file.
     */
    suspend fun exportToM2B(
        bookId: String,
        outputUri: Uri,
        includeCoverArt: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val audiobook = audiobookRepository.getAudiobook(bookId) ?: return@withContext false
            val progress = progressDao?.getProgress(bookId)
            m2bExporter.exportToM2B(
                audiobook = audiobook,
                currentPositionMs = progress?.currentPositionMs ?: 0L,
                playbackSpeed = progress?.playbackSpeed ?: 1.0f,
                outputUri = outputUri,
                includeCoverArt = includeCoverArt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export M2B", e)
            false
        }
    }

    /**
     * Export to a temporary M2B file in the cache directory.
     */
    suspend fun exportToM2BCache(
        bookId: String,
        includeCoverArt: Boolean = true
    ): File? = withContext(Dispatchers.IO) {
        try {
            val audiobook = audiobookRepository.getAudiobook(bookId) ?: return@withContext null
            val progress = progressDao?.getProgress(bookId)
            m2bExporter.exportToCache(
                audiobook = audiobook,
                currentPositionMs = progress?.currentPositionMs ?: 0L,
                playbackSpeed = progress?.playbackSpeed ?: 1.0f,
                includeCoverArt = includeCoverArt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export M2B to cache", e)
            null
        }
    }

    /**
     * Import an audiobook from an M2B bookmark file.
     */
    suspend fun importFromM2B(m2bUri: Uri): Audiobook? = withContext(Dispatchers.IO) {
        try {
            when (val result = m2bImporter.importFromM2B(m2bUri)) {
                is ImportResult.Success -> {
                    val audiobook = result.audiobook
                    val m2bFile = result.m2bFile
                    val existingBook = audiobookRepository.getAudiobook(audiobook.id)

                    if (existingBook != null) {
                        audiobookRepository.updateProgress(
                            bookId = audiobook.id,
                            progress = m2bFile.bookmark.progress,
                            currentChapter = m2bFile.bookmark.chapter,
                            positionMs = m2bFile.bookmark.positionMs
                        )
                        progressDao?.updatePlaybackSpeed(audiobook.id, m2bFile.bookmark.playbackSpeed)
                        Log.d(TAG, "Updated progress for existing book: ${audiobook.title}")
                        existingBook
                    } else {
                        audiobookDao?.insertAudiobook(audiobook.toEntity())
                        if (audiobook.chapters.isNotEmpty()) {
                            audiobookRepository.updateChapters(audiobook.id, audiobook.chapters)
                        }
                        progressDao?.insertProgress(
                            ProgressEntity(
                                bookId = audiobook.id,
                                currentPositionMs = m2bFile.bookmark.positionMs,
                                currentChapter = m2bFile.bookmark.chapter,
                                progress = m2bFile.bookmark.progress,
                                playbackSpeed = m2bFile.bookmark.playbackSpeed
                            )
                        )
                        result.coverArt?.let { bitmap ->
                            val coverUrl = audiobookScanner.saveCoverArt(audiobook.id, bitmap)
                            audiobookDao?.updateAudiobook(audiobook.toEntity().copy(coverUrl = coverUrl))
                        }
                        Log.d(TAG, "Imported new audiobook from M2B: ${audiobook.title}")
                        audiobook
                    }
                }
                is ImportResult.Error -> {
                    Log.e(TAG, "M2B import error: ${result.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import M2B", e)
            null
        }
    }

    /**
     * Validate an M2B file without importing it.
     */
    suspend fun validateM2BFile(m2bUri: Uri): Boolean = withContext(Dispatchers.IO) {
        m2bImporter.validateM2BFile(m2bUri)
    }

    /**
     * Generate a suggested filename for M2B export.
     */
    fun generateM2BFilename(bookId: String): String? {
        val audiobook = audiobookRepository.audiobooks.value.find { it.id == bookId }
        return audiobook?.let { m2bExporter.generateFilename(it) }
    }
}

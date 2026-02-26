package com.audiobook.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.audiobook.app.data.local.AudiobookDao
import com.audiobook.app.data.local.ProgressDao
import com.audiobook.app.data.local.ProgressEntity
import com.audiobook.app.data.local.toDomainModel
import com.audiobook.app.data.local.toEntity
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.Chapter
import com.audiobook.app.data.parser.ChapterParser
import com.audiobook.app.data.remote.BookMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext

/**
 * Repository for managing the audiobook library.
 *
 * This is a facade that delegates scanning to [AudiobookScanner] and
 * M2B operations to [M2BRepository], while owning library state,
 * progress tracking, and CRUD operations.
 */
class AudiobookRepository(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val audiobookDao: AudiobookDao? = null,
    private val progressDao: ProgressDao? = null,
    private val bookMetadataRepository: BookMetadataRepository? = null
) {

    /** Scanner is created lazily so callers don't need to know about it. */
    val scanner: AudiobookScanner by lazy {
        AudiobookScanner(context, preferencesRepository, audiobookDao, progressDao, bookMetadataRepository)
    }

    private val chapterParser = ChapterParser(context)

    companion object {
        const val AUDIOBOOKS_FOLDER_NAME = "Audiobooks"
    }

    // ──────────────────── observable state ────────────────────

    private val _audiobooks = MutableStateFlow<List<Audiobook>>(emptyList())
    val audiobooks: StateFlow<List<Audiobook>> = _audiobooks.asStateFlow()

    private val _currentBook = MutableStateFlow<Audiobook?>(null)
    val currentBook: StateFlow<Audiobook?> = _currentBook.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * Flow of audiobooks from Room database.
     * Falls back to in-memory list if DAO is not available.
     */
    val audiobooksFlow: Flow<List<Audiobook>> = audiobookDao?.let { dao ->
        dao.getAllAudiobooksWithChapters().map { audiobooksWithChapters ->
            audiobooksWithChapters.map { awc ->
                val progress = progressDao?.getProgress(awc.audiobook.id)
                awc.toDomainModel(progress)
            }
        }
    } ?: _audiobooks

    /**
     * Flow of the currently playing audiobook.
     */
    val currentBookFlow: Flow<Audiobook?> = audiobookDao?.let { dao ->
        dao.getCurrentlyPlaying().map { entity ->
            entity?.let {
                val progress = progressDao?.getProgress(it.id)
                it.toDomainModel(
                    progress = progress?.progress ?: 0f,
                    currentChapter = progress?.currentChapter ?: 1
                )
            }
        }
    } ?: _currentBook

    // ──────────────────── loading / scanning ────────────────────

    /**
     * Initialize audiobook library by scanning configured folder.
     */
    suspend fun loadAudiobooks() {
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            try {
                val books = scanner.scanAudiobooksFolder()
                _audiobooks.value = books
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Re-scan the audiobooks folder. Delegates to [AudiobookScanner].
     */
    suspend fun scanAudiobooksFolder(): List<Audiobook> {
        val books = scanner.scanAudiobooksFolder()
        _audiobooks.value = books
        return books
    }

    // ──────────────────── CRUD ────────────────────

    /**
     * Get audiobook by ID.
     */
    suspend fun getAudiobook(id: String): Audiobook? = withContext(Dispatchers.IO) {
        if (audiobookDao != null) {
            val awc = audiobookDao.getAudiobookWithChapters(id)
            val progress = progressDao?.getProgress(id)
            awc?.toDomainModel(progress)
        } else {
            _audiobooks.value.find { it.id == id }
        }
    }

    /**
     * Search audiobooks by query.
     */
    fun searchAudiobooks(query: String): Flow<List<Audiobook>> {
        return audiobookDao?.searchAudiobooks(query)?.map { entities ->
            entities.map { entity ->
                val progress = progressDao?.getProgress(entity.id)
                entity.toDomainModel(
                    progress = progress?.progress ?: 0f,
                    currentChapter = progress?.currentChapter ?: 1
                )
            }
        } ?: flowOf(
            _audiobooks.value.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true)
            }
        )
    }

    /**
     * Set the currently playing audiobook.
     */
    suspend fun setCurrentBook(audiobook: Audiobook) {
        withContext(Dispatchers.IO) {
            _currentBook.value = audiobook
            audiobookDao?.updateLastPlayed(audiobook.id)
            preferencesRepository.setLastPlayedBook(audiobook.id)
        }
    }

    /**
     * Add an audiobook from a content URI (e.g., from file picker).
     */
    suspend fun addFromUri(uri: Uri): Audiobook? = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        val fileName = getFileName(contentResolver, uri) ?: return@withContext null

        var title = fileName.removeSuffix(".m4b").removeSuffix(".m4a")
        var author = "Unknown Author"
        var coverUrl = ""
        var durationMs = 0L
        var chapters = emptyList<Chapter>()

        try {
            val metadata = chapterParser.parseM4bFile(uri)
            title = metadata.title
            author = metadata.author
            durationMs = metadata.durationMs
            chapters = metadata.chapters
            if (metadata.coverArt != null) {
                coverUrl = scanner.saveCoverArt(uri.toString().hashCode().toString(), metadata.coverArt)
            }
        } catch (e: Exception) {
            Log.e("AudiobookRepository", "Failed to extract metadata from URI", e)
        }

        val audiobook = Audiobook(
            id = uri.toString().hashCode().toString(),
            title = title,
            author = author,
            coverUrl = coverUrl,
            duration = scanner.formatDuration(durationMs),
            totalDurationMinutes = (durationMs / 60000).toInt(),
            chapters = chapters,
            contentUri = uri.toString()
        )

        audiobookDao?.insertAudiobook(audiobook.toEntity())
        if (chapters.isNotEmpty()) {
            val chapterEntities = chapters.mapIndexed { index, chapter ->
                chapter.toEntity(audiobook.id, index + 1)
            }
            audiobookDao?.insertChapters(chapterEntities)
        }

        _audiobooks.value = _audiobooks.value + audiobook
        audiobook
    }

    /**
     * Delete an audiobook from the library.
     */
    suspend fun deleteAudiobook(bookId: String) {
        withContext(Dispatchers.IO) {
            audiobookDao?.deleteAudiobookById(bookId)
            _audiobooks.value = _audiobooks.value.filter { it.id != bookId }
        }
    }

    /**
     * Enrich audiobook metadata from OpenLibrary.
     */
    suspend fun enrichMetadata(bookId: String): Boolean = withContext(Dispatchers.IO) {
        val audiobook = getAudiobook(bookId) ?: return@withContext false
        val bookDoc = bookMetadataRepository?.searchByTitleAndAuthor(
            audiobook.title, audiobook.author
        ) ?: return@withContext false
        val coverUrl = bookDoc.getCoverUrl("L") ?: audiobook.coverUrl
        audiobookDao?.let { dao ->
            val existing = dao.getAudiobookById(bookId) ?: return@withContext false
            dao.updateAudiobook(existing.copy(coverUrl = coverUrl))
        }
        true
    }

    /**
     * Set custom audiobook folder path and re-scan.
     */
    suspend fun setAudiobookFolder(folderPath: String) {
        withContext(Dispatchers.IO) {
            preferencesRepository.setAudiobookFolderPath(folderPath)
            scanAudiobooksFolder()
        }
    }

    // ──────────────────── progress tracking ────────────────────

    /**
     * Update progress for an audiobook.
     */
    suspend fun updateProgress(bookId: String, progress: Float, currentChapter: Int, positionMs: Long = 0) {
        withContext(Dispatchers.IO) {
            progressDao?.let { dao ->
                val existing = dao.getProgress(bookId)
                if (existing != null) {
                    dao.updatePosition(bookId, positionMs, currentChapter, progress)
                } else {
                    dao.insertProgress(
                        ProgressEntity(
                            bookId = bookId,
                            currentPositionMs = positionMs,
                            currentChapter = currentChapter,
                            progress = progress
                        )
                    )
                }
            }

            _audiobooks.value = _audiobooks.value.map { book ->
                if (book.id == bookId) book.copy(progress = progress, currentChapter = currentChapter) else book
            }
            _currentBook.value?.let { current ->
                if (current.id == bookId) {
                    _currentBook.value = current.copy(progress = progress, currentChapter = currentChapter)
                }
            }
        }
    }

    /**
     * Get playback position for a book.
     */
    suspend fun getPlaybackPosition(bookId: String): Long = withContext(Dispatchers.IO) {
        progressDao?.getProgress(bookId)?.currentPositionMs ?: 0L
    }

    /**
     * Update chapters for an audiobook.
     */
    suspend fun updateChapters(bookId: String, chapters: List<Chapter>) {
        withContext(Dispatchers.IO) {
            audiobookDao?.let { dao ->
                dao.deleteChaptersForBook(bookId)
                val entities = chapters.mapIndexed { index, ch -> ch.toEntity(bookId, index + 1) }
                dao.insertChapters(entities)
            }
        }
    }

    // ──────────────────── utilities ────────────────────

    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = cursor.getString(idx)
            }
        }
        return name
    }
}

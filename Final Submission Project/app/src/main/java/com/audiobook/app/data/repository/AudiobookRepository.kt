package com.audiobook.app.data.repository

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.audiobook.app.data.local.AudiobookDao
import com.audiobook.app.data.local.AudiobookEntity
import com.audiobook.app.data.local.ChapterEntity
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
import java.io.File

/**
 * Repository for managing the audiobook library.
 * Handles scanning local storage folder for M4B/M4A files and managing metadata.
 * Users manually add audiobooks to the "Audiobooks" folder in external storage.
 * Uses Room database for local persistence.
 */
class AudiobookRepository(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val audiobookDao: AudiobookDao? = null,
    private val progressDao: ProgressDao? = null,
    private val bookMetadataRepository: BookMetadataRepository? = null
) {
    
    private val chapterParser = ChapterParser(context)
    
    companion object {
        /**
         * The folder name where users should place their M4B audiobook files.
         * Located in the external storage: /storage/emulated/0/Audiobook tests/
         */
        const val AUDIOBOOKS_FOLDER_NAME = "Audiobook tests"
    }
    
    private val _audiobooks = MutableStateFlow<List<Audiobook>>(emptyList())
    val audiobooks: StateFlow<List<Audiobook>> = _audiobooks.asStateFlow()
    
    private val _currentBook = MutableStateFlow<Audiobook?>(null)
    val currentBook: StateFlow<Audiobook?> = _currentBook.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    /**
     * Get the audiobooks folder path.
     * Creates the folder if it doesn't exist.
     */
    suspend fun getAudiobooksFolder(): File {
        val customPath = preferencesRepository.audiobookFolderPath.first()
        val folder = if (customPath != null && customPath.isNotBlank()) {
            File(customPath)
        } else {
            File(Environment.getExternalStorageDirectory(), AUDIOBOOKS_FOLDER_NAME)
        }
        if (!folder.exists()) {
            folder.mkdirs()
        }
        return folder
    }
    
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
    
    /**
     * Initialize audiobook library.
     * Scans the local Audiobooks folder for M4B/M4A files.
     */
    suspend fun loadAudiobooks() {
        withContext(Dispatchers.IO) {
            _isLoading.value = true
            try {
                // Always scan the local folder for audiobooks
                scanAudiobooksFolder()
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Scan the Audiobooks folder for M4B/M4A files.
     * This is the primary method for loading audiobooks.
     * Supports both File-based paths and SAF URIs.
     */
    suspend fun scanAudiobooksFolder(): List<Audiobook> = withContext(Dispatchers.IO) {
        val scannedAudiobooks = mutableListOf<Audiobook>()
        val customPath = preferencesRepository.audiobookFolderPath.first()
        
        // Try SAF URI first
        if (customPath != null && customPath.startsWith("content://")) {
            val treeUri = Uri.parse(customPath)
            val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            
            if (documentFile != null && documentFile.exists() && documentFile.isDirectory) {
                scanDocumentFolder(documentFile, scannedAudiobooks)
            }
        } else {
            // Fall back to File-based access
            val folder = getAudiobooksFolder()
            
            if (!folder.exists() || !folder.isDirectory) {
                _audiobooks.value = scannedAudiobooks
                return@withContext scannedAudiobooks
            }
            
            // Get all M4B and M4A files in the folder (including subdirectories)
            val audioFiles = folder.walkTopDown()
                .filter { file ->
                    file.isFile && (file.extension.equals("m4b", ignoreCase = true) ||
                            file.extension.equals("m4a", ignoreCase = true))
                }
                .toList()
            
            for (file in audioFiles) {
                try {
                    // Check if we already have this file in database
                    val existingBook = audiobookDao?.getAudiobookByPath(file.absolutePath)
                    if (existingBook != null) {
                        // Already in database, add to list from existing data
                        val progress = progressDao?.getProgress(existingBook.id)
                        val audiobook = existingBook.toDomainModel(
                            progress = progress?.progress ?: 0f,
                            currentChapter = progress?.currentChapter ?: 1
                        )
                        scannedAudiobooks.add(audiobook)
                        continue
                    }
                
                // New file, create audiobook entry with embedded metadata
                val fileUri = Uri.fromFile(file)
                
                // Extract basic metadata using MediaMetadataRetriever
                val retriever = android.media.MediaMetadataRetriever()
                var title = file.nameWithoutExtension.replace("_", " ").replace("-", " ").trim()
                var author = "Unknown Author"
                var coverUrl = ""
                var durationMs = 0L
                
                try {
                    retriever.setDataSource(file.absolutePath)
                    
                    title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        ?: title
                    
                    author = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                        ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                        ?: author
                    
                    durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull() ?: 0L
                    
                    // Extract and save cover art
                    val coverBytes = retriever.embeddedPicture
                    if (coverBytes != null) {
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                        if (bitmap != null) {
                            coverUrl = saveCoverArt(file.absolutePath.hashCode().toString(), bitmap)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        retriever.release()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Fallback to OpenLibrary if no embedded cover
                if (coverUrl.isEmpty()) {
                    coverUrl = bookMetadataRepository?.getCoverUrl(title, author) ?: ""
                }
                
                val durationMinutes = (durationMs / 60000).toInt()
                
                val audiobook = Audiobook(
                    id = file.absolutePath.hashCode().toString(),
                    title = title,
                    author = author,
                    coverUrl = coverUrl,
                    duration = formatDuration(durationMs),
                    totalDurationMinutes = durationMinutes,
                    progress = 0f,
                    currentChapter = 1,
                    chapters = emptyList(),
                    filePath = file.absolutePath,
                    contentUri = fileUri.toString()
                )
                
                scannedAudiobooks.add(audiobook)
                
                // Save to database
                audiobookDao?.insertAudiobook(audiobook.toEntity())
                
            } catch (e: Exception) {
                // Skip files that can't be processed
                e.printStackTrace()
            }
        }
        }
        
        _audiobooks.value = scannedAudiobooks
        scannedAudiobooks
    }
    
    /**
     * Scan local storage for M4B/M4A audiobook files using MediaStore.
     * This is a fallback method that scans all device storage.
     * Requires READ_EXTERNAL_STORAGE or READ_MEDIA_AUDIO permission.
     */
    suspend fun scanLocalStorage(): List<Audiobook> = withContext(Dispatchers.IO) {
        // First scan the dedicated Audiobooks folder
        val folderBooks = scanAudiobooksFolder()
        
        // Then optionally scan MediaStore for any other M4B files
        val scannedAudiobooks = folderBooks.toMutableList()
        val existingPaths = scannedAudiobooks.mapNotNull { it.filePath }.toSet()
        
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        
        // Query for M4B and M4A files specifically
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} IN (?, ?) OR " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf(
            "audio/mp4",
            "audio/x-m4b",
            "%.m4b",
            "%.m4a"
        )
        
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"
        
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            
            while (cursor.moveToNext()) {
                val path = cursor.getString(dataColumn)
                
                // Skip if already in our list (from folder scan)
                if (path in existingPaths) continue
                
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val artist = cursor.getString(artistColumn) ?: "Unknown Author"
                val album = cursor.getString(albumColumn) ?: name.removeSuffix(".m4b").removeSuffix(".m4a")
                val durationMs = cursor.getLong(durationColumn)
                
                val contentUri = Uri.withAppendedPath(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id.toString()
                )
                
                val title = album.ifBlank { name.removeSuffix(".m4b").removeSuffix(".m4a") }
                
                // Try to fetch cover from OpenLibrary
                val coverUrl = bookMetadataRepository?.getCoverUrl(title, artist) ?: ""
                
                val audiobook = Audiobook(
                    id = id.toString(),
                    title = title,
                    author = artist,
                    coverUrl = coverUrl,
                    duration = formatDuration(durationMs),
                    totalDurationMinutes = (durationMs / 60000).toInt(),
                    progress = 0f,
                    currentChapter = 1,
                    chapters = emptyList(),
                    filePath = path,
                    contentUri = contentUri.toString()
                )
                
                scannedAudiobooks.add(audiobook)
                
                // Save to database
                audiobookDao?.insertAudiobook(audiobook.toEntity())
            }
        }
        
        _audiobooks.value = scannedAudiobooks
        scannedAudiobooks
    }
    
    /**
     * Get audiobook by ID.
     */
    suspend fun getAudiobook(id: String): Audiobook? {
        return withContext(Dispatchers.IO) {
            if (audiobookDao != null) {
                val awc = audiobookDao.getAudiobookWithChapters(id)
                val progress = progressDao?.getProgress(id)
                awc?.toDomainModel(progress)
            } else {
                _audiobooks.value.find { it.id == id }
            }
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
     * Update progress for an audiobook.
     */
    suspend fun updateProgress(bookId: String, progress: Float, currentChapter: Int, positionMs: Long = 0) {
        withContext(Dispatchers.IO) {
            // Update Room database
            progressDao?.let { dao ->
                val existingProgress = dao.getProgress(bookId)
                if (existingProgress != null) {
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
            
            // Update in-memory state
            _audiobooks.value = _audiobooks.value.map { book ->
                if (book.id == bookId) {
                    book.copy(progress = progress, currentChapter = currentChapter)
                } else book
            }
            
            // Update current book if it matches
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
    suspend fun getPlaybackPosition(bookId: String): Long {
        return withContext(Dispatchers.IO) {
            progressDao?.getProgress(bookId)?.currentPositionMs ?: 0L
        }
    }
    
    /**
     * Update chapters for an audiobook.
     */
    suspend fun updateChapters(bookId: String, chapters: List<Chapter>) {
        withContext(Dispatchers.IO) {
            audiobookDao?.let { dao ->
                dao.deleteChaptersForBook(bookId)
                val chapterEntities = chapters.mapIndexed { index, chapter ->
                    chapter.toEntity(bookId, index + 1)
                }
                dao.insertChapters(chapterEntities)
            }
        }
    }
    
    /**
     * Add an audiobook from a content URI (e.g., from file picker).
     */
    suspend fun addFromUri(uri: Uri): Audiobook? = withContext(Dispatchers.IO) {
        val contentResolver = context.contentResolver
        
        // Get file name
        val fileName = getFileName(contentResolver, uri) ?: return@withContext null
        
        // Create audiobook entry
        val audiobook = Audiobook(
            id = uri.toString().hashCode().toString(),
            title = fileName.removeSuffix(".m4b").removeSuffix(".m4a"),
            author = "Unknown Author",
            coverUrl = "",
            duration = "Unknown",
            totalDurationMinutes = 0,
            progress = 0f,
            currentChapter = 1,
            chapters = emptyList(),
            filePath = null,
            contentUri = uri.toString()
        )
        
        // Save to database
        audiobookDao?.insertAudiobook(audiobook.toEntity())
        
        // Update in-memory list
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
    suspend fun enrichMetadata(bookId: String): Boolean {
        return withContext(Dispatchers.IO) {
            val audiobook = getAudiobook(bookId) ?: return@withContext false
            
            val bookDoc = bookMetadataRepository?.searchByTitleAndAuthor(
                audiobook.title,
                audiobook.author
            ) ?: return@withContext false
            
            val coverUrl = bookDoc.getCoverUrl("L") ?: audiobook.coverUrl
            
            // Update in database
            audiobookDao?.let { dao ->
                val existing = dao.getAudiobookById(bookId) ?: return@withContext false
                dao.updateAudiobook(existing.copy(coverUrl = coverUrl))
            }
            
            true
        }
    }
    
    /**
     * Set custom audiobook folder path.
     */
    suspend fun setAudiobookFolder(folderPath: String) {
        withContext(Dispatchers.IO) {
            preferencesRepository.setAudiobookFolderPath(folderPath)
            // Rescan library with new folder
            scanAudiobooksFolder()
        }
    }
    
    private fun getFileName(contentResolver: ContentResolver, uri: Uri): String? {
        var name: String? = null
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        return name
    }
    
    private fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }
    
    /**
     * Save cover art bitmap to internal storage and return file URI
     */
    private fun saveCoverArt(bookId: String, bitmap: Bitmap): String {
        return try {
            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) {
                coversDir.mkdirs()
            }
            
            val coverFile = File(coversDir, "$bookId.jpg")
            coverFile.outputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            
            Uri.fromFile(coverFile).toString()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }
    
    /**
     * Extract description from M4B file using Media3's metadata extractor.
     * M4B files store description in various atoms like ©des, ldes, or in MDTA entries.
     */
    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun extractDescriptionWithMedia3(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
            val trackGroupsFuture = androidx.media3.exoplayer.MetadataRetriever.retrieveMetadata(context, mediaItem)
            val trackGroups = trackGroupsFuture.get() // Blocking call, but we're on IO dispatcher
            
            var foundDescription: String? = null
            
            for (i in 0 until trackGroups.length) {
                val group = trackGroups.get(i)
                for (j in 0 until group.length) {
                    val format = group.getFormat(j)
                    val metadata = format.metadata ?: continue
                    
                    for (k in 0 until metadata.length()) {
                        val entry = metadata.get(k)
                        
                        // Log all metadata entries
                        android.util.Log.d("AudiobookRepo", "Metadata entry: ${entry.javaClass.simpleName}")
                        
                        when (entry) {
                            is androidx.media3.container.MdtaMetadataEntry -> {
                                // Check for description-related keys
                                val key = entry.key.lowercase()
                                android.util.Log.d("AudiobookRepo", "MDTA key: ${entry.key}")
                                if (key.contains("desc") || key.contains("comment") || key.contains("synopsis") || key.contains("ldes")) {
                                    val value = entry.value
                                    if (value is ByteArray) {
                                        foundDescription = String(value, Charsets.UTF_8)
                                        android.util.Log.d("AudiobookRepo", "Found description in MDTA: $foundDescription")
                                    }
                                }
                            }
                            is androidx.media3.extractor.metadata.id3.CommentFrame -> {
                                // ID3 COMM frame - this is where the description usually is!
                                android.util.Log.d("AudiobookRepo", "CommentFrame: lang=${entry.language}, desc=${entry.description}, text=${entry.text?.take(100)}")
                                if (!entry.text.isNullOrBlank() && foundDescription == null) {
                                    foundDescription = entry.text
                                    android.util.Log.d("AudiobookRepo", "Found description in CommentFrame: ${foundDescription?.take(100)}...")
                                }
                            }
                            is androidx.media3.extractor.metadata.id3.TextInformationFrame -> {
                                // ID3 text frame
                                android.util.Log.d("AudiobookRepo", "ID3 frame: ${entry.id} = ${entry.values}")
                                // TIT3 = Subtitle, TXXX = User-defined text
                                if (entry.id == "TIT3" || entry.id == "TXXX") {
                                    if (foundDescription == null) {
                                        foundDescription = entry.values.firstOrNull()
                                    }
                                }
                            }
                        }
                    }
                }
            }
            foundDescription
        } catch (e: Exception) {
            android.util.Log.e("AudiobookRepo", "Error extracting description", e)
            null
        }
    }
    
    /**
     * Scan a DocumentFile folder recursively for audiobook files.
     * Uses MediaMetadataRetriever to extract metadata from SAF URIs.
     */
    private suspend fun scanDocumentFolder(folder: DocumentFile, audiobooks: MutableList<Audiobook>) {
        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                scanDocumentFolder(file, audiobooks)
            } else if (file.isFile) {
                val name = file.name ?: return@forEach
                if (name.endsWith(".m4b", ignoreCase = true) || name.endsWith(".m4a", ignoreCase = true)) {
                    try {
                        // Check if we already have this file in database
                        val fileUri = file.uri.toString()
                        val existingBook = audiobookDao?.getAudiobookByPath(fileUri)
                        if (existingBook != null) {
                            val progress = progressDao?.getProgress(existingBook.id)
                            audiobooks.add(existingBook.toDomainModel(
                                progress = progress?.progress ?: 0f,
                                currentChapter = progress?.currentChapter ?: 1
                            ))
                        } else {
                            // Extract metadata using MediaMetadataRetriever for SAF URIs
                            val retriever = android.media.MediaMetadataRetriever()
                            var title = name.substringBeforeLast('.').replace("_", " ").replace("-", " ").trim()
                            var author = "Unknown Author"
                            var coverUrl = ""
                            var durationMs = 0L
                            var description: String? = null
                            var narrator: String? = null
                            
                            try {
                                // Use content resolver to open the SAF URI
                                retriever.setDataSource(context, file.uri)
                                
                                title = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                                    ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUM)
                                    ?: title
                                
                                author = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                                    ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                                    ?: retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                                    ?: author
                                
                                durationMs = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                                    ?.toLongOrNull() ?: 0L
                                
                                // Narrator is often in the composer field for audiobooks
                                narrator = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER)
                                
                                // Log all available metadata for debugging
                                android.util.Log.d("AudiobookRepo", "=== Metadata for: $title ===")
                                android.util.Log.d("AudiobookRepo", "Composer (narrator): $narrator")
                                android.util.Log.d("AudiobookRepo", "Genre: ${retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)}")
                                android.util.Log.d("AudiobookRepo", "Writer: ${retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_WRITER)}")
                                
                                // Extract and save cover art
                                val coverBytes = retriever.embeddedPicture
                                if (coverBytes != null) {
                                    val bitmap = android.graphics.BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                                    if (bitmap != null) {
                                        coverUrl = saveCoverArt(file.uri.toString().hashCode().toString(), bitmap)
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                try {
                                    retriever.release()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            
                            // Try to extract description using Media3 (can read MP4 description atoms)
                            try {
                                description = extractDescriptionWithMedia3(file.uri)
                                android.util.Log.d("AudiobookRepo", "Media3 description: $description")
                            } catch (e: Exception) {
                                android.util.Log.e("AudiobookRepo", "Failed to extract description with Media3", e)
                            }
                            
                            // Fallback to OpenLibrary if no embedded cover
                            if (coverUrl.isEmpty()) {
                                coverUrl = bookMetadataRepository?.getCoverUrl(title, author) ?: ""
                            }
                            
                            val durationMinutes = (durationMs / 60000).toInt()
                            
                            // Create new audiobook from SAF file with extracted metadata
                            val audiobook = Audiobook(
                                id = file.uri.toString().hashCode().toString(),
                                title = title,
                                author = author,
                                coverUrl = coverUrl,
                                duration = formatDuration(durationMs),
                                totalDurationMinutes = durationMinutes,
                                progress = 0f,
                                currentChapter = 1,
                                chapters = emptyList(),
                                filePath = fileUri,
                                contentUri = file.uri.toString(),
                                description = description,
                                narrator = narrator
                            )
                            audiobooks.add(audiobook)
                            audiobookDao?.insertAudiobook(audiobook.toEntity())
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
}

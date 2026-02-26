package com.audiobook.app.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.audiobook.app.data.local.AudiobookDao
import com.audiobook.app.data.local.ProgressDao
import com.audiobook.app.data.local.toDomainModel
import com.audiobook.app.data.local.toEntity
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.parser.ChapterParser
import com.audiobook.app.data.remote.BookMetadataRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles scanning local storage and SAF directories for M4B/M4A audiobook files.
 *
 * Extracted from AudiobookRepository to separate scanning/metadata-extraction
 * concerns from library management and state handling.
 */
class AudiobookScanner(
    private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val audiobookDao: AudiobookDao? = null,
    private val progressDao: ProgressDao? = null,
    private val bookMetadataRepository: BookMetadataRepository? = null
) {

    private val chapterParser = ChapterParser(context)

    companion object {
        private const val TAG = "AudiobookScanner"
        const val AUDIOBOOKS_FOLDER_NAME = "Audiobooks"
    }

    // ───────────────────────── public API ─────────────────────────

    /**
     * Get (or create) the audiobooks folder on disk.
     */
    suspend fun getAudiobooksFolder(): File {
        val customPath = preferencesRepository.audiobookFolderPath.first()
        val folder = if (customPath != null && customPath.isNotBlank()) {
            File(customPath)
        } else {
            File(Environment.getExternalStorageDirectory(), AUDIOBOOKS_FOLDER_NAME)
        }
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    /**
     * Scan the configured audiobooks folder (SAF or file-based).
     * Returns the list of discovered [Audiobook]s and persists new entries to Room.
     */
    suspend fun scanAudiobooksFolder(): List<Audiobook> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Audiobook>()
        val customPath = preferencesRepository.audiobookFolderPath.first()

        if (customPath != null && customPath.startsWith("content://")) {
            val treeUri = Uri.parse(customPath)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            if (docFile != null && docFile.exists() && docFile.isDirectory) {
                scanDocumentFolder(docFile, results)
            }
        } else {
            scanFileFolder(results)
        }

        results
    }

    /**
     * Scan MediaStore as a fallback to discover M4B/M4A files outside the
     * configured audiobooks folder.
     */
    suspend fun scanLocalStorage(): List<Audiobook> = withContext(Dispatchers.IO) {
        val folderBooks = scanAudiobooksFolder()
        val results = folderBooks.toMutableList()
        val existingPaths = results.mapNotNull { it.filePath }.toSet()

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA
        )
        val selection = "${MediaStore.Audio.Media.MIME_TYPE} IN (?, ?) OR " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ? OR " +
                "${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("audio/mp4", "audio/x-m4b", "%.m4b", "%.m4a")
        val sortOrder = "${MediaStore.Audio.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selectionArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)

            while (cursor.moveToNext()) {
                val path = cursor.getString(dataCol)
                if (path in existingPaths) continue

                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val artist = cursor.getString(artistCol) ?: "Unknown Author"
                val album = cursor.getString(albumCol) ?: name.removeSuffix(".m4b").removeSuffix(".m4a")
                val durationMs = cursor.getLong(durationCol)
                val contentUri = Uri.withAppendedPath(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id.toString())
                val title = album.ifBlank { name.removeSuffix(".m4b").removeSuffix(".m4a") }
                val coverUrl = bookMetadataRepository?.getCoverUrl(title, artist) ?: ""

                val chapters = try {
                    chapterParser.parseM4bFile(contentUri).chapters
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to extract chapters from MediaStore file", e)
                    emptyList()
                }

                val audiobook = Audiobook(
                    id = id.toString(),
                    title = title,
                    author = artist,
                    coverUrl = coverUrl,
                    duration = formatDuration(durationMs),
                    totalDurationMinutes = (durationMs / 60000).toInt(),
                    chapters = chapters,
                    filePath = path,
                    contentUri = contentUri.toString()
                )
                results.add(audiobook)
                persistAudiobook(audiobook)
            }
        }

        results
    }

    // ──────────────────── file-based scanning ────────────────────

    private suspend fun scanFileFolder(results: MutableList<Audiobook>) {
        val folder = getAudiobooksFolder()
        if (!folder.exists() || !folder.isDirectory) return

        val audioFiles = folder.walkTopDown()
            .filter { it.isFile && it.extension.equals("m4b", true) || it.extension.equals("m4a", true) }
            .toList()

        for (file in audioFiles) {
            try {
                val existing = audiobookDao?.getAudiobookByPath(file.absolutePath)
                if (existing != null) {
                    val progress = progressDao?.getProgress(existing.id)
                    results.add(existing.toDomainModel(
                        progress = progress?.progress ?: 0f,
                        currentChapter = progress?.currentChapter ?: 1
                    ))
                    continue
                }

                val audiobook = extractFromFile(file)
                results.add(audiobook)
                persistAudiobook(audiobook)
            } catch (e: Exception) {
                Log.e(TAG, "Skipping unprocessable file during scan", e)
            }
        }
    }

    private suspend fun extractFromFile(file: File): Audiobook {
        val fileUri = Uri.fromFile(file)
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

            val coverBytes = retriever.embeddedPicture
            if (coverBytes != null) {
                val bitmap = decodeSampledBitmap(coverBytes, 512)
                if (bitmap != null) {
                    coverUrl = saveCoverArt(file.absolutePath.hashCode().toString(), bitmap)
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from ${file.name}", e)
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Log.e(TAG, "Error releasing retriever", e)
            }
        }

        if (coverUrl.isEmpty()) {
            coverUrl = bookMetadataRepository?.getCoverUrl(title, author) ?: ""
        }

        val chapters = try {
            chapterParser.parseM4bFile(fileUri).chapters
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract chapters from ${file.name}", e)
            emptyList()
        }

        return Audiobook(
            id = file.absolutePath.hashCode().toString(),
            title = title,
            author = author,
            coverUrl = coverUrl,
            duration = formatDuration(durationMs),
            totalDurationMinutes = (durationMs / 60000).toInt(),
            chapters = chapters,
            filePath = file.absolutePath,
            contentUri = fileUri.toString()
        )
    }

    // ──────────────────── SAF / DocumentFile scanning ────────────────────

    private suspend fun scanDocumentFolder(folder: DocumentFile, results: MutableList<Audiobook>) {
        for (file in folder.listFiles()) {
            if (file.isDirectory) {
                scanDocumentFolder(file, results)
            } else if (file.isFile) {
                val name = file.name ?: continue
                if (!name.endsWith(".m4b", true) && !name.endsWith(".m4a", true)) continue

                try {
                    val fileUri = file.uri.toString()
                    val existing = audiobookDao?.getAudiobookByPath(fileUri)
                    if (existing != null) {
                        val progress = progressDao?.getProgress(existing.id)
                        results.add(existing.toDomainModel(
                            progress = progress?.progress ?: 0f,
                            currentChapter = progress?.currentChapter ?: 1
                        ))
                    } else {
                        val audiobook = extractFromDocumentFile(file, name)
                        results.add(audiobook)
                        persistAudiobook(audiobook)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping unprocessable SAF file during scan", e)
                }
            }
        }
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun extractFromDocumentFile(file: DocumentFile, name: String): Audiobook {
        val retriever = android.media.MediaMetadataRetriever()
        var title = name.substringBeforeLast('.').replace("_", " ").replace("-", " ").trim()
        var author = "Unknown Author"
        var coverUrl = ""
        var durationMs = 0L
        var description: String? = null
        var narrator: String? = null

        try {
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
            narrator = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_COMPOSER)

            Log.d(TAG, "=== Metadata for: $title ===")
            Log.d(TAG, "Composer (narrator): $narrator")
            Log.d(TAG, "Genre: ${retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_GENRE)}")
            Log.d(TAG, "Writer: ${retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_WRITER)}")

            val coverBytes = retriever.embeddedPicture
            if (coverBytes != null) {
                val bitmap = decodeSampledBitmap(coverBytes, 512)
                if (bitmap != null) {
                    coverUrl = saveCoverArt(file.uri.toString().hashCode().toString(), bitmap)
                    bitmap.recycle()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting metadata from SAF file", e)
        } finally {
            try { retriever.release() } catch (e: Exception) {
                Log.e(TAG, "Error releasing retriever", e)
            }
        }

        // Try Media3 for description
        try {
            description = extractDescriptionWithMedia3(file.uri)
            Log.d(TAG, "Media3 description: $description")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract description with Media3", e)
        }

        if (coverUrl.isEmpty()) {
            coverUrl = bookMetadataRepository?.getCoverUrl(title, author) ?: ""
        }

        val chapters = try {
            chapterParser.parseM4bFile(file.uri).chapters
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract chapters from $name", e)
            emptyList()
        }

        return Audiobook(
            id = file.uri.toString().hashCode().toString(),
            title = title,
            author = author,
            coverUrl = coverUrl,
            duration = formatDuration(durationMs),
            totalDurationMinutes = (durationMs / 60000).toInt(),
            chapters = chapters,
            filePath = file.uri.toString(),
            contentUri = file.uri.toString(),
            description = description,
            narrator = narrator
        )
    }

    // ──────────────────── Media3 description extraction ────────────────────

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    private suspend fun extractDescriptionWithMedia3(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val mediaItem = androidx.media3.common.MediaItem.fromUri(uri)
            val trackGroupsFuture = androidx.media3.exoplayer.MetadataRetriever.retrieveMetadata(context, mediaItem)
            val trackGroups = kotlinx.coroutines.guava.await(trackGroupsFuture)
            var foundDescription: String? = null

            for (i in 0 until trackGroups.length) {
                val group = trackGroups.get(i)
                for (j in 0 until group.length) {
                    val metadata = group.getFormat(j).metadata ?: continue
                    for (k in 0 until metadata.length()) {
                        when (val entry = metadata.get(k)) {
                            is androidx.media3.container.MdtaMetadataEntry -> {
                                val key = entry.key.lowercase()
                                if (key.contains("desc") || key.contains("comment") || key.contains("synopsis") || key.contains("ldes")) {
                                    val value = entry.value
                                    if (value is ByteArray) foundDescription = String(value, Charsets.UTF_8)
                                }
                            }
                            is androidx.media3.extractor.metadata.id3.CommentFrame -> {
                                if (!entry.text.isNullOrBlank() && foundDescription == null) {
                                    foundDescription = entry.text
                                }
                            }
                            is androidx.media3.extractor.metadata.id3.TextInformationFrame -> {
                                if ((entry.id == "TIT3" || entry.id == "TXXX") && foundDescription == null) {
                                    foundDescription = entry.values.firstOrNull()
                                }
                            }
                        }
                    }
                }
            }
            foundDescription
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting description", e)
            null
        }
    }

    // ──────────────────── utilities ────────────────────

    private suspend fun persistAudiobook(audiobook: Audiobook) {
        audiobookDao?.insertAudiobook(audiobook.toEntity())
        if (audiobook.chapters.isNotEmpty()) {
            val entities = audiobook.chapters.mapIndexed { index, ch ->
                ch.toEntity(audiobook.id, index + 1)
            }
            audiobookDao?.insertChapters(entities)
        }
    }

    internal fun saveCoverArt(bookId: String, bitmap: Bitmap): String {
        return try {
            val coversDir = File(context.filesDir, "covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val coverFile = File(coversDir, "$bookId.jpg")
            coverFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            Uri.fromFile(coverFile).toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cover art for bookId=$bookId", e)
            ""
        }
    }

    internal fun formatDuration(durationMs: Long): String {
        val totalMinutes = durationMs / 60000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    internal fun decodeSampledBitmap(data: ByteArray, maxSize: Int): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        var sampleSize = 1
        while (opts.outWidth / sampleSize > maxSize || opts.outHeight / sampleSize > maxSize) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(data, 0, data.size, decodeOpts)
    }

}

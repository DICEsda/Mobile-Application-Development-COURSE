package com.audiobook.app.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.audiobook.app.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Parser for audiobook folders containing multiple MP3 files.
 *
 * An MP3 audiobook folder typically contains:
 * - Multiple .mp3 files (one per chapter)
 * - Optional cover art image (cover.jpg, folder.jpg, front.jpg, etc.)
 * - Optional .cue sheet (chapter markers / metadata override)
 * - Optional metadata files (metadata.json, desc.txt, etc.)
 *
 * This parser discovers and processes all relevant files in the folder,
 * producing a [FolderAudiobook] result that can be converted into the
 * app's Audiobook domain model.
 *
 * Design: This class is intentionally self-contained. It does NOT depend on
 * Room, repositories, or any UI code. It only needs a Context for
 * MediaMetadataRetriever access.
 */
class MP3FolderParser(private val context: Context) {

    companion object {
        private const val TAG = "MP3FolderParser"

        /** Audio extensions treated as chapter files */
        val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "ogg", "opus", "flac", "aac", "wma", "wav")

        /** Image filenames recognized as cover art (case-insensitive, without extension) */
        val COVER_ART_NAMES = setOf(
            "cover", "folder", "front", "album", "albumart",
            "albumartsmall", "thumb", "thumbnail", "art"
        )

        /** Image extensions */
        val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "bmp", "webp")

        /** Natural sort comparator for filenames like "01 - Chapter.mp3", "Chapter 2.mp3" */
        val NATURAL_SORT: Comparator<String> = Comparator { a, b ->
            val chunksA = NATURAL_SORT_REGEX.findAll(a.lowercase()).map { it.value }.toList()
            val chunksB = NATURAL_SORT_REGEX.findAll(b.lowercase()).map { it.value }.toList()
            val len = minOf(chunksA.size, chunksB.size)
            for (i in 0 until len) {
                val ca = chunksA[i]
                val cb = chunksB[i]
                val na = ca.toLongOrNull()
                val nb = cb.toLongOrNull()
                val cmp = if (na != null && nb != null) na.compareTo(nb) else ca.compareTo(cb)
                if (cmp != 0) return@Comparator cmp
            }
            chunksA.size.compareTo(chunksB.size)
        }

        private val NATURAL_SORT_REGEX = Regex("""\d+|\D+""")
    }

    // ──────────────────── result data classes ────────────────────

    /**
     * Complete result of parsing an MP3 folder.
     */
    data class FolderAudiobook(
        val title: String,
        val author: String,
        val narrator: String?,
        val description: String?,
        val durationMs: Long,
        val chapters: List<Chapter>,
        val coverArt: Bitmap?,
        val folderPath: String
    )

    /**
     * Intermediate info about a single audio file in the folder.
     */
    private data class AudioFileInfo(
        val fileName: String,
        val uri: Uri,
        val title: String?,
        val artist: String?,
        val album: String?,
        val trackNumber: Int?,
        val durationMs: Long,
        val hasCoverArt: Boolean
    )

    // ──────────────────── public API: File-based ────────────────────

    /**
     * Parse a folder on the filesystem that contains MP3 files.
     *
     * @param folder The directory File
     * @return FolderAudiobook with all discovered metadata, or null if no audio files found
     */
    suspend fun parseFolder(folder: File): FolderAudiobook? = withContext(Dispatchers.IO) {
        if (!folder.isDirectory) return@withContext null

        val allFiles = folder.listFiles()?.toList() ?: return@withContext null

        // 1. Categorize files
        val audioFiles = allFiles
            .filter { it.isFile && it.extension.lowercase() in AUDIO_EXTENSIONS }
            .sortedWith(compareBy(NATURAL_SORT) { it.name })

        if (audioFiles.isEmpty()) return@withContext null

        val coverImageFile = findCoverImageFile(allFiles)
        val cueFile = allFiles.find { it.isFile && it.extension.equals("cue", true) }
        val descriptionFile = allFiles.find {
            it.isFile && (it.name.equals("desc.txt", true)
                    || it.name.equals("description.txt", true)
                    || it.name.equals("info.txt", true))
        }

        // 2. Extract metadata from each audio file
        val audioInfos = audioFiles.map { file ->
            extractAudioFileInfo(file.name, Uri.fromFile(file), file.absolutePath)
        }

        // 3. Try CUE sheet first for chapter info
        val cueResult = cueFile?.let { file ->
            try {
                val cueContent = file.readText()
                val fileDurations = audioInfos.associate { it.fileName to it.durationMs }
                CueParser().parse(cueContent, fileDurations, audioInfos.sumOf { it.durationMs })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse CUE file: ${file.name}", e)
                null
            }
        }

        // 4. Build chapters
        val chapters = buildChapters(audioInfos, audioFiles.map { Uri.fromFile(it) }, cueResult)

        // 5. Resolve metadata
        val title = cueResult?.title
            ?: audioInfos.firstOrNull()?.album
            ?: folder.name.replace("_", " ").replace("-", " ").trim()

        val author = cueResult?.performer
            ?: audioInfos.firstOrNull()?.artist
            ?: "Unknown Author"

        val narrator = resolveNarrator(audioInfos)
        val description = descriptionFile?.readText()?.trim()

        // 6. Resolve cover art
        val coverArt = resolveCoverArt(coverImageFile?.absolutePath, audioInfos)

        val totalDurationMs = chapters.sumOf { it.endTimeMs - it.startTimeMs }

        FolderAudiobook(
            title = title,
            author = author,
            narrator = narrator,
            description = description,
            durationMs = totalDurationMs,
            chapters = chapters,
            coverArt = coverArt,
            folderPath = folder.absolutePath
        )
    }

    // ──────────────────── public API: SAF / DocumentFile-based ────────────────────

    /**
     * Parse a SAF DocumentFile folder that contains MP3 files.
     *
     * @param folder The DocumentFile directory
     * @return FolderAudiobook with all discovered metadata, or null if no audio files found
     */
    suspend fun parseDocumentFolder(folder: DocumentFile): FolderAudiobook? = withContext(Dispatchers.IO) {
        if (!folder.isDirectory) return@withContext null

        val allFiles = folder.listFiles().toList()

        // 1. Categorize files
        val audioFiles = allFiles
            .filter { it.isFile && getExtension(it.name).lowercase() in AUDIO_EXTENSIONS }
            .sortedWith(compareBy(NATURAL_SORT) { it.name ?: "" })

        if (audioFiles.isEmpty()) return@withContext null

        val coverImageDoc = findCoverImageDocument(allFiles)
        val cueDoc = allFiles.find { it.isFile && getExtension(it.name).equals("cue", true) }
        val descriptionDoc = allFiles.find {
            it.isFile && (it.name.equals("desc.txt", true)
                    || it.name.equals("description.txt", true)
                    || it.name.equals("info.txt", true))
        }

        // 2. Extract metadata from each audio file
        val audioInfos = audioFiles.map { doc ->
            extractAudioFileInfo(doc.name ?: "unknown", doc.uri, null)
        }

        // 3. Try CUE sheet
        val cueResult = cueDoc?.let { doc ->
            try {
                val cueContent = context.contentResolver.openInputStream(doc.uri)
                    ?.bufferedReader()?.readText() ?: return@let null
                val fileDurations = audioInfos.associate { it.fileName to it.durationMs }
                CueParser().parse(cueContent, fileDurations, audioInfos.sumOf { it.durationMs })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse CUE file", e)
                null
            }
        }

        // 4. Build chapters
        val chapters = buildChapters(audioInfos, audioFiles.map { it.uri }, cueResult)

        // 5. Resolve metadata
        val title = cueResult?.title
            ?: audioInfos.firstOrNull()?.album
            ?: (folder.name ?: "Unknown").replace("_", " ").replace("-", " ").trim()

        val author = cueResult?.performer
            ?: audioInfos.firstOrNull()?.artist
            ?: "Unknown Author"

        val narrator = resolveNarrator(audioInfos)
        val description = descriptionDoc?.let { doc ->
            try {
                context.contentResolver.openInputStream(doc.uri)?.bufferedReader()?.readText()?.trim()
            } catch (e: Exception) { null }
        }

        // 6. Resolve cover art
        val coverArt = resolveCoverArtFromUri(coverImageDoc?.uri, audioInfos)

        val totalDurationMs = chapters.sumOf { it.endTimeMs - it.startTimeMs }

        FolderAudiobook(
            title = title,
            author = author,
            narrator = narrator,
            description = description,
            durationMs = totalDurationMs,
            chapters = chapters,
            coverArt = coverArt,
            folderPath = folder.uri.toString()
        )
    }

    // ──────────────────── chapter building ────────────────────

    /**
     * Build the chapter list, preferring CUE data if available,
     * otherwise falling back to one-file-per-chapter.
     */
    private fun buildChapters(
        audioInfos: List<AudioFileInfo>,
        audioUris: List<Uri>,
        cueResult: CueParser.CueResult?
    ): List<Chapter> {
        // If CUE provided chapters, use them — but attach file URIs for multi-file
        if (cueResult != null && cueResult.chapters.isNotEmpty()) {
            return cueResult.chapters.mapIndexed { index, chapter ->
                val fileRef = cueResult.fileReferences[chapter.number]
                val matchingUri = if (fileRef != null) {
                    // Find the audio file that matches this CUE FILE reference
                    val matchIndex = audioInfos.indexOfFirst {
                        it.fileName.equals(fileRef, ignoreCase = true)
                    }
                    if (matchIndex >= 0) audioUris[matchIndex] else null
                } else if (cueResult.isMultiFile && index < audioUris.size) {
                    audioUris[index]
                } else {
                    null
                }

                chapter.copy(
                    fileUri = matchingUri?.toString()
                )
            }
        }

        // No CUE — build chapters from audio files directly
        // Each MP3 file becomes a chapter
        var cumulativeMs = 0L
        return audioInfos.mapIndexed { index, info ->
            val startMs = cumulativeMs
            val endMs = startMs + info.durationMs
            cumulativeMs = endMs

            val chapterTitle = info.title
                ?: info.trackNumber?.let { "Chapter $it" }
                ?: cleanFileName(info.fileName)

            Chapter(
                id = index + 1,
                number = index + 1,
                title = chapterTitle,
                duration = formatDuration(info.durationMs),
                durationMinutes = TimeUnit.MILLISECONDS.toMinutes(info.durationMs).toInt(),
                startTimeMs = startMs,
                endTimeMs = endMs,
                fileUri = audioUris[index].toString()
            )
        }
    }

    // ──────────────────── metadata extraction ────────────────────

    /**
     * Extract metadata from a single audio file using MediaMetadataRetriever.
     */
    private fun extractAudioFileInfo(
        fileName: String,
        uri: Uri,
        filePath: String?
    ): AudioFileInfo {
        val retriever = MediaMetadataRetriever()
        try {
            if (filePath != null) {
                retriever.setDataSource(filePath)
            } else {
                retriever.setDataSource(context, uri)
            }

            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val trackNum = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
                ?.split("/")?.firstOrNull()?.trim()?.toIntOrNull()
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val hasCover = retriever.embeddedPicture != null

            return AudioFileInfo(
                fileName = fileName,
                uri = uri,
                title = title,
                artist = artist,
                album = album,
                trackNumber = trackNum,
                durationMs = durationMs,
                hasCoverArt = hasCover
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract metadata from $fileName", e)
            return AudioFileInfo(
                fileName = fileName,
                uri = uri,
                title = null,
                artist = null,
                album = null,
                trackNumber = null,
                durationMs = 0L,
                hasCoverArt = false
            )
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun resolveNarrator(audioInfos: List<AudioFileInfo>): String? {
        // Narrator is often stored in the Composer field — check via a separate retriever call
        // For simplicity, if artist != album artist, album artist might be the narrator
        // This is a best-effort heuristic
        return null
    }

    // ──────────────────── cover art resolution ────────────────────

    /**
     * Resolve cover art. Priority:
     * 1. Image file from the folder
     * 2. Embedded art from the first audio file with art
     */
    private fun resolveCoverArt(
        coverImagePath: String?,
        audioInfos: List<AudioFileInfo>
    ): Bitmap? {
        // Try folder image first
        if (coverImagePath != null) {
            val bitmap = decodeSampledBitmap(coverImagePath)
            if (bitmap != null) return bitmap
        }

        // Fallback: embedded art from the first file that has it
        val fileWithArt = audioInfos.firstOrNull { it.hasCoverArt } ?: return null
        return extractEmbeddedArt(fileWithArt.uri, fileWithArt.fileName)
    }

    private fun resolveCoverArtFromUri(
        coverImageUri: Uri?,
        audioInfos: List<AudioFileInfo>
    ): Bitmap? {
        // Try folder image first
        if (coverImageUri != null) {
            try {
                context.contentResolver.openInputStream(coverImageUri)?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, opts)

                    var sampleSize = 1
                    while (opts.outWidth / sampleSize > 512 || opts.outHeight / sampleSize > 512) {
                        sampleSize *= 2
                    }

                    // Re-open stream for actual decode
                    context.contentResolver.openInputStream(coverImageUri)?.use { stream2 ->
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        return BitmapFactory.decodeStream(stream2, null, decodeOpts)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode cover image from URI", e)
            }
        }

        // Fallback: embedded art
        val fileWithArt = audioInfos.firstOrNull { it.hasCoverArt } ?: return null
        return extractEmbeddedArt(fileWithArt.uri, fileWithArt.fileName)
    }

    private fun extractEmbeddedArt(uri: Uri, fileName: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { bytes ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
                var sampleSize = 1
                while (opts.outWidth / sampleSize > 512 || opts.outHeight / sampleSize > 512) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract embedded art from $fileName", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    // ──────────────────── file discovery helpers ────────────────────

    /**
     * Find the best cover art image file in a list of files.
     * Priority: known cover art names > any single image in folder.
     */
    private fun findCoverImageFile(files: List<File>): File? {
        val imageFiles = files.filter {
            it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS
        }
        if (imageFiles.isEmpty()) return null

        // Check known cover art names first
        for (name in COVER_ART_NAMES) {
            val match = imageFiles.find {
                it.nameWithoutExtension.equals(name, ignoreCase = true)
            }
            if (match != null) return match
        }

        // If there's exactly one image, use it
        if (imageFiles.size == 1) return imageFiles.first()

        // Otherwise return the first image (best effort)
        return imageFiles.first()
    }

    private fun findCoverImageDocument(files: List<DocumentFile>): DocumentFile? {
        val imageFiles = files.filter {
            it.isFile && getExtension(it.name).lowercase() in IMAGE_EXTENSIONS
        }
        if (imageFiles.isEmpty()) return null

        for (name in COVER_ART_NAMES) {
            val match = imageFiles.find {
                getNameWithoutExtension(it.name).equals(name, ignoreCase = true)
            }
            if (match != null) return match
        }

        if (imageFiles.size == 1) return imageFiles.first()
        return imageFiles.first()
    }

    // ──────────────────── utilities ────────────────────

    private fun decodeSampledBitmap(path: String): Bitmap? {
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sampleSize = 1
            while (opts.outWidth / sampleSize > 512 || opts.outHeight / sampleSize > 512) {
                sampleSize *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(path, decodeOpts)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode bitmap from path: $path", e)
            null
        }
    }

    private fun cleanFileName(fileName: String): String {
        return fileName
            .substringBeforeLast('.')
            .replace(Regex("""^\d+[\s._-]*"""), "") // Strip leading track numbers
            .replace("_", " ")
            .replace("-", " ")
            .trim()
            .ifEmpty { fileName }
    }

    private fun getExtension(name: String?): String {
        return name?.substringAfterLast('.', "") ?: ""
    }

    private fun getNameWithoutExtension(name: String?): String {
        return name?.substringBeforeLast('.') ?: ""
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

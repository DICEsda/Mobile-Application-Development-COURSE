package com.audiobook.app.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.container.MdtaMetadataEntry
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.ChapterFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.Chapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Parser for extracting metadata from M4B/M4A audiobook files.
 * 
 * M4B files (MPEG-4 Audio with Bookmarks) contain:
 * - Chapter markers with timestamps
 * - Embedded cover art
 * - Author/Title metadata
 * - Narrator information
 * 
 * This parser uses both MediaMetadataRetriever (Android native) and
 * Media3's MetadataRetriever for comprehensive metadata extraction.
 */
class ChapterParser(private val context: Context) {
    
    companion object {
        private const val TAG = "ChapterParser"
    }
    
    /**
     * Parse M4B file and extract all metadata including chapters.
     * 
     * @param uri Content URI or file URI of the M4B file
     * @return AudiobookMetadata containing all extracted information
     */
    suspend fun parseM4bFile(uri: Uri): AudiobookMetadata = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        
        try {
            retriever.setDataSource(context, uri)
            
            // Extract basic metadata
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?: "Unknown Title"
            
            val author = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
                ?: retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_AUTHOR)
                ?: "Unknown Author"
            
            val narrator = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER)
            
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            
            // Extract cover art
            val coverArt = retriever.embeddedPicture?.let { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
            
            // Extract chapters using Media3 (more reliable for chapter markers)
            val chapters = try {
                val media3Chapters = extractChaptersMedia3(uri, durationMs)
                if (media3Chapters.isNullOrEmpty()) {
                    Log.d(TAG, "Media3 found no chapters, trying fallback method")
                    extractChaptersFallback(uri, durationMs)
                } else {
                    media3Chapters
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract chapters with Media3", e)
                try {
                    extractChaptersFallback(uri, durationMs)
                } catch (e2: Exception) {
                    Log.e(TAG, "Fallback chapter extraction also failed", e2)
                    null
                }
            }
            
            AudiobookMetadata(
                title = title,
                author = author,
                narrator = narrator,
                durationMs = durationMs,
                coverArt = coverArt,
                chapters = chapters ?: emptyList() // Return empty list if no chapters found
            )
        } finally {
            retriever.release()
        }
    }
    
    /**
     * Fallback method to extract chapters using MediaExtractor directly.
     * This handles cases where MetadataRetriever doesn't expose chapters properly.
     * 
     * Specifically handles MP4/M4B files with QuickTime-style text track chapters
     * that Media3's MetadataRetriever might miss.
     */
    @OptIn(UnstableApi::class)
    private suspend fun extractChaptersFallback(uri: Uri, totalDurationMs: Long): List<Chapter>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Attempting fallback chapter extraction using MediaExtractor")
            
            // Use MediaExtractor to access text tracks containing chapter info
            val extractor = android.media.MediaExtractor()
            
            try {
                // Set data source from URI
                when (uri.scheme) {
                    "file" -> extractor.setDataSource(uri.path!!)
                    "content" -> {
                        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                            extractor.setDataSource(pfd.fileDescriptor)
                        }
                    }
                    else -> {
                        Log.e(TAG, "Unsupported URI scheme: ${uri.scheme}")
                        return@withContext null
                    }
                }
                
                val chapters = mutableListOf<Chapter>()
                val trackCount = extractor.trackCount
                Log.d(TAG, "MediaExtractor found $trackCount tracks")
                
                // Look for text tracks which contain chapter information
                for (i in 0 until trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                    Log.d(TAG, "Track $i: mime=$mime")
                    
                    // Check for text tracks (chapters are stored as text tracks)
                    if (mime?.startsWith("text/") == true || mime?.contains("subrip") == true || mime?.contains("tx3g") == true) {
                        Log.d(TAG, "Found text track at index $i, attempting to extract chapters")
                        extractor.selectTrack(i)
                        
                        val buffer = java.nio.ByteBuffer.allocate(1024 * 16) // 16KB buffer
                        val bufferInfo = android.media.MediaCodec.BufferInfo()
                        
                        while (true) {
                            val sampleSize = extractor.readSampleData(buffer, 0)
                            if (sampleSize < 0) break
                            
                            bufferInfo.presentationTimeUs = extractor.sampleTime
                            bufferInfo.size = sampleSize
                            
                            // Extract chapter text
                            val chapterBytes = ByteArray(sampleSize)
                            buffer.get(chapterBytes)
                            buffer.clear()
                            
                            val chapterText = String(chapterBytes, Charsets.UTF_8).trim()
                            if (chapterText.isNotEmpty()) {
                                val startMs = bufferInfo.presentationTimeUs / 1000
                                Log.d(TAG, "Chapter at ${startMs}ms: $chapterText")
                                
                                chapters.add(Chapter(
                                    id = chapters.size + 1,
                                    number = chapters.size + 1,
                                    title = chapterText,
                                    duration = "", // Will be calculated later
                                    durationMinutes = 0,
                                    startTimeMs = startMs,
                                    endTimeMs = 0 // Will be calculated later
                                ))
                            }
                            
                            if (!extractor.advance()) break
                        }
                        
                        extractor.unselectTrack(i)
                    }
                }
                
                extractor.release()
                
                if (chapters.isNotEmpty()) {
                    // Calculate end times and durations
                    val sortedChapters = chapters.sortedBy { it.startTimeMs }.mapIndexed { index, chapter ->
                        val endMs = if (index < chapters.size - 1) {
                            chapters[index + 1].startTimeMs
                        } else {
                            totalDurationMs
                        }
                        val durationMs = endMs - chapter.startTimeMs
                        
                        chapter.copy(
                            endTimeMs = endMs,
                            duration = formatDuration(durationMs),
                            durationMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs).toInt()
                        )
                    }
                    
                    Log.d(TAG, "Fallback method extracted ${sortedChapters.size} chapters")
                    return@withContext sortedChapters
                }
                
                Log.d(TAG, "Fallback method: No chapters found in text tracks")
                null
                
            } finally {
                try {
                    extractor.release()
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing MediaExtractor", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback chapter extraction failed", e)
            null
        }
    }
    
    /**
     * Extract chapters using Media3's MetadataRetriever.
     * This method handles the chapter markers embedded in M4B files.
     * 
     * M4B files can store chapters in different ways:
     * 1. ID3 ChapterFrame tags
     * 2. MP4 MDTA metadata entries (with custom format strings)
     * 3. QuickTime chapter track (nero chapters)
     * 4. Standard MP4 chapter atoms
     */
    @OptIn(UnstableApi::class)
    private suspend fun extractChaptersMedia3(uri: Uri, totalDurationMs: Long): List<Chapter>? = withContext(Dispatchers.IO) {
        try {
            val mediaItem = MediaItem.fromUri(uri)
            // Use the static method instead of Builder
            val trackGroupsFuture = MetadataRetriever.retrieveMetadata(context, mediaItem)
            val trackGroups = trackGroupsFuture.await()
            val chapters = mutableListOf<Chapter>()
            
            Log.d(TAG, "Found ${trackGroups.length} track groups")
            
            // Iterate through all track groups looking for metadata
            for (i in 0 until trackGroups.length) {
                val group = trackGroups.get(i)
                
                for (j in 0 until group.length) {
                    val format = group.getFormat(j)
                    Log.d(TAG, "Track $i.$j: sampleMimeType=${format.sampleMimeType}, metadata entries=${format.metadata?.length() ?: 0}")
                    
                    // Check format metadata for chapter information
                    format.metadata?.let { metadata ->
                        val extractedChapters = parseMetadataForChapters(metadata, totalDurationMs)
                        if (extractedChapters.isNotEmpty()) {
                            chapters.addAll(extractedChapters)
                        }
                    }
                }
            }
            
            if (chapters.isNotEmpty()) {
                // Sort chapters by start time and assign numbers
                val sortedChapters = chapters.sortedBy { it.startTimeMs }
                    .mapIndexed { index, chapter ->
                        chapter.copy(
                            id = index + 1,
                            number = index + 1
                        )
                    }
                Log.d(TAG, "Extracted ${sortedChapters.size} chapters")
                return@withContext sortedChapters
            }
            
            Log.d(TAG, "No chapters found in metadata")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting chapters", e)
            null
        }
    }
    
    /**
     * Parse metadata entries for chapter information.
     */
    @OptIn(UnstableApi::class)
    private fun parseMetadataForChapters(metadata: Metadata, totalDurationMs: Long): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val rawChapterData = mutableListOf<RawChapterData>()
        
        for (i in 0 until metadata.length()) {
            when (val entry = metadata.get(i)) {
                is ChapterFrame -> {
                    // ID3 Chapter Frame - common in some audiobook formats
                    Log.d(TAG, "Found ChapterFrame: id=${entry.chapterId}, startTime=${entry.startTimeMs}, endTime=${entry.endTimeMs}")
                    
                    // Extract chapter title from sub-frames
                    var chapterTitle = "Chapter ${chapters.size + 1}"
                    for (j in 0 until entry.subFrameCount) {
                        val subFrame = entry.getSubFrame(j)
                        if (subFrame is TextInformationFrame && subFrame.id == "TIT2") {
                            chapterTitle = subFrame.values.firstOrNull() ?: chapterTitle
                        }
                    }
                    
                    // ChapterFrame times are Int, convert to Long for Chapter model
                    val startMs = entry.startTimeMs.toLong()
                    val endMs = entry.endTimeMs.toLong()
                    val durationMs = endMs - startMs
                    chapters.add(
                        Chapter(
                            id = chapters.size + 1,
                            number = chapters.size + 1,
                            title = chapterTitle,
                            duration = formatDuration(durationMs),
                            durationMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs).toInt(),
                            startTimeMs = startMs,
                            endTimeMs = endMs
                        )
                    )
                }
                is MdtaMetadataEntry -> {
                    // MP4 MDTA metadata - used by some M4B files
                    val valuePreview = entry.value?.let { bytes ->
                        try {
                            String(bytes, Charsets.UTF_8).take(50)
                        } catch (e: Exception) {
                            "binary data (${bytes.size} bytes)"
                        }
                    } ?: "null"
                    Log.d(TAG, "Found MdtaMetadataEntry: key='${entry.key}', value='$valuePreview'")
                    
                    // Parse chapter-related keys
                    if (entry.key.contains("chapter", ignoreCase = true) || 
                        entry.key.contains("©chp", ignoreCase = true) ||
                        entry.key.startsWith("chp", ignoreCase = true)) {
                        entry.value?.let { valueBytes ->
                            val valueStr = try {
                                String(valueBytes, Charsets.UTF_8)
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to decode MDTA value as UTF-8", e)
                                return@let
                            }
                            Log.d(TAG, "Parsing chapter MDTA string: '$valueStr'")
                            val parsed = parseChapterString(valueStr)
                            if (parsed != null) {
                                Log.d(TAG, "Successfully parsed chapter: ${parsed.title} @ ${parsed.startTimeMs}ms")
                                rawChapterData.add(parsed)
                            } else {
                                Log.w(TAG, "Failed to parse chapter string: '$valueStr'")
                            }
                        }
                    }
                }
                is TextInformationFrame -> {
                    Log.d(TAG, "Found TextInformationFrame: id=${entry.id}, values=${entry.values}")
                    
                    // Check if this contains chapter information
                    if (entry.id.contains("CHAP", ignoreCase = true) || 
                        entry.id == "TIT2" && entry.description?.contains("chapter", ignoreCase = true) == true) {
                        entry.values.forEach { value ->
                            parseChapterString(value)?.let { rawChapterData.add(it) }
                        }
                    }
                }
                else -> {
                    Log.d(TAG, "Found other metadata: ${entry.javaClass.simpleName}")
                }
            }
        }
        
        // If we found raw chapter data (from MDTA or text frames), process it
        if (rawChapterData.isNotEmpty() && chapters.isEmpty()) {
            chapters.addAll(processRawChapterData(rawChapterData, totalDurationMs))
        }
        
        return chapters
    }
    
    /**
     * Parse chapter string in format: "Menu 2# _00_00_00_000: Preface"
     * or similar variations
     */
    private fun parseChapterString(chapterStr: String): RawChapterData? {
        try {
            // Pattern: "Menu <number># _<HH>_<MM>_<SS>_<mmm>: <title>"
            // Also handle variations like just timestamps and titles
            
            // Try pattern with Menu prefix
            var regex = Regex("""Menu\s+(\d+)#\s*_(\d+)_(\d+)_(\d+)_(\d+):\s*(.+)""")
            var match = regex.find(chapterStr)
            
            if (match != null) {
                val (chapterNum, hours, minutes, seconds, millis, title) = match.destructured
                val startTimeMs = parseTimestamp(hours, minutes, seconds, millis)
                return RawChapterData(
                    chapterNumber = chapterNum.toIntOrNull() ?: 1,
                    startTimeMs = startTimeMs,
                    title = title.trim()
                )
            }
            
            // Try pattern without Menu prefix: "_00_00_00_000: Title"
            regex = Regex("""_(\d+)_(\d+)_(\d+)_(\d+):\s*(.+)""")
            match = regex.find(chapterStr)
            
            if (match != null) {
                val (hours, minutes, seconds, millis, title) = match.destructured
                val startTimeMs = parseTimestamp(hours, minutes, seconds, millis)
                return RawChapterData(
                    chapterNumber = null,
                    startTimeMs = startTimeMs,
                    title = title.trim()
                )
            }
            
            // Try pattern with dashes: "00-00-00-000: Title"
            regex = Regex("""(\d+)-(\d+)-(\d+)-(\d+):\s*(.+)""")
            match = regex.find(chapterStr)
            
            if (match != null) {
                val (hours, minutes, seconds, millis, title) = match.destructured
                val startTimeMs = parseTimestamp(hours, minutes, seconds, millis)
                return RawChapterData(
                    chapterNumber = null,
                    startTimeMs = startTimeMs,
                    title = title.trim()
                )
            }
            
            Log.d(TAG, "Could not parse chapter string: $chapterStr")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing chapter string: $chapterStr", e)
            return null
        }
    }
    
    /**
     * Parse timestamp components into milliseconds
     */
    private fun parseTimestamp(hours: String, minutes: String, seconds: String, millis: String): Long {
        val h = hours.toLongOrNull() ?: 0L
        val m = minutes.toLongOrNull() ?: 0L
        val s = seconds.toLongOrNull() ?: 0L
        val ms = millis.toLongOrNull() ?: 0L
        
        return (h * 3600000) + (m * 60000) + (s * 1000) + ms
    }
    
    /**
     * Process raw chapter data and calculate end times
     */
    private fun processRawChapterData(rawData: List<RawChapterData>, totalDurationMs: Long): List<Chapter> {
        // Sort by start time
        val sorted = rawData.sortedBy { it.startTimeMs }
        
        return sorted.mapIndexed { index, raw ->
            val endTimeMs = if (index < sorted.size - 1) {
                // End time is the start of next chapter
                sorted[index + 1].startTimeMs
            } else {
                // Last chapter ends at total duration
                totalDurationMs
            }
            
            val durationMs = endTimeMs - raw.startTimeMs
            
            Chapter(
                id = index + 1,
                number = raw.chapterNumber ?: (index + 1),
                title = raw.title,
                duration = formatDuration(durationMs),
                durationMinutes = TimeUnit.MILLISECONDS.toMinutes(durationMs).toInt(),
                startTimeMs = raw.startTimeMs,
                endTimeMs = endTimeMs
            )
        }
    }
    
    /**
     * Temporary data structure for parsed chapter strings
     */
    private data class RawChapterData(
        val chapterNumber: Int?,
        val startTimeMs: Long,
        val title: String
    )
    
    /**
     * Parse chapter information from file path (for local files).
     */
    suspend fun parseFromPath(filePath: String): AudiobookMetadata {
        return parseM4bFile(Uri.fromFile(File(filePath)))
    }
    
    /**
     * Create a MediaItem with proper metadata for ExoPlayer.
     */
    @OptIn(UnstableApi::class)
    fun createMediaItem(audiobook: Audiobook, metadata: AudiobookMetadata): MediaItem {
        val uri = when {
            !audiobook.contentUri.isNullOrBlank() -> Uri.parse(audiobook.contentUri)
            !audiobook.filePath.isNullOrBlank() -> Uri.fromFile(File(audiobook.filePath))
            else -> throw IllegalArgumentException("No valid URI for audiobook")
        }
        
        return MediaItem.Builder()
            .setMediaId(audiobook.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(metadata.title)
                    .setArtist(metadata.author)
                    .setAlbumTitle(metadata.title)
                    .setDisplayTitle(metadata.title)
                    .setSubtitle(metadata.author)
                    .build()
            )
            .build()
    }
    
    /**
     * Create MediaItems for each chapter (for chapter-based navigation).
     */
    @OptIn(UnstableApi::class)
    fun createChapterMediaItems(audiobook: Audiobook, chapters: List<Chapter>): List<MediaItem> {
        val uri = when {
            !audiobook.contentUri.isNullOrBlank() -> Uri.parse(audiobook.contentUri)
            !audiobook.filePath.isNullOrBlank() -> Uri.fromFile(File(audiobook.filePath))
            else -> return emptyList()
        }
        
        return chapters.map { chapter ->
            MediaItem.Builder()
                .setMediaId("${audiobook.id}_chapter_${chapter.number}")
                .setUri(uri)
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(chapter.startTimeMs)
                        .setEndPositionMs(chapter.endTimeMs)
                        .build()
                )
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(chapter.title)
                        .setTrackNumber(chapter.number)
                        .setDisplayTitle("${audiobook.title} - ${chapter.title}")
                        .build()
                )
                .build()
        }
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

/**
 * Data class containing all extracted audiobook metadata.
 */
data class AudiobookMetadata(
    val title: String,
    val author: String,
    val narrator: String?,
    val durationMs: Long,
    val coverArt: Bitmap?,
    val chapters: List<Chapter>
)

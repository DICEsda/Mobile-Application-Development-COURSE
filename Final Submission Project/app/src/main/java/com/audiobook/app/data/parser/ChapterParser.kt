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
                extractChaptersMedia3(uri, durationMs)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract chapters with Media3", e)
                null
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
     * Extract chapters using Media3's MetadataRetriever.
     * This method handles the chapter markers embedded in M4B files.
     * 
     * M4B files can store chapters in different ways:
     * 1. ID3 ChapterFrame tags
     * 2. MP4 MDTA metadata entries
     * 3. QuickTime chapter track (nero chapters)
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
                    Log.d(TAG, "Found MdtaMetadataEntry: key=${entry.key}")
                    
                    // Check for chapter-related keys
                    if (entry.key.contains("chapter", ignoreCase = true)) {
                        Log.d(TAG, "Chapter-related MDTA entry: ${entry.key}")
                    }
                }
                is TextInformationFrame -> {
                    Log.d(TAG, "Found TextInformationFrame: id=${entry.id}, values=${entry.values}")
                }
                else -> {
                    Log.d(TAG, "Found other metadata: ${entry.javaClass.simpleName}")
                }
            }
        }
        
        return chapters
    }
    
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

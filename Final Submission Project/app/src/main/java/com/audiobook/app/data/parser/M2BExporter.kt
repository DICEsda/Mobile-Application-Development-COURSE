package com.audiobook.app.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.M2BFile
import com.audiobook.app.data.model.toM2BFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

/**
 * M2BExporter - Creates M2B bookmark files from audiobook data
 * 
 * M2B (MPEG-2 Bookmark) files are JSON-based files that contain:
 * - Complete audiobook metadata
 * - Chapter markers with timestamps
 * - Current playback position and progress
 * - Playback settings
 * 
 * Usage:
 * ```
 * val exporter = M2BExporter(context)
 * val success = exporter.exportToM2B(
 *     audiobook = book,
 *     currentPositionMs = 123456,
 *     outputUri = uri,
 *     includeCoverArt = true
 * )
 * ```
 */
class M2BExporter(private val context: Context) {
    
    companion object {
        private const val TAG = "M2BExporter"
        private const val M2B_FILE_EXTENSION = ".m2b"
        private const val COVER_ART_MAX_SIZE = 512 // Max dimension for cover art
        private const val COVER_ART_QUALITY = 85 // JPEG quality (0-100)
    }
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    /**
     * Export an audiobook with its current progress to an M2B file.
     * 
     * @param audiobook The audiobook to export
     * @param currentPositionMs Current playback position in milliseconds
     * @param playbackSpeed Current playback speed
     * @param outputUri URI where the M2B file should be saved
     * @param includeCoverArt Whether to include cover art in the M2B file
     * @return true if export was successful, false otherwise
     */
    suspend fun exportToM2B(
        audiobook: Audiobook,
        currentPositionMs: Long,
        playbackSpeed: Float = 1.0f,
        outputUri: Uri,
        includeCoverArt: Boolean = true
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get cover art as Base64 if requested
            val coverArtBase64 = if (includeCoverArt) {
                extractCoverArtBase64(audiobook)
            } else {
                null
            }
            
            // Create M2B file structure
            val m2bFile = audiobook.toM2BFile(
                currentPositionMs = currentPositionMs,
                playbackSpeed = playbackSpeed,
                coverArtBase64 = coverArtBase64
            )
            
            // Serialize to JSON
            val jsonString = json.encodeToString(m2bFile)
            
            // Write to file
            context.contentResolver.openOutputStream(outputUri)?.use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            
            Log.d(TAG, "Successfully exported M2B file to: $outputUri")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export M2B file", e)
            false
        }
    }
    
    /**
     * Export an audiobook to a file in the app's cache directory.
     * Returns the file path if successful, null otherwise.
     */
    suspend fun exportToCache(
        audiobook: Audiobook,
        currentPositionMs: Long,
        playbackSpeed: Float = 1.0f,
        includeCoverArt: Boolean = true
    ): File? = withContext(Dispatchers.IO) {
        try {
            // Create cache directory for M2B files
            val cacheDir = File(context.cacheDir, "m2b_exports")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            
            // Generate filename: "BookTitle_timestamp.m2b"
            val sanitizedTitle = audiobook.title
                .replace(Regex("[^a-zA-Z0-9\\s]"), "")
                .replace(Regex("\\s+"), "_")
                .take(50)
            val timestamp = System.currentTimeMillis()
            val filename = "${sanitizedTitle}_${timestamp}${M2B_FILE_EXTENSION}"
            val file = File(cacheDir, filename)
            
            // Export to file
            val coverArtBase64 = if (includeCoverArt) {
                extractCoverArtBase64(audiobook)
            } else {
                null
            }
            
            val m2bFile = audiobook.toM2BFile(
                currentPositionMs = currentPositionMs,
                playbackSpeed = playbackSpeed,
                coverArtBase64 = coverArtBase64
            )
            
            val jsonString = json.encodeToString(m2bFile)
            
            FileOutputStream(file).use { outputStream ->
                outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            
            Log.d(TAG, "Successfully exported M2B file to cache: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export M2B file to cache", e)
            null
        }
    }
    
    /**
     * Create an M2B file object (without writing to disk).
     * Useful for sharing over network or creating in-memory representations.
     */
    suspend fun createM2BFile(
        audiobook: Audiobook,
        currentPositionMs: Long,
        playbackSpeed: Float = 1.0f,
        includeCoverArt: Boolean = true
    ): M2BFile = withContext(Dispatchers.IO) {
        val coverArtBase64 = if (includeCoverArt) {
            extractCoverArtBase64(audiobook)
        } else {
            null
        }
        
        audiobook.toM2BFile(
            currentPositionMs = currentPositionMs,
            playbackSpeed = playbackSpeed,
            coverArtBase64 = coverArtBase64
        )
    }
    
    /**
     * Convert M2B file to JSON string.
     */
    fun toJsonString(m2bFile: M2BFile): String {
        return json.encodeToString(m2bFile)
    }
    
    /**
     * Extract cover art from audiobook and convert to Base64 string.
     * Resizes the image to reduce file size.
     */
    private suspend fun extractCoverArtBase64(audiobook: Audiobook): String? = withContext(Dispatchers.IO) {
        try {
            // Try to get cover art from the M4B file
            val uri = audiobook.resolveUri() ?: return@withContext null
            
            val parser = ChapterParser(context)
            val metadata = parser.parseM4bFile(uri)
            val coverArt = metadata.coverArt ?: return@withContext null
            
            // Resize to reduce file size
            val resizedBitmap = resizeBitmap(coverArt, COVER_ART_MAX_SIZE)
            
            // Convert to JPEG bytes
            val outputStream = ByteArrayOutputStream()
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, COVER_ART_QUALITY, outputStream)
            val bytes = outputStream.toByteArray()
            
            // Encode to Base64
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            
            Log.d(TAG, "Extracted cover art: ${bytes.size} bytes, Base64 length: ${base64.length}")
            base64
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract cover art", e)
            null
        }
    }
    
    /**
     * Resize bitmap to fit within max dimension while maintaining aspect ratio.
     */
    private fun resizeBitmap(bitmap: Bitmap, maxSize: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        if (width <= maxSize && height <= maxSize) {
            return bitmap
        }
        
        val scale = if (width > height) {
            maxSize.toFloat() / width
        } else {
            maxSize.toFloat() / height
        }
        
        val newWidth = (width * scale).toInt()
        val newHeight = (height * scale).toInt()
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Generate a suggested filename for an M2B export.
     */
    fun generateFilename(audiobook: Audiobook): String {
        val sanitizedTitle = audiobook.title
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .replace(Regex("\\s+"), "_")
            .take(50)
        return "${sanitizedTitle}${M2B_FILE_EXTENSION}"
    }
}

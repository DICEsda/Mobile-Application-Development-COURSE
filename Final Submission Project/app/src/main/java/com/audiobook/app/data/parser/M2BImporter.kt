package com.audiobook.app.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.M2BFile
import com.audiobook.app.data.model.toAudiobook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/**
 * M2BImporter - Imports M2B bookmark files and restores audiobook state
 * 
 * This class handles:
 * - Parsing M2B JSON files
 * - Validating M2B file format and version
 * - Extracting audiobook metadata and progress
 * - Restoring cover art from Base64
 * 
 * Usage:
 * ```
 * val importer = M2BImporter(context)
 * val result = importer.importFromM2B(uri)
 * if (result is ImportResult.Success) {
 *     val audiobook = result.audiobook
 *     val positionMs = result.m2bFile.bookmark.positionMs
 *     // Restore audiobook and position
 * }
 * ```
 */
class M2BImporter(private val context: Context) {
    
    companion object {
        private const val TAG = "M2BImporter"
        private const val SUPPORTED_VERSION = "1.0"
    }
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    /**
     * Import an M2B file from a URI.
     * 
     * @param uri URI of the M2B file to import
     * @return ImportResult indicating success or failure
     */
    suspend fun importFromM2B(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            // Read file content
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext ImportResult.Error("Failed to read file")
            
            // Parse JSON
            val m2bFile = try {
                json.decodeFromString<M2BFile>(jsonString)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to parse M2B JSON", e)
                return@withContext ImportResult.Error("Invalid M2B file format: ${e.message}")
            }
            
            // Validate version
            if (!isVersionSupported(m2bFile.version)) {
                return@withContext ImportResult.Error(
                    "Unsupported M2B version: ${m2bFile.version}. Expected: $SUPPORTED_VERSION"
                )
            }
            
            // Convert to Audiobook
            val audiobook = m2bFile.toAudiobook()
            
            // Extract cover art if present
            val coverArt = m2bFile.metadata.coverArtBase64?.let { base64 ->
                decodeCoverArt(base64)
            }
            
            Log.d(TAG, "Successfully imported M2B file: ${audiobook.title}")
            ImportResult.Success(
                m2bFile = m2bFile,
                audiobook = audiobook,
                coverArt = coverArt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import M2B file", e)
            ImportResult.Error("Failed to import: ${e.message}")
        }
    }
    
    /**
     * Import an M2B file from a file path.
     */
    suspend fun importFromPath(filePath: String): ImportResult {
        return importFromM2B(Uri.fromFile(File(filePath)))
    }
    
    /**
     * Validate an M2B file without fully importing it.
     * Returns true if the file is a valid M2B file.
     */
    suspend fun validateM2BFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext false
            
            val m2bFile = json.decodeFromString<M2BFile>(jsonString)
            isVersionSupported(m2bFile.version)
        } catch (e: Exception) {
            Log.e(TAG, "M2B validation failed", e)
            false
        }
    }
    
    /**
     * Extract just the bookmark information from an M2B file.
     * Useful for quickly restoring playback position without full import.
     */
    suspend fun extractBookmark(uri: Uri): BookmarkInfo? = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.readBytes().toString(Charsets.UTF_8)
            } ?: return@withContext null
            
            val m2bFile = json.decodeFromString<M2BFile>(jsonString)
            
            BookmarkInfo(
                bookId = m2bFile.source.bookId,
                positionMs = m2bFile.bookmark.positionMs,
                chapter = m2bFile.bookmark.chapter,
                progress = m2bFile.bookmark.progress,
                playbackSpeed = m2bFile.bookmark.playbackSpeed,
                timestamp = m2bFile.bookmark.timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract bookmark", e)
            null
        }
    }
    
    /**
     * Parse M2B file from JSON string (for testing or network transfer).
     */
    fun parseFromString(jsonString: String): M2BFile? {
        return try {
            json.decodeFromString<M2BFile>(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse M2B from string", e)
            null
        }
    }
    
    /**
     * Check if an M2B version is supported by this importer.
     */
    private fun isVersionSupported(version: String): Boolean {
        // Currently only support version 1.0
        // Future versions could be handled here with compatibility logic
        return version == SUPPORTED_VERSION
    }
    
    /**
     * Decode Base64 cover art string to Bitmap.
     */
    private fun decodeCoverArt(base64: String): Bitmap? {
        return try {
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode cover art", e)
            null
        }
    }
}

/**
 * Result of an M2B import operation.
 */
sealed class ImportResult {
    /**
     * Import was successful.
     * @param m2bFile The parsed M2B file
     * @param audiobook The audiobook data extracted from the M2B file
     * @param coverArt The decoded cover art (if present)
     */
    data class Success(
        val m2bFile: M2BFile,
        val audiobook: Audiobook,
        val coverArt: Bitmap? = null
    ) : ImportResult()
    
    /**
     * Import failed.
     * @param message Error message describing what went wrong
     */
    data class Error(val message: String) : ImportResult()
}

/**
 * Extracted bookmark information from an M2B file.
 */
data class BookmarkInfo(
    val bookId: String,
    val positionMs: Long,
    val chapter: Int,
    val progress: Float,
    val playbackSpeed: Float,
    val timestamp: Long
)

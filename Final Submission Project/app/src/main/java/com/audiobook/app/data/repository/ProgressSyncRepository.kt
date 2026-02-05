package com.audiobook.app.data.repository

import com.audiobook.app.data.local.ProgressDao
import com.audiobook.app.data.local.ProgressEntity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Repository for syncing audiobook progress to Firebase Firestore.
 * 
 * Enables cross-device progress synchronization so users can seamlessly
 * continue listening on any device.
 * 
 * This repository bridges local Room database progress with cloud Firestore,
 * implementing an "offline-first" strategy:
 * 1. Progress is always saved locally first (instant response)
 * 2. Changes are synced to cloud in the background
 * 3. Cloud changes are pulled when the app starts
 * 
 * Firestore Structure:
 * users/{userId}/progress/{bookId}
 *   - positionMs: Long
 *   - totalDurationMs: Long
 *   - lastChapter: Int
 *   - lastUpdated: Timestamp
 *   - bookTitle: String
 *   - playbackSpeed: Float
 */
class ProgressSyncRepository(
    private val progressDao: ProgressDao? = null,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    
    private val userId: String?
        get() = auth.currentUser?.uid
    
    /**
     * Save playback progress to Firestore.
     * Called periodically during playback and when pausing/stopping.
     */
    suspend fun saveProgress(progress: PlaybackProgress): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            val data = mapOf(
                "positionMs" to progress.positionMs,
                "totalDurationMs" to progress.totalDurationMs,
                "lastChapter" to progress.lastChapter,
                "lastUpdated" to com.google.firebase.Timestamp.now(),
                "bookTitle" to progress.bookTitle,
                "playbackSpeed" to progress.playbackSpeed
            )
            
            firestore.collection("users")
                .document(uid)
                .collection("progress")
                .document(progress.bookId)
                .set(data, SetOptions.merge())
                .await()
            
            // Mark as synced in local database
            progressDao?.markAsSynced(progress.bookId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Sync all unsynced local progress to cloud.
     * Should be called when the app starts or regains connectivity.
     */
    suspend fun syncUnsyncedProgress(): Result<Int> = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext Result.failure(Exception("User not signed in"))
        val dao = progressDao ?: return@withContext Result.failure(Exception("ProgressDao not available"))
        
        try {
            val unsyncedProgress = dao.getUnsyncedProgress()
            var syncedCount = 0
            
            unsyncedProgress.forEach { entity ->
                try {
                    val data = mapOf(
                        "positionMs" to entity.currentPositionMs,
                        "totalDurationMs" to 0L, // We don't store total duration in local DB
                        "lastChapter" to entity.currentChapter,
                        "lastUpdated" to com.google.firebase.Timestamp.now(),
                        "bookTitle" to "", // We'd need to join with audiobooks table
                        "playbackSpeed" to entity.playbackSpeed
                    )
                    
                    firestore.collection("users")
                        .document(uid)
                        .collection("progress")
                        .document(entity.bookId)
                        .set(data, SetOptions.merge())
                        .await()
                    
                    dao.markAsSynced(entity.bookId)
                    syncedCount++
                } catch (e: Exception) {
                    // Continue with other items if one fails
                }
            }
            
            Result.success(syncedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Pull progress from cloud and update local database.
     * Called when app starts to get updates from other devices.
     */
    suspend fun pullCloudProgress(): Result<List<PlaybackProgress>> = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext Result.failure(Exception("User not signed in"))
        
        try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("progress")
                .orderBy("lastUpdated", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50)
                .get()
                .await()
            
            val progressList = snapshot.documents.mapNotNull { doc ->
                PlaybackProgress(
                    bookId = doc.id,
                    bookTitle = doc.getString("bookTitle") ?: "",
                    positionMs = doc.getLong("positionMs") ?: 0L,
                    totalDurationMs = doc.getLong("totalDurationMs") ?: 0L,
                    lastChapter = doc.getLong("lastChapter")?.toInt() ?: 1,
                    playbackSpeed = doc.getDouble("playbackSpeed")?.toFloat() ?: 1.0f
                )
            }
            
            // Update local database with cloud data
            progressDao?.let { dao ->
                progressList.forEach { cloudProgress ->
                    val localProgress = dao.getProgress(cloudProgress.bookId)
                    
                    // Only update if cloud is newer (or local doesn't exist)
                    if (localProgress == null || localProgress.isSyncedToCloud) {
                        dao.insertProgress(
                            ProgressEntity(
                                bookId = cloudProgress.bookId,
                                currentPositionMs = cloudProgress.positionMs,
                                currentChapter = cloudProgress.lastChapter,
                                progress = cloudProgress.progressPercent,
                                playbackSpeed = cloudProgress.playbackSpeed,
                                isSyncedToCloud = true
                            )
                        )
                    }
                }
            }
            
            Result.success(progressList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get progress for a specific book.
     */
    suspend fun getProgress(bookId: String): Result<PlaybackProgress?> {
        val uid = userId ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            val doc = firestore.collection("users")
                .document(uid)
                .collection("progress")
                .document(bookId)
                .get()
                .await()
            
            if (doc.exists()) {
                val progress = PlaybackProgress(
                    bookId = bookId,
                    bookTitle = doc.getString("bookTitle") ?: "",
                    positionMs = doc.getLong("positionMs") ?: 0L,
                    totalDurationMs = doc.getLong("totalDurationMs") ?: 0L,
                    lastChapter = doc.getLong("lastChapter")?.toInt() ?: 1,
                    playbackSpeed = doc.getDouble("playbackSpeed")?.toFloat() ?: 1.0f
                )
                Result.success(progress)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all progress entries for the current user.
     * Useful for showing "Continue Reading" suggestions.
     */
    suspend fun getAllProgress(): Result<List<PlaybackProgress>> {
        val uid = userId ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            val snapshot = firestore.collection("users")
                .document(uid)
                .collection("progress")
                .orderBy("lastUpdated", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
            
            val progressList = snapshot.documents.mapNotNull { doc ->
                PlaybackProgress(
                    bookId = doc.id,
                    bookTitle = doc.getString("bookTitle") ?: "",
                    positionMs = doc.getLong("positionMs") ?: 0L,
                    totalDurationMs = doc.getLong("totalDurationMs") ?: 0L,
                    lastChapter = doc.getLong("lastChapter")?.toInt() ?: 1,
                    playbackSpeed = doc.getDouble("playbackSpeed")?.toFloat() ?: 1.0f
                )
            }
            
            Result.success(progressList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Observe progress changes in real-time.
     * Useful for syncing progress when another device updates.
     */
    fun observeProgress(bookId: String): Flow<PlaybackProgress?> = callbackFlow {
        val uid = userId
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        
        val listener = firestore.collection("users")
            .document(uid)
            .collection("progress")
            .document(bookId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val progress = snapshot?.let { doc ->
                    if (doc.exists()) {
                        PlaybackProgress(
                            bookId = bookId,
                            bookTitle = doc.getString("bookTitle") ?: "",
                            positionMs = doc.getLong("positionMs") ?: 0L,
                            totalDurationMs = doc.getLong("totalDurationMs") ?: 0L,
                            lastChapter = doc.getLong("lastChapter")?.toInt() ?: 1,
                            playbackSpeed = doc.getDouble("playbackSpeed")?.toFloat() ?: 1.0f
                        )
                    } else null
                }
                trySend(progress)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Delete progress for a book.
     */
    suspend fun deleteProgress(bookId: String): Result<Unit> {
        val uid = userId ?: return Result.failure(Exception("User not signed in"))
        
        return try {
            firestore.collection("users")
                .document(uid)
                .collection("progress")
                .document(bookId)
                .delete()
                .await()
            
            // Also delete from local database
            progressDao?.deleteProgress(bookId)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Data class representing playback progress for cloud sync.
 */
data class PlaybackProgress(
    val bookId: String,
    val bookTitle: String,
    val positionMs: Long,
    val totalDurationMs: Long,
    val lastChapter: Int,
    val playbackSpeed: Float = 1.0f
) {
    /**
     * Progress as a percentage (0.0 - 1.0).
     */
    val progressPercent: Float
        get() = if (totalDurationMs > 0) {
            positionMs.toFloat() / totalDurationMs.toFloat()
        } else 0f
    
    /**
     * Remaining time in milliseconds.
     */
    val remainingMs: Long
        get() = (totalDurationMs - positionMs).coerceAtLeast(0)
}

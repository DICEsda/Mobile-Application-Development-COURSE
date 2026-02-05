package com.audiobook.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for audiobook operations.
 * Provides methods for CRUD operations on audiobooks and their chapters.
 */
@Dao
interface AudiobookDao {
    
    // ========== Audiobook Queries ==========
    
    /**
     * Get all audiobooks sorted by date added (newest first).
     */
    @Query("SELECT * FROM audiobooks ORDER BY dateAdded DESC")
    fun getAllAudiobooks(): Flow<List<AudiobookEntity>>
    
    /**
     * Get all audiobooks with their chapters.
     */
    @Transaction
    @Query("SELECT * FROM audiobooks ORDER BY dateAdded DESC")
    fun getAllAudiobooksWithChapters(): Flow<List<AudiobookWithChapters>>
    
    /**
     * Get a single audiobook by ID.
     */
    @Query("SELECT * FROM audiobooks WHERE id = :id")
    suspend fun getAudiobookById(id: String): AudiobookEntity?
    
    /**
     * Get audiobook with chapters by ID.
     */
    @Transaction
    @Query("SELECT * FROM audiobooks WHERE id = :id")
    suspend fun getAudiobookWithChapters(id: String): AudiobookWithChapters?
    
    /**
     * Search audiobooks by title or author.
     */
    @Query("""
        SELECT * FROM audiobooks 
        WHERE title LIKE '%' || :query || '%' 
        OR author LIKE '%' || :query || '%'
        ORDER BY dateAdded DESC
    """)
    fun searchAudiobooks(query: String): Flow<List<AudiobookEntity>>
    
    /**
     * Get recently played audiobooks.
     */
    @Query("""
        SELECT * FROM audiobooks 
        WHERE lastPlayed IS NOT NULL 
        ORDER BY lastPlayed DESC 
        LIMIT :limit
    """)
    fun getRecentlyPlayed(limit: Int = 10): Flow<List<AudiobookEntity>>
    
    /**
     * Get the most recently played audiobook.
     */
    @Query("""
        SELECT * FROM audiobooks 
        WHERE lastPlayed IS NOT NULL 
        ORDER BY lastPlayed DESC 
        LIMIT 1
    """)
    fun getCurrentlyPlaying(): Flow<AudiobookEntity?>
    
    /**
     * Get total count of audiobooks.
     */
    @Query("SELECT COUNT(*) FROM audiobooks")
    suspend fun getAudiobookCount(): Int
    
    /**
     * Get audiobook by file path.
     */
    @Query("SELECT * FROM audiobooks WHERE filePath = :path")
    suspend fun getAudiobookByPath(path: String): AudiobookEntity?
    
    // ========== Audiobook Modifications ==========
    
    /**
     * Insert a new audiobook (replace if exists).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobook(audiobook: AudiobookEntity)
    
    /**
     * Insert multiple audiobooks.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAudiobooks(audiobooks: List<AudiobookEntity>)
    
    /**
     * Update an existing audiobook.
     */
    @Update
    suspend fun updateAudiobook(audiobook: AudiobookEntity)
    
    /**
     * Update the last played timestamp.
     */
    @Query("UPDATE audiobooks SET lastPlayed = :timestamp WHERE id = :id")
    suspend fun updateLastPlayed(id: String, timestamp: Long = System.currentTimeMillis())
    
    /**
     * Delete an audiobook.
     */
    @Delete
    suspend fun deleteAudiobook(audiobook: AudiobookEntity)
    
    /**
     * Delete audiobook by ID.
     */
    @Query("DELETE FROM audiobooks WHERE id = :id")
    suspend fun deleteAudiobookById(id: String)
    
    /**
     * Delete all audiobooks.
     */
    @Query("DELETE FROM audiobooks")
    suspend fun deleteAllAudiobooks()
    
    // ========== Chapter Operations ==========
    
    /**
     * Get chapters for a specific audiobook.
     */
    @Query("SELECT * FROM chapters WHERE bookId = :bookId ORDER BY chapterNumber ASC")
    fun getChaptersForBook(bookId: String): Flow<List<ChapterEntity>>
    
    /**
     * Insert chapters for a book.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterEntity>)
    
    /**
     * Delete chapters for a book.
     */
    @Query("DELETE FROM chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: String)
}

/**
 * Data Access Object for progress tracking.
 * Manages playback position and progress for each audiobook.
 */
@Dao
interface ProgressDao {
    
    /**
     * Get progress for a specific audiobook.
     */
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): ProgressEntity?
    
    /**
     * Get progress as Flow for reactive updates.
     */
    @Query("SELECT * FROM progress WHERE bookId = :bookId")
    fun getProgressFlow(bookId: String): Flow<ProgressEntity?>
    
    /**
     * Get all progress entries.
     */
    @Query("SELECT * FROM progress ORDER BY lastUpdated DESC")
    fun getAllProgress(): Flow<List<ProgressEntity>>
    
    /**
     * Get progress entries that need to be synced to cloud.
     */
    @Query("SELECT * FROM progress WHERE isSyncedToCloud = 0")
    suspend fun getUnsyncedProgress(): List<ProgressEntity>
    
    /**
     * Insert or update progress.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: ProgressEntity)
    
    /**
     * Update playback position.
     */
    @Query("""
        UPDATE progress 
        SET currentPositionMs = :positionMs, 
            currentChapter = :chapter,
            progress = :progressPercent,
            lastUpdated = :timestamp,
            isSyncedToCloud = 0
        WHERE bookId = :bookId
    """)
    suspend fun updatePosition(
        bookId: String,
        positionMs: Long,
        chapter: Int,
        progressPercent: Float,
        timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Update playback speed for a book.
     */
    @Query("UPDATE progress SET playbackSpeed = :speed WHERE bookId = :bookId")
    suspend fun updatePlaybackSpeed(bookId: String, speed: Float)
    
    /**
     * Mark progress as synced to cloud.
     */
    @Query("UPDATE progress SET isSyncedToCloud = 1 WHERE bookId = :bookId")
    suspend fun markAsSynced(bookId: String)
    
    /**
     * Mark multiple progress entries as synced.
     */
    @Query("UPDATE progress SET isSyncedToCloud = 1 WHERE bookId IN (:bookIds)")
    suspend fun markMultipleAsSynced(bookIds: List<String>)
    
    /**
     * Delete progress for a book.
     */
    @Query("DELETE FROM progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: String)
    
    /**
     * Delete all progress.
     */
    @Query("DELETE FROM progress")
    suspend fun deleteAllProgress()
}

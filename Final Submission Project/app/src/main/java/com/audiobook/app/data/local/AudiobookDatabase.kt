package com.audiobook.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database for the Audiobook application.
 * 
 * This database stores:
 * - Audiobooks: Metadata about each audiobook in the library
 * - Chapters: Chapter information extracted from M4B files
 * - Progress: User's playback progress for each audiobook
 * 
 * The database uses a singleton pattern to ensure only one instance
 * exists throughout the application lifecycle.
 */
@Database(
    entities = [
        AudiobookEntity::class,
        ChapterEntity::class,
        ProgressEntity::class
    ],
    version = 2,
    exportSchema = true
)
abstract class AudiobookDatabase : RoomDatabase() {
    
    /**
     * DAO for audiobook and chapter operations.
     */
    abstract fun audiobookDao(): AudiobookDao
    
    /**
     * DAO for progress tracking operations.
     */
    abstract fun progressDao(): ProgressDao
    
    companion object {
        private const val DATABASE_NAME = "audiobook_database"
        
        @Volatile
        private var INSTANCE: AudiobookDatabase? = null
        
        /**
         * Get the singleton database instance.
         * Uses double-checked locking for thread safety.
         * 
         * @param context Application context
         * @return The database instance
         */
        fun getInstance(context: Context): AudiobookDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }
        
        private fun buildDatabase(context: Context): AudiobookDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AudiobookDatabase::class.java,
                DATABASE_NAME
            )
                // Enable destructive migration for development
                // In production, you would provide proper migrations
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

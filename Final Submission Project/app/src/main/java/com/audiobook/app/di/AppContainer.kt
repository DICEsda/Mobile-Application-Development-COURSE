package com.audiobook.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.audiobook.app.data.local.AudiobookDatabase
import com.audiobook.app.data.parser.ChapterParser
import com.audiobook.app.data.remote.ApiClient
import com.audiobook.app.data.remote.BookMetadataRepository
import com.audiobook.app.data.repository.AudiobookRepository
import com.audiobook.app.data.repository.AuthRepository
import com.audiobook.app.data.repository.PreferencesRepository
import com.audiobook.app.data.repository.ProgressSyncRepository
import com.audiobook.app.service.AudiobookPlayer

/**
 * Manual Dependency Injection Container
 * 
 * This container holds application-scoped dependencies and provides
 * factory methods for creating instances. Using manual DI demonstrates
 * understanding of dependency injection principles without framework overhead.
 * 
 * Benefits of Manual DI:
 * - Zero additional dependencies
 * - Complete control over object lifecycle
 * - No annotation processing (faster builds)
 * - Easy to understand and debug
 * 
 * Trade-offs vs Hilt/Koin:
 * - More boilerplate code
 * - Manual singleton management
 * - No compile-time validation (Hilt) or DSL (Koin)
 */
class AppContainer(private val context: Context) {
    
    // DataStore instance for user preferences (singleton)
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
        name = "audiobook_preferences"
    )
    
    // ========== Database ==========
    
    /**
     * Room Database instance - manages local storage for audiobooks,
     * chapters, and playback progress.
     */
    val database: AudiobookDatabase by lazy {
        AudiobookDatabase.getInstance(context)
    }
    
    /**
     * DAO for audiobook operations.
     */
    val audiobookDao by lazy { database.audiobookDao() }
    
    /**
     * DAO for progress tracking.
     */
    val progressDao by lazy { database.progressDao() }
    
    // ========== Network ==========
    
    /**
     * OpenLibrary API service for fetching book metadata.
     */
    val openLibraryApi by lazy { ApiClient.openLibraryApi }
    
    /**
     * Book Metadata Repository - fetches cover images and descriptions
     * from OpenLibrary API.
     */
    val bookMetadataRepository: BookMetadataRepository by lazy {
        BookMetadataRepository(openLibraryApi)
    }
    
    // ========== Repositories ==========
    
    /**
     * Preferences Repository - manages user settings like playback speed,
     * last played book, theme preferences, etc.
     */
    val preferencesRepository: PreferencesRepository by lazy {
        PreferencesRepository(context.dataStore)
    }
    
    /**
     * Audiobook Repository - manages the local audiobook library,
     * including scanning for M4B files and caching metadata.
     * Now uses Room database for persistence.
     */
    val audiobookRepository: AudiobookRepository by lazy {
        AudiobookRepository(
            context = context,
            preferencesRepository = preferencesRepository,
            audiobookDao = audiobookDao,
            progressDao = progressDao,
            bookMetadataRepository = bookMetadataRepository
        )
    }
    
    /**
     * Auth Repository - handles Firebase authentication.
     * Provides sign in, sign up, and password reset functionality.
     */
    val authRepository: AuthRepository by lazy {
        AuthRepository()
    }
    
    /**
     * Progress Sync Repository - syncs playback progress to Firestore.
     * Enables cross-device progress tracking.
     */
    val progressSyncRepository: ProgressSyncRepository by lazy {
        ProgressSyncRepository(progressDao)
    }
    
    // ========== Media ==========
    
    /**
     * Chapter Parser - extracts metadata and chapters from M4B files.
     */
    val chapterParser: ChapterParser by lazy {
        ChapterParser(context)
    }
    
    /**
     * Audiobook Player - UI-facing wrapper around MediaController.
     * Provides reactive state for Compose UI integration.
     */
    val audiobookPlayer: AudiobookPlayer by lazy {
        AudiobookPlayer(context)
    }
}

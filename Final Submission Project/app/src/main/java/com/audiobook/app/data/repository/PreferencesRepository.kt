package com.audiobook.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import com.audiobook.app.data.remote.llm.LlmConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Repository for managing user preferences using DataStore.
 * Provides reactive access to settings with Flow-based API.
 */
class PreferencesRepository(
    private val dataStore: DataStore<Preferences>
) {
    
    private object PreferenceKeys {
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LAST_PLAYED_BOOK_ID = stringPreferencesKey("last_played_book_id")
        val LAST_PLAYED_POSITION = longPreferencesKey("last_played_position")
        val REMEMBER_BIOMETRIC = booleanPreferencesKey("remember_biometric")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
        val AUTO_DOWNLOAD = booleanPreferencesKey("auto_download")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val SLEEP_TIMER_MINUTES = intPreferencesKey("sleep_timer_minutes")
        val AUDIOBOOK_FOLDER_PATH = stringPreferencesKey("audiobook_folder_path")
        val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("disclaimer_accepted")
    }
    
    /**
     * Current playback speed (0.5x - 2.0x)
     */
    val playbackSpeed: Flow<Float> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences())
            else throw exception
        }
        .map { preferences ->
            preferences[PreferenceKeys.PLAYBACK_SPEED] ?: 1.0f
        }
    
    suspend fun setPlaybackSpeed(speed: Float) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.PLAYBACK_SPEED] = speed.coerceIn(0.5f, 2.0f)
        }
    }
    
    /**
     * Last played book ID and position for seamless resume
     */
    val lastPlayedBookId: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.LAST_PLAYED_BOOK_ID] }
    
    val lastPlayedPosition: Flow<Long> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.LAST_PLAYED_POSITION] ?: 0L }
    
    suspend fun setLastPlayed(bookId: String, positionMs: Long) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_PLAYED_BOOK_ID] = bookId
            preferences[PreferenceKeys.LAST_PLAYED_POSITION] = positionMs
        }
    }
    
    suspend fun setLastPlayedBook(bookId: String) {
        dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAST_PLAYED_BOOK_ID] = bookId
        }
    }
    
    /**
     * Biometric "Remember Me" setting
     */
    val rememberBiometric: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.REMEMBER_BIOMETRIC] ?: false }
    
    suspend fun setRememberBiometric(remember: Boolean) {
        dataStore.edit { it[PreferenceKeys.REMEMBER_BIOMETRIC] = remember }
    }
    
    /**
     * Dark mode preference (default: true for Lunar theme)
     */
    val darkMode: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.DARK_MODE] ?: true }
    
    suspend fun setDarkMode(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.DARK_MODE] = enabled }
    }
    
    /**
     * Auto-download setting
     */
    val autoDownload: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.AUTO_DOWNLOAD] ?: false }
    
    suspend fun setAutoDownload(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.AUTO_DOWNLOAD] = enabled }
    }
    
    /**
     * Notifications enabled
     */
    val notificationsEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.NOTIFICATIONS_ENABLED] ?: true }
    
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.NOTIFICATIONS_ENABLED] = enabled }
    }
    
    /**
     * Sleep timer duration in minutes (0 = disabled)
     */
    val sleepTimerMinutes: Flow<Int> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.SLEEP_TIMER_MINUTES] ?: 0 }
    
    suspend fun setSleepTimerMinutes(minutes: Int) {
        dataStore.edit { it[PreferenceKeys.SLEEP_TIMER_MINUTES] = minutes }
    }
    
    /**
     * Custom audiobook folder path
     */
    val audiobookFolderPath: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.AUDIOBOOK_FOLDER_PATH] }
    
    suspend fun setAudiobookFolderPath(path: String) {
        dataStore.edit { it[PreferenceKeys.AUDIOBOOK_FOLDER_PATH] = path }
    }

    // ──────────────────── Book Companion (local LLM) ────────────────────

    /** Whether the AI Book Companion feature is enabled (default: off). */
    val llmEnabled: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.LLM_ENABLED] ?: false }

    suspend fun setLlmEnabled(enabled: Boolean) {
        dataStore.edit { it[PreferenceKeys.LLM_ENABLED] = enabled }
    }

    /** Base URL of the LM Studio (OpenAI-compatible) server. */
    val llmBaseUrl: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.LLM_BASE_URL] ?: LlmConfig.DEFAULT_BASE_URL }

    suspend fun setLlmBaseUrl(url: String) {
        dataStore.edit { it[PreferenceKeys.LLM_BASE_URL] = url }
    }

    /** Model identifier to request from the server. */
    val llmModel: Flow<String> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.LLM_MODEL] ?: LlmConfig.DEFAULT_MODEL }

    suspend fun setLlmModel(model: String) {
        dataStore.edit { it[PreferenceKeys.LLM_MODEL] = model }
    }

    // ──────────────────── First-run legal disclaimer ────────────────────

    /** Whether the user has accepted the first-run Terms & Disclaimer. */
    val disclaimerAccepted: Flow<Boolean> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[PreferenceKeys.DISCLAIMER_ACCEPTED] ?: false }

    suspend fun setDisclaimerAccepted(accepted: Boolean) {
        dataStore.edit { it[PreferenceKeys.DISCLAIMER_ACCEPTED] = accepted }
    }

    /** Combined, reactive view of the LLM configuration. */
    val llmConfig: Flow<LlmConfig> = combine(
        llmBaseUrl,
        llmModel,
        llmEnabled
    ) { baseUrl, model, enabled ->
        LlmConfig(baseUrl = baseUrl, model = model, enabled = enabled)
    }
}

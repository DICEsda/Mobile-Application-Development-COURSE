package com.audiobook.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

// DataStore extension
private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_preferences")

/**
 * Repository for managing notification preferences and FCM tokens.
 * 
 * Handles:
 * - Notification permission status
 * - User preferences for different notification types
 * - FCM token management
 * - Notification scheduling preferences
 */
class NotificationRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()
) {
    
    private val dataStore = context.notificationDataStore
    
    companion object {
        // Preference keys
        private val KEY_NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val KEY_REMINDERS_ENABLED = booleanPreferencesKey("reminders_enabled")
        private val KEY_STREAKS_ENABLED = booleanPreferencesKey("streaks_enabled")
        private val KEY_MILESTONES_ENABLED = booleanPreferencesKey("milestones_enabled")
        private val KEY_REMINDER_HOUR = intPreferencesKey("reminder_hour")
        private val KEY_REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        private val KEY_LAST_NOTIFICATION_TIME = longPreferencesKey("last_notification_time")
        private val KEY_FCM_TOKEN = booleanPreferencesKey("fcm_token_saved")
    }
    
    // ============================================================
    // Preference Flows
    // ============================================================
    
    val notificationsEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATIONS_ENABLED] ?: true
    }
    
    val remindersEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_REMINDERS_ENABLED] ?: true
    }
    
    val streaksEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_STREAKS_ENABLED] ?: true
    }
    
    val milestonesEnabled: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_MILESTONES_ENABLED] ?: true
    }
    
    val reminderTime: Flow<Pair<Int, Int>> = dataStore.data.map { prefs ->
        val hour = prefs[KEY_REMINDER_HOUR] ?: 20 // Default 8:00 PM
        val minute = prefs[KEY_REMINDER_MINUTE] ?: 0
        Pair(hour, minute)
    }
    
    val preferences: Flow<NotificationPreferences> = dataStore.data.map { prefs ->
        NotificationPreferences(
            notificationsEnabled = prefs[KEY_NOTIFICATIONS_ENABLED] ?: true,
            remindersEnabled = prefs[KEY_REMINDERS_ENABLED] ?: true,
            streaksEnabled = prefs[KEY_STREAKS_ENABLED] ?: true,
            milestonesEnabled = prefs[KEY_MILESTONES_ENABLED] ?: true,
            reminderHour = prefs[KEY_REMINDER_HOUR] ?: 20,
            reminderMinute = prefs[KEY_REMINDER_MINUTE] ?: 0
        )
    }
    
    // ============================================================
    // Preference Updates
    // ============================================================
    
    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_NOTIFICATIONS_ENABLED] = enabled
        }
        
        // If disabling, unsubscribe from all topics
        if (!enabled) {
            messaging.unsubscribeFromTopic("all_users").await()
        } else {
            messaging.subscribeToTopic("all_users").await()
        }
    }
    
    suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_REMINDERS_ENABLED] = enabled
        }
    }
    
    suspend fun setStreaksEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_STREAKS_ENABLED] = enabled
        }
    }
    
    suspend fun setMilestonesEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_MILESTONES_ENABLED] = enabled
        }
    }
    
    suspend fun setReminderTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_REMINDER_HOUR] = hour
            prefs[KEY_REMINDER_MINUTE] = minute
        }
    }
    
    suspend fun updateLastNotificationTime(timeMs: Long) {
        dataStore.edit { prefs ->
            prefs[KEY_LAST_NOTIFICATION_TIME] = timeMs
        }
    }
    
    // ============================================================
    // FCM Token Management
    // ============================================================
    
    /**
     * Get the current FCM token and save it to Firestore.
     */
    suspend fun refreshFcmToken(): Result<String> {
        return try {
            val token = messaging.token.await()
            saveFcmTokenToFirestore(token)
            
            // Mark token as saved in DataStore
            dataStore.edit { prefs ->
                prefs[KEY_FCM_TOKEN] = true
            }
            
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Save FCM token to Firestore for the current user.
     */
    private suspend fun saveFcmTokenToFirestore(token: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("users")
                .document(userId)
                .update(
                    mapOf(
                        "fcmToken" to token,
                        "tokenUpdatedAt" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (e: Exception) {
            // Document might not exist, create it
            try {
                firestore.collection("users")
                    .document(userId)
                    .set(
                        mapOf(
                            "fcmToken" to token,
                            "tokenUpdatedAt" to System.currentTimeMillis()
                        )
                    )
                    .await()
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
    
    /**
     * Subscribe to notification topics.
     */
    suspend fun subscribeToTopics() {
        try {
            messaging.subscribeToTopic("all_users").await()
            
            // Subscribe based on user preferences
            val prefs = preferences.first()
            
            if (prefs.remindersEnabled) {
                messaging.subscribeToTopic("reminders").await()
            }
            if (prefs.streaksEnabled) {
                messaging.subscribeToTopic("streaks").await()
            }
            if (prefs.milestonesEnabled) {
                messaging.subscribeToTopic("milestones").await()
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    /**
     * Unsubscribe from all notification topics.
     */
    suspend fun unsubscribeFromAllTopics() {
        try {
            messaging.unsubscribeFromTopic("all_users").await()
            messaging.unsubscribeFromTopic("reminders").await()
            messaging.unsubscribeFromTopic("streaks").await()
            messaging.unsubscribeFromTopic("milestones").await()
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    // ============================================================
    // Notification Statistics
    // ============================================================
    
    /**
     * Save notification statistics to Firestore.
     */
    suspend fun logNotificationSent(type: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("users")
                .document(userId)
                .collection("notification_stats")
                .add(
                    mapOf(
                        "type" to type,
                        "sentAt" to System.currentTimeMillis()
                    )
                )
                .await()
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    /**
     * Check if user should receive a notification based on preferences and cooldown.
     */
    suspend fun shouldSendNotification(type: String): Boolean {
        val prefs = preferences.first()
        
        // Check if notifications are enabled globally
        if (!prefs.notificationsEnabled) return false
        
        // Check type-specific preferences
        when (type) {
            "reminder" -> if (!prefs.remindersEnabled) return false
            "streak" -> if (!prefs.streaksEnabled) return false
            "milestone" -> if (!prefs.milestonesEnabled) return false
        }
        
        // Check cooldown (don't send notifications more than once per hour)
        val lastNotificationTime = dataStore.data.first()[KEY_LAST_NOTIFICATION_TIME] ?: 0L
        val hoursSinceLastNotification = (System.currentTimeMillis() - lastNotificationTime) / (1000 * 60 * 60)
        
        return hoursSinceLastNotification >= 1
    }
}

/**
 * Data class representing all notification preferences.
 */
data class NotificationPreferences(
    val notificationsEnabled: Boolean = true,
    val remindersEnabled: Boolean = true,
    val streaksEnabled: Boolean = true,
    val milestonesEnabled: Boolean = true,
    val reminderHour: Int = 20, // 8:00 PM
    val reminderMinute: Int = 0
) {
    val reminderTimeFormatted: String
        get() {
            val hourFormatted = if (reminderHour > 12) reminderHour - 12 else reminderHour
            val amPm = if (reminderHour >= 12) "PM" else "AM"
            return String.format("%d:%02d %s", hourFormatted, reminderMinute, amPm)
        }
}

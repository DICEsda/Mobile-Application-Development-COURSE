package com.audiobook.app.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// DataStore extension
private val Context.notificationDataStore: DataStore<Preferences> by preferencesDataStore(name = "notification_preferences")

/**
 * Repository for managing notification preferences (fully local).
 *
 * Handles per-type toggles, the daily reminder time, and a simple cooldown so
 * notifications aren't sent more than once per hour. No cloud / FCM involved.
 */
class NotificationRepository(
    private val context: Context
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
    }

    // Preference Flows

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

    // Preference Updates

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setRemindersEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_REMINDERS_ENABLED] = enabled }
    }

    suspend fun setStreaksEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_STREAKS_ENABLED] = enabled }
    }

    suspend fun setMilestonesEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[KEY_MILESTONES_ENABLED] = enabled }
    }

    suspend fun setReminderTime(hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_REMINDER_HOUR] = hour
            prefs[KEY_REMINDER_MINUTE] = minute
        }
    }

    suspend fun updateLastNotificationTime(timeMs: Long) {
        dataStore.edit { prefs -> prefs[KEY_LAST_NOTIFICATION_TIME] = timeMs }
    }

    /** Local breadcrumb only — no analytics backend. */
    fun logNotificationSent(type: String) {
        android.util.Log.d("NotificationRepository", "Notification sent: $type")
    }

    /**
     * Check if a notification of [type] should be sent, based on per-type
     * preferences and a 1-hour cooldown.
     */
    suspend fun shouldSendNotification(type: String): Boolean {
        val prefs = preferences.first()

        if (!prefs.notificationsEnabled) return false

        when (type) {
            "reminder" -> if (!prefs.remindersEnabled) return false
            "streak" -> if (!prefs.streaksEnabled) return false
            "milestone" -> if (!prefs.milestonesEnabled) return false
        }

        val lastNotificationTime = dataStore.data.first()[KEY_LAST_NOTIFICATION_TIME] ?: 0L
        val hoursSinceLast = (System.currentTimeMillis() - lastNotificationTime) / (1000 * 60 * 60)
        return hoursSinceLast >= 1
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

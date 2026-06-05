package com.audiobook.app.service

import android.content.Context
import com.audiobook.app.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * NotificationTriggerHelper
 *
 * Fires local notifications for in-app events. Cumulative stats (books
 * completed, hours listened, streaks) were a cloud-only feature and have been
 * removed; what remains is purely local and event-driven.
 */
class NotificationTriggerHelper(
    private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val notificationScheduler: NotificationScheduler
) {

    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Called when a book starts playing. Stat-based "first book" detection was
     * cloud-only; kept as a no-op so callers don't need to change.
     */
    fun onFirstAudiobookStarted(bookTitle: String) {
        // No-op: previously used Firestore to detect the user's first book.
    }

    /**
     * Trigger a local notification when a book is finished.
     */
    fun onBookCompleted(bookTitle: String) {
        helperScope.launch {
            if (!notificationRepository.shouldSendNotification("milestone")) return@launch
            notificationScheduler.showMilestoneNotification(
                milestoneType = "book_completed",
                message = "You've finished \"$bookTitle\"! Time to discover your next adventure."
            )
            notificationRepository.logNotificationSent("milestone")
        }
    }

    /**
     * Called when a listening session ends. Hours/streak milestones were
     * cloud-tracked; kept as a no-op so callers don't need to change.
     */
    fun onListeningSessionCompleted(minutesListened: Int) {
        // No-op: cumulative listening-time stats were a cloud-only feature.
    }

    /**
     * Initialize the local notification system on app start: schedule the daily
     * reminder if the user has it enabled.
     */
    fun initialize() {
        helperScope.launch {
            try {
                val preferences = notificationRepository.preferences.first()
                if (preferences.notificationsEnabled && preferences.remindersEnabled) {
                    notificationScheduler.scheduleDailyReminder(
                        preferences.reminderHour,
                        preferences.reminderMinute
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("NotificationTriggerHelper", "init failed", e)
            }
        }
    }
}

package com.audiobook.app.service

import android.content.Context
import com.audiobook.app.data.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first

/**
 * NotificationTriggerHelper
 * 
 * Manages notification triggers based on user activity and milestones.
 * Integrates with Firestore to track stats and trigger appropriate notifications.
 */
class NotificationTriggerHelper(
    private val context: Context,
    private val notificationRepository: NotificationRepository,
    private val notificationScheduler: NotificationScheduler
) {
    
    private val helperScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    // ============================================================
    // Book Completion
    // ============================================================
    
    /**
     * Trigger notification when a user completes a book.
     */
    fun onBookCompleted(bookTitle: String) {
        helperScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            
            // Check if notifications are enabled
            if (!notificationRepository.shouldSendNotification("milestone")) {
                return@launch
            }
            
            try {
                // Increment books completed
                val userDoc = firestore.collection("users").document(userId)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(userDoc)
                    val currentBooksCompleted = snapshot.getLong("booksCompleted") ?: 0
                    val newBooksCompleted = currentBooksCompleted + 1
                    
                    transaction.set(
                        userDoc,
                        mapOf(
                            "booksCompleted" to newBooksCompleted,
                            "lastBookCompletedAt" to System.currentTimeMillis(),
                            "lastBookCompleted" to bookTitle
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    
                    newBooksCompleted
                }.await()
                
                // Show completion notification
                notificationScheduler.showMilestoneNotification(
                    milestoneType = "book_completed",
                    message = "You've finished \"$bookTitle\"! Time to discover your next adventure."
                )
                
                // Check for books milestone
                checkBooksMilestone(userId)
                
                notificationRepository.logNotificationSent("milestone")
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
    
    /**
     * Check if user reached a books milestone (5, 10, 25, 50, 100 books).
     */
    private suspend fun checkBooksMilestone(userId: String) {
        try {
            val doc = firestore.collection("users").document(userId).get().await()
            val booksCompleted = doc.getLong("booksCompleted")?.toInt() ?: 0
            
            val milestones = listOf(5, 10, 25, 50, 100)
            if (milestones.contains(booksCompleted)) {
                notificationScheduler.showMilestoneNotification(
                    milestoneType = "books_milestone",
                    message = "Amazing! You've completed $booksCompleted audiobooks! 📚"
                )
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    // ============================================================
    // Listening Time Milestones
    // ============================================================
    
    /**
     * Update listening time and check for milestones.
     * 
     * @param minutesListened Minutes listened in this session
     */
    fun onListeningSessionCompleted(minutesListened: Int) {
        helperScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            
            try {
                // Update total hours listened
                val userDoc = firestore.collection("users").document(userId)
                val newTotalMinutes = firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(userDoc)
                    val currentMinutes = snapshot.getLong("totalMinutesListened") ?: 0
                    val newMinutes = currentMinutes + minutesListened
                    
                    transaction.set(
                        userDoc,
                        mapOf(
                            "totalMinutesListened" to newMinutes,
                            "lastListeningSessionAt" to System.currentTimeMillis()
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    )
                    
                    newMinutes
                }.await()
                
                // Check for hours milestone
                checkHoursMilestone(userId, newTotalMinutes.toInt())
                
                // Update streak
                updateStreak(userId)
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
    
    /**
     * Check if user reached listening hours milestone (10, 50, 100, 500, 1000 hours).
     */
    private suspend fun checkHoursMilestone(userId: String, totalMinutes: Int) {
        if (!notificationRepository.shouldSendNotification("milestone")) {
            return
        }
        
        try {
            val totalHours = totalMinutes / 60
            val milestones = listOf(10, 50, 100, 500, 1000)
            
            // Check if we just crossed a milestone
            val previousHours = (totalMinutes - 60) / 60 // Approximate previous hour count
            
            for (milestone in milestones) {
                if (totalHours >= milestone && previousHours < milestone) {
                    notificationScheduler.showMilestoneNotification(
                        milestoneType = "hours_milestone",
                        message = "Wow! You've listened to $milestone hours of audiobooks! 🎧"
                    )
                    notificationRepository.logNotificationSent("milestone")
                    break
                }
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    // ============================================================
    // Streak Management
    // ============================================================
    
    /**
     * Update user's listening streak.
     * Increments streak if user listened today, or resets if they missed a day.
     */
    private suspend fun updateStreak(userId: String) {
        try {
            val userDoc = firestore.collection("users").document(userId)
            val doc = userDoc.get().await()
            
            val lastListeningSessionAt = doc.getLong("lastListeningSessionAt") ?: 0L
            val currentStreak = doc.getLong("currentStreak")?.toInt() ?: 0
            
            val now = System.currentTimeMillis()
            val oneDayMs = 24 * 60 * 60 * 1000L
            val daysSinceLastSession = (now - lastListeningSessionAt) / oneDayMs
            
            val newStreak = when {
                daysSinceLastSession <= 1 -> currentStreak + 1 // Same day or consecutive day
                else -> 1 // Streak broken, restart
            }
            
            userDoc.update(
                mapOf(
                    "currentStreak" to newStreak,
                    "lastStreakUpdateAt" to now
                )
            ).await()
            
            // Schedule streak reminder for 23 hours from now
            if (notificationRepository.shouldSendNotification("streak")) {
                notificationScheduler.scheduleStreakReminder()
            }
            
            // Celebrate streak milestones
            if (newStreak in listOf(7, 30, 100, 365)) {
                notificationScheduler.showMilestoneNotification(
                    milestoneType = "streak_milestone",
                    message = "Amazing! You've maintained a $newStreak day listening streak! 🔥"
                )
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }
    
    /**
     * Check if user's streak is at risk and schedule reminder.
     * Call this when app starts or when significant time has passed.
     */
    fun checkStreakStatus() {
        helperScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            
            try {
                val doc = firestore.collection("users").document(userId).get().await()
                val lastListeningSessionAt = doc.getLong("lastListeningSessionAt") ?: 0L
                val currentStreak = doc.getLong("currentStreak")?.toInt() ?: 0
                
                if (currentStreak > 0) {
                    val now = System.currentTimeMillis()
                    val hoursSinceLastSession = (now - lastListeningSessionAt) / (60 * 60 * 1000)
                    
                    // If 20+ hours since last session and streak exists, send reminder
                    if (hoursSinceLastSession >= 20) {
                        if (notificationRepository.shouldSendNotification("streak")) {
                            notificationScheduler.showMilestoneNotification(
                                milestoneType = "streak_reminder",
                                message = "Keep your $currentStreak day streak alive! Listen to something today. 🔥"
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
    
    // ============================================================
    // Initialization
    // ============================================================
    
    /**
     * Initialize notification system on app start.
     * Sets up FCM token and schedules reminders.
     */
    fun initialize() {
        helperScope.launch {
            try {
                // Refresh FCM token
                notificationRepository.refreshFcmToken()
                
                // Subscribe to notification topics
                notificationRepository.subscribeToTopics()
                
                // Schedule daily reminder if enabled
                val preferences = notificationRepository.preferences.first()
                if (preferences.notificationsEnabled && preferences.remindersEnabled) {
                    notificationScheduler.scheduleDailyReminder(
                        preferences.reminderHour,
                        preferences.reminderMinute
                    )
                }
                
                // Check streak status
                checkStreakStatus()
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }
}

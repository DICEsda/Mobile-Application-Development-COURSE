package com.audiobook.app.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.audiobook.app.MainActivity
import com.audiobook.app.data.repository.NotificationRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar

/**
 * NotificationScheduler
 * 
 * Handles scheduling of local notifications using AlarmManager.
 * Supports daily reminders, streak notifications, and milestone alerts.
 */
class NotificationScheduler(private val context: Context) {
    
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    
    companion object {
        const val ACTION_DAILY_REMINDER = "com.audiobook.app.DAILY_REMINDER"
        const val ACTION_STREAK_REMINDER = "com.audiobook.app.STREAK_REMINDER"
        const val ACTION_MILESTONE = "com.audiobook.app.MILESTONE"
        
        private const val REQUEST_CODE_DAILY = 1001
        private const val REQUEST_CODE_STREAK = 1002
    }
    
    /**
     * Schedule daily listening reminder at user's preferred time.
     */
    suspend fun scheduleDailyReminder(hour: Int, minute: Int) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Set the alarm to start at the specified time
        val calendar = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            
            // If the time has already passed today, schedule for tomorrow
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        
        // Schedule repeating alarm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        } else {
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
        }
    }
    
    /**
     * Schedule streak reminder (23 hours after last listening session).
     */
    fun scheduleStreakReminder() {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_STREAK_REMINDER
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STREAK,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Schedule for 23 hours from now
        val triggerTime = System.currentTimeMillis() + (23 * 60 * 60 * 1000)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }
    
    /**
     * Show immediate milestone notification.
     */
    fun showMilestoneNotification(milestoneType: String, message: String) {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_MILESTONE
            putExtra("milestone_type", milestoneType)
            putExtra("message", message)
        }
        
        // Send broadcast immediately
        context.sendBroadcast(intent)
    }
    
    /**
     * Cancel daily reminder.
     */
    fun cancelDailyReminder() {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_DAILY_REMINDER
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_DAILY,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
    
    /**
     * Cancel streak reminder.
     */
    fun cancelStreakReminder() {
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            action = ACTION_STREAK_REMINDER
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_STREAK,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE
        )
        
        pendingIntent?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }
    
    /**
     * Cancel all scheduled notifications.
     */
    fun cancelAllNotifications() {
        cancelDailyReminder()
        cancelStreakReminder()
    }
}

/**
 * BroadcastReceiver for handling scheduled notification events.
 */
class NotificationReceiver : BroadcastReceiver() {
    
    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationScheduler.ACTION_DAILY_REMINDER -> {
                receiverScope.launch {
                    handleDailyReminder(context)
                }
            }
            NotificationScheduler.ACTION_STREAK_REMINDER -> {
                receiverScope.launch {
                    handleStreakReminder(context)
                }
            }
            NotificationScheduler.ACTION_MILESTONE -> {
                val milestoneType = intent.getStringExtra("milestone_type") ?: ""
                val message = intent.getStringExtra("message") ?: ""
                handleMilestone(context, milestoneType, message)
            }
        }
    }
    
    /**
     * Handle daily listening reminder.
     */
    private suspend fun handleDailyReminder(context: Context) {
        val notificationRepository = NotificationRepository(context)
        
        // Check if user has enabled reminders
        if (!notificationRepository.shouldSendNotification("reminder")) {
            return
        }
        
        // Get current book in progress (if any)
        val currentBook = getCurrentBookInProgress(context)
        
        val title = "Time to Listen 📚"
        val body = if (currentBook != null) {
            "Continue listening to \"$currentBook\""
        } else {
            "Discover your next great audiobook!"
        }
        
        showNotification(
            context,
            title,
            body,
            MyFirebaseMessagingService.CHANNEL_ID_REMINDERS
        )
        
        notificationRepository.updateLastNotificationTime(System.currentTimeMillis())
        notificationRepository.logNotificationSent("reminder")
    }
    
    /**
     * Handle streak reminder (23 hours since last session).
     */
    private suspend fun handleStreakReminder(context: Context) {
        val notificationRepository = NotificationRepository(context)
        
        // Check if user has enabled streak notifications
        if (!notificationRepository.shouldSendNotification("streak")) {
            return
        }
        
        // Get user's current streak
        val streak = getCurrentStreak(context)
        
        if (streak > 0) {
            val title = "Keep Your Streak Alive! 🔥"
            val body = "You have a $streak day listening streak. Listen today to keep it going!"
            
            showNotification(
                context,
                title,
                body,
                MyFirebaseMessagingService.CHANNEL_ID_STREAKS
            )
            
            notificationRepository.updateLastNotificationTime(System.currentTimeMillis())
            notificationRepository.logNotificationSent("streak")
        }
    }
    
    /**
     * Handle milestone notification.
     */
    private fun handleMilestone(context: Context, milestoneType: String, message: String) {
        val title = when (milestoneType) {
            "book_completed" -> "Book Completed! 🎉"
            "hours_milestone" -> "Listening Milestone! 🎧"
            "books_milestone" -> "Books Milestone! 📚"
            else -> "Achievement Unlocked! ⭐"
        }
        
        showNotification(
            context,
            title,
            message,
            MyFirebaseMessagingService.CHANNEL_ID_MILESTONES
        )
    }
    
    /**
     * Show a local notification.
     */
    private fun showNotification(context: Context, title: String, body: String, channelId: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Create intent to open app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with app icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()
        
        // Generate unique notification ID
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * Get the title of the book currently in progress.
     */
    private suspend fun getCurrentBookInProgress(context: Context): String? {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return null
        
        return try {
            val snapshot = firestore.collection("users")
                .document(userId)
                .collection("progress")
                .orderBy("lastUpdated", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            snapshot.documents.firstOrNull()?.getString("bookTitle")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Get user's current listening streak.
     */
    private suspend fun getCurrentStreak(context: Context): Int {
        val auth = FirebaseAuth.getInstance()
        val firestore = FirebaseFirestore.getInstance()
        val userId = auth.currentUser?.uid ?: return 0
        
        return try {
            val doc = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            
            doc.getLong("currentStreak")?.toInt() ?: 0
        } catch (e: Exception) {
            0
        }
    }
}

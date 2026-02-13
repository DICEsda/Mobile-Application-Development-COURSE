package com.audiobook.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.audiobook.app.MainActivity
import com.audiobook.app.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Firebase Cloud Messaging Service
 * 
 * Handles incoming push notifications and FCM token updates.
 * Creates notification channels and displays notifications with proper styling.
 */
class MyFirebaseMessagingService : FirebaseMessagingService() {
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    
    companion object {
        const val CHANNEL_ID_REMINDERS = "audiobook_reminders"
        const val CHANNEL_ID_MILESTONES = "audiobook_milestones"
        const val CHANNEL_ID_STREAKS = "audiobook_streaks"
        const val CHANNEL_ID_GENERAL = "audiobook_general"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    /**
     * Called when a new FCM token is generated.
     * This happens on first app install, after reinstall, or when token is rotated.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        
        // Store token in Firestore for targeted push notifications
        serviceScope.launch {
            saveTokenToFirestore(token)
        }
    }
    
    /**
     * Called when a message is received from Firebase.
     */
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        // Handle notification payload
        message.notification?.let { notification ->
            val title = notification.title ?: "Audiobook Player"
            val body = notification.body ?: ""
            val channelId = message.data["channel"] ?: CHANNEL_ID_GENERAL
            
            showNotification(title, body, channelId)
        }
        
        // Handle data payload (for custom actions)
        if (message.data.isNotEmpty()) {
            handleDataPayload(message.data)
        }
    }
    
    /**
     * Save FCM token to Firestore for the current user.
     */
    private suspend fun saveTokenToFirestore(token: String) {
        val userId = auth.currentUser?.uid ?: return
        
        try {
            firestore.collection("users")
                .document(userId)
                .update("fcmToken", token, "tokenUpdatedAt", System.currentTimeMillis())
                .await()
        } catch (e: Exception) {
            // Document might not exist yet, create it
            try {
                firestore.collection("users")
                    .document(userId)
                    .set(mapOf(
                        "fcmToken" to token,
                        "tokenUpdatedAt" to System.currentTimeMillis()
                    ))
                    .await()
            } catch (e: Exception) {
                // Silently fail - token will be updated next time
            }
        }
    }
    
    /**
     * Handle custom data payloads for specific actions.
     */
    private fun handleDataPayload(data: Map<String, String>) {
        when (data["type"]) {
            "streak_reminder" -> {
                val title = "Keep Your Streak Alive! 🔥"
                val body = "You have a ${data["streak"]} day listening streak. Listen today to keep it going!"
                showNotification(title, body, CHANNEL_ID_STREAKS)
            }
            "milestone" -> {
                val title = "Milestone Achieved! 🎉"
                val body = data["message"] ?: "You've reached a new milestone!"
                showNotification(title, body, CHANNEL_ID_MILESTONES)
            }
            "listening_reminder" -> {
                val bookTitle = data["bookTitle"] ?: "your audiobook"
                val title = "Time to Listen 📚"
                val body = "Continue listening to $bookTitle"
                showNotification(title, body, CHANNEL_ID_REMINDERS)
            }
        }
    }
    
    /**
     * Display a notification with the given title, body, and channel.
     */
    private fun showNotification(title: String, body: String, channelId: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        
        // Create intent to open app when notification is tapped
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Build notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // TODO: Replace with app icon
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .build()
        
        // Generate unique notification ID based on timestamp
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
    }
    
    /**
     * Create notification channels for Android O and above.
     * Each channel allows users to customize notification settings.
     */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            
            // Reminders channel (daily/custom reminders)
            val remindersChannel = NotificationChannel(
                CHANNEL_ID_REMINDERS,
                "Listening Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to continue listening to your audiobooks"
                enableVibration(true)
            }
            
            // Milestones channel (achievements, book completion)
            val milestonesChannel = NotificationChannel(
                CHANNEL_ID_MILESTONES,
                "Milestones & Achievements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for completed books and achievements"
                enableVibration(true)
            }
            
            // Streaks channel (streak maintenance reminders)
            val streaksChannel = NotificationChannel(
                CHANNEL_ID_STREAKS,
                "Streak Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to maintain your listening streak"
                enableVibration(true)
            }
            
            // General channel (miscellaneous notifications)
            val generalChannel = NotificationChannel(
                CHANNEL_ID_GENERAL,
                "General Notifications",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "General app notifications"
            }
            
            notificationManager.createNotificationChannels(listOf(
                remindersChannel,
                milestonesChannel,
                streaksChannel,
                generalChannel
            ))
        }
    }
}

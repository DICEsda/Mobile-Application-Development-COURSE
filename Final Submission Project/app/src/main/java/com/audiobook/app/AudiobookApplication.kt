package com.audiobook.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.audiobook.app.di.AppContainer

/**
 * Application class for the Audiobook Player.
 * Initializes the Manual DI container at application startup.
 */
class AudiobookApplication : Application() {
    
    /**
     * Manual DI container holding application-scoped dependencies.
     * Accessible from any context via (context.applicationContext as AudiobookApplication).container
     */
    lateinit var container: AppContainer
        private set
    
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        
        // Create notification channels at startup
        createNotificationChannels()
        
        // Initialize notification system
        container.notificationTriggerHelper.initialize()
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
                "audiobook_reminders",
                "Listening Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to continue listening to your audiobooks"
                enableVibration(true)
            }
            
            // Milestones channel (achievements, book completion)
            val milestonesChannel = NotificationChannel(
                "audiobook_milestones",
                "Milestones & Achievements",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for completed books and achievements"
                enableVibration(true)
            }
            
            // Streaks channel (streak maintenance reminders)
            val streaksChannel = NotificationChannel(
                "audiobook_streaks",
                "Streak Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Reminders to maintain your listening streak"
                enableVibration(true)
            }
            
            // General channel (miscellaneous notifications)
            val generalChannel = NotificationChannel(
                "audiobook_general",
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

/**
 * Extension property to easily access the DI container from any Context.
 */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as AudiobookApplication).container

package com.audiobook.app

import android.app.Application
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
    }
}

/**
 * Extension property to easily access the DI container from any Context.
 */
val android.content.Context.appContainer: AppContainer
    get() = (applicationContext as AudiobookApplication).container

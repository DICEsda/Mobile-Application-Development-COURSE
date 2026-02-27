package com.audiobook.app.service

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.audiobook.app.MainActivity
import com.audiobook.app.appContainer
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * Media3 MediaSessionService for background audiobook playback.
 * 
 * This service extends MediaSessionService to:
 * - Decouple playback from UI lifecycle (keeps playing when app is backgrounded)
 * - Provide system-level integration (lock screen, Bluetooth, Android Auto)
 * - Handle audio focus (pause on calls, ducking for notifications)
 * - Display media notification with playback controls
 * 
 * Architecture Notes (for academic report):
 * - MediaSessionService is the recommended approach for media apps in Android 13+
 * - ExoPlayer is wrapped by Media3 and handles M4B/AAC decoding natively
 * - Audio focus is handled automatically by ExoPlayer when configured
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
    
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Custom session commands for audiobook-specific actions
    companion object {
        const val ACTION_SKIP_FORWARD = "com.audiobook.app.SKIP_FORWARD"
        const val ACTION_SKIP_BACKWARD = "com.audiobook.app.SKIP_BACKWARD"
        const val SKIP_FORWARD_MS = 30_000L  // 30 seconds forward
        const val SKIP_BACKWARD_MS = 15_000L // 15 seconds backward
        
        val COMMAND_SKIP_FORWARD = SessionCommand(ACTION_SKIP_FORWARD, Bundle.EMPTY)
        val COMMAND_SKIP_BACKWARD = SessionCommand(ACTION_SKIP_BACKWARD, Bundle.EMPTY)
    }
    
    override fun onCreate() {
        super.onCreate()
        initializePlayer()
        initializeMediaSession()
    }
    
    /**
     * Initialize ExoPlayer with audiobook-optimized configuration.
     * 
     * Key configurations:
     * - AudioAttributes: CONTENT_TYPE_SPEECH for better audio focus handling
     * - handleAudioFocus: true - automatic pause on calls, ducking for notifications
     * - Wake lock: prevents CPU sleep during playback
     */
    private fun initializePlayer() {
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH) // Optimized for audiobooks
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true // Automatic audio focus management
            )
            .setHandleAudioBecomingNoisy(true) // Pause when headphones unplugged
            .setWakeMode(C.WAKE_MODE_LOCAL) // Keep CPU awake during playback
            .build()
            .apply {
                // Restore playback speed from preferences
                serviceScope.launch {
                    val savedSpeed = appContainer.preferencesRepository.playbackSpeed.first()
                    setPlaybackSpeed(savedSpeed)
                }
                
                // Add listener for state changes
                addListener(PlayerListener())
            }
    }
    
    /**
     * Initialize MediaSession with custom callbacks for audiobook controls.
     */
    private fun initializeMediaSession() {
        // Intent to launch MainActivity when notification is tapped
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        mediaSession = MediaSession.Builder(this, checkNotNull(player) { "Player failed to initialize in PlaybackService" })
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(MediaSessionCallback())
            .setCustomLayout(buildCustomLayout())
            .build()
    }
    
    /**
     * Build custom notification layout with audiobook-specific actions.
     * Shows: Skip Back 15s | Play/Pause | Skip Forward 30s
     */
    private fun buildCustomLayout(): ImmutableList<CommandButton> {
        return ImmutableList.of(
            CommandButton.Builder()
                .setDisplayName("Rewind 15s")
                .setIconResId(android.R.drawable.ic_media_rew)
                .setSessionCommand(COMMAND_SKIP_BACKWARD)
                .build(),
            CommandButton.Builder()
                .setDisplayName("Forward 30s")
                .setIconResId(android.R.drawable.ic_media_ff)
                .setSessionCommand(COMMAND_SKIP_FORWARD)
                .build()
        )
    }
    
    /**
     * MediaSession callback for handling custom commands and connection requests.
     */
    private inner class MediaSessionCallback : MediaSession.Callback {
        
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            // Allow all controllers to connect and use custom commands
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(COMMAND_SKIP_FORWARD)
                .add(COMMAND_SKIP_BACKWARD)
                .build()
            
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }
        
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                ACTION_SKIP_FORWARD -> {
                    player?.let {
                        it.seekTo(it.currentPosition + SKIP_FORWARD_MS)
                    }
                }
                ACTION_SKIP_BACKWARD -> {
                    player?.let {
                        it.seekTo((it.currentPosition - SKIP_BACKWARD_MS).coerceAtLeast(0))
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
        
        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            // Update custom layout after connection
            session.setCustomLayout(buildCustomLayout())
        }
    }
    
    /**
     * Player listener for handling playback state changes.
     * Saves progress to preferences when playback pauses or stops.
     */
    private inner class PlayerListener : Player.Listener {
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_ENDED -> {
                    saveCurrentProgress()
                }
                Player.STATE_IDLE -> {
                    // Player stopped, save progress
                }
            }
        }
        
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) {
                saveCurrentProgress()
            }
        }
        
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            // Save playback speed preference
            serviceScope.launch {
                appContainer.preferencesRepository.setPlaybackSpeed(playbackParameters.speed)
            }
        }
    }
    
    /**
     * Save current playback position to preferences for seamless resume.
     */
    private fun saveCurrentProgress() {
        val currentPlayer = player ?: return
        val currentItem = currentPlayer.currentMediaItem ?: return
        
        serviceScope.launch {
            appContainer.preferencesRepository.setLastPlayed(
                bookId = currentItem.mediaId,
                positionMs = currentPlayer.currentPosition
            )
        }
    }
    
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }
    
    override fun onTaskRemoved(rootIntent: Intent?) {
        // Save progress before stopping
        saveCurrentProgress()
        
        // Always stop playback and service when user swipes app away
        val player = mediaSession?.player
        player?.let {
            it.playWhenReady = false
            it.stop()
        }
        stopSelf()
    }
    
    override fun onDestroy() {
        // Clean up resources
        serviceScope.cancel()
        
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        
        super.onDestroy()
    }
}

package com.audiobook.app.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.Chapter
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * UI-facing wrapper around MediaController.
 * 
 * This class provides a clean, coroutine-friendly API for the UI layer
 * to interact with the PlaybackService. It exposes playback state as
 * StateFlows for reactive UI updates in Compose.
 * 
 * Design Pattern: Adapter/Facade
 * - Adapts Media3's callback-based API to Kotlin Flow
 * - Provides audiobook-specific methods (seekToChapter, skipForward30s, etc.)
 * - Handles MediaController connection lifecycle
 */
@OptIn(UnstableApi::class)
class AudiobookPlayer(private val context: Context) {
    
    private var controllerFuture: ListenableFuture<MediaController>? = null
    @Volatile
    private var mediaController: MediaController? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Playback state exposed as StateFlows for Compose
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()
    
    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()
    
    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()
    
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()
    
    private val _currentMediaItem = MutableStateFlow<MediaItem?>(null)
    val currentMediaItem: StateFlow<MediaItem?> = _currentMediaItem.asStateFlow()
    
    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private var positionUpdateJob: Job? = null
    
    /**
     * Connect to the PlaybackService's MediaSession.
     * Call this when the UI becomes active.
     */
    fun connect() {
        if (controllerFuture != null) return
        
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener(
            {
                try {
                    mediaController = controllerFuture?.get()
                    mediaController?.addListener(PlayerListener())
                    _isConnected.value = true
                    
                    // Initial state sync
                    syncPlaybackState()
                    startPositionUpdates()
                } catch (e: Exception) {
                    Log.e("AudiobookPlayer", "Failed to connect to MediaController", e)
                    _isConnected.value = false
                }
            },
            context.mainExecutor
        )
    }
    
    /**
     * Disconnect from the MediaSession.
     * Call this when the UI is no longer active.
     */
    fun disconnect() {
        positionUpdateJob?.cancel()
        scope.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
        _isConnected.value = false
    }
    
    /**
     * Start periodic position updates for smooth progress bar.
     */
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (isActive) {
                updatePosition()
                delay(500) // Update every 500ms
            }
        }
    }
    
    private fun updatePosition() {
        mediaController?.let { controller ->
            _currentPosition.value = controller.currentPosition
            _duration.value = controller.duration.coerceAtLeast(0)
            _progress.value = if (_duration.value > 0) {
                _currentPosition.value.toFloat() / _duration.value.toFloat()
            } else 0f
        }
    }
    
    private fun syncPlaybackState() {
        mediaController?.let { controller ->
            _isPlaying.value = controller.isPlaying
            _playbackState.value = controller.playbackState
            _playbackSpeed.value = controller.playbackParameters.speed
            _currentMediaItem.value = controller.currentMediaItem
            updatePosition()
        }
    }
    
    // ============================================================
    // Playback Controls
    // ============================================================
    
    /**
     * Play the current media item.
     */
    fun play() {
        mediaController?.play()
    }
    
    /**
     * Pause playback.
     */
    fun pause() {
        mediaController?.pause()
    }
    
    /**
     * Toggle play/pause.
     */
    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }
    
    /**
     * Seek to a specific position.
     * @param positionMs Position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs)
        _currentPosition.value = positionMs
    }
    
    /**
     * Seek to a progress percentage (0.0 - 1.0).
     */
    fun seekToProgress(progress: Float) {
        val positionMs = (progress * _duration.value).toLong()
        seekTo(positionMs)
    }
    
    /**
     * Skip forward 30 seconds (audiobook standard).
     */
    fun skipForward() {
        mediaController?.let { controller ->
            val newPosition = (controller.currentPosition + SKIP_FORWARD_MS)
                .coerceAtMost(controller.duration)
            controller.seekTo(newPosition)
        }
    }
    
    /**
     * Skip backward 15 seconds (audiobook standard).
     */
    fun skipBackward() {
        mediaController?.let { controller ->
            val newPosition = (controller.currentPosition - SKIP_BACKWARD_MS)
                .coerceAtLeast(0)
            controller.seekTo(newPosition)
        }
    }
    
    /**
     * Set playback speed (0.5x - 2.0x).
     */
    fun setPlaybackSpeed(speed: Float) {
        val clampedSpeed = speed.coerceIn(0.5f, 2.0f)
        mediaController?.setPlaybackSpeed(clampedSpeed)
        _playbackSpeed.value = clampedSpeed
    }
    
    /**
     * Stop playback and release resources.
     */
    fun stop() {
        mediaController?.stop()
    }
    
    // ============================================================
    // Media Loading
    // ============================================================
    
    /**
     * Load and play an audiobook.
     */
    fun playAudiobook(audiobook: Audiobook) {
        val uri = when {
            !audiobook.contentUri.isNullOrBlank() -> Uri.parse(audiobook.contentUri)
            !audiobook.filePath.isNullOrBlank() -> Uri.fromFile(File(audiobook.filePath))
            else -> return
        }
        
        val mediaItem = MediaItem.Builder()
            .setMediaId(audiobook.id)
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(audiobook.title)
                    .setArtist(audiobook.author)
                    .setAlbumTitle(audiobook.title)
                    .setArtworkUri(
                        if (audiobook.coverUrl.isNotBlank()) Uri.parse(audiobook.coverUrl)
                        else null
                    )
                    .build()
            )
            .build()
        
        mediaController?.let { controller ->
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }
    
    /**
     * Load and play from a content URI (e.g., from file picker).
     */
    fun playFromUri(uri: Uri, title: String = "Audiobook", author: String = "Unknown") {
        val mediaItem = MediaItem.Builder()
            .setMediaId(uri.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(author)
                    .build()
            )
            .build()
        
        mediaController?.let { controller ->
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
        }
    }
    
    /**
     * Resume from a saved position.
     */
    fun resumeFromPosition(audiobook: Audiobook, positionMs: Long) {
        playAudiobook(audiobook)
        mediaController?.let { controller ->
            // Use player state listener to seek when ready instead of hardcoded delay
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        controller.seekTo(positionMs)
                        controller.removeListener(this)
                    }
                }
            }
            controller.addListener(listener)
        }
    }
    
    // ============================================================
    // Chapter Navigation
    // ============================================================
    
    /**
     * Seek to a specific chapter.
     */
    fun seekToChapter(chapter: Chapter) {
        seekTo(chapter.startTimeMs)
    }
    
    /**
     * Get the current chapter based on playback position.
     */
    fun getCurrentChapter(chapters: List<Chapter>): Chapter? {
        val position = _currentPosition.value
        return chapters.find { chapter ->
            position >= chapter.startTimeMs && position < chapter.endTimeMs
        }
    }
    
    /**
     * Jump to the next chapter.
     */
    fun nextChapter(chapters: List<Chapter>) {
        val currentChapter = getCurrentChapter(chapters) ?: return
        val nextChapter = chapters.find { it.number == currentChapter.number + 1 }
        nextChapter?.let { seekToChapter(it) }
    }
    
    /**
     * Jump to the previous chapter.
     */
    fun previousChapter(chapters: List<Chapter>) {
        val currentChapter = getCurrentChapter(chapters) ?: return
        val prevChapter = chapters.find { it.number == currentChapter.number - 1 }
        prevChapter?.let { seekToChapter(it) }
    }
    
    // ============================================================
    // Utility Methods
    // ============================================================
    
    /**
     * Format duration in milliseconds to "HH:MM:SS" or "MM:SS".
     */
    fun formatTime(timeMs: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(timeMs)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }
    
    /**
     * Calculate remaining time.
     */
    fun getRemainingTime(): String {
        val remaining = _duration.value - _currentPosition.value
        return formatTime(remaining.coerceAtLeast(0))
    }
    
    // ============================================================
    // Player Listener
    // ============================================================
    
    private inner class PlayerListener : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
        }
        
        override fun onPlaybackStateChanged(playbackState: Int) {
            _playbackState.value = playbackState
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _currentMediaItem.value = mediaItem
        }
        
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _playbackSpeed.value = playbackParameters.speed
        }
    }
    
    companion object {
        private const val SKIP_FORWARD_MS = 30_000L  // 30 seconds
        private const val SKIP_BACKWARD_MS = 15_000L // 15 seconds
    }
}

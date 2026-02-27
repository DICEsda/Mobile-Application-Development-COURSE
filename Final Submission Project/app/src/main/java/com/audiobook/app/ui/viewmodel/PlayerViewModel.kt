package com.audiobook.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.Chapter
import com.audiobook.app.data.parser.ChapterParser
import com.audiobook.app.data.repository.AudiobookRepository
import com.audiobook.app.data.repository.PreferencesRepository
import com.audiobook.app.service.AudiobookPlayer
import com.audiobook.app.service.NotificationTriggerHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the PlayerScreen.
 * 
 * Manages playback state and provides a clean interface between
 * the UI layer and the AudiobookPlayer/PlaybackService.
 */
class PlayerViewModel(
    private val audiobookPlayer: AudiobookPlayer,
    private val audiobookRepository: AudiobookRepository,
    private val preferencesRepository: PreferencesRepository,
    private val chapterParser: ChapterParser,
    private val notificationTriggerHelper: NotificationTriggerHelper
) : ViewModel() {
    
    // Current audiobook being played
    private val _currentBook = MutableStateFlow<Audiobook?>(null)
    val currentBook: StateFlow<Audiobook?> = _currentBook.asStateFlow()
    
    // Chapters for the current book
    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters.asStateFlow()
    
    // Current chapter (calculated from position)
    private val _currentChapter = MutableStateFlow<Chapter?>(null)
    val currentChapter: StateFlow<Chapter?> = _currentChapter.asStateFlow()
    
    // Sleep timer state
    private val _sleepTimerMinutes = MutableStateFlow<Int?>(null)
    val sleepTimerMinutes: StateFlow<Int?> = _sleepTimerMinutes.asStateFlow()
    
    // Remaining seconds for the active countdown (null = no timer)
    private val _sleepTimerRemainingSeconds = MutableStateFlow<Long?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Long?> = _sleepTimerRemainingSeconds.asStateFlow()
    
    private var sleepTimerJob: Job? = null
    
    // Error state for player failures
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // Expose player states directly
    val isPlaying: StateFlow<Boolean> = audiobookPlayer.isPlaying
    val progress: StateFlow<Float> = audiobookPlayer.progress
    val currentPosition: StateFlow<Long> = audiobookPlayer.currentPosition
    val duration: StateFlow<Long> = audiobookPlayer.duration
    val playbackSpeed: StateFlow<Float> = audiobookPlayer.playbackSpeed
    val isConnected: StateFlow<Boolean> = audiobookPlayer.isConnected
    
    // Formatted time strings for UI
    val currentTimeFormatted: StateFlow<String> = currentPosition.map { 
        audiobookPlayer.formatTime(it) 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0:00")
    
    val durationFormatted: StateFlow<String> = duration.map { 
        audiobookPlayer.formatTime(it) 
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0:00")
    
    val remainingTimeFormatted: StateFlow<String> = combine(currentPosition, duration) { pos, dur ->
        audiobookPlayer.formatTime((dur - pos).coerceAtLeast(0))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0:00")
    
    /**
     * Chapter-relative progress (0.0 - 1.0) for the current chapter.
     * Falls back to overall progress if no chapters are available.
     */
    val chapterProgress: StateFlow<Float> = combine(currentPosition, _currentChapter) { pos, chapter ->
        if (chapter != null && chapter.endTimeMs > chapter.startTimeMs) {
            val chapterDuration = chapter.endTimeMs - chapter.startTimeMs
            val positionInChapter = pos - chapter.startTimeMs
            (positionInChapter.toFloat() / chapterDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            // Fallback to overall progress
            progress.value
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)
    
    /**
     * Formatted time within the current chapter.
     */
    val chapterTimeFormatted: StateFlow<String> = combine(currentPosition, _currentChapter) { pos, chapter ->
        if (chapter != null) {
            val posInChapter = (pos - chapter.startTimeMs).coerceAtLeast(0)
            audiobookPlayer.formatTime(posInChapter)
        } else {
            audiobookPlayer.formatTime(pos)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0:00")
    
    /**
     * Formatted duration of the current chapter.
     */
    val chapterDurationFormatted: StateFlow<String> = _currentChapter.map { chapter ->
        if (chapter != null && chapter.endTimeMs > chapter.startTimeMs) {
            audiobookPlayer.formatTime(chapter.endTimeMs - chapter.startTimeMs)
        } else {
            audiobookPlayer.formatTime(duration.value)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "0:00")
    
    /**
     * Current chapter title for display.
     */
    val currentChapterTitle: StateFlow<String?> = _currentChapter.map { it?.title }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    
    init {
        audiobookPlayer.connect()
        observeCurrentChapter()
        restoreLastPlayedBook()
    }
    
    /**
     * Track which chapter is currently playing based on playback position.
     */
    private fun observeCurrentChapter() {
        viewModelScope.launch {
            combine(currentPosition, _chapters) { position, chapters ->
                chapters.find { chapter ->
                    position >= chapter.startTimeMs &&
                    (chapter.endTimeMs == 0L || position < chapter.endTimeMs)
                }
            }.collect { chapter ->
                _currentChapter.value = chapter
            }
        }
        
        // Save progress periodically during playback
        viewModelScope.launch {
            combine(
                currentPosition,
                duration,
                _currentBook,
                _currentChapter
            ) { position, dur, book, chapter ->
                if (book != null && dur > 0) {
                    val progressValue = (position.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                    Triple(book.id, progressValue, chapter?.number ?: 0)
                } else {
                    null
                }
            }
            .filterNotNull()
            .debounce(500) // Save every 500ms for instant updates
            .collect { (bookId, progressValue, chapterNum) ->
                audiobookRepository.updateProgress(
                    bookId = bookId,
                    progress = progressValue,
                    currentChapter = chapterNum,
                    positionMs = currentPosition.value
                )
                
                // Trigger book completion notification when reaching 98%+ (close to end)
                if (progressValue >= 0.98f && _currentBook.value != null) {
                    val book = _currentBook.value!!
                    notificationTriggerHelper.onBookCompleted(book.title)
                }
            }
        }
        
        // Track listening session time
        var lastPlaybackTime = System.currentTimeMillis()
        viewModelScope.launch {
            isPlaying.collect { playing ->
                if (playing) {
                    lastPlaybackTime = System.currentTimeMillis()
                } else {
                    // When pausing, calculate session duration
                    val sessionDurationMs = System.currentTimeMillis() - lastPlaybackTime
                    val sessionMinutes = (sessionDurationMs / (1000 * 60)).toInt()
                    
                    // Only count sessions longer than 1 minute
                    if (sessionMinutes >= 1) {
                        notificationTriggerHelper.onListeningSessionCompleted(sessionMinutes)
                    }
                }
            }
        }
    }
    
    /**
     * Restore the last played book from preferences so the player screen
     * shows meaningful state immediately.
     */
    private fun restoreLastPlayedBook() {
        viewModelScope.launch {
            preferencesRepository.lastPlayedBookId.collect { bookId ->
                if (bookId != null && _currentBook.value == null) {
                    val book = audiobookRepository.getAudiobook(bookId)
                    if (book != null) {
                        _currentBook.value = book
                        _chapters.value = book.chapters
                    }
                }
            }
        }
    }
    
    /**
     * Load a specific audiobook for playback.
     * Parses chapters from the M4B file if not already cached.
     */
    fun loadBook(bookId: String) {
        viewModelScope.launch {
            val book = audiobookRepository.getAudiobook(bookId)
            if (book != null) {
                _currentBook.value = book
                
                // Parse chapters from the M4B file
                val chapters = if (book.chapters.isEmpty()) {
                    try {
                        val uri = book.resolveUri()
                        if (uri != null) {
                            val metadata = chapterParser.parseM4bFile(uri)
                            // Update repository with parsed chapters if any were found
                            if (metadata.chapters.isNotEmpty()) {
                                audiobookRepository.updateChapters(bookId, metadata.chapters)
                            }
                            metadata.chapters
                        } else {
                            emptyList()
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerViewModel", "Failed to parse chapters", e)
                        emptyList()
                    }
                } else {
                    book.chapters
                }
                
                _chapters.value = chapters
                
                // Start playback, resuming from saved position if available
                try {
                    val savedPosition = audiobookRepository.getPlaybackPosition(bookId)
                    if (savedPosition > 0) {
                        audiobookPlayer.resumeFromPosition(book, savedPosition)
                    } else {
                        audiobookPlayer.playAudiobook(book)
                    }
                    audiobookRepository.setCurrentBook(book)
                    _error.value = null
                } catch (e: Exception) {
                    _error.value = "Failed to play audiobook: ${e.localizedMessage ?: "Unknown error"}"
                }
            } else {
                _error.value = "Audiobook not found"
            }
            // If book not found, currentBook remains null and UI should handle this gracefully
        }
    }
    
    /**
     * Clear the current error state.
     */
    fun clearError() {
        _error.value = null
    }
    
    /**
     * Start playing the current book.
     */
    fun play() {
        _currentBook.value?.let { book ->
            audiobookPlayer.playAudiobook(book)
        } ?: audiobookPlayer.play()
    }
    
    /**
     * Pause playback.
     */
    fun pause() {
        audiobookPlayer.pause()
    }
    
    /**
     * Toggle play/pause.
     */
    fun togglePlayPause() {
        audiobookPlayer.togglePlayPause()
    }
    
    /**
     * Seek to a specific progress (0.0 - 1.0).
     */
    fun seekToProgress(progress: Float) {
        audiobookPlayer.seekToProgress(progress)
    }
    
    /**
     * Seek to a specific progress within the current chapter (0.0 - 1.0).
     * Falls back to overall seek if no chapter is active.
     */
    fun seekToChapterProgress(chapterProg: Float) {
        val chapter = _currentChapter.value
        if (chapter != null && chapter.endTimeMs > chapter.startTimeMs) {
            val chapterDuration = chapter.endTimeMs - chapter.startTimeMs
            val targetPosition = chapter.startTimeMs + (chapterDuration * chapterProg).toLong()
            audiobookPlayer.seekTo(targetPosition)
        } else {
            audiobookPlayer.seekToProgress(chapterProg)
        }
    }
    
    /**
     * Skip forward 30 seconds.
     */
    fun skipForward() {
        audiobookPlayer.skipForward()
    }
    
    /**
     * Skip backward 15 seconds.
     */
    fun skipBackward() {
        audiobookPlayer.skipBackward()
    }
    
    /**
     * Set playback speed.
     */
    fun setPlaybackSpeed(speed: Float) {
        audiobookPlayer.setPlaybackSpeed(speed)
        viewModelScope.launch {
            preferencesRepository.setPlaybackSpeed(speed)
        }
    }
    
    /**
     * Seek to a specific chapter.
     */
    fun seekToChapter(chapter: Chapter) {
        audiobookPlayer.seekToChapter(chapter)
    }
    
    /**
     * Set sleep timer.
     * @param minutes Positive value = countdown minutes, -1 = end of chapter, null = cancel.
     */
    fun setSleepTimer(minutes: Int?) {
        _sleepTimerMinutes.value = minutes
        viewModelScope.launch {
            preferencesRepository.setSleepTimerMinutes(minutes ?: 0)
        }
        
        // Cancel any existing timer
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerRemainingSeconds.value = null
        
        if (minutes == null) return
        
        if (minutes == -1) {
            // End-of-chapter mode: watch for chapter changes and pause when the current one ends
            val startChapter = _currentChapter.value
            sleepTimerJob = viewModelScope.launch {
                _currentChapter
                    .filter { it != null && startChapter != null && it.number != startChapter.number }
                    .first()
                // Chapter changed — pause playback
                audiobookPlayer.pause()
                _sleepTimerMinutes.value = null
                _sleepTimerRemainingSeconds.value = null
            }
        } else if (minutes > 0) {
            // Timed countdown
            val totalSeconds = minutes * 60L
            _sleepTimerRemainingSeconds.value = totalSeconds
            sleepTimerJob = viewModelScope.launch {
                var remaining = totalSeconds
                while (remaining > 0) {
                    delay(1000L)
                    remaining--
                    _sleepTimerRemainingSeconds.value = remaining
                    // Update the displayed minutes (rounded up)
                    val displayMinutes = ((remaining + 59) / 60).toInt()
                    _sleepTimerMinutes.value = displayMinutes
                }
                // Timer expired — pause playback
                audiobookPlayer.pause()
                _sleepTimerMinutes.value = null
                _sleepTimerRemainingSeconds.value = null
            }
        }
    }
    
    /**
     * Update chapters with current playback state.
     */
    fun getChaptersWithPlayingState(): List<Chapter> {
        val currentPos = currentPosition.value
        val currentChapterNum = _currentChapter.value?.number
        
        return _chapters.value.map { chapter ->
            val isPlaying = chapter.number == currentChapterNum
            val chapterProgress = if (isPlaying && chapter.endTimeMs > chapter.startTimeMs) {
                val chapterDuration = chapter.endTimeMs - chapter.startTimeMs
                val positionInChapter = currentPos - chapter.startTimeMs
                (positionInChapter.toFloat() / chapterDuration.toFloat()).coerceIn(0f, 1f)
            } else if (chapter.endTimeMs > 0 && currentPos >= chapter.endTimeMs) {
                1f // Completed chapter
            } else {
                0f
            }
            
            chapter.copy(
                isPlaying = isPlaying,
                progress = chapterProgress
            )
        }
    }
    
    /**
     * Rename a chapter title. Persists the change to the database.
     */
    fun renameChapter(chapterNumber: Int, newTitle: String) {
        val updated = _chapters.value.map { ch ->
            if (ch.number == chapterNumber) ch.copy(title = newTitle) else ch
        }
        _chapters.value = updated
        val bookId = _currentBook.value?.id ?: return
        viewModelScope.launch {
            audiobookRepository.updateChapters(bookId, updated)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        // Don't disconnect - let service continue playing
        // audiobookPlayer.disconnect()
    }
    
    /**
     * Factory for creating PlayerViewModel with dependencies.
     */
    class Factory(
        private val audiobookPlayer: AudiobookPlayer,
        private val audiobookRepository: AudiobookRepository,
        private val preferencesRepository: PreferencesRepository,
        private val chapterParser: ChapterParser,
        private val notificationTriggerHelper: NotificationTriggerHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
                return PlayerViewModel(
                    audiobookPlayer,
                    audiobookRepository,
                    preferencesRepository,
                    chapterParser,
                    notificationTriggerHelper
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}

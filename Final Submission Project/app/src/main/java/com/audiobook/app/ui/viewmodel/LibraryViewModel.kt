package com.audiobook.app.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.repository.AudiobookRepository
import com.audiobook.app.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * ViewModel for the Library screen.
 * Manages audiobook library state and user interactions.
 */
class LibraryViewModel(
    private val audiobookRepository: AudiobookRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {
    
    // ========== UI State ==========
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    
    // ========== Data Flows ==========
    
    /**
     * All audiobooks in the library.
     * Automatically updates when database changes.
     */
    val audiobooks: StateFlow<List<Audiobook>> = audiobookRepository.audiobooksFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    
    /**
     * Currently playing audiobook.
     */
    val currentBook: StateFlow<Audiobook?> = audiobookRepository.currentBookFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    
    /**
     * Filtered audiobooks based on search query.
     */
    val filteredAudiobooks: StateFlow<List<Audiobook>> = combine(
        audiobooks,
        searchQuery
    ) { books, query ->
        if (query.isBlank()) {
            books
        } else {
            books.filter {
                it.title.contains(query, ignoreCase = true) ||
                it.author.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )
    
    /**
     * Total count of audiobooks.
     */
    val audiobookCount: StateFlow<Int> = audiobooks.map { it.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = 0
        )
    
    /**
     * Time remaining for current book (formatted string).
     */
    val currentBookTimeRemaining: StateFlow<String> = currentBook.map { book ->
        book?.let {
            val totalMinutes = it.totalDurationMinutes
            val progressMinutes = (totalMinutes * it.progress).toInt()
            val remainingMinutes = totalMinutes - progressMinutes
            val hours = remainingMinutes / 60
            val minutes = remainingMinutes % 60
            if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
        } ?: ""
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ""
    )
    
    // ========== Initialization ==========
    
    init {
        loadLibrary()
    }
    
    /**
     * Load the audiobook library from database.
     */
    fun loadLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                audiobookRepository.loadAudiobooks()
            } catch (e: Exception) {
                _error.value = "Failed to load library: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Refresh library by scanning the Audiobooks folder for new files.
     */
    fun refreshLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                audiobookRepository.scanAudiobooksFolder()
            } catch (e: Exception) {
                _error.value = "Failed to scan library: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    // ========== User Actions ==========
    
    /**
     * Update search query.
     */
    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }
    
    /**
     * Clear search query.
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }
    
    /**
     * Select a book to play.
     */
    fun selectBook(audiobook: Audiobook) {
        viewModelScope.launch {
            audiobookRepository.setCurrentBook(audiobook)
        }
    }
    
    /**
     * Get audiobook by ID.
     */
    suspend fun getAudiobook(id: String): Audiobook? {
        return audiobookRepository.getAudiobook(id)
    }
    
    /**
     * Add audiobook from URI (file picker).
     */
    fun addAudiobookFromUri(uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val audiobook = audiobookRepository.addFromUri(uri)
                if (audiobook != null) {
                    // Try to enrich metadata from OpenLibrary
                    audiobookRepository.enrichMetadata(audiobook.id)
                }
            } catch (e: Exception) {
                _error.value = "Failed to add audiobook: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Delete audiobook from library.
     */
    fun deleteAudiobook(bookId: String) {
        viewModelScope.launch {
            try {
                audiobookRepository.deleteAudiobook(bookId)
            } catch (e: Exception) {
                _error.value = "Failed to delete audiobook: ${e.message}"
            }
        }
    }
    
    /**
     * Set custom audiobook folder.
     */
    fun setAudiobookFolder(folderPath: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                audiobookRepository.setAudiobookFolder(folderPath)
            } catch (e: Exception) {
                _error.value = "Failed to set folder: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
    
    /**
     * Clear error message.
     */
    fun clearError() {
        _error.value = null
    }
    
    // ========== Factory ==========
    
    /**
     * Factory for creating LibraryViewModel with dependencies.
     */
    class Factory(
        private val audiobookRepository: AudiobookRepository,
        private val preferencesRepository: PreferencesRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LibraryViewModel::class.java)) {
                return LibraryViewModel(
                    audiobookRepository = audiobookRepository,
                    preferencesRepository = preferencesRepository
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

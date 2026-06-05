package com.audiobook.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.remote.llm.ChatMessage
import com.audiobook.app.data.repository.BookCompanionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * State for the Book Companion chat screen.
 *
 * [messages] is the display history (user/assistant turns the user sees) — it
 * deliberately excludes the grounding preamble, which the repository injects on
 * the wire only.
 */
data class BookCompanionUiState(
    val book: Audiobook? = null,
    val messages: List<ChatMessage> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for the Book Companion chat. Owns the conversation history and
 * mediates between the UI and [BookCompanionRepository].
 */
class BookCompanionViewModel(
    private val repository: BookCompanionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookCompanionUiState())
    val uiState: StateFlow<BookCompanionUiState> = _uiState.asStateFlow()

    /** Bind the chat to a book. Safe to call again with the same book (no-op reset). */
    fun start(book: Audiobook) {
        if (_uiState.value.book?.id == book.id) return
        _uiState.value = BookCompanionUiState(
            book = book,
            suggestions = repository.suggestedQuestions(book)
        )
    }

    /** Send a user question and append the assistant's reply. */
    fun send(question: String) {
        val text = question.trim()
        val book = _uiState.value.book ?: return
        if (text.isEmpty() || _uiState.value.isLoading) return

        // History sent to the repository excludes the message we're adding now.
        val history = _uiState.value.messages

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage.user(text),
                isLoading = true,
                error = null
            )
        }

        viewModelScope.launch {
            val result = repository.ask(book, history, text)
            _uiState.update { state ->
                result.fold(
                    onSuccess = { reply ->
                        state.copy(
                            messages = state.messages + ChatMessage.assistant(reply),
                            isLoading = false
                        )
                    },
                    onFailure = { e ->
                        state.copy(
                            isLoading = false,
                            error = friendlyError(e)
                        )
                    }
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun friendlyError(e: Throwable): String {
        val detail = e.message?.takeIf { it.isNotBlank() }
        return "Couldn't reach the AI model. Make sure LM Studio is running and the " +
            "server address in Settings is correct." + (detail?.let { "\n\n($it)" } ?: "")
    }

    /**
     * Factory for creating [BookCompanionViewModel] with its dependency,
     * matching the manual-DI pattern used by the other ViewModels.
     */
    class Factory(
        private val repository: BookCompanionRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(BookCompanionViewModel::class.java)) {
                return BookCompanionViewModel(repository) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}

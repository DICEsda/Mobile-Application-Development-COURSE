package com.example.randomdog.ui-layer.mvi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============================================================================
// Reusable MVI Base: Generic <State, Intent, Effect>
// Enforces unidirectional data flow: state DOWN, intents UP
// ============================================================================

abstract class MviViewModel<S, I, E>(initialState: S) : ViewModel() {
    // <S> = State type (e.g., HomeUiState)
    // <I> = Intent type (e.g., HomeIntent)
    // <E> = Effect type (e.g., HomeEffect)

    // ========== STATE: Single source of truth ==========
    private val _state = MutableStateFlow(initialState)  // Mutable internally
    val state: StateFlow<S> = _state.asStateFlow()  // Exposed as immutable StateFlow

    // ========== INTENT: Only entry point ==========
    abstract fun onIntent(intent: I)  // Subclasses dispatch intents to private functions

    // ========== EFFECT: One-off events ==========
    private val _effects = Channel<E>(Channel.BUFFERED)  // Buffered: sender doesn't wait
    val effects: Flow<E> = _effects.receiveAsFlow()  // Exposed as Flow for collection

    // ========== HELPERS FOR SUBCLASSES ==========
    protected val currentState: S get() = _state.value  // Read current state

    protected fun reduce(block: (S) -> S) = _state.update(block)
    // ONLY way to mutate state (enforces immutability pattern)
    // block: (oldState) -> newState via immutable copy
    // Example: reduce { it.copy(isLoading = true) }

    protected fun sendEffect(effect: E) {
        viewModelScope.launch { _effects.send(effect) }  // Queue one-off event (non-blocking)
    }
}

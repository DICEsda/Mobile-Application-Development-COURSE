package com.example.randomdog.ui-layer.home

import com.example.randomdog.domain-layer.model.Dog

// ============================================================================
// MVI CONTRACT: Three components define the UI contract
// ============================================================================

// Immutable state — single source of truth for rendering
data class HomeUiState(
    val isLoading: Boolean = false,  // Fetch in progress? Show spinner
    val dog: Dog? = null,  // Current dog (null = not loaded yet)
    val isFavourite: Boolean = false,  // Is dog in favourites? (heart icon filled or outline)
    val errorMessage: String? = null,  // Error message (null = no error)
) // State changes ONLY via reduce { } in ViewModel; never directly mutated

// Only ways to interact — UI sends intents UP, ViewModel receives DOWN
sealed interface HomeIntent {
    data object LoadNewDog : HomeIntent  // User tapped "New dog" button
    data object ToggleFavourite : HomeIntent  // User tapped heart icon
    data object Retry : HomeIntent  // User tapped retry on error
} // Sealed = exhaustive pattern matching in reducer

// One-off events (not state) — toasts, snackbars, transient messages
sealed interface HomeEffect {
    data class ShowMessage(val text: String) : HomeEffect  // Show snackbar text
} // Effects are emitted once, consumed, discarded (not persistent like state)

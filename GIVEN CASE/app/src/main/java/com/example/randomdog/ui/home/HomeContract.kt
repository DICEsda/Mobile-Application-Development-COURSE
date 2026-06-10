package com.example.randomdog.ui.home

import com.example.randomdog.domain.model.Dog

data class HomeUiState(
    val isLoading: Boolean = false,
    val dog: Dog? = null,
    val isFavourite: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface HomeIntent {
    data object LoadNewDog : HomeIntent
    data object ToggleFavourite : HomeIntent
    data object Retry : HomeIntent
}

sealed interface HomeEffect {
    data class ShowMessage(val text: String) : HomeEffect
}

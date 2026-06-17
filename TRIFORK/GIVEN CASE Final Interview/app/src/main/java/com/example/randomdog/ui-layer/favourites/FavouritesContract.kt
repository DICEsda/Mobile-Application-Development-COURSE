package com.example.randomdog.ui-layer.favourites

import com.example.randomdog.domain-layer.model.Dog

data class FavouritesUiState(
    val isLoading: Boolean = true,
    val favourites: List<Dog> = emptyList(),
)

sealed interface FavouritesIntent {
    data class RemoveFavourite(val dog: Dog) : FavouritesIntent
}

sealed interface FavouritesEffect {
    data class ShowMessage(val text: String) : FavouritesEffect
}

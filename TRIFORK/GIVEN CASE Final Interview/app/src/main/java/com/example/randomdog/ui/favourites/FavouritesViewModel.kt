package com.example.randomdog.ui.favourites

import androidx.lifecycle.viewModelScope
import com.example.randomdog.domain.usecase.GetFavouriteDogsUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
import com.example.randomdog.ui.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    getFavourites: GetFavouriteDogsUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
) : MviViewModel<FavouritesUiState, FavouritesIntent, FavouritesEffect>(FavouritesUiState()) {

    init {
        viewModelScope.launch {
            getFavourites().collect { dogs ->
                reduce { it.copy(isLoading = false, favourites = dogs) }
            }
        }
    }

    override fun onIntent(intent: FavouritesIntent) {
        when (intent) {
            is FavouritesIntent.RemoveFavourite ->
                viewModelScope.launch {
                    toggleFavourite(intent.dog)
                    sendEffect(FavouritesEffect.ShowMessage("Removed from favourites"))
                }
        }
    }
}

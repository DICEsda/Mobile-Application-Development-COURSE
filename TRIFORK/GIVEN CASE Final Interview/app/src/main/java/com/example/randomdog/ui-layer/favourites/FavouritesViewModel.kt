package com.example.randomdog.ui-layer.favourites

import androidx.lifecycle.viewModelScope
import com.example.randomdog.domain-layer.usecase.GetFavouriteDogsUseCase
import com.example.randomdog.domain-layer.usecase.ToggleFavouriteUseCase
import com.example.randomdog.ui-layer.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    getFavourites: GetFavouriteDogsUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
) : MviViewModel<FavouritesUiState, FavouritesIntent, FavouritesEffect>(FavouritesUiState()) {

    init {
        // Subscribe to Room's reactive list on init
        viewModelScope.launch {
            getFavourites()
                .collect { dogs ->
                    // Each Room emission: dogs added/removed → reduce to state → grid recomposes
                    reduce { it.copy(isLoading = false, favourites = dogs) }
                }
        }
    }

    override fun onIntent(intent: FavouritesIntent) {
        when (intent) {
            is FavouritesIntent.RemoveFavourite ->
                viewModelScope.launch {
                    // Room delete completes → Room emits new list → this collect fires → reduces new state
                    toggleFavourite(intent.dog)
                    sendEffect(FavouritesEffect.ShowMessage("Removed from favourites"))
                }
        }
    }
}

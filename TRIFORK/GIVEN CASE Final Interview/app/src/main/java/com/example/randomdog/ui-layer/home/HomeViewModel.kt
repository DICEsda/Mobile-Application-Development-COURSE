package com.example.randomdog.ui-layer.home

import androidx.lifecycle.viewModelScope
import com.example.randomdog.domain-layer.usecase.GetRandomDogUseCase
import com.example.randomdog.domain-layer.usecase.ObserveIsFavouriteUseCase
import com.example.randomdog.domain-layer.usecase.ToggleFavouriteUseCase
import com.example.randomdog.ui-layer.mvi.MviViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val getRandomDog: GetRandomDogUseCase,
    private val toggleFavourite: ToggleFavouriteUseCase,
    private val observeIsFavourite: ObserveIsFavouriteUseCase,
) : MviViewModel<HomeUiState, HomeIntent, HomeEffect>(HomeUiState()) {

    private var favouriteJob: Job? = null

    init { loadNewDog() }

    override fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.LoadNewDog -> loadNewDog()
            HomeIntent.Retry -> loadNewDog()
            HomeIntent.ToggleFavourite -> toggleCurrent()
        }
    }

    private fun loadNewDog() {
        // Optimistic UI: show loading immediately
        reduce { it.copy(isLoading = true, errorMessage = null) }

        // Launch coroutine on IO dispatcher (network call pauses, main thread continues)
        viewModelScope.launch {
            getRandomDog()
                .onSuccess { dog ->
                    // Network succeeded: emit state, then start reactive favourite tracking
                    reduce { it.copy(isLoading = false, dog = dog) }
                    observeFavouriteFor(dog.imageUrl)
                }
                .onFailure { error ->
                    // Error becomes state, snackbar is one-off effect
                    reduce { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                    sendEffect(HomeEffect.ShowMessage(error.toUserMessage()))
                }
        }
    }

    private fun observeFavouriteFor(imageUrl: String) {
        // Cancel previous favourite subscription when switching dogs
        favouriteJob?.cancel()

        // Subscribe to Room's reactive query: is this URL favourited?
        favouriteJob = viewModelScope.launch {
            observeIsFavourite(imageUrl)
                .collectLatest { fav ->
                    // New favourite status → reduce to state → heart icon updates
                    reduce { it.copy(isFavourite = fav) }
                }
        }
    }

    private fun toggleCurrent() {
        val dog = currentState.dog ?: return

        // Fire-and-forget: Room insert/delete triggers observeAll() in other listeners
        viewModelScope.launch {
            toggleFavourite(dog)
        }
    }

    private fun Throwable.toUserMessage(): String =
        "Couldn't fetch a dog. Check your connection and try again."
}

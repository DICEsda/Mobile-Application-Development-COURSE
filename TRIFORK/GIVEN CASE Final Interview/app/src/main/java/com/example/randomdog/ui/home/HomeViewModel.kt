package com.example.randomdog.ui.home

import androidx.lifecycle.viewModelScope
import com.example.randomdog.domain.usecase.GetRandomDogUseCase
import com.example.randomdog.domain.usecase.ObserveIsFavouriteUseCase
import com.example.randomdog.domain.usecase.ToggleFavouriteUseCase
import com.example.randomdog.ui.mvi.MviViewModel
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
        reduce { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            getRandomDog()
                .onSuccess { dog ->
                    reduce { it.copy(isLoading = false, dog = dog) }
                    observeFavouriteFor(dog.imageUrl)
                }
                .onFailure { error ->
                    reduce { it.copy(isLoading = false, errorMessage = error.toUserMessage()) }
                    sendEffect(HomeEffect.ShowMessage(error.toUserMessage()))
                }
        }
    }

    private fun observeFavouriteFor(imageUrl: String) {
        favouriteJob?.cancel()
        favouriteJob = viewModelScope.launch {
            observeIsFavourite(imageUrl).collectLatest { fav ->
                reduce { it.copy(isFavourite = fav) }
            }
        }
    }

    private fun toggleCurrent() {
        val dog = currentState.dog ?: return
        viewModelScope.launch { toggleFavourite(dog) }
    }

    private fun Throwable.toUserMessage(): String =
        "Couldn't fetch a dog. Check your connection and try again."
}

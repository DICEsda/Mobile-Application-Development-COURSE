package com.example.randomdog.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.randomdog.ui.components.DogImage
import com.example.randomdog.ui.components.ErrorState
import com.example.randomdog.ui.components.LoadingState
import com.example.randomdog.ui.theme.DogButton

/** Stateful entry point: owns the ViewModel, collects state + one-off effects. */
@Composable
fun HomeScreen(
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is HomeEffect.ShowMessage -> onShowMessage(effect.text)
                }
            }
        }
    }

    HomeScreenContent(
        state = state,
        onNewDog = { viewModel.onIntent(HomeIntent.LoadNewDog) },
        onToggleFavourite = { viewModel.onIntent(HomeIntent.ToggleFavourite) },
        onRetry = { viewModel.onIntent(HomeIntent.Retry) },
        modifier = modifier,
    )
}

/** Stateless content: a pure function of [HomeUiState] — trivially testable and previewable. */
@Composable
fun HomeScreenContent(
    state: HomeUiState,
    onNewDog: () -> Unit,
    onToggleFavourite: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading && state.dog == null -> LoadingState(modifier)
        state.errorMessage != null && state.dog == null ->
            ErrorState(state.errorMessage, onRetry = onRetry, modifier = modifier)
        // On refresh failure with a dog already shown, the last image is kept and the error surfaces via the snackbar effect (intentional UX).
        else -> HomeContent(
            state = state,
            onNewDog = onNewDog,
            onToggleFavourite = onToggleFavourite,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onNewDog: () -> Unit,
    onToggleFavourite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        state.dog?.let { dog ->
            DogImage(url = dog.imageUrl, contentDescription = dog.breed ?: "A random dog")
            dog.breed?.let { Text(it) }
            IconButton(onClick = onToggleFavourite) {
                Icon(
                    imageVector = if (state.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (state.isFavourite) "Remove from favourites" else "Add to favourites",
                )
            }
        }
        DogButton(onClick = onNewDog, text = "New dog", leadingIcon = Icons.Filled.Refresh)
    }
}

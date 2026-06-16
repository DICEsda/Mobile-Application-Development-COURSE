package com.example.randomdog.ui.favourites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.example.randomdog.domain.model.Dog
import com.example.randomdog.ui.components.DogImage
import com.example.randomdog.ui.components.LoadingState

/** Stateful entry point: owns the ViewModel, collects state + one-off effects. */
@Composable
fun FavouritesScreen(
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FavouritesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.effects.collect { effect ->
                when (effect) {
                    is FavouritesEffect.ShowMessage -> onShowMessage(effect.text)
                }
            }
        }
    }

    FavouritesScreenContent(
        state = state,
        onRemove = { dog -> viewModel.onIntent(FavouritesIntent.RemoveFavourite(dog)) },
        modifier = modifier,
    )
}

/** Stateless content: a pure function of [FavouritesUiState] — trivially testable and previewable. */
@Composable
fun FavouritesScreenContent(
    state: FavouritesUiState,
    onRemove: (Dog) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoading -> LoadingState(modifier)
        state.favourites.isEmpty() -> Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { Text("No favourites yet — tap the heart on a dog.") }
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier.fillMaxSize().padding(12.dp),
        ) {
            items(state.favourites, key = { it.imageUrl }) { dog ->
                Box {
                    DogImage(
                        url = dog.imageUrl,
                        contentDescription = dog.breed ?: "A favourite dog",
                        modifier = Modifier.padding(4.dp),
                    )
                    IconButton(
                        onClick = { onRemove(dog) },
                        modifier = Modifier.align(Alignment.TopEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove from favourites",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    }
}

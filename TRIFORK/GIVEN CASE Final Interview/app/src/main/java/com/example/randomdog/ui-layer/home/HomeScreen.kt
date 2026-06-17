package com.example.randomdog.ui-layer.home

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
import com.example.randomdog.ui-layer.components.DogImage
import com.example.randomdog.ui-layer.components.ErrorState
import com.example.randomdog.ui-layer.components.LoadingState
import com.example.randomdog.ui-layer.theme.DogButton

// ============================================================================
// Stateful Wrapper: Owns ViewModel, collects state & effects
// Handles lifecycle, bubbles effects to parent
// ============================================================================

@Composable
fun HomeScreen(
    onShowMessage: (String) -> Unit,  // Callback to parent (NavGraph) for snackbar
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),  // Hilt lazy injection (survives rotation)
) {
    // ========== Collect State (lifecycle-aware) ==========
    val state by viewModel.state.collectAsStateWithLifecycle()
    // collectAsStateWithLifecycle():
    //   - Collects StateFlow<HomeUiState> when screen visible (Lifecycle.STARTED)
    //   - Pauses when hidden (PAUSED)
    //   - Auto-cancels on destroy
    //   - Result: Compose State<HomeUiState> that triggers recompose on change

    // ========== Collect Effects (one-off events) ==========
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        // LaunchedEffect: run once per key change (viewModel, lifecycleOwner)

        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // repeatOnLifecycle: restart collection each time lifecycle enters STARTED
            //   - Collect when visible
            //   - Cancel when hidden
            //   - Restart when becomes visible again
            // (collectAsState would lose effects emitted while hidden)

            viewModel.effects.collect { effect ->
                when (effect) {
                    is HomeEffect.ShowMessage -> onShowMessage(effect.text)
                    // Bubble effect to parent (NavGraph) for snackbar
                }
            }
        }
    }

    // ========== Render ==========
    HomeScreenContent(
        state = state,  // Pass state DOWN
        onNewDog = { viewModel.onIntent(HomeIntent.LoadNewDog) },  // Send intents UP
        onToggleFavourite = { viewModel.onIntent(HomeIntent.ToggleFavourite) },
        onRetry = { viewModel.onIntent(HomeIntent.Retry) },
        modifier = modifier,
    )
}

// ============================================================================
// Stateless Content: Pure function of state — testable, previewable
// ============================================================================

@Composable
fun HomeScreenContent(
    state: HomeUiState,  // All inputs from state
    onNewDog: () -> Unit,  // All outputs as callbacks
    onToggleFavourite: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Branch on state: exactly one branch per state combination
    when {
        state.isLoading && state.dog == null -> LoadingState(modifier)
        // Initial load: show spinner

        state.errorMessage != null && state.dog == null ->
            ErrorState(state.errorMessage, onRetry = onRetry, modifier = modifier)
        // Error before dog loaded: show error + retry button

        // On refresh failure with a dog shown, keep last image; error surfaces via snackbar effect
        else -> HomeContent(
            state = state,
            onNewDog = onNewDog,
            onToggleFavourite = onToggleFavourite,
            modifier = modifier,
        )
        // Dog loaded (or refresh error after dog shown): show content
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

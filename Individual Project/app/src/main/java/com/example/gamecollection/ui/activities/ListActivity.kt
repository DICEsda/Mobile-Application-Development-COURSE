package com.example.gamecollection.ui.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gamecollection.data.model.Category
import com.example.gamecollection.data.model.Game
import com.example.gamecollection.ui.components.GameCard
import com.example.gamecollection.ui.components.getCategoryColor
import com.example.gamecollection.ui.theme.GameCollectionTheme
import com.example.gamecollection.viewmodel.GameViewModel

/**
 * ListActivity - The List View of the application.
 * 
 * This Activity displays a list of games belonging to a specific category.
 * Each game card shows summary information and can be clicked to view details.
 * 
 * Includes a manual back button to return to MainActivity.
 */
class ListActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_CATEGORY_ID = "extra_category_id"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val categoryId = intent.getStringExtra(EXTRA_CATEGORY_ID) ?: ""
        
        setContent {
            GameCollectionTheme {
                ListScreen(
                    categoryId = categoryId,
                    onBackClick = { navigateBack() },
                    onGameClick = { game -> navigateToDetailsActivity(game.id) }
                )
            }
        }
    }
    
    /**
     * Navigates back to MainActivity.
     * Uses explicit Intent navigation as required.
     */
    private fun navigateBack() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }
    
    /**
     * Navigates to DetailsActivity with the selected game ID.
     */
    private fun navigateToDetailsActivity(gameId: String) {
        val intent = Intent(this, DetailsActivity::class.java).apply {
            putExtra(DetailsActivity.EXTRA_GAME_ID, gameId)
        }
        startActivity(intent)
    }
}

/**
 * List screen composable displaying games in a category.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListScreen(
    categoryId: String,
    onBackClick: () -> Unit,
    onGameClick: (Game) -> Unit,
    viewModel: GameViewModel = viewModel()
) {
    val category = viewModel.getCategoryById(categoryId)
    val games = viewModel.getGamesByCategory(categoryId)
    val categoryColor = getCategoryColor(categoryId)
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = category?.name ?: "Games",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    // Manual back button as required
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go back to main menu"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = categoryColor,
                    titleContentColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        ListContent(
            category = category,
            games = games,
            categoryColor = categoryColor,
            onGameClick = onGameClick,
            modifier = Modifier.padding(paddingValues)
        )
    }
}

/**
 * Content composable displaying the list of games.
 */
@Composable
fun ListContent(
    category: Category?,
    games: List<Game>,
    categoryColor: androidx.compose.ui.graphics.Color,
    onGameClick: (Game) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Category info header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(categoryColor.copy(alpha = 0.1f))
                .padding(16.dp)
        ) {
            Column {
                Text(
                    text = category?.description ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${games.size} games available",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = categoryColor
                )
            }
        }
        
        // Games list
        if (games.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = androidx.compose.ui.Alignment.Center
            ) {
                Text(
                    text = "No games found in this category",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(games) { game ->
                    GameCard(
                        game = game,
                        onClick = { onGameClick(game) }
                    )
                }
                
                // Bottom spacing
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

package com.example.randomdog.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    data object Home : Screen("home", "Home", Icons.Filled.Home)
    data object Favourites : Screen("favourites", "Favourites", Icons.Filled.Favorite)

    companion object {
        val bottomBar = listOf(Home, Favourites)
    }
}

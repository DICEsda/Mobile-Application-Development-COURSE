package com.audiobook.app.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.audiobook.app.appContainer
import com.audiobook.app.ui.screens.*
import kotlinx.coroutines.launch

sealed class Screen(val route: String) {
    object LibraryLocked : Screen("library_locked")
    object Library : Screen("library")
    object BookDetail : Screen("book_detail/{bookId}") {
        fun createRoute(bookId: String) = "book_detail/$bookId"
    }
    object Player : Screen("player/{bookId}") {
        fun createRoute(bookId: String) = "player/$bookId"
    }
    object Profile : Screen("profile")
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    isAuthenticated: Boolean,
    onAuthenticate: () -> Unit
) {
    val context = LocalContext.current
    val audiobookRepository = context.appContainer.audiobookRepository
    val scope = rememberCoroutineScope()
    
    NavHost(
        navController = navController,
        startDestination = if (isAuthenticated) Screen.Library.route else Screen.LibraryLocked.route
    ) {
        composable(Screen.LibraryLocked.route) {
            LibraryLockedScreen(
                onUnlock = onAuthenticate
            )
        }
        
        composable(Screen.Library.route) {
            LibraryScreen(
                onBookClick = { bookId ->
                    // Navigate directly to player (book details are shown in popup)
                    navController.navigate(Screen.Player.createRoute(bookId))
                },
                onPlayerClick = {
                    // Navigate to player with the first available book or show library
                    scope.launch {
                        val books = audiobookRepository.audiobooks.value
                        val bookId = books.firstOrNull()?.id
                        if (bookId != null) {
                            navController.navigate(Screen.Player.createRoute(bookId))
                        }
                    }
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }
        
        composable(Screen.BookDetail.route) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            
            // Get audiobook from repository
            var audiobook by remember { mutableStateOf<com.audiobook.app.data.model.Audiobook?>(null) }
            
            LaunchedEffect(bookId) {
                audiobook = audiobookRepository.getAudiobook(bookId)
            }
            
            audiobook?.let { book ->
                BookDetailScreen(
                    audiobook = book,
                    onBackClick = {
                        navController.popBackStack()
                    },
                    onPlayClick = {
                        // Set as current book and navigate to player
                        scope.launch {
                            audiobookRepository.setCurrentBook(book)
                        }
                        navController.navigate(Screen.Player.createRoute(bookId))
                    },
                    onChapterClick = { chapter ->
                        // Navigate to player with chapter info (could pass chapter ID as param)
                        scope.launch {
                            audiobookRepository.setCurrentBook(book)
                        }
                        navController.navigate(Screen.Player.createRoute(bookId))
                    }
                )
            }
        }
        
        composable(Screen.Player.route) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: return@composable
            PlayerScreen(
                bookId = bookId,
                onLibraryClick = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Library.route) { inclusive = true }
                    }
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }
        
        composable(Screen.Profile.route) {
            ProfileScreen(
                onLibraryClick = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Library.route) { inclusive = true }
                    }
                },
                onPlayerClick = {
                    // Navigate to player with the first available book
                    scope.launch {
                        val books = audiobookRepository.audiobooks.value
                        val bookId = books.firstOrNull()?.id
                        if (bookId != null) {
                            navController.navigate(Screen.Player.createRoute(bookId))
                        }
                    }
                },
                onSettingsClick = {
                    navController.navigate(Screen.Settings.route)
                },
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                },
                onLibraryClick = {
                    navController.navigate(Screen.Library.route) {
                        popUpTo(Screen.Library.route) { inclusive = true }
                    }
                },
                onPlayerClick = {
                    // Navigate to player with the first available book
                    scope.launch {
                        val books = audiobookRepository.audiobooks.value
                        val bookId = books.firstOrNull()?.id
                        if (bookId != null) {
                            navController.navigate(Screen.Player.createRoute(bookId))
                        }
                    }
                },
                onProfileClick = {
                    navController.navigate(Screen.Profile.route)
                }
            )
        }
    }
}

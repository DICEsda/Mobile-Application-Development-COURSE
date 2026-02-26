package com.audiobook.app.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.audiobook.app.appContainer
import com.audiobook.app.ui.screens.*
import kotlinx.coroutines.launch

/**
 * Navigate to a top-level (bottom nav) destination without accumulating
 * duplicate entries on the back stack.
 */
private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

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
    
    /** Navigate to the player with the first available book. */
    val navigateToPlayer: () -> Unit = {
        scope.launch {
            val bookId = audiobookRepository.audiobooks.value.firstOrNull()?.id
            if (bookId != null) {
                navController.navigate(Screen.Player.createRoute(bookId))
            }
        }
    }
    
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
                onPlayerClick = navigateToPlayer,
                onProfileClick = {
                    navController.navigateTopLevel(Screen.Profile.route)
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
                    navController.navigateTopLevel(Screen.Library.route)
                },
                onProfileClick = {
                    navController.navigateTopLevel(Screen.Profile.route)
                }
            )
        }
        
        composable(Screen.Profile.route) {
            ProfileScreen(
                onLibraryClick = {
                    navController.navigateTopLevel(Screen.Library.route)
                },
                onPlayerClick = navigateToPlayer,
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
                    navController.navigateTopLevel(Screen.Library.route)
                },
                onPlayerClick = navigateToPlayer,
                onProfileClick = {
                    navController.navigateTopLevel(Screen.Profile.route)
                }
            )
        }
    }
}

package com.audiobook.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audiobook.app.appContainer
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.ui.components.BottomNavBar
import com.audiobook.app.ui.components.NavItem
import com.audiobook.app.ui.components.ProgressBar
import com.audiobook.app.ui.theme.*
import com.audiobook.app.ui.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onPlayerClick: () -> Unit,
    onProfileClick: () -> Unit,
    viewModel: LibraryViewModel = viewModel(
        factory = LibraryViewModel.Factory(
            audiobookRepository = LocalContext.current.appContainer.audiobookRepository,
            preferencesRepository = LocalContext.current.appContainer.preferencesRepository
        )
    )
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val audiobooks by viewModel.filteredAudiobooks.collectAsState()
    val currentBook by viewModel.currentBook.collectAsState()
    val audiobookCount by viewModel.audiobookCount.collectAsState()
    val timeRemaining by viewModel.currentBookTimeRemaining.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // State for the book detail bottom sheet
    var selectedBook by remember { mutableStateOf<Audiobook?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    // SAF folder picker launcher
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistent permission so we can access files later and for playback
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                // Some URIs may not support persistent permissions, continue anyway
                e.printStackTrace()
            }
            viewModel.setAudiobookFolder(it.toString())
        }
    }
    
    // Book detail bottom sheet
    if (selectedBook != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedBook = null },
            sheetState = sheetState,
            containerColor = Surface1,
            contentColor = TextPrimary,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(TextTertiary)
                )
            }
        ) {
            BookDetailPopup(
                audiobook = selectedBook!!,
                onPlayClick = {
                    val bookId = selectedBook!!.id
                    scope.launch { sheetState.hide() }
                    selectedBook = null
                    onBookClick(bookId)
                },
                onDismiss = {
                    scope.launch { sheetState.hide() }
                    selectedBook = null
                }
            )
        }
    }
    
    Scaffold(
        containerColor = Background,
        bottomBar = {
            BottomNavBar(
                selectedItem = NavItem.Library,
                onItemSelected = { item ->
                    when (item) {
                        NavItem.Library -> {}
                        NavItem.Player -> onPlayerClick()
                        NavItem.Profile -> onProfileClick()
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                // Header
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Background)
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Library",
                                style = MaterialTheme.typography.displaySmall,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$audiobookCount audiobooks",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                        
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Folder picker button - uses SAF document tree picker
                            IconButton(
                                onClick = { folderPicker.launch(null) },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Select audiobook folder",
                                    tint = if (isLoading) TextTertiary else TextSecondary
                                )
                            }
                            
                            // Refresh button
                            IconButton(
                                onClick = { viewModel.refreshLibrary() },
                                enabled = !isLoading
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh library",
                                    tint = if (isLoading) TextTertiary else TextSecondary
                                )
                            }
                        }
                    }
                }
                
                // Search bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.onSearchQueryChange(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 24.dp),
                        placeholder = {
                            Text(
                                text = "Search audiobooks...",
                                color = TextTertiary
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = TextTertiary
                            )
                        },
                        trailingIcon = {
                            AnimatedVisibility(
                                visible = searchQuery.isNotEmpty(),
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                IconButton(onClick = { viewModel.clearSearch() }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = TextTertiary
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Surface2,
                            unfocusedContainerColor = Surface2,
                            focusedBorderColor = AccentOrange.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.Transparent,
                            cursorColor = AccentOrange,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(16.dp),
                        singleLine = true
                    )
                }
                
                // Continue Reading section (only show if there's a current book)
                currentBook?.let { book ->
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = "Continue Reading",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary,
                                modifier = Modifier.padding(bottom = 16.dp)
                            )
                            ContinueReadingCard(
                                book = book,
                                timeRemaining = timeRemaining,
                                onPlayClick = { onBookClick(book.id) }
                            )
                        }
                    }
                }
                
                // All Audiobooks section header
                item {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "Search Results" else "All Audiobooks",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 32.dp, bottom = 16.dp)
                    )
                }
                
                // Empty state
                if (audiobooks.isEmpty() && !isLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        "No audiobooks found"
                                    } else {
                                        "Your library is empty"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = if (searchQuery.isNotEmpty()) {
                                        "Try a different search term"
                                    } else {
                                        "Add M4B files to the Audiobooks folder"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextTertiary
                                )
                                if (searchQuery.isEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "/storage/emulated/0/Audiobook tests/",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentOrange.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Audiobooks grid - using Column with Row pairs instead of LazyVerticalGrid
                // to avoid nested scrolling issues and height calculation problems
                if (audiobooks.isNotEmpty()) {
                    val chunkedBooks = audiobooks.chunked(2)
                    chunkedBooks.forEach { rowBooks ->
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                rowBooks.forEach { book ->
                                    AudiobookGridItem(
                                        book = book,
                                        onClick = { selectedBook = book },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                // If odd number, add empty spacer to maintain layout
                                if (rowBooks.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
            
            // Loading indicator
            AnimatedVisibility(
                visible = isLoading,
                modifier = Modifier.align(Alignment.Center),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                CircularProgressIndicator(
                    color = AccentOrange
                )
            }
            
            // Error snackbar
            error?.let { errorMessage ->
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.clearError() }) {
                            Text("Dismiss", color = AccentOrange)
                        }
                    },
                    containerColor = Surface2,
                    contentColor = TextPrimary
                ) {
                    Text(errorMessage)
                }
            }
        }
    }
}

/**
 * Book detail popup shown in a bottom sheet when a book is tapped in the library.
 */
@Composable
private fun BookDetailPopup(
    audiobook: Audiobook,
    onPlayClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var isDescriptionExpanded by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp)
    ) {
        // Header with cover and info
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cover image
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface3)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(audiobook.coverUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = audiobook.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Book info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(180.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = audiobook.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = audiobook.author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    // Narrator if available
                    audiobook.narrator?.let { narrator ->
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Narrated by $narrator",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // Duration and chapters info
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column {
                        Text(
                            text = "Duration",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                        Text(
                            text = audiobook.duration,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary
                        )
                    }
                    if (audiobook.chapters.isNotEmpty()) {
                        Column {
                            Text(
                                text = "Chapters",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextTertiary
                            )
                            Text(
                                text = "${audiobook.chapters.size}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }
        }
        
        // Description section (if available)
        audiobook.description?.let { description ->
            Spacer(modifier = Modifier.height(16.dp))
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .padding(12.dp)
            ) {
                Text(
                    text = "About",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextTertiary
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                // Show description with expand/collapse
                val maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 3
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = maxLines,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Show "Read more" / "Read less" if description is long
                if (description.length > 150) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isDescriptionExpanded) "Read less" else "Read more",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange,
                        modifier = Modifier.clickable { isDescriptionExpanded = !isDescriptionExpanded }
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Progress section (if book has been started)
        if (audiobook.progress > 0f) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface2)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Progress",
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Text(
                        text = "${(audiobook.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.titleSmall,
                        color = AccentOrange
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                ProgressBar(
                    progress = audiobook.progress,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Calculate remaining time
                val totalMinutes = audiobook.totalDurationMinutes
                val listenedMinutes = (totalMinutes * audiobook.progress).toInt()
                val remainingMinutes = totalMinutes - listenedMinutes
                val hours = remainingMinutes / 60
                val minutes = remainingMinutes % 60
                val remainingText = if (hours > 0) "${hours}h ${minutes}m remaining" else "${minutes}m remaining"
                
                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
        }
        
        // Play button
        Button(
            onClick = onPlayClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = AccentOrange
            )
        ) {
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = TextPrimary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (audiobook.progress > 0f) "Continue Listening" else "Start Listening",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ContinueReadingCard(
    book: Audiobook,
    timeRemaining: String,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Surface2)
    ) {
        // Progress background overlay
        Box(
            modifier = Modifier
                .fillMaxWidth(book.progress)
                .matchParentSize()
                .background(AccentOverlay10)
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Book cover
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(book.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = book.title,
                modifier = Modifier
                    .width(80.dp)
                    .height(112.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Surface3),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Book info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (timeRemaining.isNotEmpty()) {
                    Text(
                        text = timeRemaining,
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ProgressBar(
                        progress = book.progress,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${(book.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Play button
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(AccentOrange)
                    .clickable(onClick = onPlayClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun AudiobookGridItem(
    book: Audiobook,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    Column(
        modifier = modifier.clickable(onClick = onClick)
    ) {
        // Cover image with progress overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp))
                .background(Surface3)
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(book.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = book.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Progress indicator overlay
            if (book.progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Surface2.copy(alpha = 0.8f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(book.progress)
                            .fillMaxHeight()
                            .background(AccentOrange)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Metadata section
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                minLines = 2 // Ensure consistent height even for short titles
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = book.author,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = book.duration,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                maxLines = 1
            )
            // Short description preview (if available)
            book.description?.let { desc ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.labelSmall.lineHeight
                )
            }
        }
    }
}

package com.audiobook.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.audiobook.app.appContainer
import com.audiobook.app.data.model.Chapter
import com.audiobook.app.ui.components.BottomNavBar
import com.audiobook.app.ui.components.NavItem
import com.audiobook.app.ui.components.ProgressBar
import com.audiobook.app.ui.theme.*
import com.audiobook.app.ui.viewmodel.PlayerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    bookId: String,
    onLibraryClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val container = context.appContainer
    
    // Scope ViewModel to the Activity so it survives navigation
    val activity = context as? androidx.activity.ComponentActivity
    val viewModel: PlayerViewModel = viewModel(
        viewModelStoreOwner = activity ?: error("PlayerScreen must be hosted in ComponentActivity"),
        factory = PlayerViewModel.Factory(
            audiobookPlayer = container.audiobookPlayer,
            audiobookRepository = container.audiobookRepository,
            preferencesRepository = container.preferencesRepository,
            chapterParser = container.chapterParser,
            notificationTriggerHelper = container.notificationTriggerHelper
        )
    )
    
    // Load the book when screen appears or when bookId changes
    LaunchedEffect(bookId) {
        // Only load if it's a different book
        if (viewModel.currentBook.value?.id != bookId) {
            viewModel.loadBook(bookId)
        }
    }
    
    // Collect state from ViewModel
    val currentBook by viewModel.currentBook.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val progress by viewModel.progress.collectAsState()
    val currentTimeFormatted by viewModel.currentTimeFormatted.collectAsState()
    val durationFormatted by viewModel.durationFormatted.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val chapters by viewModel.chapters.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val error by viewModel.error.collectAsState()
    val chapterProgress by viewModel.chapterProgress.collectAsState()
    val chapterTimeFormatted by viewModel.chapterTimeFormatted.collectAsState()
    val chapterDurationFormatted by viewModel.chapterDurationFormatted.collectAsState()
    val currentChapterTitle by viewModel.currentChapterTitle.collectAsState()
    val hasChaptersLoaded = chapters.isNotEmpty()
    
    // Handle null book - show loading or empty state
    val book = currentBook
    if (book == null) {
        // Loading or empty state
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = AccentOrange)
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Loading audiobook...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextSecondary
                )
            }
        }
        return
    }
    
    // UI state for bottom sheets
    var showChapters by remember { mutableStateOf(false) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
    val sleepTimerOptions = listOf(
        "5m" to 5,
        "10m" to 10,
        "15m" to 15,
        "20m" to 20,
        "25m" to 25,
        "30m" to 30,
        "Chapter" to -1
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                BottomNavBar(
                    selectedItem = NavItem.Player,
                    onItemSelected = { item ->
                        when (item) {
                            NavItem.Library -> onLibraryClick()
                            NavItem.Player -> {}
                            NavItem.Profile -> onProfileClick()
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(24.dp))
                
                // Error banner
                if (error != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = error ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.clearError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                // Album art with glow
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    // Glow effect
                    Box(
                        modifier = Modifier
                            .size(336.dp)
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(
                                        AccentOrange.copy(alpha = 0.2f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    
                    // Album cover - 20% larger (260 -> 312)
                    AsyncImage(
                        model = book.coverUrl,
                        contentDescription = book.title,
                        modifier = Modifier
                            .size(312.dp)
                            .clip(RoundedCornerShape(20.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
                
                // Book info
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 12.dp)
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = book.author,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextSecondary
                    )
                }
                
                // Interactive progress slider (chapter-relative when chapters exist)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                ) {
                    // Show current chapter title when available
                    if (currentChapterTitle != null) {
                        Text(
                            text = currentChapterTitle ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentOrange,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    
                    // Custom seekable progress bar — chapter-relative when chapters exist
                    SeekableProgressBar(
                        progress = if (hasChaptersLoaded) chapterProgress else progress,
                        onSeek = { newProgress ->
                            if (hasChaptersLoaded) {
                                viewModel.seekToChapterProgress(newProgress)
                            } else {
                                viewModel.seekToProgress(newProgress)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (hasChaptersLoaded) chapterTimeFormatted else currentTimeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                        Text(
                            text = if (hasChaptersLoaded) chapterDurationFormatted else durationFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }
                
                // Secondary controls
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    // Speed button
                    Surface(
                        onClick = { showSpeed = true },
                        color = Surface2,
                        shape = RoundedCornerShape(50)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Speed",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextPrimary
                            )
                            Text(
                                text = "${playbackSpeed}x",
                                style = MaterialTheme.typography.labelLarge,
                                color = AccentOrange
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Chapters button - disabled if no chapters
                    val hasChapters = chapters.isNotEmpty()
                    Surface(
                        onClick = { if (hasChapters) showChapters = true },
                        color = if (hasChapters) Surface2 else Surface2.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(50),
                        enabled = hasChapters
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (hasChapters) "Chapters" else "No Chapters",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (hasChapters) TextPrimary else TextTertiary
                            )
                            if (hasChapters) {
                                Icon(
                                    imageVector = Icons.Outlined.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
                
                // Primary controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sleep timer
                    IconButton(
                        onClick = { showSleepTimer = true },
                        modifier = Modifier.size(64.dp)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Bedtime,
                                contentDescription = "Sleep Timer",
                                tint = if (sleepTimerMinutes != null) AccentOrange else TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            if (sleepTimerMinutes != null) {
                                Text(
                                    text = if (sleepTimerMinutes == -1) "Ch" else "${sleepTimerMinutes}m",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AccentOrange
                                )
                            }
                        }
                    }
                    
                    // Skip back 15s
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.skipBackward()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Surface2)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Replay,
                                contentDescription = "Skip back 15 seconds",
                                tint = TextPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "15",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Play/Pause - animated
                    PlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.togglePlayPause()
                        }
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    // Skip forward 30s (mirrored Replay icon to match skip back)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.skipForward()
                        },
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Surface2)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Replay,
                                contentDescription = "Skip forward 30 seconds",
                                tint = TextPrimary,
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer(scaleX = -1f) // Mirror to point forward
                            )
                            Text(
                                text = "30",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(64.dp))
                }
            }
        }
        
        // Chapters bottom sheet
        if (showChapters) {
            ModalBottomSheet(
                onDismissRequest = { showChapters = false },
                containerColor = Surface1,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Surface4)
                    )
                }
            ) {
            ChaptersSheet(
                    chapters = viewModel.getChaptersWithPlayingState(),
                    onChapterClick = { chapter ->
                        viewModel.seekToChapter(chapter)
                        showChapters = false
                    },
                    onRenameChapter = { chapterNumber, newTitle ->
                        viewModel.renameChapter(chapterNumber, newTitle)
                    },
                    onDismiss = { showChapters = false }
                )
            }
        }
        
        // Speed bottom sheet
        if (showSpeed) {
            ModalBottomSheet(
                onDismissRequest = { showSpeed = false },
                containerColor = Surface1,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Surface4)
                    )
                }
            ) {
                SpeedSheet(
                    currentSpeed = playbackSpeed,
                    options = speedOptions,
                    onSpeedSelected = { 
                        viewModel.setPlaybackSpeed(it)
                        showSpeed = false
                    },
                    onDismiss = { showSpeed = false }
                )
            }
        }
        
        // Sleep timer bottom sheet
        if (showSleepTimer) {
            ModalBottomSheet(
                onDismissRequest = { showSleepTimer = false },
                containerColor = Surface1,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                dragHandle = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 16.dp)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Surface4)
                    )
                }
            ) {
                SleepTimerSheet(
                    currentTimer = sleepTimerMinutes,
                    options = sleepTimerOptions,
                    onTimerSelected = { minutes ->
                        viewModel.setSleepTimer(if (minutes == 0) null else minutes)
                        showSleepTimer = false
                    },
                    onDismiss = { showSleepTimer = false }
                )
            }
        }
    }
}

/**
 * Seekable progress bar component.
 */
@Composable
private fun SeekableProgressBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(progress) }
    
    // Update drag progress when not dragging
    LaunchedEffect(progress, isDragging) {
        if (!isDragging) {
            dragProgress = progress
        }
    }
    
    Slider(
        value = if (isDragging) dragProgress else progress,
        onValueChange = { newValue ->
            isDragging = true
            dragProgress = newValue
        },
        onValueChangeFinished = {
            onSeek(dragProgress)
            isDragging = false
        },
        modifier = modifier.height(24.dp),
        colors = SliderDefaults.colors(
            thumbColor = AccentOrange,
            activeTrackColor = AccentOrange,
            inactiveTrackColor = Surface4
        )
    )
}

/**
 * Animated play/pause button.
 */
@Composable
private fun PlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 1.05f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1500f),
        label = "scale"
    )
    
    Box(
        modifier = Modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(AccentOrange)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(
            targetState = isPlaying,
            animationSpec = tween(80),
            label = "playPause"
        ) { playing ->
            Icon(
                imageVector = if (playing) Icons.Outlined.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = TextPrimary,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}

@Composable
private fun ChaptersSheet(
    chapters: List<Chapter>,
    onChapterClick: (Chapter) -> Unit,
    onRenameChapter: (chapterNumber: Int, newTitle: String) -> Unit,
    onDismiss: () -> Unit
) {
    // State for the rename dialog
    var editingChapter by remember { mutableStateOf<Chapter?>(null) }
    
    // Rename dialog
    editingChapter?.let { chapter ->
        RenameChapterDialog(
            currentTitle = chapter.title,
            onDismiss = { editingChapter = null },
            onConfirm = { newTitle ->
                onRenameChapter(chapter.number, newTitle)
                editingChapter = null
            }
        )
    }
    
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chapters",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = TextTertiary
                )
            }
        }
        
        // Chapters list
        LazyColumn(
            modifier = Modifier.heightIn(max = 500.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(chapters) { chapter ->
                ChapterItem(
                    chapter = chapter,
                    onClick = { onChapterClick(chapter) },
                    onEditClick = { editingChapter = chapter }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun ChapterItem(
    chapter: Chapter,
    onClick: () -> Unit,
    onEditClick: () -> Unit
) {
    val playingGradient = remember {
        Brush.horizontalGradient(
            colors = listOf(
                AccentOrange.copy(alpha = 0.1f),
                Color.Transparent
            )
        )
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (chapter.isPlaying) Surface2 else Surface3)
            .then(
                if (chapter.isPlaying) {
                    Modifier.background(brush = playingGradient)
                } else Modifier
            )
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chapter number or play icon
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (chapter.isPlaying) AccentOrange else Surface4),
                    contentAlignment = Alignment.Center
                ) {
                    if (chapter.isPlaying) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = TextPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text(
                            text = chapter.number.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // Chapter info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = chapter.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (chapter.isPlaying) TextPrimary else TextHighlight,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = chapter.duration,
                        style = MaterialTheme.typography.bodySmall,
                        color = TextTertiary
                    )
                }
                
                // Edit chapter title button
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit chapter title",
                        tint = TextTertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                
                // Playing indicator — animations only run when chapter is playing
                if (chapter.isPlaying) {
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val pulseHeight1 by infiniteTransition.animateFloat(
                        initialValue = 0.6f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(300),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse1"
                    )
                    val pulseHeight2 by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.6f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(300, delayMillis = 120),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse2"
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.height(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(pulseHeight1 * 0.6f)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(AccentOrange)
                        )
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(pulseHeight2)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(AccentOrange)
                        )
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(pulseHeight1 * 0.8f)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(AccentOrange)
                        )
                    }
                }
            }
            
            // Progress bar for playing chapter
            if (chapter.isPlaying) {
                Spacer(modifier = Modifier.height(12.dp))
                ProgressBar(progress = chapter.progress)
            }
        }
    }
}

/**
 * Dialog for renaming a chapter title.
 */
@Composable
private fun RenameChapterDialog(
    currentTitle: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentTitle) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Edit Chapter Title",
                color = TextPrimary
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Chapter title") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    focusedLabelColor = AccentOrange,
                    cursorColor = AccentOrange,
                    unfocusedBorderColor = Surface4,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmed = text.trim()
                    if (trimmed.isNotEmpty()) onConfirm(trimmed)
                },
                enabled = text.trim().isNotEmpty() && text.trim() != currentTitle
            ) {
                Text("Save", color = if (text.trim().isNotEmpty() && text.trim() != currentTitle) AccentOrange else TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextSecondary)
            }
        },
        containerColor = Surface2
    )
}

@Composable
private fun SpeedSheet(
    currentSpeed: Float,
    options: List<Float>,
    onSpeedSelected: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playback Speed",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = TextTertiary
                )
            }
        }
        
        // Speed grid
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            options.chunked(4).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { speed ->
                        val isSelected = speed == currentSpeed
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) AccentOrange else Surface2)
                                .clickable { onSpeedSelected(speed) }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${speed}x",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    // Fill empty slots
                    repeat(4 - row.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun SleepTimerSheet(
    currentTimer: Int?,
    options: List<Pair<String, Int>>,
    onTimerSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sleep Timer",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = TextTertiary
                )
            }
        }
        
        // Timer grid
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Off option
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (currentTimer == null) AccentOrange else Surface2)
                    .clickable { onTimerSelected(0) }
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Off",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (currentTimer == null) TextPrimary else TextSecondary,
                    fontWeight = FontWeight.Bold
                )
            }
            
            options.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { (label, value) ->
                        val isSelected = currentTimer == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSelected) AccentOrange else Surface2)
                                .clickable { onTimerSelected(value) }
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) TextPrimary else TextSecondary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

private val Color.Companion.Transparent: Color
    get() = Color(0x00000000)

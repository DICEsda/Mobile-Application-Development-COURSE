package com.audiobook.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.Chapter
import com.audiobook.app.ui.components.ProgressBar
import com.audiobook.app.ui.theme.*

/**
 * Book Detail Screen - displays audiobook metadata, chapters, and progress.
 * Allows user to start playback from a specific chapter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    audiobook: Audiobook,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onChapterClick: (Chapter) -> Unit
) {
    val context = LocalContext.current
    
    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            // Cover and title header
            item {
                BookHeader(
                    audiobook = audiobook,
                    onPlayClick = onPlayClick
                )
            }
            
            // Progress section
            item {
                ProgressSection(audiobook = audiobook)
            }
            
            // Book info section
            item {
                BookInfoSection(audiobook = audiobook)
            }
            
            // Chapters section header
            if (audiobook.chapters.isNotEmpty()) {
                item {
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 32.dp, bottom = 16.dp)
                    )
                }
                
                // Chapter list
                itemsIndexed(audiobook.chapters) { index, chapter ->
                    ChapterItem(
                        chapter = chapter,
                        chapterNumber = index + 1,
                        isCurrentChapter = index + 1 == audiobook.currentChapter,
                        onClick = { onChapterClick(chapter) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BookHeader(
    audiobook: Audiobook,
    onPlayClick: () -> Unit
) {
    val context = LocalContext.current
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        // Background cover (blurred effect via gradient)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(audiobook.coverUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alpha = 0.3f
        )
        
        // Gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Background.copy(alpha = 0.5f),
                            Background
                        )
                    )
                )
        )
        
        // Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Cover image
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(audiobook.coverUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = audiobook.title,
                modifier = Modifier
                    .width(160.dp)
                    .height(224.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Surface3),
                contentScale = ContentScale.Crop
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Title
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Author
            Text(
                text = audiobook.author,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
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
}

@Composable
private fun ProgressSection(audiobook: Audiobook) {
    if (audiobook.progress <= 0f) return
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
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
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Only show chapter info if chapters exist
            if (audiobook.chapters.isNotEmpty()) {
                Text(
                    text = "Chapter ${audiobook.currentChapter} of ${audiobook.chapters.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            } else {
                // Show empty space to maintain layout
                Spacer(modifier = Modifier.width(1.dp))
            }
            
            // Calculate remaining time
            val totalMinutes = audiobook.totalDurationMinutes
            val listenedMinutes = (totalMinutes * audiobook.progress).toInt()
            val remainingMinutes = totalMinutes - listenedMinutes
            val hours = remainingMinutes / 60
            val minutes = remainingMinutes % 60
            val remainingText = if (hours > 0) "${hours}h ${minutes}m left" else "${minutes}m left"
            
            Text(
                text = remainingText,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
    }
}

@Composable
private fun BookInfoSection(audiobook: Audiobook) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp)
    ) {
        Text(
            text = "About this Book",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Info cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InfoCard(
                label = "Duration",
                value = audiobook.duration,
                modifier = Modifier.weight(1f)
            )
            InfoCard(
                label = "Chapters",
                value = if (audiobook.chapters.isNotEmpty()) "${audiobook.chapters.size}" else "N/A",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun InfoCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(16.dp)
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun ChapterItem(
    chapter: Chapter,
    chapterNumber: Int,
    isCurrentChapter: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrentChapter) AccentOverlay10 else Color.Transparent)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Chapter number
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isCurrentChapter) AccentOrange else Surface2),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = chapterNumber.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = if (isCurrentChapter) TextPrimary else TextSecondary
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // Chapter info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = chapter.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrentChapter) TextPrimary else TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatDuration(chapter.endTimeMs - chapter.startTimeMs),
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary
            )
        }
        
        // Current indicator
        AnimatedVisibility(
            visible = isCurrentChapter,
            enter = fadeIn(animationSpec = tween(100)),
            exit = fadeOut(animationSpec = tween(100))
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(AccentOrange)
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes}:${seconds.toString().padStart(2, '0')}"
}

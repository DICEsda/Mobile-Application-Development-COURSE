package com.audiobook.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.audiobook.app.data.model.Audiobook
import com.audiobook.app.data.model.Chapter
import com.audiobook.app.ui.theme.*

/**
 * Book Detail Screen - displays comprehensive audiobook metadata,
 * per-chapter progress, and playback controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    audiobook: Audiobook,
    currentPositionMs: Long = 0,
    onBackClick: () -> Unit,
    onPlayClick: () -> Unit,
    onChapterClick: (Chapter) -> Unit
) {
    val context = LocalContext.current
    var isDescriptionExpanded by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Book Details",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                },
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
                    containerColor = Surface1
                )
            )
        },
        bottomBar = {
            // Bottom play button
            Surface(
                color = Surface1,
                tonalElevation = 8.dp
            ) {
                Button(
                    onClick = onPlayClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp)
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
                        text = if (audiobook.progress > 0f) "Continue Listening" else "Play",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // ── Cover + basic info section ──
            item {
                CoverInfoSection(audiobook = audiobook)
            }

            // ── Stats row ──
            item {
                StatsRow(audiobook = audiobook)
            }

            // ── Description section ──
            audiobook.description?.let { description ->
                item {
                    DescriptionSection(
                        description = description,
                        isExpanded = isDescriptionExpanded,
                        onToggle = { isDescriptionExpanded = !isDescriptionExpanded }
                    )
                }
            }

            // ── File info ──
            item {
                FileInfoSection(audiobook = audiobook)
            }

            // ── Chapters section ──
            if (audiobook.chapters.isNotEmpty()) {
                item {
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(top = 24.dp, bottom = 12.dp)
                    )
                }

                itemsIndexed(audiobook.chapters) { index, chapter ->
                    val chapterProgress = computeChapterProgress(
                        chapter = chapter,
                        currentPositionMs = currentPositionMs
                    )
                    val isCurrentChapter = currentPositionMs >= chapter.startTimeMs &&
                            currentPositionMs < chapter.endTimeMs
                    val isCompleted = chapterProgress >= 1f

                    ChapterRow(
                        chapter = chapter,
                        chapterNumber = index + 1,
                        progress = chapterProgress,
                        isCurrent = isCurrentChapter,
                        isCompleted = isCompleted,
                        onClick = { onChapterClick(chapter) }
                    )
                }
            }
        }
    }
}

// ── Cover + basic info ──

@Composable
private fun CoverInfoSection(audiobook: Audiobook) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Large cover image
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(280.dp)
                .clip(RoundedCornerShape(16.dp))
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

        // Title / Author / Narrator
        Column(
            modifier = Modifier
                .weight(1f)
                .height(280.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = audiobook.title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = audiobook.author,
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            audiobook.narrator?.let { narrator ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Narrated by $narrator",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Stats row ──

@Composable
private fun StatsRow(audiobook: Audiobook) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            label = "Duration",
            value = audiobook.duration,
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Chapters",
            value = if (audiobook.chapters.isNotEmpty()) "${audiobook.chapters.size}" else "N/A",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            label = "Progress",
            value = "${(audiobook.progress * 100).toInt()}%",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(12.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = TextTertiary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ── Description section ──

@Composable
private fun DescriptionSection(
    description: String,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 20.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(16.dp)
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.titleSmall,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = if (isExpanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis
        )
        if (description.length > 200) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isExpanded) "Read less" else "Read more",
                style = MaterialTheme.typography.labelSmall,
                color = AccentOrange,
                modifier = Modifier.clickable(onClick = onToggle)
            )
        }
    }
}

// ── File info ──

@Composable
private fun FileInfoSection(audiobook: Audiobook) {
    val displayPath = audiobook.contentUri ?: audiobook.filePath
    if (displayPath.isNullOrBlank()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp)
    ) {
        Text(
            text = "File",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = displayPath,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary.copy(alpha = 0.7f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ── Chapter row ──

@Composable
private fun ChapterRow(
    chapter: Chapter,
    chapterNumber: Int,
    progress: Float,
    isCurrent: Boolean,
    isCompleted: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) AccentOverlay10 else Color.Transparent)
            .padding(horizontal = 24.dp)
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Chapter indicator circle
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isCompleted -> SuccessGreen
                            isCurrent -> AccentOrange
                            else -> Surface3
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isCompleted -> {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Completed",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    isCurrent -> {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Currently playing",
                            tint = TextPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = chapterNumber.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Chapter title + duration
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = chapter.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent || isCompleted) TextPrimary else TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = formatDuration(chapter.endTimeMs - chapter.startTimeMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextTertiary
                )
            }
        }

        // Progress bar under each chapter
        Spacer(modifier = Modifier.height(8.dp))

        val animatedProgress by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = tween(durationMillis = 150, easing = FastOutSlowInEasing),
            label = "chapterProgress"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 56.dp) // align with text (40dp circle + 16dp spacer)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Surface3)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        when {
                            isCompleted -> SuccessGreen
                            isCurrent -> AccentOrange
                            else -> Color.Transparent
                        }
                    )
            )
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

// ── Helpers ──

/**
 * Compute per-chapter progress from the global playback position.
 *  - If position >= chapter.endTimeMs  → 1.0 (completed)
 *  - If position is within the chapter → partial (0..1)
 *  - Otherwise                         → 0.0
 */
private fun computeChapterProgress(chapter: Chapter, currentPositionMs: Long): Float {
    if (currentPositionMs >= chapter.endTimeMs) return 1f
    if (currentPositionMs > chapter.startTimeMs && currentPositionMs < chapter.endTimeMs) {
        val chapterDuration = (chapter.endTimeMs - chapter.startTimeMs).toFloat()
        if (chapterDuration <= 0f) return 0f
        return ((currentPositionMs - chapter.startTimeMs).toFloat() / chapterDuration)
    }
    return 0f
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m ${seconds.toString().padStart(2, '0')}s"
    } else {
        "${minutes}:${seconds.toString().padStart(2, '0')}"
    }
}

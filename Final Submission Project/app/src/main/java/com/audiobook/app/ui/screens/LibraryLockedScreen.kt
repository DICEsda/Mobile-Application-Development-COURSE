package com.audiobook.app.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.audiobook.app.ui.theme.*

@Composable
fun LibraryLockedScreen(
    onUnlock: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "scale"
    )
    
    // Glow animation
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )
    
    val bookColors = listOf(
        Color(0xFF4338CA),
        Color(0xFF0891B2),
        Color(0xFF7C3AED),
        Color(0xFF0D9488),
        Color(0xFF4F46E5),
        Color(0xFF0284C7)
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Blurred background with books grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(12.dp)
                .padding(24.dp)
                .padding(top = 64.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                for (row in 0..2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        for (col in 0..1) {
                            val index = row * 2 + col
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(2f / 3f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(bookColors.getOrElse(index) { bookColors[0] }.copy(alpha = 0.3f))
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.75f)
                                        .height(12.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(WhiteOverlay20)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(WhiteOverlay10)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Lock overlay content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Fingerprint button with glow
            Box(
                modifier = Modifier.padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                // Glow effect
                Box(
                    modifier = Modifier
                        .size(140.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    AccentOrange.copy(alpha = glowAlpha),
                                    Color.Transparent
                                )
                            )
                        )
                )
                
                // Fingerprint button
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .scale(scale)
                        .clip(CircleShape)
                        .background(Surface1)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isPressed = true
                            onUnlock()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        AccentOrange.copy(alpha = 0.1f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = "Unlock with fingerprint",
                        tint = AccentOrange,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            // Text
            Text(
                text = "Unlock Library",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Use biometric authentication to access your audiobook collection",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 250.dp)
            )
        }

        // Bottom hint
        Text(
            text = "Touch sensor to unlock",
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
        )
    }
    
    LaunchedEffect(isPressed) {
        if (isPressed) {
            kotlinx.coroutines.delay(150)
            isPressed = false
        }
    }
}

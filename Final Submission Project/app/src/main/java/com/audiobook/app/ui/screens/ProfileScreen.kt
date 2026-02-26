package com.audiobook.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.audiobook.app.data.model.UserProfile
import com.audiobook.app.data.model.UserStats
import com.audiobook.app.ui.components.BottomNavBar
import com.audiobook.app.ui.components.NavItem
import com.audiobook.app.ui.theme.*

@Composable
fun ProfileScreen(
    onLibraryClick: () -> Unit,
    onPlayerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onBackClick: () -> Unit
) {
    // Use default profile - in a real app this would come from AuthRepository
    val profile = UserProfile.default
    
    Scaffold(
        containerColor = Background,
        bottomBar = {
            BottomNavBar(
                selectedItem = NavItem.Profile,
                onItemSelected = { item ->
                    when (item) {
                        NavItem.Library -> onLibraryClick()
                        NavItem.Player -> onPlayerClick()
                        NavItem.Profile -> {}
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp, bottom = 32.dp)
                ) {
                    Text(
                        text = "Profile",
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary
                    )
                }
            }
            
            // User info
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Surface2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = "Profile",
                            tint = AccentOrange,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column {
                        Text(
                            text = profile.name,
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = profile.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
            
            // Stats section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 32.dp)
                ) {
                    Text(
                        text = "Your Stats",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    StatsGrid(stats = profile.stats)
                }
            }
            
            // Settings section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    SettingsItem(
                        icon = Icons.Outlined.Settings,
                        label = "App Settings",
                        onClick = onSettingsClick
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingsItem(
                        icon = Icons.Outlined.Person,
                        label = "Account",
                        onClick = { }
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: UserStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            icon = Icons.Outlined.MenuBook,
            value = stats.booksCompleted.toString(),
            label = "Books Completed",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Outlined.Timer,
            value = "${stats.hoursListened}h",
            label = "Hours Listened",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            icon = Icons.Outlined.TrendingUp,
            value = "${stats.currentStreak}d",
            label = "Current Streak",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun StatCard(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentOrange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextTertiary
        )
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Surface2)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextSecondary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}

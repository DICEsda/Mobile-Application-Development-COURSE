package com.audiobook.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsState
import kotlinx.coroutines.flow.map
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.audiobook.app.appContainer
import com.audiobook.app.ui.components.BottomNavBar
import com.audiobook.app.ui.components.NavItem
import com.audiobook.app.ui.components.ToggleSwitch
import com.audiobook.app.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onLibraryClick: () -> Unit,
    onPlayerClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = context.appContainer.preferencesRepository
    
    val darkMode by preferencesRepository.darkMode.collectAsState(initial = true)
    val notifications by preferencesRepository.notificationsEnabled.collectAsState(initial = true)
    val autoDownload by preferencesRepository.autoDownload.collectAsState(initial = false)
    val currentFolderPath by preferencesRepository.audiobookFolderPath
        .map { it?.takeIf { p -> p.isNotBlank() } ?: "Default (Audiobooks)" }
        .collectAsState(initial = "Default (Audiobooks)")
    
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
                android.util.Log.w("SettingsScreen", "Could not take persistable URI permission", e)
            }
            scope.launch {
                preferencesRepository.setAudiobookFolderPath(it.toString())
            }
        }
    }
    
    Scaffold(
        containerColor = Background,
        bottomBar = {
            BottomNavBar(
                selectedItem = NavItem.Profile,
                onItemSelected = { item ->
                    when (item) {
                        NavItem.Library -> onLibraryClick()
                        NavItem.Player -> onPlayerClick()
                        NavItem.Profile -> onProfileClick()
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
            // Header with back button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 24.dp, bottom = 32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Surface2)
                            .clickable(onClick = onBackClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = "Back",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.displaySmall,
                        color = TextPrimary
                    )
                }
            }
            
            // Storage section
            item {
                SettingsSection(
                    title = "Storage",
                    items = listOf(
                        SettingItemData(
                            icon = Icons.Outlined.Folder,
                            label = "Audiobook Folder",
                            type = SettingType.Action,
                            onClick = { folderPicker.launch(null) },
                            showValue = currentFolderPath
                        )
                    )
                )
            }
            
            // Playback section
            item {
                SettingsSection(
                    title = "Playback",
                    items = listOf(
                        SettingItemData(
                            icon = Icons.Outlined.VolumeUp,
                            label = "Audio Quality",
                            type = SettingType.Select("High")
                        ),
                        SettingItemData(
                            icon = Icons.Outlined.Download,
                            label = "Auto-Download",
                            type = SettingType.Toggle(autoDownload) { scope.launch { preferencesRepository.setAutoDownload(it) } }
                        )
                    )
                )
            }
            
            // Appearance section
            item {
                SettingsSection(
                    title = "Appearance",
                    items = listOf(
                        SettingItemData(
                            icon = Icons.Outlined.DarkMode,
                            label = "Dark Mode",
                            type = SettingType.Toggle(darkMode) { scope.launch { preferencesRepository.setDarkMode(it) } }
                        )
                    )
                )
            }
            
            // Notifications section
            item {
                SettingsSection(
                    title = "Notifications",
                    items = listOf(
                        SettingItemData(
                            icon = Icons.Outlined.Notifications,
                            label = "Push Notifications",
                            type = SettingType.Toggle(notifications) { scope.launch { preferencesRepository.setNotificationsEnabled(it) } }
                        )
                    )
                )
            }
            
            // Account section
            item {
                SettingsSection(
                    title = "Account",
                    items = listOf(
                        SettingItemData(
                            icon = Icons.Outlined.Logout,
                            label = "Sign Out",
                            type = SettingType.Action,
                            isDanger = true
                        )
                    )
                )
            }
            
            // Version info
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Version 1.0.0",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary
                    )
                }
            }
        }
    }
}

sealed class SettingType {
    data class Toggle(val value: Boolean, val onValueChange: (Boolean) -> Unit) : SettingType()
    data class Select(val value: String) : SettingType()
    data object Action : SettingType()
}

data class SettingItemData(
    val icon: ImageVector,
    val label: String,
    val type: SettingType,
    val isDanger: Boolean = false,
    val onClick: (() -> Unit)? = null,
    val showValue: String? = null
)

@Composable
private fun SettingsSection(
    title: String,
    items: List<SettingItemData>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface2,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column {
                items.forEachIndexed { index, item ->
                    SettingRow(item = item)
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = Surface4
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingRow(item: SettingItemData) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = item.type is SettingType.Action || item.onClick != null) { 
                    item.onClick?.invoke()
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null,
                tint = if (item.isDanger) ErrorRed else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = item.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isDanger) ErrorRed else TextPrimary,
                modifier = Modifier.weight(1f)
            )
            
            when (val type = item.type) {
                is SettingType.Toggle -> {
                    ToggleSwitch(
                        checked = type.value,
                        onCheckedChange = type.onValueChange
                    )
                }
                is SettingType.Select -> {
                    Text(
                        text = type.value,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextTertiary
                    )
                }
                SettingType.Action -> {
                    if (item.onClick != null) {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = "Browse",
                            tint = TextTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        
        // Show current folder path if available
        item.showValue?.let { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = TextTertiary,
                modifier = Modifier.padding(start = 48.dp, end = 16.dp, bottom = 16.dp)
            )
        }
    }
}

package com.audiobook.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import com.audiobook.app.data.repository.NotificationRepository
import com.audiobook.app.service.NotificationScheduler
import com.audiobook.app.ui.components.BottomNavBar
import com.audiobook.app.ui.components.NavItem
import com.audiobook.app.ui.components.NotificationPermissionCard
import com.audiobook.app.ui.components.ToggleSwitch
import com.audiobook.app.ui.components.rememberNotificationPermissionState
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
    val notificationRepository = remember { NotificationRepository(context) }
    val notificationScheduler = remember { NotificationScheduler(context) }
    
    // Get playing state from audio player
    val isPlaying by context.appContainer.audiobookPlayer.isPlaying.collectAsState()
    
    // Notification permission state
    val hasNotificationPermission = rememberNotificationPermissionState()
    
    // Notification preferences
    val notificationPrefs by notificationRepository.preferences.collectAsState(
        initial = com.audiobook.app.data.repository.NotificationPreferences()
    )
    
    // Persisted preferences
    val darkMode by preferencesRepository.darkMode.collectAsState(initial = true)
    val autoDownload by preferencesRepository.autoDownload.collectAsState(initial = false)
    val currentFolderPath by preferencesRepository.audiobookFolderPath
        .map { it?.takeIf { p -> p.isNotBlank() } ?: "Default (Audiobooks)" }
        .collectAsState(initial = "Default (Audiobooks)")
    var showTimePickerDialog by remember { mutableStateOf(false) }
    
    // Permission launcher for Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            scope.launch {
                notificationRepository.refreshFcmToken()
                notificationRepository.subscribeToTopics()
            }
        }
    }
    
    // Time Picker Dialog for reminder time
    if (showTimePickerDialog) {
        TimePickerDialog(
            initialHour = notificationPrefs.reminderHour,
            initialMinute = notificationPrefs.reminderMinute,
            onDismiss = { showTimePickerDialog = false },
            onConfirm = { hour, minute ->
                scope.launch {
                    notificationRepository.setReminderTime(hour, minute)
                    notificationScheduler.scheduleDailyReminder(hour, minute)
                }
                showTimePickerDialog = false
            }
        )
    }
    
    // Folder picker
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
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
        containerColor = Surface1,
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Surface1
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary
                    )
                }
            }
        },
        bottomBar = {
            BottomNavBar(
                selectedItem = NavItem.Profile,
                onItemSelected = { item ->
                    when (item) {
                        NavItem.Library -> onLibraryClick()
                        NavItem.Player -> onPlayerClick()
                        NavItem.Profile -> onProfileClick()
                    }
                },
                hasPlayingIndicator = isPlaying
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 24.dp)
        ) {
            // Notification permission card
            if (!hasNotificationPermission) {
                item {
                    NotificationPermissionCard(
                        onRequestPermission = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
            
            // Notifications Section
            item {
                SettingsSection(
                    title = "Notifications",
                    items = listOf(
                        SettingItemData(
                            icon = Icons.Outlined.Notifications,
                            label = "Daily Reminders",
                            type = SettingType.Toggle(
                                value = notificationPrefs.remindersEnabled,
                                onValueChange = { enabled ->
                                    scope.launch {
                                        notificationRepository.setRemindersEnabled(enabled)
                                        if (enabled) {
                                            notificationScheduler.scheduleDailyReminder(
                                                notificationPrefs.reminderHour,
                                                notificationPrefs.reminderMinute
                                            )
                                        } else {
                                            notificationScheduler.cancelDailyReminder()
                                        }
                                    }
                                }
                            )
                        ),
                        SettingItemData(
                            icon = Icons.Outlined.Schedule,
                            label = "Reminder Time",
                            type = SettingType.Select(
                                value = String.format("%02d:%02d", notificationPrefs.reminderHour, notificationPrefs.reminderMinute)
                            ),
                            onClick = { showTimePickerDialog = true }
                        ),
                        SettingItemData(
                            icon = Icons.Outlined.EmojiEvents,
                            label = "Milestone Notifications",
                            type = SettingType.Toggle(
                                value = notificationPrefs.milestonesEnabled,
                                onValueChange = { enabled ->
                                    scope.launch {
                                        notificationRepository.setMilestonesEnabled(enabled)
                                    }
                                }
                            )
                        ),
                        SettingItemData(
                            icon = Icons.Outlined.LocalFireDepartment,
                            label = "Streak Reminders",
                            type = SettingType.Toggle(
                                value = notificationPrefs.streaksEnabled,
                                onValueChange = { enabled ->
                                    scope.launch {
                                        notificationRepository.setStreaksEnabled(enabled)
                                    }
                                }
                            )
                        )
                    )
                )
            }
            
            // App Settings Section
            item {
                SettingsSection(
                    title = "App Settings",
                    items = listOf(
                        SettingItemData(
                            icon = Icons.Outlined.Folder,
                            label = "Audiobook Folder",
                            type = SettingType.Action,
                            onClick = { folderPickerLauncher.launch(null) },
                            showValue = currentFolderPath
                        ),
                        SettingItemData(
                            icon = Icons.Outlined.DarkMode,
                            label = "Dark Mode",
                            type = SettingType.Toggle(value = darkMode, onValueChange = { scope.launch { preferencesRepository.setDarkMode(it) } })
                        ),
                        SettingItemData(
                            icon = Icons.Outlined.Download,
                            label = "Auto Download Covers",
                            type = SettingType.Toggle(value = autoDownload, onValueChange = { scope.launch { preferencesRepository.setAutoDownload(it) } })
                        )
                    )
                )
            }
            
            // Developer Testing Section
            item {
                NotificationTestingSection(
                    notificationScheduler = notificationScheduler
                )
            }
            
            // Account Section
            item {
                SettingsSection(
                    title = "Account",
                    items = listOf(
                        SettingItemData(
                            icon = Icons.Outlined.Logout,
                            label = "Sign Out",
                            type = SettingType.Action,
                            isDanger = true,
                            onClick = {
                                scope.launch {
                                    context.appContainer.authRepository.signOut()
                                }
                            }
                        )
                    )
                )
            }
        }
    }
}

/**
 * Time picker dialog for setting notification reminder time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = false
    )
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Reminder Time") },
        text = {
            TimePicker(
                state = timePickerState,
                colors = TimePickerDefaults.colors(
                    clockDialColor = Surface3,
                    selectorColor = MaterialTheme.colorScheme.primary,
                    containerColor = Surface2,
                    periodSelectorBorderColor = MaterialTheme.colorScheme.primary,
                    clockDialSelectedContentColor = TextPrimary,
                    clockDialUnselectedContentColor = TextSecondary,
                    periodSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                    periodSelectorUnselectedContainerColor = Surface3,
                    periodSelectorSelectedContentColor = TextPrimary,
                    periodSelectorUnselectedContentColor = TextSecondary,
                    timeSelectorSelectedContainerColor = MaterialTheme.colorScheme.primary,
                    timeSelectorUnselectedContainerColor = Surface3,
                    timeSelectorSelectedContentColor = TextPrimary,
                    timeSelectorUnselectedContentColor = TextSecondary
                )
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(timePickerState.hour, timePickerState.minute)
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        containerColor = Surface2
    )
}

/**
 * Notification Testing Section - for development/testing purposes.
 * Provides buttons to test all notification types.
 */
@Composable
private fun NotificationTestingSection(
    notificationScheduler: NotificationScheduler
) {
    var showTestDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp)
    ) {
        Text(
            text = "NOTIFICATION TESTING",
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary,
            modifier = Modifier.padding(start = 8.dp, bottom = 12.dp)
        )
        
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Surface2,
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Test all notification types to verify they're working correctly",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                Button(
                    onClick = { showTestDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Science,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test All Notifications")
                }
            }
        }
    }
    
    // Test notification dialog
    if (showTestDialog) {
        NotificationTestDialog(
            onDismiss = { showTestDialog = false },
            notificationScheduler = notificationScheduler
        )
    }
}

/**
 * Dialog with individual buttons for testing each notification type.
 */
@Composable
private fun NotificationTestDialog(
    onDismiss: () -> Unit,
    notificationScheduler: NotificationScheduler
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Test Notifications")
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    Text(
                        text = "Tap any button to send that notification type:",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                // Daily Reminder Test
                item {
                    TestNotificationButton(
                        icon = Icons.Outlined.Schedule,
                        label = "Daily Reminder",
                        description = "Time to listen",
                        onClick = {
                            notificationScheduler.showMilestoneNotification(
                                milestoneType = "listening_reminder",
                                message = "Continue listening to your audiobook! 📚"
                            )
                        }
                    )
                }
                
                // Streak Reminder Test
                item {
                    TestNotificationButton(
                        icon = Icons.Outlined.LocalFireDepartment,
                        label = "Streak Reminder",
                        description = "Keep your streak alive",
                        onClick = {
                            notificationScheduler.showMilestoneNotification(
                                milestoneType = "streak_reminder",
                                message = "You have a 7 day listening streak! Listen today to keep it going! 🔥"
                            )
                        }
                    )
                }
                
                // Book Completed Test
                item {
                    TestNotificationButton(
                        icon = Icons.Outlined.CheckCircle,
                        label = "Book Completed",
                        description = "Finished a book",
                        onClick = {
                            notificationScheduler.showMilestoneNotification(
                                milestoneType = "book_completed",
                                message = "You've finished \"The Great Gatsby\"! Time to discover your next adventure. 🎉"
                            )
                        }
                    )
                }
                
                // Books Milestone Test
                item {
                    TestNotificationButton(
                        icon = Icons.Outlined.MenuBook,
                        label = "Books Milestone",
                        description = "10 books completed",
                        onClick = {
                            notificationScheduler.showMilestoneNotification(
                                milestoneType = "books_milestone",
                                message = "Amazing! You've completed 10 audiobooks! 📚"
                            )
                        }
                    )
                }
                
                // Hours Milestone Test
                item {
                    TestNotificationButton(
                        icon = Icons.Outlined.Headphones,
                        label = "Hours Milestone",
                        description = "50 hours listened",
                        onClick = {
                            notificationScheduler.showMilestoneNotification(
                                milestoneType = "hours_milestone",
                                message = "Wow! You've listened to 50 hours of audiobooks! 🎧"
                            )
                        }
                    )
                }
                
                // Streak Milestone Test
                item {
                    TestNotificationButton(
                        icon = Icons.Outlined.EmojiEvents,
                        label = "Streak Milestone",
                        description = "30 day streak",
                        onClick = {
                            notificationScheduler.showMilestoneNotification(
                                milestoneType = "streak_milestone",
                                message = "Amazing! You've maintained a 30 day listening streak! 🔥"
                            )
                        }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "💡 Swipe down from the top of your screen to see notifications in the notification drawer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
        containerColor = Surface2
    )
}

/**
 * Individual test notification button.
 */
@Composable
private fun TestNotificationButton(
    icon: ImageVector,
    label: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Surface3,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }
            Icon(
                imageVector = Icons.Outlined.Send,
                contentDescription = "Send",
                tint = TextTertiary,
                modifier = Modifier.size(20.dp)
            )
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

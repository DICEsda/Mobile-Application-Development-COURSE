package com.audiobook.app.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.audiobook.app.appContainer
import com.audiobook.app.data.model.UserProfile
import com.audiobook.app.data.model.UserStats
import com.audiobook.app.data.repository.NotificationRepository
import com.audiobook.app.service.NotificationScheduler
import com.audiobook.app.ui.components.BottomNavBar
import com.audiobook.app.ui.components.NavItem
import com.audiobook.app.ui.components.NotificationPermissionCard
import com.audiobook.app.ui.components.rememberNotificationPermissionState
import com.audiobook.app.ui.theme.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLibraryClick: () -> Unit,
    onPlayerClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = context.appContainer.preferencesRepository
    val notificationRepository = remember { NotificationRepository(context) }
    val notificationScheduler = remember { NotificationScheduler(context) }

    // Auth state
    val authUser by context.appContainer.authRepository.authState.collectAsState(initial = null)
    val profile = if (authUser != null) {
        UserProfile.default.copy(
            name = authUser?.displayName ?: authUser?.email?.substringBefore("@") ?: "User",
            email = authUser?.email ?: "Not signed in"
        )
    } else {
        UserProfile.default
    }
    val isSignedIn = context.appContainer.authRepository.isSignedIn

    // Playing state
    val isPlaying by context.appContainer.audiobookPlayer.isPlaying.collectAsState()

    // Notification state
    val hasNotificationPermission = rememberNotificationPermissionState()
    val notificationPrefs by notificationRepository.preferences.collectAsState(
        initial = com.audiobook.app.data.repository.NotificationPreferences()
    )

    // Preferences
    val currentFolderPath by preferencesRepository.audiobookFolderPath
        .map { it?.takeIf { p -> p.isNotBlank() } ?: "Default (Audiobooks)" }
        .collectAsState(initial = "Default (Audiobooks)")
    var showTimePickerDialog by remember { mutableStateOf(false) }

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

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            try {
                context.contentResolver.takePersistableUriPermission(it, takeFlags)
            } catch (e: Exception) {
                android.util.Log.w("ProfileScreen", "Could not take persistable URI permission", e)
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
                        NavItem.Profile -> {}
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
                        text = "Settings",
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

            // Stats
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

            // Notifications section
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
                                    scope.launch { notificationRepository.setMilestonesEnabled(enabled) }
                                }
                            )
                        ),
                        SettingItemData(
                            icon = Icons.Outlined.LocalFireDepartment,
                            label = "Streak Reminders",
                            type = SettingType.Toggle(
                                value = notificationPrefs.streaksEnabled,
                                onValueChange = { enabled ->
                                    scope.launch { notificationRepository.setStreaksEnabled(enabled) }
                                }
                            )
                        )
                    )
                )
            }

            // App Settings section
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
                        )
                    )
                )
            }

            // Notification testing
            item {
                NotificationTestingSection(notificationScheduler = notificationScheduler)
            }

            // Account section
            if (isSignedIn) {
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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

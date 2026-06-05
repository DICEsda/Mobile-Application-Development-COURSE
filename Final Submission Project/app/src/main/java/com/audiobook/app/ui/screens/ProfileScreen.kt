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
    onBackClick: () -> Unit = {},
    onSignInClick: () -> Unit = {},
    onSignUpClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val preferencesRepository = context.appContainer.preferencesRepository
    val notificationRepository = remember { NotificationRepository(context) }
    val notificationScheduler = remember { NotificationScheduler(context) }

    // Playing state
    val isPlaying by context.appContainer.audiobookPlayer.isPlaying.collectAsState()

    // Notification state
    val hasNotificationPermission = rememberNotificationPermissionState()
    val notificationPrefs by notificationRepository.preferences.collectAsState(
        initial = com.audiobook.app.data.repository.NotificationPreferences()
    )

    // Biometric lock preference
    val biometricEnabled by preferencesRepository.rememberBiometric
        .collectAsState(initial = false)

    // Preferences
    val currentFolderPath by preferencesRepository.audiobookFolderPath
        .map { it?.takeIf { p -> p.isNotBlank() } ?: "Default (Audiobooks)" }
        .collectAsState(initial = "Default (Audiobooks)")
    var showTimePickerDialog by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Local notifications only — nothing to register once permission is granted.
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
                            icon = Icons.Outlined.Fingerprint,
                            label = "Biometric Lock",
                            type = SettingType.Toggle(
                                value = biometricEnabled,
                                onValueChange = { enabled ->
                                    scope.launch {
                                        preferencesRepository.setRememberBiometric(enabled)
                                    }
                                }
                            )
                        ),
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

            // Book Companion (AI) section
            item {
                BookCompanionSettingsSection()
            }

            // Notification testing
            item {
                NotificationTestingSection(notificationScheduler = notificationScheduler)
            }
        }
    }
}


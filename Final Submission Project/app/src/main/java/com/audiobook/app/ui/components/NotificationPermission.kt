package com.audiobook.app.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/**
 * Composable for handling notification permissions on Android 13+.
 * 
 * Shows a permission request dialog and handles the permission flow.
 */
@Composable
fun NotificationPermissionHandler(
    onPermissionGranted: () -> Unit,
    onPermissionDenied: () -> Unit = {}
) {
    val context = LocalContext.current
    
    // Check if we need to request permission (Android 13+)
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    
    if (!needsPermission) {
        // No permission needed on older Android versions
        LaunchedEffect(Unit) {
            onPermissionGranted()
        }
        return
    }
    
    // Check current permission status
    val hasPermission = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }
    
    var permissionGranted by remember { mutableStateOf(hasPermission) }
    var showRationale by remember { mutableStateOf(false) }
    
    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionGranted = isGranted
        if (isGranted) {
            onPermissionGranted()
        } else {
            showRationale = true
            onPermissionDenied()
        }
    }
    
    // If permission already granted, notify callback
    LaunchedEffect(permissionGranted) {
        if (permissionGranted) {
            onPermissionGranted()
        }
    }
    
    // Show rationale dialog if permission was denied
    if (showRationale) {
        NotificationPermissionRationaleDialog(
            onDismiss = { showRationale = false },
            onRequestAgain = {
                showRationale = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        )
    }
    
    // Request permission if not granted
    if (!permissionGranted && !showRationale) {
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * Dialog explaining why notification permission is needed.
 */
@Composable
private fun NotificationPermissionRationaleDialog(
    onDismiss: () -> Unit,
    onRequestAgain: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text("Enable Notifications")
        },
        text = {
            Text(
                text = "Stay on track with your audiobook listening! Enable notifications to receive:\n\n" +
                        "• Daily listening reminders\n" +
                        "• Streak maintenance alerts\n" +
                        "• Book completion celebrations\n\n" +
                        "You can customize notification preferences in Settings.",
                textAlign = TextAlign.Start
            )
        },
        confirmButton = {
            Button(onClick = onRequestAgain) {
                Text("Enable")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}

/**
 * Composable card for requesting notification permission within Settings.
 */
@Composable
fun NotificationPermissionCard(
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    // Check if permission is needed and not granted
    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
    
    if (!needsPermission) {
        return // Permission already granted or not needed
    }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Text(
                text = "Notification Permission Required",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                textAlign = TextAlign.Center
            )
            
            Text(
                text = "Enable notifications to receive listening reminders and achievement alerts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )
            
            Button(
                onClick = onRequestPermission,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Enable Notifications")
            }
        }
    }
}

/**
 * Check if notification permission is granted.
 */
@Composable
fun rememberNotificationPermissionState(): Boolean {
    val context = LocalContext.current
    
    return remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // No permission needed on older versions
        }
    }
}

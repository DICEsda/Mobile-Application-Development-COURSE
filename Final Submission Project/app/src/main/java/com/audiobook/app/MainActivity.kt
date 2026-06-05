package com.audiobook.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.audiobook.app.navigation.AppNavigation
import com.audiobook.app.navigation.Screen
import com.audiobook.app.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class MainActivity : FragmentActivity() {

    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo
    private var onAuthSuccess: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setupBiometricAuth()

        val biometricEnabled = runBlocking {
            appContainer.preferencesRepository.rememberBiometric.first()
        }
        val disclaimerAlreadyAccepted = runBlocking {
            appContainer.preferencesRepository.disclaimerAccepted.first()
        }

        setContent {
            PremiumAudiobookAppTheme {
                val scope = rememberCoroutineScope()
                var isAuthenticated by remember { mutableStateOf(!biometricEnabled) }
                var permissionsGranted by remember { mutableStateOf(hasRequiredPermissions()) }
                var disclaimerAccepted by remember { mutableStateOf(disclaimerAlreadyAccepted) }
                val navController = rememberNavController()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    if (!disclaimerAccepted) {
                        DisclaimerDialog(
                            onAccept = {
                                scope.launch {
                                    appContainer.preferencesRepository.setDisclaimerAccepted(true)
                                }
                                disclaimerAccepted = true
                            }
                        )
                    } else if (!permissionsGranted) {
                        PermissionRequestScreen(
                            onPermissionsGranted = { permissionsGranted = true }
                        )
                    } else {
                        AppNavigation(
                            navController = navController,
                            isAuthenticated = isAuthenticated,
                            onAuthenticate = {
                                authenticate { success ->
                                    if (success) {
                                        isAuthenticated = true
                                        navController.navigate(Screen.Library.route) {
                                            popUpTo(Screen.LibraryLocked.route) { inclusive = true }
                                        }
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return audioPermission == PackageManager.PERMISSION_GRANTED
    }

    private fun setupBiometricAuth() {
        val executor = ContextCompat.getMainExecutor(this)

        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess?.invoke()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            })
    }

    private fun authenticate(onResult: (Boolean) -> Unit) {
        val biometricManager = BiometricManager.from(this)
        val canBiometric = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        val canDeviceCredential = biometricManager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL)

        onAuthSuccess = { onResult(true) }

        when {
            canBiometric == BiometricManager.BIOMETRIC_SUCCESS -> {
                // Strong biometric available — prefer fingerprint/face
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Library")
                    .setSubtitle("Use your fingerprint to access your audiobooks")
                    .setNegativeButtonText("Use PIN instead")
                    .build()
                biometricPrompt.authenticate(promptInfo)
            }
            canDeviceCredential == BiometricManager.BIOMETRIC_SUCCESS -> {
                // No biometric but device has PIN/pattern/password — fall back
                promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Library")
                    .setSubtitle("Verify your identity to access your audiobooks")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()
                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                // No secure lock screen at all — deny access
                onResult(false)
                android.widget.Toast.makeText(
                    this,
                    "Please set up a screen lock (PIN, pattern, or fingerprint) in device settings to use this feature",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}

/**
 * One-time, non-dismissable Terms & Disclaimer modal shown on first launch.
 * The user must explicitly agree before they can use the app; acceptance is
 * persisted so it is not shown again.
 */
@Composable
private fun DisclaimerDialog(onAccept: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* Non-dismissable: agreement is required. */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
        containerColor = Surface2,
        title = {
            Text(
                text = "Terms of Use & Disclaimer",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "This application is a personal audiobook player. It plays only the " +
                        "audio files that you choose to add from your own device or storage.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "The developer does not host, supply, distribute, or control any audio " +
                        "content, and accepts no responsibility or liability for the material you " +
                        "import, play, or manage within the app. You are solely responsible for the " +
                        "content you add and for ensuring you hold the necessary rights to access and " +
                        "play it, in compliance with applicable copyright laws and any third-party terms.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "Any AI-generated responses are provided for convenience only, may be " +
                        "inaccurate or incomplete, and should not be relied upon as professional advice.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Text(
                    text = "The app is provided \"as is\", without warranties of any kind. By selecting " +
                        "\"I Understand and Agree\", you acknowledge and accept these terms.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "I Understand and Agree",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
            }
        }
    )
}

@Composable
private fun PermissionRequestScreen(onPermissionsGranted: () -> Unit) {
    val context = LocalContext.current

    val permissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.READ_MEDIA_AUDIO)
            add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }.toTypedArray()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            results[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            results[Manifest.permission.READ_EXTERNAL_STORAGE] == true
        }
        if (audioGranted) {
            onPermissionsGranted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                text = "Welcome",
                style = MaterialTheme.typography.displaySmall,
                color = TextPrimary
            )

            Text(
                text = "To play your audiobooks, the app needs access to audio files on your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Text(
                    text = "We'll also ask for notification permissions so we can send you listening reminders and streak updates.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextTertiary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { launcher.launch(permissions) },
                colors = ButtonDefaults.buttonColors(containerColor = AccentTeal),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = "Grant Permissions",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

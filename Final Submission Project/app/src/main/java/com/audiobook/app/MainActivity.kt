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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.audiobook.app.navigation.AppNavigation
import com.audiobook.app.navigation.Screen
import com.audiobook.app.ui.theme.*
import kotlinx.coroutines.flow.first
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

        setContent {
            PremiumAudiobookAppTheme {
                var isAuthenticated by remember { mutableStateOf(!biometricEnabled) }
                var permissionsGranted by remember { mutableStateOf(hasRequiredPermissions()) }
                val navController = rememberNavController()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
                    if (!permissionsGranted) {
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

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Library")
            .setSubtitle("Use your fingerprint to access your audiobooks")
            .setNegativeButtonText("Cancel")
            .build()
    }

    private fun authenticate(onResult: (Boolean) -> Unit) {
        val biometricManager = BiometricManager.from(this)

        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                onAuthSuccess = { onResult(true) }
                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                onResult(false)
                android.widget.Toast.makeText(
                    this,
                    "Biometric authentication is required but not available on this device",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    }
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
                colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
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

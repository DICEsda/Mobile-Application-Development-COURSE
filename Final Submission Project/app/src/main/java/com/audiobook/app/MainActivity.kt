package com.audiobook.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.rememberNavController
import com.audiobook.app.navigation.AppNavigation
import com.audiobook.app.navigation.Screen
import com.audiobook.app.ui.theme.Background
import com.audiobook.app.ui.theme.PremiumAudiobookAppTheme
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
        
        // Check if biometric lock is enabled (default: off)
        val biometricEnabled = runBlocking {
            appContainer.preferencesRepository.rememberBiometric.first()
        }
        
        setContent {
            PremiumAudiobookAppTheme {
                // If biometrics are disabled, skip the lock screen entirely
                var isAuthenticated by remember { mutableStateOf(!biometricEnabled) }
                val navController = rememberNavController()
                
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Background
                ) {
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
    
    private fun setupBiometricAuth() {
        val executor = ContextCompat.getMainExecutor(this)
        
        biometricPrompt = BiometricPrompt(this, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // Only allow bypass on user cancellation if device has no biometric
                    // Do NOT invoke success - user must authenticate
                }
                
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onAuthSuccess?.invoke()
                }
                
                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    // Authentication failed, user can try again
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
                // Biometric not available, bypass for demo
                onResult(true)
            }
        }
    }
}

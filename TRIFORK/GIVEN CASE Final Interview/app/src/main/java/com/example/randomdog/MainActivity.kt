package com.example.randomdog

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.example.randomdog.ui-layer.navigation.RandomDogApp
import com.example.randomdog.ui-layer.theme.RandomDogTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            RandomDogTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RandomDogApp()
                }
            }
        }
    }
}

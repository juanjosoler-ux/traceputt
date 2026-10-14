package com.puttvision

import android.app.Application
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.puttvision.feature.onboarding.OnboardingScreen
import com.puttvision.feature.onboarding.OnboardingViewModel
import com.puttvision.navigation.PuttVisionNavHost
import com.puttvision.ui.theme.PuttVisionTheme
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PuttVisionApp : Application()

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            PuttVisionTheme {
                AppRoot()
            }
        }
    }
}

@Composable
private fun AppRoot() {
    val onboardingVm: OnboardingViewModel = hiltViewModel()
    val isDone by onboardingVm.isOnboardingDone.collectAsStateWithLifecycle()

    // isDone is null while loading from DataStore — show nothing to avoid flash
    when (isDone) {
        null  -> { /* loading */ }
        false -> OnboardingScreen(
            onFinish = { onboardingVm.markDone() }
        )
        true  -> PuttVisionNavHost()
    }
}

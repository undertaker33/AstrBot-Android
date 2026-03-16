package com.astrbot.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.astrbot.android.data.AppPreferencesRepository
import com.astrbot.android.data.AppSettings
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.AstrBotApp
import com.astrbot.android.ui.theme.AstrBotTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val appPreferencesRepository = AppPreferencesRepository(applicationContext)
        setContent {
            val appSettings by appPreferencesRepository.settings.collectAsState(
                initial = AppSettings(
                    qqEnabled = true,
                    napCatContainerEnabled = true,
                    preferredChatProvider = "",
                ),
            )
            AstrBotTheme(themeMode = appSettings.themeMode) {
                SideEffect {
                    window.statusBarColor = MonochromeUi.pageBackground.toArgb()
                    WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = !MonochromeUi.isDarkTheme
                }
                AstrBotApp()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleScope.launch {
            maybeAutoStartBridge()
        }
    }

    private fun maybeAutoStartBridge() {
        val bridgeConfig = NapCatBridgeRepository.config.value
        val runtimeState = NapCatBridgeRepository.runtimeState.value
        if (!bridgeConfig.autoStart) return
        if (runtimeState.status == "Running" || runtimeState.status == "Starting") return

        RuntimeLogRepository.append("Bridge auto-start triggered from app launch")
        ContainerBridgeController.start(applicationContext)
    }
}

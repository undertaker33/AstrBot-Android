package com.astrbot.android

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.astrbot.android.data.AppPreferencesRepository
import com.astrbot.android.data.AppSettings
import com.astrbot.android.data.NapCatBridgeRepository
import com.astrbot.android.data.ThemeMode
import com.astrbot.android.runtime.ContainerBridgeController
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.ui.AstrBotApp
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.theme.AstrBotTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        private val LightTransitionColor = Color(0xFFF7F7F7)
        private val DarkTransitionColor = Color(0xFF071226)
    }

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

            val systemIsDark = isSystemInDarkTheme()
            var appliedThemeMode by remember { mutableStateOf(appSettings.themeMode) }
            var themeTransitionInitialized by remember { mutableStateOf(false) }

            val overlayAlpha = remember { Animatable(0f) }
            var overlayColor by remember { mutableStateOf(Color.Transparent) }

            LaunchedEffect(appSettings.themeMode, systemIsDark) {
                if (!themeTransitionInitialized) {
                    appliedThemeMode = appSettings.themeMode
                    themeTransitionInitialized = true
                    return@LaunchedEffect
                }

                val targetThemeMode = appSettings.themeMode
                val currentAppliedMode = appliedThemeMode

                val currentIsDark = resolveIsDark(
                    themeMode = currentAppliedMode,
                    systemIsDark = systemIsDark,
                )
                val targetIsDark = resolveIsDark(
                    themeMode = targetThemeMode,
                    systemIsDark = systemIsDark,
                )

                if (currentIsDark == targetIsDark && targetThemeMode == currentAppliedMode) {
                    return@LaunchedEffect
                }

                overlayColor = if (targetIsDark) {
                    DarkTransitionColor
                } else {
                    LightTransitionColor
                }
                WindowInsetsControllerCompat(
                    window,
                    window.decorView,
                ).isAppearanceLightStatusBars = !targetIsDark

                overlayAlpha.stop()
                overlayAlpha.snapTo(0f)

                overlayAlpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 110,
                        easing = FastOutLinearInEasing,
                    ),
                )

                appliedThemeMode = targetThemeMode

                withFrameNanos { }
                withFrameNanos { }

                overlayAlpha.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = 240,
                        easing = LinearOutSlowInEasing,
                    ),
                )
            }

            AstrBotTheme(themeMode = appliedThemeMode) {
                SideEffect {
                    window.statusBarColor = AndroidColor.TRANSPARENT

                    val iconBackground = if (overlayAlpha.value > 0f) {
                        overlayColor
                    } else {
                        MonochromeUi.pageBackground
                    }

                    WindowInsetsControllerCompat(
                        window,
                        window.decorView,
                    ).isAppearanceLightStatusBars = iconBackground.luminance() > 0.5f
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MonochromeUi.pageBackground),
                ) {
                    AstrBotApp()

                    if (overlayAlpha.value > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    alpha = overlayAlpha.value
                                }
                                .background(overlayColor),
                        )
                    }
                }
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

    private fun resolveIsDark(
        themeMode: ThemeMode,
        systemIsDark: Boolean,
    ): Boolean {
        return when (themeMode) {
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
            ThemeMode.SYSTEM -> systemIsDark
        }
    }
}

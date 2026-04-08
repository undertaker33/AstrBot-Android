package com.astrbot.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.astrbot.android.data.AppPreferencesRepository
import com.astrbot.android.data.AppSettings
import com.astrbot.android.data.ThemeMode
import com.astrbot.android.di.MainActivityDependencies
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.ui.AstrBotApp
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.theme.AstrBotTheme
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val appDependencies: MainActivityDependencies
        get() = (application as AstrBotApplication).appContainer.mainActivityDependencies
    private var notificationPermissionRequested = false
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { _ ->
        notificationPermissionRequested = true
    }

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

        if (savedInstanceState == null) {
            handlePluginDeepLink(intent?.dataString)
        }
    }

    override fun onStart() {
        super.onStart()
        maybeRequestNotificationPermission()
        lifecycleScope.launch {
            maybeAutoStartBridge()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handlePluginDeepLink(intent.dataString)
    }

    private fun maybeAutoStartBridge() {
        if (!shouldAutoStartBridgeForTests(appDependencies.autoStartEnabled, appDependencies.runtimeState)) return

        appDependencies.log("Bridge auto-start triggered from app launch")
        appDependencies.startBridge(applicationContext)
    }

    private fun maybeRequestNotificationPermission() {
        if (notificationPermissionRequested) return
        val permissionGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!shouldRequestNotificationPermissionForTests(Build.VERSION.SDK_INT, permissionGranted)) {
            notificationPermissionRequested = permissionGranted
            return
        }
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun handlePluginDeepLink(rawDeepLink: String?) {
        val installIntent = parsePluginInstallIntentFromDeepLink(rawDeepLink) ?: return
        lifecycleScope.launch {
            runCatching {
                appDependencies.handlePluginInstallIntent(installIntent)
            }.onFailure { error ->
                appDependencies.log(
                    "Plugin deep link failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
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

internal fun shouldAutoStartBridgeForTests(
    autoStartEnabled: Boolean,
    runtimeState: NapCatRuntimeState,
): Boolean {
    return autoStartEnabled && !runtimeState.blocksAutoStart()
}

internal fun shouldRequestNotificationPermissionForTests(
    sdkInt: Int,
    permissionGranted: Boolean,
): Boolean {
    return sdkInt >= Build.VERSION_CODES.TIRAMISU && !permissionGranted
}

internal fun parsePluginInstallIntentFromDeepLink(rawDeepLink: String?): PluginInstallIntent? {
    if (rawDeepLink.isNullOrBlank()) return null
    val uri = runCatching { URI(rawDeepLink) }.getOrNull() ?: return null
    if (uri.scheme?.lowercase() != "astrbot") return null
    if (uri.host?.lowercase() != "plugin") return null
    val url = decodeQueryParameter(uri.rawQuery, "url") ?: return null
    return when (uri.path?.trim('/')) {
        "repository" -> runCatching { PluginInstallIntent.repositoryUrl(url) }.getOrNull()
        "install" -> runCatching { PluginInstallIntent.directPackageUrl(url) }.getOrNull()
        else -> null
    }
}

private fun decodeQueryParameter(rawQuery: String?, key: String): String? {
    if (rawQuery.isNullOrBlank()) return null
    return rawQuery.split("&")
        .asSequence()
        .mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            val name = part.substring(0, separator)
            val value = part.substring(separator + 1)
            name to value
        }
        .firstOrNull { (name, _) -> name == key }
        ?.second
        ?.let { value -> URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }
}

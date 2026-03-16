package com.astrbot.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import com.astrbot.android.data.ThemeMode
import com.astrbot.android.ui.MonochromeUi

private val LightColors = lightColorScheme(
    primary = Clay,
    secondary = Moss,
    background = Sand,
    surface = Color.White,
    surfaceVariant = Color(0xFFE7E7E4),
    onPrimary = Color.White,
    onBackground = Ink,
    onSurface = Ink,
    outline = Color(0xFFD4D4D0),
)

private val DarkColors = darkColorScheme(
    primary = Clay,
    secondary = Moss,
    background = Color(0xFF121212),
    surface = Color(0xFF1C1C1C),
    surfaceVariant = Color(0xFF262626),
    onPrimary = Color(0xFF111111),
    onBackground = Color(0xFFF5F5F5),
    onSurface = Color(0xFFF5F5F5),
    outline = Color(0xFF404040),
)

@Composable
fun AstrBotTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    MonochromeUi.isDarkTheme = darkTheme
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AstrBotTypography,
        content = content,
    )
}

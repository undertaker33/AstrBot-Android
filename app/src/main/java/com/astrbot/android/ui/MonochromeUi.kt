package com.astrbot.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object MonochromeUi {
    var isDarkTheme by mutableStateOf(false)

    val pageBackground get() = if (isDarkTheme) Color(0xFF0E1116) else Color(0xFFF3F3F1)
    val topBarSurface get() = if (isDarkTheme) Color(0xFF090C12) else Color(0xFFF3F3F1)
    val cardBackground get() = if (isDarkTheme) Color(0xFF171B22) else Color(0xFFEDEDEC)
    val cardAltBackground get() = if (isDarkTheme) Color(0xFF1D2430) else Color(0xFFE4E4E1)
    val mutedSurface get() = if (isDarkTheme) Color(0xFF232A36) else Color(0xFFE1E1DE)
    val elevatedSurface get() = if (isDarkTheme) Color(0xFF141922) else Color.White
    val inputBackground get() = if (isDarkTheme) Color(0xFF131820) else Color.White
    val drawerSurface get() = if (isDarkTheme) Color(0xFF12161D) else Color(0xFFF3F3F1)
    val divider get() = if (isDarkTheme) Color(0xFF252C38) else Color(0xFFD6D6D2)
    val textPrimary get() = if (isDarkTheme) Color(0xFFF3F6FB) else Color(0xFF111111)
    val textSecondary get() = if (isDarkTheme) Color(0xFF9EA7B4) else Color(0xFF666666)
    val border get() = if (isDarkTheme) Color(0xFF303745) else Color(0xFFD6D6D2)
    val strong get() = if (isDarkTheme) Color(0xFFE9EEF5) else Color(0xFF151515)
    val strongText get() = if (isDarkTheme) Color(0xFF111318) else Color.White
    val strongAlt get() = if (isDarkTheme) Color(0xFF2E3644) else Color(0xFF2A2A2A)
    val fabBackground get() = if (isDarkTheme) Color(0xFFE9EEF5) else Color(0xFF151515)
    val fabContent get() = if (isDarkTheme) Color(0xFF111318) else Color.White
    val activeIndicator get() = if (isDarkTheme) Color(0xFF2A303B) else Color(0xFFE8E8E5)
    val navBarBackground get() = if (isDarkTheme) Color(0xFF0B0E14) else Color.White
    val iconButtonSurface get() = if (isDarkTheme) Color(0xFF161C25) else Color.White
    val chipBackground get() = if (isDarkTheme) Color(0xFF1B212B) else Color.White
    val chipSelectedBackground get() = if (isDarkTheme) Color(0xFF252E3A) else Color(0xFFE8E8E5)
    val radiusCard = RoundedCornerShape(26.dp)
    val radiusInput = RoundedCornerShape(24.dp)
}

@Composable
fun monochromeOutlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MonochromeUi.textPrimary,
    unfocusedBorderColor = MonochromeUi.border,
    disabledBorderColor = MonochromeUi.border.copy(alpha = 0.6f),
    focusedLabelColor = MonochromeUi.textPrimary,
    unfocusedLabelColor = MonochromeUi.textSecondary,
    focusedTextColor = MonochromeUi.textPrimary,
    unfocusedTextColor = MonochromeUi.textPrimary,
    cursorColor = MonochromeUi.textPrimary,
    focusedLeadingIconColor = MonochromeUi.textSecondary,
    unfocusedLeadingIconColor = MonochromeUi.textSecondary,
    focusedPlaceholderColor = MonochromeUi.textSecondary,
    unfocusedPlaceholderColor = MonochromeUi.textSecondary,
    focusedContainerColor = MonochromeUi.inputBackground,
    unfocusedContainerColor = MonochromeUi.inputBackground,
)

@Composable
fun monochromeSwitchColors() = SwitchDefaults.colors(
    checkedThumbColor = if (MonochromeUi.isDarkTheme) Color(0xFF111318) else Color.White,
    checkedTrackColor = MonochromeUi.strong,
    checkedBorderColor = MonochromeUi.strong,
    uncheckedThumbColor = if (MonochromeUi.isDarkTheme) Color(0xFFE8ECF3) else Color.White,
    uncheckedTrackColor = if (MonochromeUi.isDarkTheme) Color(0xFF4B5565) else Color(0xFFD4D4D0),
    uncheckedBorderColor = if (MonochromeUi.isDarkTheme) Color(0xFF4B5565) else Color(0xFFD4D4D0),
)

@Composable
fun monochromeOutlinedButtonColors() = ButtonDefaults.outlinedButtonColors(
    containerColor = MonochromeUi.elevatedSurface,
    contentColor = MonochromeUi.textPrimary,
)

@Composable
fun monochromeOutlinedButtonBorder() = BorderStroke(1.dp, MonochromeUi.border)

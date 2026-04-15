package com.astrbot.android.ui.app

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class MonochromeUiTest {

    @Test
    fun dark_theme_fab_palette_stays_black_and_white() {
        assertEquals(Color(0xFF111111), monochromeActionFabBackgroundColor(isDarkTheme = true))
        assertEquals(Color.White, monochromeActionFabContentColor(isDarkTheme = true))
    }

    @Test
    fun light_theme_fab_palette_preserves_existing_contrast() {
        assertEquals(Color(0xFF151515), monochromeActionFabBackgroundColor(isDarkTheme = false))
        assertEquals(Color.White, monochromeActionFabContentColor(isDarkTheme = false))
    }
}

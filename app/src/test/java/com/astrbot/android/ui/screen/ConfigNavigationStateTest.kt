package com.astrbot.android.ui.screen

import com.astrbot.android.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigNavigationStateTest {
    @Test
    fun `current section prefers nearest visible offset`() {
        val sections = listOf(
            ConfigSection.ModelSettings,
            ConfigSection.SpeechSettings,
            ConfigSection.StreamingSettings,
        )

        val result = currentSectionFor(
            visibleSectionOffsets = listOf(
                ConfigSection.ModelSettings.name to -220,
                ConfigSection.SpeechSettings.name to -20,
                ConfigSection.StreamingSettings.name to 180,
            ),
            sections = sections,
        )

        assertEquals(ConfigSection.SpeechSettings, result)
    }

    @Test
    fun `toggle expanded group adds then removes group`() {
        val once = toggleExpandedGroup(emptySet(), R.string.config_nav_group_model)
        val twice = toggleExpandedGroup(once, R.string.config_nav_group_model)

        assertEquals(setOf(R.string.config_nav_group_model), once)
        assertEquals(emptySet<Int>(), twice)
    }
}

package com.astrbot.android.ui.screen

import com.astrbot.android.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfigNavigationStructureTest {
    @Test
    fun `model group exposes four independent settings modules`() {
        val groups = configDrawerGroups()
        val modelGroup = groups.first { it.titleRes == R.string.config_nav_group_model }

        assertEquals(
            listOf(
                ConfigSection.ModelSettings,
                ConfigSection.SpeechSettings,
                ConfigSection.StreamingSettings,
                ConfigSection.RuntimeHelpers,
                ConfigSection.KnowledgeBase,
                ConfigSection.ContextStrategy,
                ConfigSection.Automation,
            ),
            modelGroup.children,
        )
    }

    @Test
    fun `drawer groups follow requested category order and other is empty`() {
        val groups = configDrawerGroups()

        assertEquals(
            listOf(
                R.string.config_nav_group_model,
                R.string.config_nav_group_platform,
                R.string.config_nav_group_plugin,
                R.string.config_nav_group_other,
            ),
            groups.map { it.titleRes },
        )

        val otherGroup = groups.first { it.titleRes == R.string.config_nav_group_other }
        assertEquals(emptyList<ConfigSection>(), otherGroup.children)
    }
}

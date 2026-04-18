package com.astrbot.android.feature.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeLogCleanupRepositoryTest {

    @Test
    fun update_settings_enables_interval_cleanup_and_anchors_first_cleanup_time() {
        val store = InMemoryPluginRuntimeLogCleanupSettingsStore()
        PluginRuntimeLogCleanupRepository.setStoreOverrideForTests(store)
        try {
            PluginRuntimeLogCleanupRepository.updateSettings(
                pluginId = "plugin.demo",
                enabled = true,
                intervalHours = 2,
                intervalMinutes = 30,
                now = 5_000L,
            )

            val settings = PluginRuntimeLogCleanupRepository.settingsFor("plugin.demo")
            assertTrue(settings.enabled)
            assertEquals(2, settings.intervalHours)
            assertEquals(30, settings.intervalMinutes)
            assertEquals(5_000L, settings.lastCleanupAtEpochMillis)
        } finally {
            PluginRuntimeLogCleanupRepository.setStoreOverrideForTests(null)
        }
    }

    @Test
    fun maybe_auto_clear_runs_only_after_interval_and_updates_last_cleanup_time() {
        val store = InMemoryPluginRuntimeLogCleanupSettingsStore()
        PluginRuntimeLogCleanupRepository.setStoreOverrideForTests(store)
        try {
            PluginRuntimeLogCleanupRepository.updateSettings(
                pluginId = "plugin.demo",
                enabled = true,
                intervalHours = 1,
                intervalMinutes = 0,
                now = 1_000L,
            )

            var cleared = false
            val beforeInterval = PluginRuntimeLogCleanupRepository.maybeAutoClear(
                pluginId = "plugin.demo",
                now = 1_000L + 59 * 60 * 1000L,
            ) {
                cleared = true
            }
            assertFalse(beforeInterval)
            assertFalse(cleared)

            val afterInterval = PluginRuntimeLogCleanupRepository.maybeAutoClear(
                pluginId = "plugin.demo",
                now = 1_000L + 60 * 60 * 1000L,
            ) {
                cleared = true
            }
            assertTrue(afterInterval)
            assertTrue(cleared)
            assertEquals(
                1_000L + 60 * 60 * 1000L,
                PluginRuntimeLogCleanupRepository.settingsFor("plugin.demo").lastCleanupAtEpochMillis,
            )
        } finally {
            PluginRuntimeLogCleanupRepository.setStoreOverrideForTests(null)
        }
    }
}

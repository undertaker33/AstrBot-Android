package com.astrbot.android.core.runtime.container

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogCleanupRepositoryTest {

    @After
    fun tearDown() {
        RuntimeLogCleanupRepository.setStoreOverrideForTests(null)
    }

    @Test
    fun update_settings_starts_baseline_timestamp_when_enabled_for_first_time() {
        RuntimeLogCleanupRepository.setStoreOverrideForTests(InMemoryRuntimeLogCleanupSettingsStore())

        RuntimeLogCleanupRepository.updateSettings(
            enabled = true,
            intervalHours = 2,
            intervalMinutes = 30,
            now = 12_000L,
        )

        val settings = RuntimeLogCleanupRepository.settings.value
        assertTrue(settings.enabled)
        assertEquals(2, settings.intervalHours)
        assertEquals(30, settings.intervalMinutes)
        assertEquals(12_000L, settings.lastCleanupAtEpochMillis)
    }

    @Test
    fun maybe_auto_clear_runs_when_interval_is_reached() {
        RuntimeLogCleanupRepository.setStoreOverrideForTests(InMemoryRuntimeLogCleanupSettingsStore())
        RuntimeLogCleanupRepository.updateSettings(
            enabled = true,
            intervalHours = 0,
            intervalMinutes = 30,
            now = 1_000L,
        )

        var cleared = false
        val didClear = RuntimeLogCleanupRepository.maybeAutoClear(now = 31 * 60 * 1000L) {
            cleared = true
        }

        assertTrue(didClear)
        assertTrue(cleared)
        assertEquals(31 * 60 * 1000L, RuntimeLogCleanupRepository.settings.value.lastCleanupAtEpochMillis)
    }

    @Test
    fun record_cleanup_is_ignored_when_feature_is_disabled() {
        RuntimeLogCleanupRepository.setStoreOverrideForTests(InMemoryRuntimeLogCleanupSettingsStore())

        RuntimeLogCleanupRepository.recordCleanup(now = 9_999L)

        val settings = RuntimeLogCleanupRepository.settings.value
        assertFalse(settings.enabled)
        assertEquals(0L, settings.lastCleanupAtEpochMillis)
    }
}

package com.astrbot.android.data.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class ModuleBackupSupportTest {
    @Test
    fun `module import plan only replaces the selected module`() {
        val plan = moduleOnlyImportPlan(
            module = AppBackupModuleKind.BOTS,
            mode = AppBackupImportMode.REPLACE_ALL,
        )

        assertEquals(AppBackupImportMode.REPLACE_ALL, plan.bots)
        assertEquals(AppBackupImportMode.MERGE_SKIP_DUPLICATES, plan.providers)
        assertEquals(AppBackupImportMode.MERGE_SKIP_DUPLICATES, plan.personas)
        assertEquals(AppBackupImportMode.MERGE_SKIP_DUPLICATES, plan.configs)
        assertEquals(AppBackupImportMode.MERGE_SKIP_DUPLICATES, plan.conversations)
        assertEquals(AppBackupImportMode.MERGE_SKIP_DUPLICATES, plan.qqAccounts)
        assertEquals(AppBackupImportMode.MERGE_SKIP_DUPLICATES, plan.ttsAssets)
    }

    @Test
    fun `module conflict picker returns the matching preview slice`() {
        val preview = AppBackupConflictPreview(
            bots = AppBackupModuleConflict(duplicateCount = 2, newCount = 3),
            ttsAssets = AppBackupModuleConflict(duplicateCount = 1, newCount = 4),
        )

        assertEquals(preview.bots, moduleConflictFor(AppBackupModuleKind.BOTS, preview))
        assertEquals(preview.ttsAssets, moduleConflictFor(AppBackupModuleKind.TTS_ASSETS, preview))
    }
}

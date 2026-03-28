package com.astrbot.android.ui.screen

import com.astrbot.android.R
import com.astrbot.android.data.backup.AppBackupImportMode
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupImportPresentationTest {
    @Test
    fun `backup hub cards follow the required top to bottom order`() {
        val ordered = sortBackupModuleTitlesForDisplay(
            listOf(
                "TTS Voice Backup",
                "Config Backup",
                "Persona Backup",
                "Bot Backup",
                "Conversation Backup",
                "Model Backup",
                "Full Backup",
            ),
        )

        assertEquals(
            listOf(
                "Full Backup",
                "Conversation Backup",
                "Bot Backup",
                "Model Backup",
                "Persona Backup",
                "Config Backup",
                "TTS Voice Backup",
            ),
            ordered,
        )
    }

    @Test
    fun `backup import file picker accepts zip and json`() {
        assertEquals(
            listOf("application/zip", "application/json"),
            backupImportDocumentMimeTypes().toList(),
        )
    }

    @Test
    fun `full backup import dialog keeps a stable scroll threshold`() {
        assertEquals(460, fullBackupImportDialogScrollableMaxHeightDp())
    }

    @Test
    fun `quick actions are grouped into two rows`() {
        val rows = appBackupImportQuickActionRows()

        assertEquals(2, rows.size)
        assertEquals(
            listOf(AppBackupImportMode.REPLACE_ALL, AppBackupImportMode.MERGE_SKIP_DUPLICATES),
            rows[0],
        )
        assertEquals(listOf(AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES), rows[1])
    }

    @Test
    fun `module import actions are grouped into two outlined rows`() {
        val rows = moduleImportActionRows()

        assertEquals(2, rows.size)
        assertEquals(
            listOf(AppBackupImportMode.REPLACE_ALL, AppBackupImportMode.MERGE_SKIP_DUPLICATES),
            rows[0],
        )
        assertEquals(listOf(AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES), rows[1])
    }

    @Test
    fun `module import dialog keeps cancel inline with second row`() {
        assertEquals(true, moduleImportUsesInlineCancel())
    }

    @Test
    fun `mode label resources stay aligned with display copy`() {
        assertEquals(R.string.backup_import_mode_replace_all, appBackupImportModeLabelRes(AppBackupImportMode.REPLACE_ALL))
        assertEquals(R.string.backup_import_skip_duplicates, appBackupImportModeLabelRes(AppBackupImportMode.MERGE_SKIP_DUPLICATES))
        assertEquals(
            R.string.backup_import_overwrite_duplicates,
            appBackupImportModeLabelRes(AppBackupImportMode.MERGE_OVERWRITE_DUPLICATES),
        )
    }
}

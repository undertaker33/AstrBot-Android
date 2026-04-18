package com.astrbot.android.core.db.backup

import java.io.File

data class AppBackupItem(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val trigger: String,
    val moduleCounts: Map<String, Int>,
)

data class AppBackupImportSource(
    val label: String,
    val manifest: AppBackupManifest,
    val preview: AppBackupConflictPreview,
    val extractedFiles: Map<String, File> = emptyMap(),
)

data class ModuleBackupItem(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val trigger: String,
    val module: AppBackupModuleKind,
    val recordCount: Int,
    val hasFiles: Boolean,
)

data class ModuleBackupImportSource(
    val label: String,
    val module: AppBackupModuleKind,
    val manifest: AppBackupManifest,
    val preview: AppBackupModuleConflict,
    val extractedFiles: Map<String, File> = emptyMap(),
)

data class AppBackupRestoreResult(
    val botCount: Int,
    val providerCount: Int,
    val personaCount: Int,
    val configCount: Int,
    val conversationCount: Int,
    val qqAccountCount: Int,
    val ttsAssetCount: Int,
)

internal data class BackupPayload(
    val manifest: AppBackupManifest,
    val extractedFiles: Map<String, File> = emptyMap(),
)

package com.astrbot.android.core.db.backup

data class AppBackupModuleSnapshot(
    val count: Int = 0,
    val hasFiles: Boolean = false,
    val files: List<String> = emptyList(),
    val records: List<Any> = emptyList(),
)

data class AppBackupModules(
    val bots: AppBackupModuleSnapshot = AppBackupModuleSnapshot(),
    val providers: AppBackupModuleSnapshot = AppBackupModuleSnapshot(),
    val personas: AppBackupModuleSnapshot = AppBackupModuleSnapshot(),
    val configs: AppBackupModuleSnapshot = AppBackupModuleSnapshot(),
    val conversations: AppBackupModuleSnapshot = AppBackupModuleSnapshot(),
    val qqLogin: AppBackupModuleSnapshot = AppBackupModuleSnapshot(),
    val ttsAssets: AppBackupModuleSnapshot = AppBackupModuleSnapshot(),
)

data class AppBackupManifest(
    val schema: String = AppBackupJson.FULL_BACKUP_SCHEMA,
    val createdAt: Long,
    val trigger: String = "manual",
    val modules: AppBackupModules = AppBackupModules(),
    val appState: AppBackupAppState = AppBackupAppState(),
)

data class AppBackupAppState(
    val selectedBotId: String = "",
    val selectedConfigId: String = "",
    val preferredChatProvider: String = "",
    val themeMode: String = "",
)

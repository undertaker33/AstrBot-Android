package com.astrbot.android.model.plugin

data class PluginWorkspaceFileEntry(
    val relativePath: String,
    val sizeBytes: Long = 0L,
    val lastModifiedAtEpochMillis: Long = 0L,
)

data class PluginHostWorkspaceSnapshot(
    val privateRootPath: String = "",
    val importsPath: String = "",
    val runtimePath: String = "",
    val exportsPath: String = "",
    val cachePath: String = "",
    val files: List<PluginWorkspaceFileEntry> = emptyList(),
)

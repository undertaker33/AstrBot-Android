package com.astrbot.android.feature.plugin.presentation.bindings

import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot

interface PluginWorkspaceBindings {
    fun resolvePluginWorkspaceSnapshot(pluginId: String): PluginHostWorkspaceSnapshot

    suspend fun importPluginWorkspaceFile(
        pluginId: String,
        uri: String,
    ): PluginHostWorkspaceSnapshot

    fun deletePluginWorkspaceFile(
        pluginId: String,
        relativePath: String,
    ): PluginHostWorkspaceSnapshot
}

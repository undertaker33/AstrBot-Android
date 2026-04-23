package com.astrbot.android.feature.plugin.presentation.bindings

import com.astrbot.android.feature.plugin.data.PluginUninstallResult
import com.astrbot.android.feature.plugin.domain.PluginManagementResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginUninstallPolicy

interface PluginManagementBindings {
    fun enablePlugin(pluginId: String): PluginInstallRecord

    fun disablePlugin(pluginId: String): PluginInstallRecord

    fun uninstallPlugin(pluginId: String, policy: PluginUninstallPolicy): PluginUninstallResult

    suspend fun refreshCatalog(): PluginManagementResult

    fun recoverPlugin(pluginId: String)

    fun suspendPlugin(pluginId: String, reason: String)

    fun findByPluginId(pluginId: String): PluginInstallRecord?
}

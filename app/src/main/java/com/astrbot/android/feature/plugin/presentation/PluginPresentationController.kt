package com.astrbot.android.feature.plugin.presentation

import com.astrbot.android.feature.plugin.domain.PluginManagementResult
import com.astrbot.android.feature.plugin.domain.PluginManagementUseCases
import com.astrbot.android.feature.plugin.domain.PluginUninstallResult
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUninstallPolicy

class PluginPresentationController(
    private val useCases: PluginManagementUseCases,
) {
    fun enablePlugin(pluginId: String): PluginInstallRecord =
        useCases.enablePlugin(pluginId)

    fun disablePlugin(pluginId: String): PluginInstallRecord =
        useCases.disablePlugin(pluginId)

    fun uninstallPlugin(pluginId: String, policy: PluginUninstallPolicy): PluginUninstallResult =
        useCases.uninstallPlugin(pluginId, policy)

    fun recoverPlugin(pluginId: String) =
        useCases.recoverPlugin(pluginId)

    fun suspendPlugin(pluginId: String, reason: String) =
        useCases.suspendPlugin(pluginId, reason)

    fun findPlugin(pluginId: String): PluginInstallRecord? =
        useCases.findPlugin(pluginId)

    suspend fun refreshCatalog(): PluginManagementResult =
        useCases.refreshCatalog()
}

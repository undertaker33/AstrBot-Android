package com.astrbot.android.feature.plugin.presentation

import com.astrbot.android.feature.plugin.data.PluginUninstallResult
import com.astrbot.android.feature.plugin.domain.PluginManagementResult
import com.astrbot.android.feature.plugin.presentation.bindings.PluginManagementBindings
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import javax.inject.Inject

class PluginPresentationController @Inject constructor(
    private val bindings: PluginManagementBindings,
) {
    fun enablePlugin(pluginId: String): PluginInstallRecord =
        bindings.enablePlugin(pluginId)

    fun disablePlugin(pluginId: String): PluginInstallRecord =
        bindings.disablePlugin(pluginId)

    fun uninstallPlugin(pluginId: String, policy: PluginUninstallPolicy): PluginUninstallResult =
        bindings.uninstallPlugin(pluginId, policy)

    fun recoverPlugin(pluginId: String) =
        bindings.recoverPlugin(pluginId)

    fun suspendPlugin(pluginId: String, reason: String) =
        bindings.suspendPlugin(pluginId, reason)

    fun findPlugin(pluginId: String): PluginInstallRecord? =
        bindings.findByPluginId(pluginId)

    suspend fun refreshCatalog(): PluginManagementResult {
        return try {
            bindings.refreshCatalog()
        } catch (error: Exception) {
            PluginManagementResult.Failed(error.message ?: "Unknown error")
        }
    }
}

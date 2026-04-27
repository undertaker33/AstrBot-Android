package com.astrbot.android.feature.plugin.domain

import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginUninstallPolicy

class PluginManagementUseCases(
    private val repository: PluginRepositoryPort,
    private val runtime: PluginRuntimePort,
    private val governance: PluginGovernancePort,
) {
    fun enablePlugin(pluginId: String): PluginInstallRecord {
        return repository.setEnabled(pluginId, enabled = true)
    }

    fun disablePlugin(pluginId: String): PluginInstallRecord {
        return repository.setEnabled(pluginId, enabled = false)
    }

    fun uninstallPlugin(pluginId: String, policy: PluginUninstallPolicy): PluginUninstallResult {
        return repository.uninstall(pluginId, policy)
    }

    suspend fun refreshCatalog(): PluginManagementResult {
        return try {
            runtime.refreshRuntimeRegistry()
            PluginManagementResult.Success
        } catch (e: Exception) {
            PluginManagementResult.Failed(e.message ?: "Unknown error")
        }
    }

    fun recoverPlugin(pluginId: String) {
        governance.recoverPlugin(pluginId)
    }

    fun suspendPlugin(pluginId: String, reason: String) {
        governance.suspendPlugin(pluginId, reason)
    }

    fun findPlugin(pluginId: String): PluginInstallRecord? {
        return repository.findByPluginId(pluginId)
    }

    fun isPluginLoaded(pluginId: String): Boolean {
        return runtime.isPluginLoaded(pluginId)
    }
}

sealed interface PluginManagementResult {
    object Success : PluginManagementResult
    data class Failed(val message: String) : PluginManagementResult
}

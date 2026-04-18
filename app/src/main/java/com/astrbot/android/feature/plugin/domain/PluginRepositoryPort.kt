package com.astrbot.android.feature.plugin.domain

import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginUninstallPolicy

interface PluginRepositoryPort {
    fun findByPluginId(pluginId: String): PluginInstallRecord?
    fun upsert(record: PluginInstallRecord)
    fun delete(pluginId: String)
    fun setEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord
    fun updateFailureState(pluginId: String, failureState: PluginFailureState): PluginInstallRecord
    fun uninstall(pluginId: String, policy: PluginUninstallPolicy): PluginUninstallResult
    fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema?
    fun saveCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot
    fun saveExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot
}

data class PluginUninstallResult(
    val success: Boolean,
    val pluginId: String,
    val reason: String = "",
)

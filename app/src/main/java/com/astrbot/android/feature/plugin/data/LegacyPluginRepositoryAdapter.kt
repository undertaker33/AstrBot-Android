package com.astrbot.android.feature.plugin.data

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.feature.plugin.domain.PluginRepositoryPort
import com.astrbot.android.feature.plugin.domain.PluginUninstallResult
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginUninstallPolicy

class LegacyPluginRepositoryAdapter : PluginRepositoryPort {

    override fun findByPluginId(pluginId: String): PluginInstallRecord? =
        PluginRepository.findByPluginId(pluginId)

    override fun upsert(record: PluginInstallRecord) =
        PluginRepository.upsert(record)

    override fun delete(pluginId: String) =
        PluginRepository.delete(pluginId)

    override fun setEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord =
        PluginRepository.setEnabled(pluginId, enabled)

    override fun updateFailureState(pluginId: String, failureState: PluginFailureState): PluginInstallRecord =
        PluginRepository.updateFailureState(pluginId, failureState)

    override fun uninstall(pluginId: String, policy: PluginUninstallPolicy): PluginUninstallResult {
        val legacyResult = PluginRepository.uninstall(pluginId, policy)
        return PluginUninstallResult(
            success = true,
            pluginId = legacyResult.pluginId,
            reason = "",
        )
    }

    override fun getInstalledStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? =
        PluginRepository.getInstalledStaticConfigSchema(pluginId)

    override fun saveCoreConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        coreValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot =
        PluginRepository.saveCoreConfig(pluginId, boundary, coreValues)

    override fun saveExtensionConfig(
        pluginId: String,
        boundary: PluginConfigStorageBoundary,
        extensionValues: Map<String, PluginStaticConfigValue>,
    ): PluginConfigStoreSnapshot =
        PluginRepository.saveExtensionConfig(pluginId, boundary, extensionValues)
}

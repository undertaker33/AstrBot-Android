package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.model.plugin.ExternalPluginTriggerPolicy
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus

object ExternalPluginRuntimeCatalog {
    fun plugins(
        records: List<PluginInstallRecord> = PluginRepository.records.value,
        binder: ExternalPluginRuntimeBinder = ExternalPluginRuntimeBinder(),
        bridgeRuntime: ExternalPluginBridgeRuntime = ExternalPluginBridgeRuntime(),
    ): List<PluginRuntimePlugin> {
        return records.mapNotNull { record ->
            createRuntimePlugin(
                record = record,
                binder = binder,
                bridgeRuntime = bridgeRuntime,
            )
        }
    }

    fun createRuntimePlugin(
        record: PluginInstallRecord,
        binder: ExternalPluginRuntimeBinder = ExternalPluginRuntimeBinder(),
        bridgeRuntime: ExternalPluginBridgeRuntime = ExternalPluginBridgeRuntime(),
    ): PluginRuntimePlugin? {
        val binding = binder.bind(record)
        if (!binding.isReady) {
            return null
        }
        val supportedTriggers = binding.contract
            ?.supportedTriggers
            ?.filter(ExternalPluginTriggerPolicy::isOpen)
            ?.toSet()
            .orEmpty()
        if (supportedTriggers.isEmpty()) {
            return null
        }
        return PluginRuntimePlugin(
            pluginId = record.pluginId,
            pluginVersion = record.installedVersion,
            installState = record.toRuntimeInstallState(),
            supportedTriggers = supportedTriggers,
            handler = PluginRuntimeHandler { context ->
                bridgeRuntime.execute(binding = binding, context = context)
            },
        )
    }
}

private fun PluginInstallRecord.toRuntimeInstallState(): PluginInstallState {
    return PluginInstallState(
        status = PluginInstallStatus.INSTALLED,
        installedVersion = installedVersion,
        source = source,
        manifestSnapshot = manifestSnapshot,
        permissionSnapshot = permissionSnapshot,
        compatibilityState = compatibilityState,
        enabled = enabled,
        failureState = failureState,
        lastInstalledAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = localPackagePath,
        extractedDir = extractedDir,
    )
}

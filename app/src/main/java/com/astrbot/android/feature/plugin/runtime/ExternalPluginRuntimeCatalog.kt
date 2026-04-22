@file:Suppress("DEPRECATION")

package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.feature.plugin.data.PluginRepositoryStatePort
import com.astrbot.android.feature.plugin.data.EmptyPluginRepositoryStatePort
import com.astrbot.android.model.plugin.ExternalPluginTriggerPolicy
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus

class ExternalPluginRuntimeCatalog(
    private val repositoryStatePort: PluginRepositoryStatePort = EmptyPluginRepositoryStatePort,
    private val binderFactory: () -> ExternalPluginRuntimeBinder = { ExternalPluginRuntimeBinder() },
    private val bridgeRuntimeFactory: () -> ExternalPluginBridgeRuntime = { ExternalPluginBridgeRuntime() },
) {
    fun plugins(): List<PluginRuntimePlugin> {
        return Companion.plugins(
            records = repositoryStatePort.records.value,
            binder = binderFactory(),
            bridgeRuntime = bridgeRuntimeFactory(),
        )
    }

    fun createRuntimePlugin(record: PluginInstallRecord): PluginRuntimePlugin? {
        return Companion.createRuntimePlugin(
            record = record,
            binder = binderFactory(),
            bridgeRuntime = bridgeRuntimeFactory(),
        )
    }

    companion object {
        fun plugins(): List<PluginRuntimePlugin> {
            return ExternalPluginRuntimeCatalog().plugins()
        }

        fun plugins(
            records: List<PluginInstallRecord>,
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
            if (record.packageContractSnapshot?.protocolVersion == 2) {
                return null
            }
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


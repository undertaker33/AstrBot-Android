package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginCompatibilityState
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginManifest
import com.elymbot.android.model.plugin.PluginPackageContractSnapshot
import com.elymbot.android.model.plugin.PluginPermissionDeclaration
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.elymbot.android.model.plugin.PluginSource
import com.elymbot.android.model.plugin.PluginSourceType

internal object PluginV2ScheduledHandlerRegistryTestSupport {
    fun installRecord(
        pluginId: String = "plugin.schedule",
        permissionIds: Set<String> = setOf(PluginV2HostApiPermissions.SCHEDULE_MANAGE),
        version: String = "1.0.0",
    ): PluginInstallRecord {
        val permissions = permissionIds.map { permissionId ->
            PluginPermissionDeclaration(
                permissionId = permissionId,
                title = permissionId,
                description = permissionId,
                riskLevel = PluginRiskLevel.MEDIUM,
                required = true,
            )
        }
        val manifest = PluginManifest(
            pluginId = pluginId,
            version = version,
            protocolVersion = 2,
            author = "ElymBot",
            title = "Schedule Plugin",
            description = "Schedule plugin",
            permissions = permissions,
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Schedule entry",
        )
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(PluginSourceType.LOCAL_FILE, "/tmp/$pluginId.zip", 1L),
            packageContractSnapshot = PluginPackageContractSnapshot(
                protocolVersion = 2,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "quickjs",
                    bootstrap = "runtime/bootstrap.js",
                    apiVersion = 2,
                ),
            ),
            permissionSnapshot = permissions,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            enabled = true,
            installedAt = 1L,
            lastUpdatedAt = 1L,
            extractedDir = "/tmp/$pluginId",
        )
    }
}

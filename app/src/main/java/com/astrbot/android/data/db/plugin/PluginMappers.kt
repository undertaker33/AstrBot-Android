package com.astrbot.android.data.db

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginConfigEntryPointsSnapshot
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy

fun PluginInstallAggregate.toInstallRecord(): PluginInstallRecord {
    val manifestEntity = manifestSnapshots.singleOrNull()
        ?: error("Expected a single manifest snapshot for pluginId=${record.pluginId}")
    val manifestPermissions = manifestPermissions
        .sortedBy { entity -> entity.sortIndex }
        .map(PluginManifestPermissionEntity::toPermissionDeclaration)
    val packageContractSnapshot = packageContractSnapshots.singleOrNull()?.toSnapshot()
    val permissionSnapshot = permissionSnapshots
        .sortedBy { entity -> entity.sortIndex }
        .map(PluginPermissionSnapshotEntity::toPermissionDeclaration)
        .ifEmpty { manifestPermissions }
    val manifestSnapshot = PluginManifest(
        pluginId = manifestEntity.pluginId,
        version = manifestEntity.version,
        protocolVersion = manifestEntity.protocolVersion,
        author = manifestEntity.author,
        title = manifestEntity.title,
        description = manifestEntity.description,
        permissions = manifestPermissions,
        minHostVersion = manifestEntity.minHostVersion,
        maxHostVersion = manifestEntity.maxHostVersion,
        sourceType = enumValueOf(manifestEntity.sourceType),
        entrySummary = manifestEntity.entrySummary,
        riskLevel = enumValueOf(manifestEntity.riskLevel),
    )
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifestSnapshot,
        source = PluginSource(
            sourceType = enumValueOf(record.sourceType),
            location = record.sourceLocation,
            importedAt = record.sourceImportedAt,
        ),
        packageContractSnapshot = packageContractSnapshot,
        permissionSnapshot = permissionSnapshot,
        compatibilityState = PluginCompatibilityState.fromChecks(
            protocolSupported = record.protocolSupported,
            minHostVersionSatisfied = record.minHostVersionSatisfied,
            maxHostVersionSatisfied = record.maxHostVersionSatisfied,
            notes = record.compatibilityNotes,
        ),
        uninstallPolicy = enumValueOf(record.uninstallPolicy),
        failureState = PluginFailureState(
            consecutiveFailureCount = record.consecutiveFailureCount,
            lastFailureAtEpochMillis = record.lastFailureAtEpochMillis,
            lastErrorSummary = record.lastErrorSummary,
            suspendedUntilEpochMillis = record.suspendedUntilEpochMillis,
        ),
        catalogSourceId = record.catalogSourceId,
        installedPackageUrl = record.installedPackageUrl,
        lastCatalogCheckAtEpochMillis = record.lastCatalogCheckAtEpochMillis,
        enabled = record.enabled,
        installedAt = record.installedAt,
        lastUpdatedAt = record.lastUpdatedAt,
        localPackagePath = record.localPackagePath,
        extractedDir = record.extractedDir,
    )
}

fun PluginInstallRecord.toWriteModel(): PluginInstallWriteModel {
    return PluginInstallWriteModel(
        record = PluginInstallRecordEntity(
            pluginId = pluginId,
            sourceType = source.sourceType.name,
            sourceLocation = source.location,
            sourceImportedAt = source.importedAt,
            protocolSupported = compatibilityState.protocolSupported,
            minHostVersionSatisfied = compatibilityState.minHostVersionSatisfied,
            maxHostVersionSatisfied = compatibilityState.maxHostVersionSatisfied,
            compatibilityNotes = compatibilityState.notes,
            uninstallPolicy = uninstallPolicy.name,
            consecutiveFailureCount = failureState.consecutiveFailureCount,
            lastFailureAtEpochMillis = failureState.lastFailureAtEpochMillis,
            lastErrorSummary = failureState.lastErrorSummary,
            suspendedUntilEpochMillis = failureState.suspendedUntilEpochMillis,
            catalogSourceId = catalogSourceId,
            installedPackageUrl = installedPackageUrl,
            lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
            enabled = enabled,
            installedAt = installedAt,
            lastUpdatedAt = lastUpdatedAt,
            localPackagePath = localPackagePath,
            extractedDir = extractedDir,
        ),
        manifestSnapshot = PluginManifestSnapshotEntity(
            pluginId = pluginId,
            version = manifestSnapshot.version,
            protocolVersion = manifestSnapshot.protocolVersion,
            author = manifestSnapshot.author,
            title = manifestSnapshot.title,
            description = manifestSnapshot.description,
            minHostVersion = manifestSnapshot.minHostVersion,
            maxHostVersion = manifestSnapshot.maxHostVersion,
            sourceType = manifestSnapshot.sourceType.name,
            entrySummary = manifestSnapshot.entrySummary,
            riskLevel = manifestSnapshot.riskLevel.name,
        ),
        packageContractSnapshot = packageContractSnapshot?.toEntity(pluginId = pluginId),
        manifestPermissions = manifestSnapshot.permissions.mapIndexed { index, permission ->
            PluginManifestPermissionEntity(
                pluginId = pluginId,
                permissionId = permission.permissionId,
                title = permission.title,
                description = permission.description,
                riskLevel = permission.riskLevel.name,
                required = permission.required,
                sortIndex = index,
            )
        },
        permissionSnapshots = permissionSnapshot.mapIndexed { index, permission ->
            PluginPermissionSnapshotEntity(
                pluginId = pluginId,
                permissionId = permission.permissionId,
                title = permission.title,
                description = permission.description,
                riskLevel = permission.riskLevel.name,
                required = permission.required,
                sortIndex = index,
            )
        },
    )
}

private fun PluginPackageContractSnapshotEntity.toSnapshot(): PluginPackageContractSnapshot {
    return PluginPackageContractSnapshot(
        protocolVersion = protocolVersion,
        runtime = PluginRuntimeDeclarationSnapshot(
            kind = runtimeKind,
            bootstrap = runtimeBootstrap,
            apiVersion = runtimeApiVersion,
        ),
        config = PluginConfigEntryPointsSnapshot(
            staticSchema = configStaticSchema,
            settingsSchema = configSettingsSchema,
        ),
    )
}

private fun PluginPackageContractSnapshot.toEntity(pluginId: String): PluginPackageContractSnapshotEntity {
    return PluginPackageContractSnapshotEntity(
        pluginId = pluginId,
        protocolVersion = protocolVersion,
        runtimeKind = runtime.kind,
        runtimeBootstrap = runtime.bootstrap,
        runtimeApiVersion = runtime.apiVersion,
        configStaticSchema = config.staticSchema,
        configSettingsSchema = config.settingsSchema,
    )
}

private fun PluginManifestPermissionEntity.toPermissionDeclaration(): PluginPermissionDeclaration {
    return PluginPermissionDeclaration(
        permissionId = permissionId,
        title = title,
        description = description,
        riskLevel = enumValueOf(riskLevel),
        required = required,
    )
}

private fun PluginPermissionSnapshotEntity.toPermissionDeclaration(): PluginPermissionDeclaration {
    return PluginPermissionDeclaration(
        permissionId = permissionId,
        title = title,
        description = description,
        riskLevel = enumValueOf(riskLevel),
        required = required,
    )
}

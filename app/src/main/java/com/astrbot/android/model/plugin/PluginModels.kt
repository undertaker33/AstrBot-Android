package com.astrbot.android.model.plugin

enum class PluginSourceType {
    LOCAL_FILE,
    MANUAL_IMPORT,
}

enum class PluginRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
}

enum class PluginInstallStatus {
    NOT_INSTALLED,
    INSTALLING,
    INSTALLED,
    FAILED,
}

enum class PluginCompatibilityStatus {
    UNKNOWN,
    COMPATIBLE,
    INCOMPATIBLE,
}

enum class PluginUninstallPolicy(
    val retainUserData: Boolean,
) {
    KEEP_DATA(true),
    REMOVE_DATA(false);

    companion object {
        fun default(): PluginUninstallPolicy = KEEP_DATA
    }
}

data class PluginPermissionDeclaration(
    val permissionId: String,
    val title: String,
    val description: String,
    val riskLevel: PluginRiskLevel = PluginRiskLevel.MEDIUM,
    val required: Boolean = true,
)

data class PluginSource(
    val sourceType: PluginSourceType = PluginSourceType.LOCAL_FILE,
    val location: String = "",
    val importedAt: Long = 0L,
)

data class PluginManifest(
    val pluginId: String,
    val version: String,
    val protocolVersion: Int,
    val author: String,
    val title: String,
    val description: String,
    val permissions: List<PluginPermissionDeclaration> = emptyList(),
    val minHostVersion: String,
    val maxHostVersion: String = "",
    val sourceType: PluginSourceType,
    val entrySummary: String,
    val riskLevel: PluginRiskLevel = PluginRiskLevel.LOW,
)

data class PluginFailureState(
    val consecutiveFailureCount: Int = 0,
    val lastFailureAtEpochMillis: Long? = null,
    val lastErrorSummary: String = "",
    val suspendedUntilEpochMillis: Long? = null,
) {
    init {
        require(consecutiveFailureCount >= 0) {
            "consecutiveFailureCount must not be negative."
        }
    }

    val hasFailures: Boolean
        get() = consecutiveFailureCount > 0 ||
            lastFailureAtEpochMillis != null ||
            lastErrorSummary.isNotBlank() ||
            suspendedUntilEpochMillis != null

    companion object {
        fun none(): PluginFailureState = PluginFailureState()
    }
}

data class PluginInstallState(
    val status: PluginInstallStatus = PluginInstallStatus.NOT_INSTALLED,
    val installedVersion: String = "",
    val source: PluginSource = PluginSource(),
    val manifestSnapshot: PluginManifest? = null,
    val permissionSnapshot: List<PluginPermissionDeclaration> = emptyList(),
    val compatibilityState: PluginCompatibilityState = PluginCompatibilityState.unknown(),
    val enabled: Boolean = false,
    val failureState: PluginFailureState = PluginFailureState.none(),
    val lastInstalledAt: Long = 0L,
    val lastUpdatedAt: Long = 0L,
    val localPackagePath: String = "",
    val extractedDir: String = "",
) {
    fun isActivated(): Boolean {
        return status == PluginInstallStatus.INSTALLED && enabled
    }
}

// PluginInstallState describes the current install/enable state.
// PluginInstallRecord stores the persisted record and immutable snapshot.

data class PluginCompatibilityState private constructor(
    val protocolSupported: Boolean?,
    val minHostVersionSatisfied: Boolean?,
    val maxHostVersionSatisfied: Boolean?,
    val notes: String = "",
) {
    val status: PluginCompatibilityStatus
        get() {
            if (protocolSupported == null && minHostVersionSatisfied == null && maxHostVersionSatisfied == null) {
                return PluginCompatibilityStatus.UNKNOWN
            }
            if (protocolSupported == false || minHostVersionSatisfied == false || maxHostVersionSatisfied == false) {
                return PluginCompatibilityStatus.INCOMPATIBLE
            }
            if (protocolSupported == true && minHostVersionSatisfied == true && maxHostVersionSatisfied == true) {
                return PluginCompatibilityStatus.COMPATIBLE
            }
            return PluginCompatibilityStatus.UNKNOWN
        }

    val isEvaluated: Boolean
        get() = status != PluginCompatibilityStatus.UNKNOWN

    fun isCompatible(): Boolean {
        return status == PluginCompatibilityStatus.COMPATIBLE
    }

    companion object {
        fun unknown(notes: String = ""): PluginCompatibilityState {
            return PluginCompatibilityState(
                protocolSupported = null,
                minHostVersionSatisfied = null,
                maxHostVersionSatisfied = null,
                notes = notes,
            )
        }

        fun fromChecks(
            protocolSupported: Boolean?,
            minHostVersionSatisfied: Boolean?,
            maxHostVersionSatisfied: Boolean?,
            notes: String = "",
        ): PluginCompatibilityState {
            return PluginCompatibilityState(
                protocolSupported = protocolSupported,
                minHostVersionSatisfied = minHostVersionSatisfied,
                maxHostVersionSatisfied = maxHostVersionSatisfied,
                notes = notes,
            )
        }

        fun evaluated(
            protocolSupported: Boolean,
            minHostVersionSatisfied: Boolean,
            maxHostVersionSatisfied: Boolean,
            notes: String = "",
        ): PluginCompatibilityState {
            return fromChecks(
                protocolSupported = protocolSupported,
                minHostVersionSatisfied = minHostVersionSatisfied,
                maxHostVersionSatisfied = maxHostVersionSatisfied,
                notes = notes,
            )
        }
    }
}

class PluginInstallRecord private constructor(
    val source: PluginSource,
    val manifestSnapshot: PluginManifest,
    val permissionSnapshot: List<PluginPermissionDeclaration> = emptyList(),
    val compatibilityState: PluginCompatibilityState = PluginCompatibilityState.unknown(),
    val uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
    val enabled: Boolean = false,
    val failureState: PluginFailureState = PluginFailureState.none(),
    val installedAt: Long = 0L,
    val lastUpdatedAt: Long = 0L,
    val localPackagePath: String = "",
    val extractedDir: String = "",
) {
    val pluginId: String
        get() = manifestSnapshot.pluginId

    val installedVersion: String
        get() = manifestSnapshot.version

    val isSourceTypeAligned: Boolean
        get() = source.sourceType == manifestSnapshot.sourceType

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PluginInstallRecord) return false

        return source == other.source &&
            manifestSnapshot == other.manifestSnapshot &&
            permissionSnapshot == other.permissionSnapshot &&
            compatibilityState == other.compatibilityState &&
            uninstallPolicy == other.uninstallPolicy &&
            enabled == other.enabled &&
            failureState == other.failureState &&
            installedAt == other.installedAt &&
            lastUpdatedAt == other.lastUpdatedAt &&
            localPackagePath == other.localPackagePath &&
            extractedDir == other.extractedDir
    }

    override fun hashCode(): Int {
        var result = source.hashCode()
        result = 31 * result + manifestSnapshot.hashCode()
        result = 31 * result + permissionSnapshot.hashCode()
        result = 31 * result + compatibilityState.hashCode()
        result = 31 * result + uninstallPolicy.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + failureState.hashCode()
        result = 31 * result + installedAt.hashCode()
        result = 31 * result + lastUpdatedAt.hashCode()
        result = 31 * result + localPackagePath.hashCode()
        result = 31 * result + extractedDir.hashCode()
        return result
    }

    override fun toString(): String {
        return "PluginInstallRecord(" +
            "source=$source, " +
            "manifestSnapshot=$manifestSnapshot, " +
            "permissionSnapshot=$permissionSnapshot, " +
            "compatibilityState=$compatibilityState, " +
            "uninstallPolicy=$uninstallPolicy, " +
            "enabled=$enabled, " +
            "failureState=$failureState, " +
            "installedAt=$installedAt, " +
            "lastUpdatedAt=$lastUpdatedAt, " +
            "localPackagePath=$localPackagePath, " +
            "extractedDir=$extractedDir" +
            ")"
    }

    companion object {
        fun installFromManifest(
            manifestSnapshot: PluginManifest,
            source: PluginSource,
        ): PluginInstallRecord {
            return restoreFromPersistedState(
                manifestSnapshot = manifestSnapshot,
                source = source,
                permissionSnapshot = manifestSnapshot.permissions,
                compatibilityState = PluginCompatibilityState.unknown(),
                uninstallPolicy = PluginUninstallPolicy.default(),
                enabled = false,
                failureState = PluginFailureState.none(),
                installedAt = 0L,
                lastUpdatedAt = 0L,
                localPackagePath = "",
                extractedDir = "",
            )
        }

        fun restoreFromPersistedState(
            manifestSnapshot: PluginManifest,
            source: PluginSource,
            permissionSnapshot: List<PluginPermissionDeclaration> = manifestSnapshot.permissions,
            compatibilityState: PluginCompatibilityState = PluginCompatibilityState.unknown(),
            uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
            enabled: Boolean = false,
            failureState: PluginFailureState = PluginFailureState.none(),
            installedAt: Long = 0L,
            lastUpdatedAt: Long = 0L,
            localPackagePath: String = "",
            extractedDir: String = "",
        ): PluginInstallRecord {
            require(source.sourceType == manifestSnapshot.sourceType) {
                "Plugin install record requires matching source types."
            }
            return PluginInstallRecord(
                source = source,
                manifestSnapshot = manifestSnapshot.copy(permissions = manifestSnapshot.permissions.toList()),
                permissionSnapshot = permissionSnapshot.toList(),
                compatibilityState = compatibilityState,
                uninstallPolicy = uninstallPolicy,
                enabled = enabled,
                failureState = failureState,
                installedAt = installedAt,
                lastUpdatedAt = lastUpdatedAt,
                localPackagePath = localPackagePath,
                extractedDir = extractedDir,
            )
        }
    }
}

fun PluginRiskLevel.isBlocking(): Boolean {
    return this == PluginRiskLevel.HIGH || this == PluginRiskLevel.CRITICAL
}

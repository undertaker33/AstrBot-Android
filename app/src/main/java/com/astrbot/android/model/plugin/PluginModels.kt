package com.astrbot.android.model.plugin

import java.util.Locale

enum class PluginSourceType {
    LOCAL_FILE,
    MANUAL_IMPORT,
    REPOSITORY,
    DIRECT_LINK,
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

data class PluginRepositorySource(
    val sourceId: String,
    val title: String,
    val catalogUrl: String,
    val updatedAt: Long,
    val lastSyncAtEpochMillis: Long? = null,
    val lastSyncStatus: PluginCatalogSyncStatus = PluginCatalogSyncStatus.NEVER_SYNCED,
    val lastSyncErrorSummary: String = "",
    val plugins: List<PluginCatalogEntry> = emptyList(),
)

enum class PluginCatalogSyncStatus {
    NEVER_SYNCED,
    SUCCESS,
    EMPTY,
    FAILED,
}

enum class PluginDownloadProgressStage {
    DOWNLOADING,
    INSTALLING,
}

data class PluginDownloadProgress(
    val stage: PluginDownloadProgressStage,
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val bytesPerSecond: Long,
) {
    val progressFraction: Float?
        get() = if (totalBytes > 0L) {
            (bytesDownloaded.coerceIn(0L, totalBytes).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else {
            null
        }

    val isIndeterminate: Boolean
        get() = progressFraction == null

    val downloadedMegabytesLabel: String
        get() = formatMegabytes(bytesDownloaded.coerceAtLeast(0L))

    val totalMegabytesLabel: String
        get() = if (totalBytes > 0L) {
            formatMegabytes(totalBytes)
        } else {
            "-- MB"
        }

    val speedLabel: String
        get() = if (bytesPerSecond > 0L && stage == PluginDownloadProgressStage.DOWNLOADING) {
            "${formatMegabytes(bytesPerSecond)}/s"
        } else {
            "-- MB/s"
        }

    companion object {
        fun downloading(
            bytesDownloaded: Long,
            totalBytes: Long,
            bytesPerSecond: Long,
        ): PluginDownloadProgress {
            return PluginDownloadProgress(
                stage = PluginDownloadProgressStage.DOWNLOADING,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                bytesPerSecond = bytesPerSecond,
            )
        }

        fun installing(
            bytesDownloaded: Long,
            totalBytes: Long,
        ): PluginDownloadProgress {
            return PluginDownloadProgress(
                stage = PluginDownloadProgressStage.INSTALLING,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                bytesPerSecond = 0L,
            )
        }

        private fun formatMegabytes(bytes: Long): String {
            return String.format(Locale.US, "%.1f MB", bytes.toDouble() / BYTES_PER_MEGABYTE)
        }

        private const val BYTES_PER_MEGABYTE = 1_048_576.0
    }
}

data class PluginCatalogSyncState(
    val sourceId: String,
    val lastSyncAtEpochMillis: Long? = null,
    val lastSyncStatus: PluginCatalogSyncStatus = PluginCatalogSyncStatus.NEVER_SYNCED,
    val lastSyncErrorSummary: String = "",
)

data class PluginCatalogEntryRecord(
    val sourceId: String,
    val sourceTitle: String,
    val catalogUrl: String,
    val entry: PluginCatalogEntry,
)

data class PluginCatalogEntry(
    val pluginId: String,
    val title: String,
    val author: String,
    val repositoryUrl: String = "",
    val description: String,
    val entrySummary: String,
    val scenarios: List<String> = emptyList(),
    val versions: List<PluginCatalogVersion> = emptyList(),
)

data class PluginCatalogVersion(
    val version: String,
    val packageUrl: String,
    val publishedAt: Long,
    val protocolVersion: Int,
    val minHostVersion: String,
    val maxHostVersion: String = "",
    val permissions: List<PluginPermissionDeclaration> = emptyList(),
    val changelog: String = "",
) {
    fun resolvePackageUrl(catalogUrl: String): String {
        return if (isAbsoluteUrl(packageUrl) || catalogUrl.isBlank()) {
            packageUrl
        } else {
            java.net.URI(catalogUrl).resolve(packageUrl).toString()
        }
    }

    private fun isAbsoluteUrl(value: String): Boolean {
        return runCatching { java.net.URI(value).isAbsolute }.getOrDefault(false)
    }
}

sealed interface PluginInstallIntent {
    data class CatalogVersion(
        val pluginId: String,
        val version: String,
        val packageUrl: String,
        val catalogSourceId: String,
    ) : PluginInstallIntent

    data class RepositoryUrl(
        val url: String,
    ) : PluginInstallIntent

    data class DirectPackageUrl(
        val url: String,
    ) : PluginInstallIntent

    companion object {
        fun catalogVersion(
            pluginId: String,
            version: String,
            packageUrl: String,
            catalogSourceId: String,
        ): CatalogVersion {
            require(pluginId.isNotBlank()) { "Plugin id must not be blank." }
            require(version.isNotBlank()) { "Plugin version must not be blank." }
            require(catalogSourceId.isNotBlank()) { "Catalog source id must not be blank." }
            return CatalogVersion(
                pluginId = pluginId.trim(),
                version = version.trim(),
                packageUrl = normalizeRemotePluginUrl(packageUrl),
                catalogSourceId = catalogSourceId.trim(),
            )
        }

        fun repositoryUrl(url: String): RepositoryUrl {
            return RepositoryUrl(url = normalizeRemotePluginUrl(url))
        }

        fun directPackageUrl(url: String): DirectPackageUrl {
            return DirectPackageUrl(url = normalizeRemotePluginUrl(url))
        }
    }
}

sealed interface PluginInstallIntentResult {
    data class Installed(
        val record: PluginInstallRecord,
    ) : PluginInstallIntentResult

    data class RepositorySynced(
        val syncState: PluginCatalogSyncState,
    ) : PluginInstallIntentResult

    data object Ignored : PluginInstallIntentResult
}

data class PluginPermissionDiff(
    val added: List<PluginPermissionDeclaration> = emptyList(),
    val removed: List<PluginPermissionDeclaration> = emptyList(),
    val changed: List<PluginPermissionDeclaration> = emptyList(),
    val riskUpgraded: List<PluginPermissionUpgrade> = emptyList(),
) {
    val requiresSecondaryConfirmation: Boolean
        get() = added.isNotEmpty() || riskUpgraded.isNotEmpty()
}

data class PluginPermissionUpgrade(
    val from: PluginPermissionDeclaration,
    val to: PluginPermissionDeclaration,
)

data class PluginSourceBadge(
    val sourceType: PluginSourceType,
    val label: String,
    val highlighted: Boolean = false,
)

data class PluginUpdateAvailability(
    val pluginId: String,
    val installedVersion: String,
    val latestVersion: String,
    val updateAvailable: Boolean,
    val canUpgrade: Boolean = updateAvailable,
    val publishedAt: Long? = null,
    val changelogSummary: String = "",
    val permissionDiff: PluginPermissionDiff = PluginPermissionDiff(),
    val compatibilityState: PluginCompatibilityState = PluginCompatibilityState.unknown(),
    val incompatibilityReason: String = "",
    val catalogSourceId: String? = null,
    val packageUrl: String = "",
    val sourceBadge: PluginSourceBadge? = null,
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
    val catalogSourceId: String? = null,
    val installedPackageUrl: String = "",
    val lastCatalogCheckAtEpochMillis: Long? = null,
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
            catalogSourceId == other.catalogSourceId &&
            installedPackageUrl == other.installedPackageUrl &&
            lastCatalogCheckAtEpochMillis == other.lastCatalogCheckAtEpochMillis &&
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
        result = 31 * result + (catalogSourceId?.hashCode() ?: 0)
        result = 31 * result + installedPackageUrl.hashCode()
        result = 31 * result + (lastCatalogCheckAtEpochMillis?.hashCode() ?: 0)
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
            "catalogSourceId=$catalogSourceId, " +
            "installedPackageUrl=$installedPackageUrl, " +
            "lastCatalogCheckAtEpochMillis=$lastCatalogCheckAtEpochMillis, " +
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
                catalogSourceId = null,
                installedPackageUrl = "",
                lastCatalogCheckAtEpochMillis = null,
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
            catalogSourceId: String? = null,
            installedPackageUrl: String = "",
            lastCatalogCheckAtEpochMillis: Long? = null,
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
                catalogSourceId = catalogSourceId,
                installedPackageUrl = installedPackageUrl,
                lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
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

private fun normalizeRemotePluginUrl(rawValue: String): String {
    val trimmed = rawValue.trim()
    require(trimmed.isNotBlank()) { "Remote URL must not be blank." }
    val uri = runCatching { java.net.URI(trimmed) }
        .getOrElse { throw IllegalArgumentException("Invalid remote URL: $trimmed", it) }
    require(uri.isAbsolute) { "Remote URL must be absolute." }
    val scheme = uri.scheme?.lowercase().orEmpty()
    require(scheme == "http" || scheme == "https") {
        "Remote URL must use http or https."
    }
    return uri.toGithubRawUrlIfBlob() ?: uri.toString()
}

private fun java.net.URI.toGithubRawUrlIfBlob(): String? {
    if (!host.equals("github.com", ignoreCase = true)) return null
    val segments = path.split('/').filter(String::isNotBlank)
    if (segments.size < 5 || segments[2] != "blob") return null
    val owner = segments[0]
    val repo = segments[1]
    val branch = segments[3]
    val filePath = segments.drop(4).joinToString(separator = "/")
    return "https://raw.githubusercontent.com/$owner/$repo/$branch/$filePath"
}

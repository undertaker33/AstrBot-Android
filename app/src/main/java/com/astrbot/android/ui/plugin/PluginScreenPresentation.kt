package com.astrbot.android.ui.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginGovernanceSnapshot
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginPermissionDiff
import com.astrbot.android.model.plugin.PluginRuntimeHealthStatus
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.runtime.plugin.compareVersions
import com.astrbot.android.ui.plugin.PluginBadgePalette
import com.astrbot.android.ui.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginCatalogEntryCardUiState
import com.astrbot.android.ui.viewmodel.PluginCatalogEntryVersionUiState
import com.astrbot.android.ui.viewmodel.PluginFailureUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import com.astrbot.android.ui.app.MonochromeUi
import java.net.URI

enum class PluginHomepageSection {
    Hero,
    QuickInstall,
    HealthOverview,
    InstalledLibrary,
    Discover,
    Repositories,
}

enum class PluginQuickInstallMode {
    LocalZip,
    RepositoryUrl,
    DirectPackageUrl,
}

enum class PluginLocalFilter {
    ALL,
    ENABLED,
    DISABLED,
    UPDATES,
}

enum class PluginInstalledLibraryFilter {
    All,
    Enabled,
    Disabled,
    Updates,
    Issues,
    PermissionChanges,
}

enum class PluginInstalledLibraryPriority {
    Normal,
    Attention,
    Critical,
}

enum class PluginInstalledLibraryStatus {
    Enabled,
    Disabled,
    UpdateAvailable,
    CompatibilityUnknown,
    Incompatible,
    PermissionChanges,
    Suspended,
}

enum class PluginInstalledLibraryInsight {
    UpToDate,
    DisabledReady,
    UpdateAvailable,
    CompatibilityUnknown,
    Incompatible,
    PermissionChanges,
    Suspended,
}

enum class PluginInstalledLibraryPrimaryAction {
    Open,
    Review,
    Update,
}

enum class PluginInstalledLibraryBulkAction {
    ReviewIssues,
    ReviewUpdates,
    ReviewDisabled,
}

data class PluginInstalledLibrarySummaryPresentation(
    val totalCount: Int,
    val enabledCount: Int,
    val needsReviewCount: Int,
    val updatesCount: Int,
    val disabledCount: Int,
)

data class PluginInstalledLibraryBulkActionPresentation(
    val action: PluginInstalledLibraryBulkAction,
    val count: Int,
    val targetFilter: PluginInstalledLibraryFilter,
    val enabled: Boolean,
)

data class PluginInstalledLibraryFilterPresentation(
    val filter: PluginInstalledLibraryFilter,
    val count: Int,
    val selected: Boolean,
)

data class PluginInstalledLibraryCardPresentation(
    val pluginId: String,
    val title: String,
    val versionLabel: String,
    val sourceLabel: String,
    val priority: PluginInstalledLibraryPriority,
    val status: PluginInstalledLibraryStatus,
    val insight: PluginInstalledLibraryInsight,
    val primaryAction: PluginInstalledLibraryPrimaryAction,
    val isEnabled: Boolean,
    val hasUpdateAvailable: Boolean,
    val hasPermissionChanges: Boolean,
    val hasIssue: Boolean,
)

data class PluginInstalledLibraryPresentation(
    val summary: PluginInstalledLibrarySummaryPresentation,
    val bulkActions: List<PluginInstalledLibraryBulkActionPresentation>,
    val filters: List<PluginInstalledLibraryFilterPresentation>,
    val cards: List<PluginInstalledLibraryCardPresentation>,
)

data class PluginLocalFilterPresentation(
    val filter: PluginLocalFilter,
    val count: Int,
    val selected: Boolean,
)

data class PluginLocalCardPresentation(
    val pluginId: String,
    val title: String,
    val description: String,
    val author: String,
    val isEnabled: Boolean,
    val hasUpdateAvailable: Boolean,
    val protocolVersion: Int,
    val runtimeHealthLabel: String,
    val runtimeHealthDetail: String,
)

data class PluginLocalWorkspacePresentation(
    val filters: List<PluginLocalFilterPresentation>,
    val cards: List<PluginLocalCardPresentation>,
)

enum class PluginManagerPrimaryAction {
    Open,
    Update,
}

data class PluginManagerCardPresentation(
    val pluginId: String,
    val title: String,
    val author: String,
    val installedVersion: String,
    val latestVersion: String,
    val hasUpdateAvailable: Boolean,
    val primaryAction: PluginManagerPrimaryAction,
)

data class PluginManagerPresentation(
    val cards: List<PluginManagerCardPresentation>,
    val updatableCount: Int,
)

enum class PluginMarketStatus {
    NOT_INSTALLED,
    INSTALLED,
    UPDATE_AVAILABLE,
}

enum class PluginMarketPrimaryAction {
    INSTALL,
    UPDATE,
    INSTALLED,
}

data class PluginMarketVersionOptionPresentation(
    val stableKey: String,
    val sourceId: String,
    val sourceName: String,
    val versionLabel: String,
    val packageUrl: String,
    val publishedAt: Long,
    val protocolVersion: Int,
    val minHostVersion: String,
    val maxHostVersion: String,
    val changelogSummary: String,
    val compatibilityState: PluginCompatibilityState,
    val isSelectable: Boolean,
)

private data class PluginMarketVersionOptionWithSourceOrder(
    val option: PluginMarketVersionOptionPresentation,
    val sourceOrder: Int,
)

data class PluginMarketCardPresentation(
    val sourceId: String,
    val pluginId: String,
    val title: String,
    val description: String,
    val author: String,
    val versionLabel: String,
    val status: PluginMarketStatus,
    val repositoryUrl: String,
) {
    val stableKey: String = "$sourceId:$pluginId"
}

data class PluginMarketWorkspacePresentation(
    val cards: List<PluginMarketCardPresentation>,
)

data class PluginMarketPagePresentation(
    val currentPage: Int,
    val totalPages: Int,
    val visibleCards: List<PluginMarketCardPresentation>,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
)

data class PluginMarketDetailPresentation(
    val pluginId: String,
    val title: String,
    val author: String,
    val summary: String,
    val latestVersionLabel: String,
    val installedVersionLabel: String,
    val status: PluginMarketStatus,
    val primaryAction: PluginMarketPrimaryAction,
    val repositoryUrl: String,
    val repositoryHost: String,
    val sourceName: String,
    val versionOptions: List<PluginMarketVersionOptionPresentation> = emptyList(),
    val selectedVersionKey: String = "",
    val selectedVersionLabel: String = "",
    val selectedVersionCompatibility: PluginCompatibilityState = PluginCompatibilityState.unknown(),
    val selectedVersionIsSelectable: Boolean = true,
)

data class PluginHealthOverviewPresentation(
    val installedCount: Int,
    val updatesAvailableCount: Int,
    val needsReviewCount: Int,
    val sourceCount: Int,
)

data class PluginQuickInstallPresentation(
    val selectedMode: PluginQuickInstallMode,
    val showLocalZipAction: Boolean,
    val showRepositoryUrlForm: Boolean,
    val showDirectPackageUrlForm: Boolean,
    val promotedModes: List<PluginQuickInstallMode>,
    val advancedModes: List<PluginQuickInstallMode>,
    val isAdvancedModeSelected: Boolean,
)

data class PluginLocalInstallSheetPresentation(
    val showSheet: Boolean,
    val selectedMode: PluginQuickInstallMode,
    val availableModes: List<PluginQuickInstallMode>,
)

data class PluginRecordPresentation(
    val badges: List<String>,
)

data class PluginPermissionPresentation(
    val title: String,
    val description: String,
    val requirementLabel: String,
)

internal fun buildPluginHomepageSections(): List<PluginHomepageSection> {
    return listOf(
        PluginHomepageSection.Hero,
        PluginHomepageSection.HealthOverview,
        PluginHomepageSection.InstalledLibrary,
        PluginHomepageSection.QuickInstall,
        PluginHomepageSection.Discover,
        PluginHomepageSection.Repositories,
    )
}

internal fun buildPluginHealthOverviewPresentation(
    uiState: PluginScreenUiState,
): PluginHealthOverviewPresentation {
    val updatesAvailableCount = uiState.updateAvailabilitiesByPluginId.values.count { it.updateAvailable }
    val needsReviewCount = uiState.records
        .map { record -> buildPluginInstalledLibraryCardPresentation(record, uiState) }
        .count { card -> card.hasIssue }
    return PluginHealthOverviewPresentation(
        installedCount = uiState.records.size,
        updatesAvailableCount = updatesAvailableCount,
        needsReviewCount = needsReviewCount,
        sourceCount = uiState.repositorySources.size,
    )
}

internal fun buildPluginLocalWorkspacePresentation(
    uiState: PluginScreenUiState,
    searchQuery: String,
    selectedFilter: PluginLocalFilter,
): PluginLocalWorkspacePresentation {
    val normalizedQuery = searchQuery.trim()
    val cards = uiState.records
        .map { record -> buildPluginLocalCardPresentation(record, uiState) }
        .sortedBy { it.title.lowercase() }
    return PluginLocalWorkspacePresentation(
        filters = listOf(
            PluginLocalFilter.ALL,
            PluginLocalFilter.ENABLED,
            PluginLocalFilter.DISABLED,
            PluginLocalFilter.UPDATES,
        ).map { filter ->
            PluginLocalFilterPresentation(
                filter = filter,
                count = cards.count { it.matchesLocalFilter(filter) },
                selected = filter == selectedFilter,
            )
        },
        cards = cards
            .filter { it.matchesSearch(normalizedQuery) }
            .filter { it.matchesLocalFilter(selectedFilter) },
    )
}

internal fun buildPluginManagerPresentation(
    uiState: PluginScreenUiState,
): PluginManagerPresentation {
    val cards = uiState.records
        .map { record ->
            val updateAvailability = uiState.updateAvailabilitiesByPluginId[record.pluginId]
            val hasUpdateAvailable = updateAvailability?.updateAvailable == true
            PluginManagerCardPresentation(
                pluginId = record.pluginId,
                title = record.manifestSnapshot.title,
                author = record.manifestSnapshot.author,
                installedVersion = record.installedVersion,
                latestVersion = if (hasUpdateAvailable) {
                    updateAvailability?.latestVersion.orEmpty()
                } else {
                    ""
                },
                hasUpdateAvailable = hasUpdateAvailable,
                primaryAction = if (hasUpdateAvailable) {
                    PluginManagerPrimaryAction.Update
                } else {
                    PluginManagerPrimaryAction.Open
                },
            )
        }
        .sortedBy { it.title.lowercase() }

    return PluginManagerPresentation(
        cards = cards,
        updatableCount = cards.count { it.hasUpdateAvailable },
    )
}

internal fun buildPluginMarketWorkspacePresentation(
    uiState: PluginScreenUiState,
    searchQuery: String,
): PluginMarketWorkspacePresentation {
    val normalizedQuery = searchQuery.trim()
    val cards = uiState.catalogEntries
        .groupBy { entry -> entry.pluginId }
        .mapNotNull { (pluginId, entries) ->
            val defaultOption = buildPluginMarketVersionOptions(
                entries = entries,
                pluginId = pluginId,
            ).firstOrNull()
            val entry = defaultOption?.let { option ->
                entries.firstOrNull { it.sourceId == option.sourceId }
            } ?: entries.minWithOrNull(compareBy<PluginCatalogEntryCardUiState> { it.title.lowercase() }.thenBy { it.sourceId })
                ?: return@mapNotNull null
            val versionLabel = defaultOption?.versionLabel ?: entry.latestVersion
            val installedRecord = uiState.records.firstOrNull { it.pluginId == pluginId }
            PluginMarketCardPresentation(
                sourceId = defaultOption?.sourceId ?: entry.sourceId,
                pluginId = pluginId,
                title = entry.title,
                description = entry.summary,
                author = entry.author,
                versionLabel = versionLabel,
                status = when {
                    installedRecord == null -> PluginMarketStatus.NOT_INSTALLED
                    compareVersions(versionLabel, installedRecord.installedVersion) > 0 ->
                        PluginMarketStatus.UPDATE_AVAILABLE
                    else -> PluginMarketStatus.INSTALLED
                },
                repositoryUrl = entry.repositoryUrl,
            )
        }
        .sortedBy { it.title.lowercase() }
        .filter { it.matchesSearch(normalizedQuery) }
    return PluginMarketWorkspacePresentation(cards = cards)
}

internal fun buildPluginMarketVersionOptions(
    entries: List<PluginCatalogEntryCardUiState>,
    pluginId: String,
): List<PluginMarketVersionOptionPresentation> {
    return entries
        .withIndex()
        .asSequence()
        .filter { (_, entry) -> entry.pluginId == pluginId }
        .flatMap { (sourceOrder, entry) ->
            entry.effectiveMarketVersions().asSequence().map { version ->
                PluginMarketVersionOptionWithSourceOrder(
                    option = PluginMarketVersionOptionPresentation(
                        stableKey = listOf(entry.pluginId, entry.sourceId, version.version, version.packageUrl)
                            .joinToString(separator = "|"),
                        sourceId = entry.sourceId,
                        sourceName = entry.sourceName,
                        versionLabel = version.version,
                        packageUrl = version.packageUrl,
                        publishedAt = version.publishedAt,
                        protocolVersion = version.protocolVersion,
                        minHostVersion = version.minHostVersion,
                        maxHostVersion = version.maxHostVersion,
                        changelogSummary = summarizeVersionChangelog(version.changelog),
                        compatibilityState = version.compatibilityState,
                        isSelectable = version.installable,
                    ),
                    sourceOrder = sourceOrder,
                )
            }
        }
        .sortedWith(
            compareByDescending<PluginMarketVersionOptionWithSourceOrder> { it.option.isSelectable }
                .thenComparator { left, right ->
                    compareVersions(right.option.versionLabel, left.option.versionLabel)
                }
                .thenByDescending { it.option.publishedAt }
                .thenBy { it.sourceOrder }
                .thenBy { it.option.sourceId },
        )
        .distinctBy { wrapped -> listOf(pluginId, wrapped.option.versionLabel, wrapped.option.packageUrl) }
        .map { wrapped -> wrapped.option }
        .toList()
}

internal fun buildPluginMarketPagePresentation(
    cards: List<PluginMarketCardPresentation>,
    requestedPage: Int,
    pageSize: Int = 2,
): PluginMarketPagePresentation {
    require(pageSize > 0) { "pageSize must be greater than 0." }
    val totalPages = maxOf(1, (cards.size + pageSize - 1) / pageSize)
    val currentPage = requestedPage.coerceIn(1, totalPages)
    val startIndex = (currentPage - 1) * pageSize
    val visibleCards = cards.drop(startIndex).take(pageSize)
    return PluginMarketPagePresentation(
        currentPage = currentPage,
        totalPages = totalPages,
        visibleCards = visibleCards,
        canGoPrevious = currentPage > 1,
        canGoNext = currentPage < totalPages,
    )
}

internal fun buildPluginMarketDetailPresentation(
    uiState: PluginScreenUiState,
    pluginId: String,
): PluginMarketDetailPresentation? {
    val entries = uiState.catalogEntries.filter { it.pluginId == pluginId }
    if (entries.isEmpty()) return null
    val versionOptions = buildPluginMarketVersionOptions(
        entries = entries,
        pluginId = pluginId,
    )
    val selectedVersion = uiState.selectedMarketVersionKeys[pluginId]
        ?.let { key -> versionOptions.firstOrNull { it.stableKey == key && it.isSelectable } }
        ?: versionOptions.firstOrNull { it.isSelectable }
        ?: versionOptions.firstOrNull()
    val entry = selectedVersion?.let { option ->
        entries.firstOrNull { it.sourceId == option.sourceId }
    } ?: entries.first()
    val selectedVersionLabel = selectedVersion?.versionLabel ?: entry.latestVersion
    val installedRecord = uiState.records.firstOrNull { it.pluginId == pluginId }
    val status = when {
        installedRecord == null -> PluginMarketStatus.NOT_INSTALLED
        compareVersions(selectedVersionLabel, installedRecord.installedVersion) > 0 ->
            PluginMarketStatus.UPDATE_AVAILABLE
        else -> PluginMarketStatus.INSTALLED
    }
    return PluginMarketDetailPresentation(
        pluginId = entry.pluginId,
        title = entry.title,
        author = entry.author,
        summary = entry.summary,
        latestVersionLabel = selectedVersionLabel,
        installedVersionLabel = installedRecord?.installedVersion.orEmpty(),
        status = status,
        primaryAction = when (status) {
            PluginMarketStatus.NOT_INSTALLED -> PluginMarketPrimaryAction.INSTALL
            PluginMarketStatus.UPDATE_AVAILABLE -> PluginMarketPrimaryAction.UPDATE
            PluginMarketStatus.INSTALLED -> PluginMarketPrimaryAction.INSTALLED
        },
        repositoryUrl = entry.repositoryUrl,
        repositoryHost = entry.repositoryUrl.toRepositoryHost(),
        sourceName = entry.sourceName,
        versionOptions = versionOptions,
        selectedVersionKey = selectedVersion?.stableKey.orEmpty(),
        selectedVersionLabel = selectedVersionLabel,
        selectedVersionCompatibility = selectedVersion?.compatibilityState ?: PluginCompatibilityState.unknown(),
        selectedVersionIsSelectable = selectedVersion?.isSelectable ?: true,
    )
}

private fun PluginCatalogEntryCardUiState.effectiveMarketVersions(): List<PluginCatalogEntryVersionUiState> {
    if (versions.isNotEmpty()) return versions
    if (latestVersion.isBlank()) return emptyList()
    return listOf(
        PluginCatalogEntryVersionUiState(
            version = latestVersion,
            packageUrl = "",
            publishedAt = 0L,
            protocolVersion = 0,
            minHostVersion = "0.0.0",
            maxHostVersion = "",
            compatibilityState = PluginCompatibilityState.unknown(),
            installable = false,
        ),
    )
}

private fun summarizeVersionChangelog(changelog: String): String {
    return changelog
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
}

internal fun buildPluginInstalledLibraryPresentation(
    uiState: PluginScreenUiState,
    selectedFilter: PluginInstalledLibraryFilter,
): PluginInstalledLibraryPresentation {
    val cards = uiState.records
        .map { record -> buildPluginInstalledLibraryCardPresentation(record, uiState) }
        .sortedWith(
            compareBy<PluginInstalledLibraryCardPresentation>({ it.priority.sortOrder() })
                .thenBy { it.title.lowercase() }
                .thenBy { it.pluginId },
        )
    return PluginInstalledLibraryPresentation(
        summary = PluginInstalledLibrarySummaryPresentation(
            totalCount = cards.size,
            enabledCount = cards.count { it.isEnabled },
            needsReviewCount = cards.count { it.hasIssue },
            updatesCount = cards.count { it.hasUpdateAvailable },
            disabledCount = cards.count { !it.isEnabled },
        ),
        bulkActions = listOf(
            PluginInstalledLibraryBulkActionPresentation(
                action = PluginInstalledLibraryBulkAction.ReviewIssues,
                count = cards.count { it.hasIssue },
                targetFilter = PluginInstalledLibraryFilter.Issues,
                enabled = cards.any { it.hasIssue },
            ),
            PluginInstalledLibraryBulkActionPresentation(
                action = PluginInstalledLibraryBulkAction.ReviewUpdates,
                count = cards.count { it.hasUpdateAvailable },
                targetFilter = PluginInstalledLibraryFilter.Updates,
                enabled = cards.any { it.hasUpdateAvailable },
            ),
            PluginInstalledLibraryBulkActionPresentation(
                action = PluginInstalledLibraryBulkAction.ReviewDisabled,
                count = cards.count { !it.isEnabled },
                targetFilter = PluginInstalledLibraryFilter.Disabled,
                enabled = cards.any { !it.isEnabled },
            ),
        ),
        filters = listOf(
            PluginInstalledLibraryFilter.All,
            PluginInstalledLibraryFilter.Enabled,
            PluginInstalledLibraryFilter.Disabled,
            PluginInstalledLibraryFilter.Updates,
            PluginInstalledLibraryFilter.Issues,
            PluginInstalledLibraryFilter.PermissionChanges,
        ).map { filter ->
            PluginInstalledLibraryFilterPresentation(
                filter = filter,
                count = cards.count { it.matchesFilter(filter) },
                selected = filter == selectedFilter,
            )
        },
        cards = cards.filter { it.matchesFilter(selectedFilter) },
    )
}

internal fun installedLibraryBadgePalette(priority: PluginInstalledLibraryPriority): PluginBadgePalette {
    return when (priority) {
        PluginInstalledLibraryPriority.Normal -> PluginBadgePalette(
            containerColor = MonochromeUi.mutedSurface,
            contentColor = MonochromeUi.textSecondary,
        )
        PluginInstalledLibraryPriority.Attention -> {
            val palette = PluginUiSpec.schemaStatusPalette(com.astrbot.android.model.plugin.PluginUiStatus.Warning)
            PluginBadgePalette(
                containerColor = palette.containerColor,
                contentColor = palette.contentColor,
            )
        }
        PluginInstalledLibraryPriority.Critical -> {
            val palette = PluginUiSpec.schemaStatusPalette(com.astrbot.android.model.plugin.PluginUiStatus.Error)
            PluginBadgePalette(
                containerColor = palette.containerColor,
                contentColor = palette.contentColor,
            )
        }
    }
}

internal fun buildPluginQuickInstallPresentation(
    selectedMode: PluginQuickInstallMode,
): PluginQuickInstallPresentation {
    return PluginQuickInstallPresentation(
        selectedMode = selectedMode,
        showLocalZipAction = selectedMode == PluginQuickInstallMode.LocalZip,
        showRepositoryUrlForm = selectedMode == PluginQuickInstallMode.RepositoryUrl,
        showDirectPackageUrlForm = selectedMode == PluginQuickInstallMode.DirectPackageUrl,
        promotedModes = listOf(
            PluginQuickInstallMode.LocalZip,
            PluginQuickInstallMode.RepositoryUrl,
        ),
        advancedModes = listOf(PluginQuickInstallMode.DirectPackageUrl),
        isAdvancedModeSelected = selectedMode == PluginQuickInstallMode.DirectPackageUrl,
    )
}

internal fun buildPluginLocalInstallSheetPresentation(
    showSheet: Boolean = true,
    selectedMode: PluginQuickInstallMode = PluginQuickInstallMode.LocalZip,
): PluginLocalInstallSheetPresentation {
    return PluginLocalInstallSheetPresentation(
        showSheet = showSheet,
        selectedMode = selectedMode,
        availableModes = PluginQuickInstallMode.entries,
    )
}

internal fun buildPluginLocalInstallSheetPresentation(): PluginLocalInstallSheetPresentation {
    return buildPluginLocalInstallSheetPresentation(
        showSheet = true,
        selectedMode = PluginQuickInstallMode.LocalZip,
    )
}

private fun buildPluginLocalCardPresentation(
    record: PluginInstallRecord,
    uiState: PluginScreenUiState,
): PluginLocalCardPresentation {
    val governance = uiState.governanceByPluginId[record.pluginId]?.snapshot
    return PluginLocalCardPresentation(
        pluginId = record.pluginId,
        title = record.manifestSnapshot.title,
        description = record.manifestSnapshot.description.ifBlank { record.manifestSnapshot.entrySummary },
        author = record.manifestSnapshot.author,
        isEnabled = record.enabled,
        hasUpdateAvailable = uiState.updateAvailabilitiesByPluginId[record.pluginId]?.updateAvailable == true,
        protocolVersion = governance?.protocolVersion?.takeIf { it > 0 } ?: record.manifestSnapshot.protocolVersion,
        runtimeHealthLabel = governance?.runtimeHealth?.status?.toLocalRuntimeHealthLabel() ?: "Unknown",
        runtimeHealthDetail = localRuntimeHealthDetail(
            governance = governance,
            runtimeHealthStatus = governance?.runtimeHealth?.status,
            record = record,
        ),
    )
}

private fun buildPluginInstalledLibraryCardPresentation(
    record: PluginInstallRecord,
    uiState: PluginScreenUiState,
): PluginInstalledLibraryCardPresentation {
    val updateAvailability = uiState.updateAvailabilitiesByPluginId[record.pluginId]
    val failureState = uiState.failureStatesByPluginId[record.pluginId]
    val runtimeHealthStatus = uiState.governanceByPluginId[record.pluginId]?.snapshot?.runtimeHealth?.status
    val hasUpdateAvailable = updateAvailability?.updateAvailable == true
    val hasPermissionChanges = updateAvailability?.permissionDiff?.hasMeaningfulChanges() == true
    val criticalPermissionChanges = updateAvailability?.permissionDiff?.requiresSecondaryConfirmation == true
    val compatibilityStatus = record.compatibilityState.status

    return when {
        failureState?.isSuspended == true -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Critical,
            status = PluginInstalledLibraryStatus.Suspended,
            insight = PluginInstalledLibraryInsight.Suspended,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = hasPermissionChanges,
            hasIssue = true,
        )
        runtimeHealthStatus == PluginRuntimeHealthStatus.Suspended -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Critical,
            status = PluginInstalledLibraryStatus.Suspended,
            insight = PluginInstalledLibraryInsight.Suspended,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = hasPermissionChanges,
            hasIssue = true,
        )
        compatibilityStatus == PluginCompatibilityStatus.INCOMPATIBLE -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Critical,
            status = PluginInstalledLibraryStatus.Incompatible,
            insight = PluginInstalledLibraryInsight.Incompatible,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = hasPermissionChanges,
            hasIssue = true,
        )
        runtimeHealthStatus == PluginRuntimeHealthStatus.UnsupportedProtocol -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Critical,
            status = PluginInstalledLibraryStatus.Incompatible,
            insight = PluginInstalledLibraryInsight.Incompatible,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = hasPermissionChanges,
            hasIssue = true,
        )
        hasPermissionChanges && criticalPermissionChanges -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Critical,
            status = PluginInstalledLibraryStatus.PermissionChanges,
            insight = PluginInstalledLibraryInsight.PermissionChanges,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = true,
            hasIssue = true,
        )
        hasUpdateAvailable -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Attention,
            status = PluginInstalledLibraryStatus.UpdateAvailable,
            insight = PluginInstalledLibraryInsight.UpdateAvailable,
            primaryAction = PluginInstalledLibraryPrimaryAction.Update,
            isEnabled = record.enabled,
            hasUpdateAvailable = true,
            hasPermissionChanges = hasPermissionChanges,
            hasIssue = true,
        )
        hasPermissionChanges -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Attention,
            status = PluginInstalledLibraryStatus.PermissionChanges,
            insight = PluginInstalledLibraryInsight.PermissionChanges,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = false,
            hasPermissionChanges = true,
            hasIssue = true,
        )
        runtimeHealthStatus == PluginRuntimeHealthStatus.BootstrapFailed ||
            runtimeHealthStatus == PluginRuntimeHealthStatus.UpgradeRequired -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Attention,
            status = PluginInstalledLibraryStatus.CompatibilityUnknown,
            insight = PluginInstalledLibraryInsight.CompatibilityUnknown,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = hasUpdateAvailable,
            hasPermissionChanges = hasPermissionChanges,
            hasIssue = true,
        )
        compatibilityStatus == PluginCompatibilityStatus.UNKNOWN -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Attention,
            status = PluginInstalledLibraryStatus.CompatibilityUnknown,
            insight = PluginInstalledLibraryInsight.CompatibilityUnknown,
            primaryAction = PluginInstalledLibraryPrimaryAction.Review,
            isEnabled = record.enabled,
            hasUpdateAvailable = false,
            hasPermissionChanges = false,
            hasIssue = true,
        )
        else -> PluginInstalledLibraryCardPresentation(
            pluginId = record.pluginId,
            title = record.manifestSnapshot.title,
            versionLabel = record.installedVersion,
            sourceLabel = pluginSourceLabelPlain(record.source.sourceType),
            priority = PluginInstalledLibraryPriority.Normal,
            status = if (record.enabled) PluginInstalledLibraryStatus.Enabled else PluginInstalledLibraryStatus.Disabled,
            insight = if (record.enabled) {
                PluginInstalledLibraryInsight.UpToDate
            } else {
                PluginInstalledLibraryInsight.DisabledReady
            },
            primaryAction = PluginInstalledLibraryPrimaryAction.Open,
            isEnabled = record.enabled,
            hasUpdateAvailable = false,
            hasPermissionChanges = false,
            hasIssue = false,
        )
    }
}

internal fun buildPluginRecordPresentation(
    record: PluginInstallRecord,
): PluginRecordPresentation {
    return PluginRecordPresentation(
        badges = listOf(
            pluginSourceLabelPlain(record.source.sourceType),
            pluginCompatibilityLabelPlain(record.compatibilityState.status),
        ),
    )
}

internal fun buildPluginPermissionPresentation(
    record: PluginInstallRecord,
): List<PluginPermissionPresentation> {
    return record.permissionSnapshot.map(::toPermissionPresentation)
}

internal fun pluginSourceLabelPlain(sourceType: PluginSourceType): String {
    return when (sourceType) {
        PluginSourceType.LOCAL_FILE -> "Local file"
        PluginSourceType.MANUAL_IMPORT -> "Manual import"
        PluginSourceType.REPOSITORY -> "Repository"
        PluginSourceType.DIRECT_LINK -> "Direct link"
    }
}

private fun PluginInstalledLibraryPriority.sortOrder(): Int {
    return when (this) {
        PluginInstalledLibraryPriority.Critical -> 0
        PluginInstalledLibraryPriority.Attention -> 1
        PluginInstalledLibraryPriority.Normal -> 2
    }
}

private fun PluginInstalledLibraryCardPresentation.matchesFilter(filter: PluginInstalledLibraryFilter): Boolean {
    return when (filter) {
        PluginInstalledLibraryFilter.All -> true
        PluginInstalledLibraryFilter.Enabled -> isEnabled
        PluginInstalledLibraryFilter.Disabled -> !isEnabled
        PluginInstalledLibraryFilter.Updates -> hasUpdateAvailable
        PluginInstalledLibraryFilter.Issues -> hasIssue
        PluginInstalledLibraryFilter.PermissionChanges -> hasPermissionChanges
    }
}

private fun PluginLocalCardPresentation.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        description.contains(query, ignoreCase = true) ||
        author.contains(query, ignoreCase = true)
}

private fun PluginMarketCardPresentation.matchesSearch(query: String): Boolean {
    if (query.isBlank()) return true
    return title.contains(query, ignoreCase = true) ||
        description.contains(query, ignoreCase = true) ||
        author.contains(query, ignoreCase = true)
}

private fun String.toRepositoryHost(): String {
    if (isBlank()) return ""
    return runCatching { URI(this).host.orEmpty() }.getOrDefault("")
}

private fun PluginLocalCardPresentation.matchesLocalFilter(filter: PluginLocalFilter): Boolean {
    return when (filter) {
        PluginLocalFilter.ALL -> true
        PluginLocalFilter.ENABLED -> isEnabled
        PluginLocalFilter.DISABLED -> !isEnabled
        PluginLocalFilter.UPDATES -> hasUpdateAvailable
    }
}

private fun PluginPermissionDiff.hasMeaningfulChanges(): Boolean {
    return added.isNotEmpty() || removed.isNotEmpty() || changed.isNotEmpty() || riskUpgraded.isNotEmpty()
}

private fun PluginRuntimeHealthStatus.toLocalRuntimeHealthLabel(): String {
    return when (this) {
        PluginRuntimeHealthStatus.Healthy -> "Healthy"
        PluginRuntimeHealthStatus.BootstrapFailed -> "Bootstrap failed"
        PluginRuntimeHealthStatus.Suspended -> "Suspended"
        PluginRuntimeHealthStatus.Disabled -> "Disabled"
        PluginRuntimeHealthStatus.UnsupportedProtocol -> "Unsupported protocol"
        PluginRuntimeHealthStatus.UpgradeRequired -> "Upgrade required"
    }
}

private fun localRuntimeHealthDetail(
    governance: PluginGovernanceSnapshot?,
    runtimeHealthStatus: PluginRuntimeHealthStatus?,
    record: PluginInstallRecord,
): String {
    governance?.runtimeHealth?.detail
        ?.takeIf { detail -> detail.isNotBlank() }
        ?.let { return it }
    if (runtimeHealthStatus == null) {
        return "Governance snapshot unavailable."
    }
    return when (runtimeHealthStatus) {
        PluginRuntimeHealthStatus.Suspended -> "This plugin is suspended until recovery is available."
        PluginRuntimeHealthStatus.UpgradeRequired -> "Upgrade the host before enabling this plugin."
        PluginRuntimeHealthStatus.UnsupportedProtocol -> "This plugin protocol is not supported on the current host."
        PluginRuntimeHealthStatus.BootstrapFailed -> "Runtime bootstrap failed."
        PluginRuntimeHealthStatus.Disabled -> if (record.enabled) "" else "Runtime is currently disabled."
        PluginRuntimeHealthStatus.Healthy -> ""
    }
}

internal fun pluginCompatibilityLabelPlain(status: PluginCompatibilityStatus): String {
    return when (status) {
        PluginCompatibilityStatus.COMPATIBLE -> "Compatible"
        PluginCompatibilityStatus.INCOMPATIBLE -> "Incompatible"
        PluginCompatibilityStatus.UNKNOWN -> "Compatibility unknown"
    }
}

private fun toPermissionPresentation(
    permission: PluginPermissionDeclaration,
): PluginPermissionPresentation {
    return PluginPermissionPresentation(
        title = permission.title,
        description = permission.description,
        requirementLabel = if (permission.required) "Required permission" else "Optional permission",
    )
}

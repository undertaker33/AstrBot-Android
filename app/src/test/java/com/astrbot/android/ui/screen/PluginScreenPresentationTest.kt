package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginPermissionDiff
import com.astrbot.android.model.plugin.PluginPermissionUpgrade
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUiStatus
import com.astrbot.android.ui.MonochromeUi
import com.astrbot.android.ui.screen.plugin.PluginBadgePalette
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginActionFeedback
import com.astrbot.android.ui.viewmodel.PluginFailureUiState
import com.astrbot.android.ui.viewmodel.PluginRepositorySourceCardUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PluginScreenPresentationTest {

    @Test
    fun `homepage section builder keeps hero quick install health overview installed discover repositories order`() {
        assertEquals(
            listOf(
                PluginHomepageSection.Hero,
                PluginHomepageSection.QuickInstall,
                PluginHomepageSection.HealthOverview,
                PluginHomepageSection.InstalledLibrary,
                PluginHomepageSection.Discover,
                PluginHomepageSection.Repositories,
            ),
            buildPluginHomepageSections(),
        )
    }

    @Test
    fun `plugin card and permissions presentation do not expose risk labels`() {
        val record = pluginRecord("demo")

        val card = buildPluginRecordPresentation(record)
        val permissions = buildPluginPermissionPresentation(record)

        assertEquals(listOf("Local file", "Compatible"), card.badges)
        assertFalse(card.badges.any { it.contains("risk", ignoreCase = true) })
        assertFalse(permissions.any { item -> item.requirementLabel.contains("risk", ignoreCase = true) })
    }

    @Test
    fun `quick install presentation shows only the selected mode input`() {
        val local = buildPluginQuickInstallPresentation(PluginQuickInstallMode.LocalZip)
        val repository = buildPluginQuickInstallPresentation(PluginQuickInstallMode.RepositoryUrl)
        val direct = buildPluginQuickInstallPresentation(PluginQuickInstallMode.DirectPackageUrl)

        assertEquals(
            PluginQuickInstallPresentation(
                selectedMode = PluginQuickInstallMode.LocalZip,
                showLocalZipAction = true,
                showRepositoryUrlForm = false,
                showDirectPackageUrlForm = false,
            ),
            local,
        )
        assertEquals(
            PluginQuickInstallPresentation(
                selectedMode = PluginQuickInstallMode.RepositoryUrl,
                showLocalZipAction = false,
                showRepositoryUrlForm = true,
                showDirectPackageUrlForm = false,
            ),
            repository,
        )
        assertEquals(
            PluginQuickInstallPresentation(
                selectedMode = PluginQuickInstallMode.DirectPackageUrl,
                showLocalZipAction = false,
                showRepositoryUrlForm = false,
                showDirectPackageUrlForm = true,
            ),
            direct,
        )
    }

    @Test
    fun `health overview presentation aggregates installed updates needs review and sources`() {
        val healthy = pluginRecord("healthy")
        val incompatible = pluginRecord(pluginId = "incompatible", compatible = false)
        val updateAvailable = pluginRecord("update")
        val failing = pluginRecordWithFailure("failing")

        val uiState = PluginScreenUiState(
            records = listOf(healthy, incompatible, updateAvailable, failing),
            repositorySources = listOf(
                PluginRepositorySourceCardUiState(
                    sourceId = "repo-1",
                    title = "Repo 1",
                    catalogUrl = "https://repo.example.com/catalog.json",
                    pluginCount = 2,
                ),
                PluginRepositorySourceCardUiState(
                    sourceId = "repo-2",
                    title = "Repo 2",
                    catalogUrl = "https://repo.example.org/catalog.json",
                    pluginCount = 1,
                ),
            ),
            updateAvailabilitiesByPluginId = mapOf(
                "update" to PluginUpdateAvailability(
                    pluginId = "update",
                    installedVersion = "1.0.0",
                    latestVersion = "1.1.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    changelogSummary = "Adds more weather cards.",
                    catalogSourceId = "repo-1",
                    packageUrl = "https://repo.example.com/packages/update-1.1.0.zip",
                ),
            ),
            failureStatesByPluginId = mapOf(
                "failing" to PluginFailureUiState(
                    consecutiveFailureCount = 2,
                    isSuspended = false,
                    statusMessage = PluginActionFeedback.Text("Active"),
                    summaryMessage = PluginActionFeedback.Text("Failure"),
                    recoveryMessage = PluginActionFeedback.Text("Recover"),
                ),
            ),
        )

        val overview = buildPluginHealthOverviewPresentation(uiState)

        assertEquals(4, overview.installedCount)
        assertEquals(1, overview.updatesAvailableCount)
        assertEquals(3, overview.needsReviewCount)
        assertEquals(2, overview.sourceCount)
    }

    @Test
    fun `installed library presentation filters cards and maps priority actions`() {
        val enabled = installedPluginRecord("enabled", enabled = true)
        val disabled = installedPluginRecord("disabled", enabled = false)
        val update = installedPluginRecord("update", enabled = true)
        val unknown = installedPluginRecord(
            pluginId = "unknown",
            enabled = true,
            compatibilityState = PluginCompatibilityState.unknown(),
        )
        val incompatible = installedPluginRecord(
            pluginId = "incompatible",
            enabled = true,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = false,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
        )
        val permissionChanges = installedPluginRecord("permission", enabled = true)
        val suspended = installedPluginRecord(
            pluginId = "suspended",
            enabled = false,
            failureState = PluginFailureState(
                consecutiveFailureCount = 4,
                lastFailureAtEpochMillis = 1L,
                lastErrorSummary = "socket timeout",
                suspendedUntilEpochMillis = 1L,
            ),
        )

        val uiState = PluginScreenUiState(
            records = listOf(
                enabled,
                disabled,
                update,
                unknown,
                incompatible,
                permissionChanges,
                suspended,
            ),
            updateAvailabilitiesByPluginId = mapOf(
                "update" to PluginUpdateAvailability(
                    pluginId = "update",
                    installedVersion = "1.0.0",
                    latestVersion = "1.1.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    changelogSummary = "Adds more weather cards.",
                    catalogSourceId = "repo-1",
                    packageUrl = "https://repo.example.com/packages/update-1.1.0.zip",
                ),
                "permission" to PluginUpdateAvailability(
                    pluginId = "permission",
                    installedVersion = "2.0.0",
                    latestVersion = "2.1.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    changelogSummary = "Requires new permissions.",
                    permissionDiff = PluginPermissionDiff(
                        added = listOf(
                            PluginPermissionDeclaration(
                                permissionId = "host.logs.read",
                                title = "Read host logs",
                                description = "Reads host logs.",
                            ),
                        ),
                    ),
                    catalogSourceId = "repo-1",
                    packageUrl = "https://repo.example.com/packages/permission-2.1.0.zip",
                ),
            ),
            failureStatesByPluginId = mapOf(
                "suspended" to PluginFailureUiState(
                    consecutiveFailureCount = 4,
                    isSuspended = true,
                    statusMessage = PluginActionFeedback.Text("Suspended"),
                    summaryMessage = PluginActionFeedback.Text("Repeated failures"),
                    recoveryMessage = PluginActionFeedback.Text("Recover"),
                ),
            ),
        )

        val presentation = buildPluginInstalledLibraryPresentation(
            uiState = uiState,
            selectedFilter = PluginInstalledLibraryFilter.All,
        )

        assertEquals(
            listOf(
                PluginInstalledLibraryFilter.All,
                PluginInstalledLibraryFilter.Enabled,
                PluginInstalledLibraryFilter.Updates,
                PluginInstalledLibraryFilter.Issues,
                PluginInstalledLibraryFilter.PermissionChanges,
            ),
            presentation.filters.map { it.filter },
        )
        assertEquals(
            listOf("incompatible", "permission", "suspended", "unknown", "update", "disabled", "enabled"),
            presentation.cards.map { it.pluginId },
        )

        val enabledCard = presentation.cards.single { it.pluginId == "enabled" }
        val disabledCard = presentation.cards.single { it.pluginId == "disabled" }
        val updateCard = presentation.cards.single { it.pluginId == "update" }
        val unknownCard = presentation.cards.single { it.pluginId == "unknown" }
        val incompatibleCard = presentation.cards.single { it.pluginId == "incompatible" }
        val permissionCard = presentation.cards.single { it.pluginId == "permission" }
        val suspendedCard = presentation.cards.single { it.pluginId == "suspended" }

        assertEquals(PluginInstalledLibraryPriority.Normal, enabledCard.priority)
        assertEquals(PluginInstalledLibraryStatus.Enabled, enabledCard.status)
        assertEquals(PluginInstalledLibraryInsight.UpToDate, enabledCard.insight)
        assertEquals(PluginInstalledLibraryPrimaryAction.Open, enabledCard.primaryAction)

        assertEquals(PluginInstalledLibraryPriority.Normal, disabledCard.priority)
        assertEquals(PluginInstalledLibraryStatus.Disabled, disabledCard.status)
        assertEquals(PluginInstalledLibraryInsight.DisabledReady, disabledCard.insight)
        assertEquals(PluginInstalledLibraryPrimaryAction.Open, disabledCard.primaryAction)

        assertEquals(PluginInstalledLibraryPriority.Attention, updateCard.priority)
        assertEquals(PluginInstalledLibraryStatus.UpdateAvailable, updateCard.status)
        assertEquals(PluginInstalledLibraryInsight.UpdateAvailable, updateCard.insight)
        assertEquals(PluginInstalledLibraryPrimaryAction.Update, updateCard.primaryAction)

        assertEquals(PluginInstalledLibraryPriority.Attention, unknownCard.priority)
        assertEquals(PluginInstalledLibraryStatus.CompatibilityUnknown, unknownCard.status)
        assertEquals(PluginInstalledLibraryInsight.CompatibilityUnknown, unknownCard.insight)
        assertEquals(PluginInstalledLibraryPrimaryAction.Review, unknownCard.primaryAction)

        assertEquals(PluginInstalledLibraryPriority.Critical, incompatibleCard.priority)
        assertEquals(PluginInstalledLibraryStatus.Incompatible, incompatibleCard.status)
        assertEquals(PluginInstalledLibraryInsight.Incompatible, incompatibleCard.insight)
        assertEquals(PluginInstalledLibraryPrimaryAction.Review, incompatibleCard.primaryAction)

        assertEquals(PluginInstalledLibraryPriority.Critical, permissionCard.priority)
        assertEquals(PluginInstalledLibraryStatus.PermissionChanges, permissionCard.status)
        assertEquals(PluginInstalledLibraryInsight.PermissionChanges, permissionCard.insight)
        assertEquals(PluginInstalledLibraryPrimaryAction.Review, permissionCard.primaryAction)

        assertEquals(PluginInstalledLibraryPriority.Critical, suspendedCard.priority)
        assertEquals(PluginInstalledLibraryStatus.Suspended, suspendedCard.status)
        assertEquals(PluginInstalledLibraryInsight.Suspended, suspendedCard.insight)
        assertEquals(PluginInstalledLibraryPrimaryAction.Review, suspendedCard.primaryAction)

        assertEquals(
            listOf("incompatible", "permission", "unknown", "update", "enabled"),
            buildPluginInstalledLibraryPresentation(uiState, PluginInstalledLibraryFilter.Enabled).cards.map { it.pluginId },
        )
        assertEquals(
            listOf("incompatible", "permission", "suspended", "unknown", "update"),
            buildPluginInstalledLibraryPresentation(uiState, PluginInstalledLibraryFilter.Issues).cards.map { it.pluginId },
        )
        assertEquals(
            listOf("permission", "update"),
            buildPluginInstalledLibraryPresentation(uiState, PluginInstalledLibraryFilter.Updates).cards.map { it.pluginId },
        )
        assertEquals(
            listOf("permission"),
            buildPluginInstalledLibraryPresentation(uiState, PluginInstalledLibraryFilter.PermissionChanges).cards.map { it.pluginId },
        )

        assertEquals(
            PluginBadgePalette(
                containerColor = MonochromeUi.mutedSurface,
                contentColor = MonochromeUi.textSecondary,
            ),
            installedLibraryBadgePalette(PluginInstalledLibraryPriority.Normal),
        )
        val warningPalette = PluginUiSpec.schemaStatusPalette(PluginUiStatus.Warning)
        val errorPalette = PluginUiSpec.schemaStatusPalette(PluginUiStatus.Error)
        assertEquals(
            PluginBadgePalette(
                containerColor = warningPalette.containerColor,
                contentColor = warningPalette.contentColor,
            ),
            installedLibraryBadgePalette(PluginInstalledLibraryPriority.Attention),
        )
        assertEquals(
            PluginBadgePalette(
                containerColor = errorPalette.containerColor,
                contentColor = errorPalette.contentColor,
            ),
            installedLibraryBadgePalette(PluginInstalledLibraryPriority.Critical),
        )
    }

    private fun pluginRecord(pluginId: String): PluginInstallRecord {
        return pluginRecord(pluginId = pluginId, compatible = true)
    }

    private fun pluginRecord(
        pluginId: String,
        compatible: Boolean,
    ): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = pluginId,
                version = "1.0.0",
                protocolVersion = 1,
                author = "AstrBot",
                title = pluginId,
                description = "Plugin $pluginId",
                permissions = listOf(
                    PluginPermissionDeclaration(
                        permissionId = "$pluginId.permission",
                        title = "Permission",
                        description = "Permission for $pluginId",
                    ),
                ),
                minHostVersion = "1.0.0",
                maxHostVersion = "2.0.0",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "Entry",
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/tmp/$pluginId.zip",
                importedAt = 1L,
            ),
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = compatible,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            lastUpdatedAt = 1L,
        )
    }

    private fun pluginRecordWithFailure(pluginId: String): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = pluginId,
                version = "1.0.0",
                protocolVersion = 1,
                author = "AstrBot",
                title = pluginId,
                description = "Plugin $pluginId",
                permissions = listOf(
                    PluginPermissionDeclaration(
                        permissionId = "$pluginId.permission",
                        title = "Permission",
                        description = "Permission for $pluginId",
                    ),
                ),
                minHostVersion = "1.0.0",
                maxHostVersion = "2.0.0",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "Entry",
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/tmp/$pluginId.zip",
                importedAt = 1L,
            ),
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            failureState = com.astrbot.android.model.plugin.PluginFailureState(
                consecutiveFailureCount = 1,
                lastFailureAtEpochMillis = 1L,
                lastErrorSummary = "failure",
                suspendedUntilEpochMillis = null,
            ),
            lastUpdatedAt = 1L,
        )
    }

    private fun installedPluginRecord(
        pluginId: String,
        enabled: Boolean,
        compatibilityState: PluginCompatibilityState = PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        ),
        failureState: PluginFailureState = PluginFailureState.none(),
    ): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = pluginId,
                version = "1.0.0",
                protocolVersion = 1,
                author = "AstrBot",
                title = pluginId,
                description = "Plugin $pluginId",
                permissions = listOf(
                    PluginPermissionDeclaration(
                        permissionId = "$pluginId.permission",
                        title = "Permission",
                        description = "Permission for $pluginId",
                    ),
                ),
                minHostVersion = "1.0.0",
                maxHostVersion = "2.0.0",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "Entry",
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/tmp/$pluginId.zip",
                importedAt = 1L,
            ),
            compatibilityState = compatibilityState,
            failureState = failureState,
            enabled = enabled,
            lastUpdatedAt = 1L,
        )
    }
}

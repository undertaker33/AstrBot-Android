package com.astrbot.android.ui.screen

import java.lang.reflect.Method
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
import com.astrbot.android.ui.viewmodel.PluginCatalogEntryCardUiState
import com.astrbot.android.ui.viewmodel.PluginCatalogEntryVersionUiState
import com.astrbot.android.ui.viewmodel.PluginFailureUiState
import com.astrbot.android.ui.viewmodel.PluginRepositorySourceCardUiState
import com.astrbot.android.ui.viewmodel.PluginScreenUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginScreenPresentationTest {

    @Test
    fun `download progress dialog exposes reusable test tag`() {
        assertEquals("download-progress-dialog", PluginUiSpec.DownloadProgressDialogTag)
    }

    @Test
    fun `local workspace presentation filters cards by search and local status`() {
        val enabled = installedPluginRecord("enabled", enabled = true)
        val disabled = installedPluginRecord("disabled", enabled = false)
        val update = installedPluginRecord("update", enabled = true).copyWithManifest(
            title = "Weather Toolkit",
            description = "Forecast cards and weather commands",
            author = "Alice",
        )

        val uiState = PluginScreenUiState(
            records = listOf(enabled, disabled, update),
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
        )

        val localFilterClass = Class.forName("com.astrbot.android.ui.screen.PluginLocalFilter")
        val updatesFilter = java.lang.Enum.valueOf(localFilterClass.asSubclass(Enum::class.java), "UPDATES")
        val presentationMethod = pluginPresentationMethod(
            "buildPluginLocalWorkspacePresentation",
            PluginScreenUiState::class.java,
            String::class.java,
            localFilterClass,
        )

        val presentation = presentationMethod.invoke(null, uiState, "alice", updatesFilter)
        val filters = propertyValue<List<*>>(presentation, "filters")
        val cards = propertyValue<List<*>>(presentation, "cards")

        assertEquals(listOf("ALL", "ENABLED", "DISABLED", "UPDATES"), filters.map { propertyValue<Enum<*>>(it, "filter").name })
        assertEquals(listOf("update"), cards.map { propertyValue<String>(it, "pluginId") })
        assertEquals("Weather Toolkit", propertyValue<String>(cards.single(), "title"))
        assertEquals("Alice", propertyValue<String>(cards.single(), "author"))
    }

    @Test
    fun `market workspace presentation maps install states and repository links`() {
        val uiState = PluginScreenUiState(
            records = listOf(
                installedPluginRecord("installed", enabled = true),
                installedPluginRecord("update", enabled = true),
            ),
            catalogEntries = listOf(
                PluginCatalogEntryCardUiState(
                    sourceId = "repo-1",
                    pluginId = "installed",
                    title = "Installed plugin",
                    author = "Alice",
                    summary = "Already on this device",
                    latestVersion = "1.0.0",
                    repositoryUrl = "https://github.com/example/installed-plugin",
                ),
                PluginCatalogEntryCardUiState(
                    sourceId = "repo-1",
                    pluginId = "update",
                    title = "Needs update",
                    author = "Bob",
                    summary = "Has a newer cloud build",
                    latestVersion = "1.1.0",
                    repositoryUrl = "https://github.com/example/update-plugin",
                ),
                PluginCatalogEntryCardUiState(
                    sourceId = "repo-1",
                    pluginId = "cloud-only",
                    title = "Cloud only",
                    author = "Carol",
                    summary = "Ready to install",
                    latestVersion = "2.0.0",
                    repositoryUrl = "https://github.com/example/cloud-only",
                ),
            ),
        )

        val presentationMethod = pluginPresentationMethod(
            "buildPluginMarketWorkspacePresentation",
            PluginScreenUiState::class.java,
            String::class.java,
        )
        val presentation = presentationMethod.invoke(null, uiState, "")
        val cards = propertyValue<List<*>>(presentation, "cards")

        assertEquals(listOf("cloud-only", "installed", "update"), cards.map { propertyValue<String>(it, "pluginId") })
        assertEquals(
            listOf("NOT_INSTALLED", "INSTALLED", "UPDATE_AVAILABLE"),
            cards.map { propertyValue<Enum<*>>(it, "status").name },
        )
        assertEquals(
            "https://github.com/example/installed-plugin",
            propertyValue<String>(cards.first { propertyValue<String>(it, "pluginId") == "installed" }, "repositoryUrl"),
        )
        assertEquals(
            "https://github.com/example/update-plugin",
            propertyValue<String>(cards.first { propertyValue<String>(it, "pluginId") == "update" }, "repositoryUrl"),
        )
    }

    @Test
    fun `market workspace presentation deduplicates repeated plugin ids from multiple sources`() {
        val uiState = PluginScreenUiState(
            catalogEntries = listOf(
                PluginCatalogEntryCardUiState(
                    sourceId = "repo-raw",
                    pluginId = "cc.astrbot.android.plugin.meme_manager.native",
                    title = "Meme Manager",
                    author = "AstrBot",
                    summary = "Ready to install",
                    latestVersion = "1.0.0",
                    repositoryUrl = "https://github.com/example/meme/raw",
                    sourceName = "Raw",
                ),
                PluginCatalogEntryCardUiState(
                    sourceId = "repo-blob",
                    pluginId = "cc.astrbot.android.plugin.meme_manager.native",
                    title = "Meme Manager",
                    author = "AstrBot",
                    summary = "Duplicate entry",
                    latestVersion = "1.0.0",
                    repositoryUrl = "https://github.com/example/meme/blob",
                    sourceName = "Blob",
                ),
            ),
        )

        val presentationMethod = pluginPresentationMethod(
            "buildPluginMarketWorkspacePresentation",
            PluginScreenUiState::class.java,
            String::class.java,
        )
        val presentation = presentationMethod.invoke(null, uiState, "")
        val cards = propertyValue<List<*>>(presentation, "cards")

        assertEquals(listOf("cc.astrbot.android.plugin.meme_manager.native"), cards.map { propertyValue<String>(it, "pluginId") })
        assertEquals(listOf("repo-raw:cc.astrbot.android.plugin.meme_manager.native"), cards.map { propertyValue<String>(it, "stableKey") })
    }

    @Test
    fun `market version options default to highest compatible version across sources`() {
        val entries = listOf(
            PluginCatalogEntryCardUiState(
                sourceId = "repo-new",
                pluginId = "weather",
                title = "Weather",
                author = "AstrBot",
                summary = "Weather tools",
                latestVersion = "0.2.0",
                sourceName = "Nightly",
                versions = listOf(
                    catalogEntryVersion(
                        version = "0.2.0",
                        packageUrl = "https://repo.example.com/weather-0.2.0.zip",
                        publishedAt = 200L,
                        minHostVersion = "9.9.9",
                    ),
                ),
            ),
            PluginCatalogEntryCardUiState(
                sourceId = "repo-stable",
                pluginId = "weather",
                title = "Weather",
                author = "AstrBot",
                summary = "Weather tools",
                latestVersion = "0.1.5",
                sourceName = "Stable",
                versions = listOf(
                    catalogEntryVersion(
                        version = "0.1.5",
                        packageUrl = "https://repo.example.com/weather-0.1.5.zip",
                        publishedAt = 150L,
                        minHostVersion = "0.1.0",
                    ),
                    catalogEntryVersion(
                        version = "0.1.5",
                        packageUrl = "https://repo.example.com/weather-0.1.5.zip",
                        publishedAt = 140L,
                        minHostVersion = "0.1.0",
                    ),
                ),
            ),
        )

        val options = buildPluginMarketVersionOptions(
            entries = entries,
            pluginId = "weather",
            hostVersion = "0.4.6",
            supportedProtocolVersion = 1,
        )

        assertEquals(listOf("0.1.5", "0.2.0"), options.map { it.versionLabel })
        assertEquals("0.1.5", options.first().versionLabel)
        assertTrue(options.first().isSelectable)
        assertFalse(options.last().isSelectable)
        assertEquals("INCOMPATIBLE", options.last().compatibilityState.status.name)
    }

    @Test
    fun `market list and detail use default compatible version option`() {
        val uiState = PluginScreenUiState(
            hostVersion = "0.4.6",
            catalogEntries = marketVersionEntries(),
        )

        val workspace = buildPluginMarketWorkspacePresentation(uiState, "")
        val card = workspace.cards.single()

        assertEquals("0.1.5", card.versionLabel)
        assertEquals("repo-stable:weather", card.stableKey)

        val detail = buildPluginMarketDetailPresentation(uiState, "weather")
        val versionOptions = propertyValue<List<*>>(detail, "versionOptions")
        val selectedVersionKey = propertyValue<String>(detail, "selectedVersionKey")

        assertEquals("0.1.5", propertyValue<String>(detail, "selectedVersionLabel"))
        assertEquals(listOf("0.1.5", "0.2.0"), versionOptions.map { propertyValue<String>(it, "versionLabel") })
        assertEquals(propertyValue<String>(versionOptions.first(), "stableKey"), selectedVersionKey)
        assertEquals("COMPATIBLE", propertyValue<PluginCompatibilityState>(detail, "selectedVersionCompatibility").status.name)
    }

    @Test
    fun `market detail presentation exposes detail metadata and primary action state`() {
        val uiState = PluginScreenUiState(
            records = listOf(
                installedPluginRecord("installed", enabled = true),
                installedPluginRecord("update", enabled = true),
            ),
            catalogEntries = listOf(
                PluginCatalogEntryCardUiState(
                    sourceId = "repo-1",
                    pluginId = "installed",
                    title = "Installed plugin",
                    author = "Alice",
                    summary = "Already installed from another source",
                    latestVersion = "1.0.0",
                    repositoryUrl = "https://github.com/example/installed-plugin",
                    sourceName = "AstrBot Market",
                ),
                PluginCatalogEntryCardUiState(
                    sourceId = "repo-1",
                    pluginId = "update",
                    title = "Needs update",
                    author = "Bob",
                    summary = "Has a newer cloud build",
                    latestVersion = "1.1.0",
                    repositoryUrl = "https://github.com/example/update-plugin",
                    sourceName = "AstrBot Market",
                ),
                PluginCatalogEntryCardUiState(
                    sourceId = "repo-1",
                    pluginId = "cloud-only",
                    title = "Cloud only",
                    author = "Carol",
                    summary = "Ready to install",
                    latestVersion = "2.0.0",
                    repositoryUrl = "https://github.com/example/cloud-only",
                    sourceName = "AstrBot Market",
                ),
            ),
        )

        val presentationMethod = pluginPresentationMethod(
            "buildPluginMarketDetailPresentation",
            PluginScreenUiState::class.java,
            String::class.java,
        )

        val installed = presentationMethod.invoke(null, uiState, "installed")
        val update = presentationMethod.invoke(null, uiState, "update")
        val cloudOnly = presentationMethod.invoke(null, uiState, "cloud-only")
        val missing = presentationMethod.invoke(null, uiState, "missing")

        assertEquals("INSTALLED", propertyValue<Enum<*>>(installed, "status").name)
        assertEquals("INSTALLED", propertyValue<Enum<*>>(installed, "primaryAction").name)
        assertEquals("1.0.0", propertyValue<String>(installed, "installedVersionLabel"))

        assertEquals("UPDATE_AVAILABLE", propertyValue<Enum<*>>(update, "status").name)
        assertEquals("UPDATE", propertyValue<Enum<*>>(update, "primaryAction").name)
        assertEquals("github.com", propertyValue<String>(update, "repositoryHost"))
        assertEquals("AstrBot Market", propertyValue<String>(update, "sourceName"))

        assertEquals("NOT_INSTALLED", propertyValue<Enum<*>>(cloudOnly, "status").name)
        assertEquals("INSTALL", propertyValue<Enum<*>>(cloudOnly, "primaryAction").name)
        assertEquals("", propertyValue<String>(cloudOnly, "installedVersionLabel"))

        assertEquals(null, missing)
    }

    @Test
    fun `market pagination uses fixed two cards and does not add filler cards`() {
        val cards = (1..5).map { index -> marketCard("plugin-$index") }

        val page1 = buildPluginMarketPagePresentation(cards, requestedPage = 1)
        val page2 = buildPluginMarketPagePresentation(cards, requestedPage = 2)
        val page3 = buildPluginMarketPagePresentation(cards, requestedPage = 3)

        assertEquals(1, page1.currentPage)
        assertEquals(3, page1.totalPages)
        assertEquals(listOf("plugin-1", "plugin-2"), page1.visibleCards.map { it.pluginId })
        assertFalse(page1.canGoPrevious)
        assertTrue(page1.canGoNext)

        assertEquals(listOf("plugin-3", "plugin-4"), page2.visibleCards.map { it.pluginId })
        assertTrue(page2.canGoPrevious)
        assertTrue(page2.canGoNext)

        assertEquals(listOf("plugin-5"), page3.visibleCards.map { it.pluginId })
        assertTrue(page3.canGoPrevious)
        assertFalse(page3.canGoNext)
    }

    @Test
    fun `market pagination clamps requested page bounds and keeps empty list stable`() {
        val cards = (1..3).map { index -> marketCard("plugin-$index") }

        val belowFirst = buildPluginMarketPagePresentation(cards, requestedPage = 0)
        val beyondLast = buildPluginMarketPagePresentation(cards, requestedPage = 99)
        val empty = buildPluginMarketPagePresentation(emptyList(), requestedPage = 3)

        assertEquals(1, belowFirst.currentPage)
        assertEquals(listOf("plugin-1", "plugin-2"), belowFirst.visibleCards.map { it.pluginId })

        assertEquals(2, beyondLast.currentPage)
        assertEquals(listOf("plugin-3"), beyondLast.visibleCards.map { it.pluginId })

        assertEquals(1, empty.currentPage)
        assertEquals(1, empty.totalPages)
        assertTrue(empty.visibleCards.isEmpty())
        assertFalse(empty.canGoPrevious)
        assertFalse(empty.canGoNext)
    }

    @Test
    fun `homepage section builder keeps hero health installed quick install discover repositories order`() {
        assertEquals(
            listOf(
                PluginHomepageSection.Hero,
                PluginHomepageSection.HealthOverview,
                PluginHomepageSection.InstalledLibrary,
                PluginHomepageSection.QuickInstall,
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
                promotedModes = listOf(
                    PluginQuickInstallMode.LocalZip,
                    PluginQuickInstallMode.RepositoryUrl,
                ),
                advancedModes = listOf(PluginQuickInstallMode.DirectPackageUrl),
                isAdvancedModeSelected = false,
            ),
            local,
        )
        assertEquals(
            PluginQuickInstallPresentation(
                selectedMode = PluginQuickInstallMode.RepositoryUrl,
                showLocalZipAction = false,
                showRepositoryUrlForm = true,
                showDirectPackageUrlForm = false,
                promotedModes = listOf(
                    PluginQuickInstallMode.LocalZip,
                    PluginQuickInstallMode.RepositoryUrl,
                ),
                advancedModes = listOf(PluginQuickInstallMode.DirectPackageUrl),
                isAdvancedModeSelected = false,
            ),
            repository,
        )
        assertEquals(
            PluginQuickInstallPresentation(
                selectedMode = PluginQuickInstallMode.DirectPackageUrl,
                showLocalZipAction = false,
                showRepositoryUrlForm = false,
                showDirectPackageUrlForm = true,
                promotedModes = listOf(
                    PluginQuickInstallMode.LocalZip,
                    PluginQuickInstallMode.RepositoryUrl,
                ),
                advancedModes = listOf(PluginQuickInstallMode.DirectPackageUrl),
                isAdvancedModeSelected = true,
            ),
            direct,
        )
    }

    @Test
    fun `local install sheet presentation defaults to local zip and exposes all supported modes`() {
        val method = pluginPresentationMethod("buildPluginLocalInstallSheetPresentation")
        val presentation = method.invoke(null)

        assertEquals(
            PluginQuickInstallMode.LocalZip,
            propertyValue<PluginQuickInstallMode>(presentation, "selectedMode"),
        )
        assertEquals(
            PluginQuickInstallMode.entries,
            propertyValue<List<PluginQuickInstallMode>>(presentation, "availableModes"),
        )
        assertFalse(propertyValue<Boolean>(presentation, "showSheet").not())
    }

    @Test
    fun `local install dialog uses scrollable content container`() {
        assertTrue(pluginLocalInstallDialogUsesScrollableContent())
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
                PluginInstalledLibraryFilter.Disabled,
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

    @Test
    fun `installed library presentation exposes summary counts and batch shortcuts`() {
        val enabled = installedPluginRecord("enabled", enabled = true)
        val disabled = installedPluginRecord("disabled", enabled = false)
        val update = installedPluginRecord("update", enabled = true)
        val incompatible = installedPluginRecord(
            pluginId = "incompatible",
            enabled = true,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = false,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
        )
        val suspended = installedPluginRecord(
            pluginId = "suspended",
            enabled = false,
            failureState = PluginFailureState(
                consecutiveFailureCount = 4,
                lastFailureAtEpochMillis = 1L,
                lastErrorSummary = "socket timeout",
                suspendedUntilEpochMillis = 4_102_444_800_000L,
            ),
        )

        val uiState = PluginScreenUiState(
            records = listOf(enabled, disabled, update, incompatible, suspended),
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

        val summary = propertyValue<Any>(presentation, "summary")
        assertEquals(5, propertyValue<Int>(summary, "totalCount"))
        assertEquals(3, propertyValue<Int>(summary, "enabledCount"))
        assertEquals(3, propertyValue<Int>(summary, "needsReviewCount"))
        assertEquals(1, propertyValue<Int>(summary, "updatesCount"))
        assertEquals(2, propertyValue<Int>(summary, "disabledCount"))

        val bulkActions = propertyValue<List<*>>(presentation, "bulkActions")
        assertEquals(
            listOf("ReviewIssues", "ReviewUpdates", "ReviewDisabled"),
            bulkActions.map { propertyValue<Enum<*>>(it, "action").name },
        )
        assertEquals(listOf(3, 1, 2), bulkActions.map { propertyValue<Int>(it, "count") })
        assertEquals(
            listOf("Issues", "Updates", "Disabled"),
            bulkActions.map { propertyValue<Enum<*>>(it, "targetFilter").name },
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

    private fun marketCard(pluginId: String): PluginMarketCardPresentation {
        return PluginMarketCardPresentation(
            sourceId = "repo-$pluginId",
            pluginId = pluginId,
            title = pluginId,
            description = "Description for $pluginId",
            author = "AstrBot",
            versionLabel = "1.0.0",
            status = PluginMarketStatus.NOT_INSTALLED,
            repositoryUrl = "",
        )
    }

    private fun marketVersionEntries(): List<PluginCatalogEntryCardUiState> {
        return listOf(
            PluginCatalogEntryCardUiState(
                sourceId = "repo-new",
                pluginId = "weather",
                title = "Weather",
                author = "AstrBot",
                summary = "Weather tools",
                latestVersion = "0.2.0",
                sourceName = "Nightly",
                versions = listOf(
                    catalogEntryVersion(
                        version = "0.2.0",
                        packageUrl = "https://repo.example.com/weather-0.2.0.zip",
                        publishedAt = 200L,
                        minHostVersion = "9.9.9",
                    ),
                ),
            ),
            PluginCatalogEntryCardUiState(
                sourceId = "repo-stable",
                pluginId = "weather",
                title = "Weather",
                author = "AstrBot",
                summary = "Weather tools",
                latestVersion = "0.1.5",
                sourceName = "Stable",
                versions = listOf(
                    catalogEntryVersion(
                        version = "0.1.5",
                        packageUrl = "https://repo.example.com/weather-0.1.5.zip",
                        publishedAt = 150L,
                        minHostVersion = "0.1.0",
                    ),
                ),
            ),
        )
    }

    private fun catalogEntryVersion(
        version: String,
        packageUrl: String,
        publishedAt: Long,
        protocolVersion: Int = 1,
        minHostVersion: String = "0.0.0",
        maxHostVersion: String = "",
        changelog: String = "",
    ): PluginCatalogEntryVersionUiState {
        return PluginCatalogEntryVersionUiState(
            version = version,
            packageUrl = packageUrl,
            publishedAt = publishedAt,
            protocolVersion = protocolVersion,
            minHostVersion = minHostVersion,
            maxHostVersion = maxHostVersion,
            changelog = changelog,
        )
    }

    private fun installedPluginRecord(
        pluginId: String,
        enabled: Boolean,
        version: String = "1.0.0",
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
                version = version,
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

    @Suppress("UNCHECKED_CAST")
    private fun <T> propertyValue(target: Any?, propertyName: String): T {
        val getter = target!!.javaClass.getMethod("get${propertyName.replaceFirstChar(Char::titlecase)}")
        return getter.invoke(target) as T
    }

    private fun pluginPresentationMethod(name: String, vararg parameterTypes: Class<*>): Method {
        return Class.forName("com.astrbot.android.ui.screen.PluginScreenPresentationKt")
            .getDeclaredMethod(name, *parameterTypes)
    }

    private fun PluginInstallRecord.copyWithManifest(
        title: String = manifestSnapshot.title,
        description: String = manifestSnapshot.description,
        author: String = manifestSnapshot.author,
    ): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifestSnapshot.copy(
                title = title,
                description = description,
                author = author,
            ),
            source = source,
            compatibilityState = compatibilityState,
            permissionSnapshot = permissionSnapshot,
            failureState = failureState,
            uninstallPolicy = uninstallPolicy,
            catalogSourceId = catalogSourceId,
            installedPackageUrl = installedPackageUrl,
            lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
            enabled = enabled,
            installedAt = installedAt,
            lastUpdatedAt = lastUpdatedAt,
            localPackagePath = localPackagePath,
            extractedDir = extractedDir,
        )
    }
}

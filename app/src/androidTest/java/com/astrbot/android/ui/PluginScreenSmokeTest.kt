package com.astrbot.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.astrbot.android.R
import com.astrbot.android.data.PluginUninstallResult
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginPermissionDiff
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.ui.screen.PluginDetailScreenRoute
import com.astrbot.android.ui.screen.PluginScreen
import com.astrbot.android.ui.screen.PluginWorkspaceTab
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

class PluginScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun pluginScreenSupportsLocalAndMarketWorkspaceLayouts() {
        val dependencies = FakePluginViewModelDependencies(
            records = listOf(
                normalRecord(pluginId = "enabled", enabled = true).copyWithManifest(
                    title = "Enabled Assistant",
                    description = "Handles enabled workflows in the local plugin library.",
                    author = "Alice",
                ),
                normalRecord(pluginId = "disabled", enabled = false).copyWithManifest(
                    title = "Disabled Assistant",
                    description = "Shown only when the disabled filter is active.",
                    author = "Bob",
                ),
                normalRecord(pluginId = "update", enabled = true).copyWithManifest(
                    title = "Update Toolkit",
                    description = "Provides an update-focused plugin card for the new workspace.",
                    author = "Carol",
                ),
            ),
            updateAvailabilities = mapOf(
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

        composeRule.setContent {
            MaterialTheme {
                PluginRouteHost(dependencies)
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.LocalPageTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.LocalSearchTag).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_installed_library_search_placeholder))
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(PluginUiSpec.LocalFilterChipTag).assertCountEquals(3)
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_installed_library_filter_enabled))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_local_status_disabled))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_installed_library_filter_updates))
            .assertIsDisplayed()
        composeRule.onAllNodesWithTag(PluginUiSpec.LocalCardTag).assertCountEquals(2)
        composeRule.onAllNodesWithTag(PluginUiSpec.installedLibraryCardTag("disabled")).assertCountEquals(0)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_local_status_disabled)).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(PluginUiSpec.LocalCardTag).assertCountEquals(1)
        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("disabled")).assertIsDisplayed()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_installed_library_filter_updates))
            .performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(PluginUiSpec.LocalCardTag).assertCountEquals(1)
        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("update")).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.LocalSearchTag).performTextInput("enabled")
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(PluginUiSpec.LocalCardTag).assertCountEquals(0)

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_workspace_tab_market)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PluginUiSpec.MarketPageTag).assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_market_placeholder_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_market_placeholder_message))
            .assertIsDisplayed()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_workspace_tab_local)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PluginUiSpec.LocalPageTag).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PluginUiSpec.HeroSectionTag).assertCountEquals(0)
        composeRule.onAllNodesWithTag(PluginUiSpec.QuickInstallSectionTag).assertCountEquals(0)

        composeRule.onNodeWithTag(PluginUiSpec.LocalSearchTag).performTextClearance()
        composeRule.onNodeWithTag(PluginUiSpec.LocalSearchTag).performTextInput("update")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("update")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag).assertIsDisplayed()
        composeRule.onNodeWithText("Carol").assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailBackActionTag).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PluginUiSpec.LocalPageTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("update")).assertIsDisplayed()
    }

    @Test
    fun pluginScreenSupportsPolicyToggleAndDisableFeedback() {
        val dependencies = FakePluginViewModelDependencies()

        composeRule.setContent {
            MaterialTheme {
                PluginRouteHost(dependencies)
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("weather-toolkit")).performClick()
        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailRemoveDataPolicyTag).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            check(dependencies.records.value.single().uninstallPolicy == PluginUninstallPolicy.REMOVE_DATA)
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_action_feedback_uninstall_policy_remove_data),
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.DetailDisableActionTag).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            check(!dependencies.records.value.single().enabled)
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_action_feedback_disabled),
        ).assertIsDisplayed()
    }

    @Test
    fun pluginScreenShowsFailureStateInDetail() {
        val dependencies = FakePluginViewModelDependencies(
            records = listOf(
                failingRecord(),
                normalRecord(),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                PluginRouteHost(dependencies)
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("weather-toolkit")).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailFailureBannerTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailFailureRecoveryTag).assertIsDisplayed()
    }

    @Test
    fun pluginScreenShowsUpgradeDialogAndRunsUpgradeAfterSecondaryConfirmation() {
        val dependencies = FakePluginViewModelDependencies(
            records = listOf(
                normalRecord(
                    pluginId = "weather-repo",
                    sourceType = PluginSourceType.REPOSITORY,
                    catalogSourceId = "repo-1",
                ),
            ),
            updateAvailabilities = mapOf(
                "weather-repo" to PluginUpdateAvailability(
                    pluginId = "weather-repo",
                    installedVersion = "1.2.0",
                    latestVersion = "1.3.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    changelogSummary = "Adds synced repository metadata.",
                    catalogSourceId = "repo-1",
                    packageUrl = "https://repo.example.com/packages/weather-toolkit-1.3.0.zip",
                    permissionDiff = PluginPermissionDiff(
                        added = listOf(
                            PluginPermissionDeclaration(
                                permissionId = "logs.read",
                                title = "Read logs",
                                description = "Reads host logs",
                                riskLevel = PluginRiskLevel.MEDIUM,
                            ),
                        ),
                    ),
                ),
            ),
        )

        composeRule.setContent {
            MaterialTheme {
                PluginRouteHost(dependencies)
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("weather-repo")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PluginUiSpec.DetailUpgradeActionTag).performClick()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_upgrade_confirm_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_upgrade_continue))
            .performClick()

        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_upgrade_confirm_secondary_title))
            .assertIsDisplayed()
        composeRule.onNodeWithText(composeRule.activity.getString(R.string.plugin_action_upgrade))
            .performClick()

        composeRule.runOnIdle {
            check(dependencies.upgradeRequests.map { it.pluginId } == listOf("weather-repo"))
        }
        composeRule.onNodeWithText("Updated Weather Toolkit to 1.3.0.").assertIsDisplayed()
    }
}

@Composable
private fun PluginRouteHost(dependencies: FakePluginViewModelDependencies) {
    val navController = rememberNavController()
    val listViewModel = remember(dependencies) { PluginViewModel(dependencies) }
    val detailViewModel = remember(dependencies) { PluginViewModel(dependencies) }
    var workspaceTab by rememberSaveable { mutableStateOf(PluginWorkspaceTab.LOCAL) }

    Column {
        TopBarSegmentedToggle(
            options = listOf(
                stringResource(R.string.plugin_workspace_tab_local),
                stringResource(R.string.plugin_workspace_tab_market),
            ),
            selectedIndex = if (workspaceTab == PluginWorkspaceTab.LOCAL) 0 else 1,
            onSelect = { index ->
                workspaceTab = if (index == 0) PluginWorkspaceTab.LOCAL else PluginWorkspaceTab.MARKET
            },
        )
        NavHost(
            navController = navController,
            startDestination = AppDestination.Plugins.route,
        ) {
            composable(AppDestination.Plugins.route) {
                PluginScreen(
                    pluginViewModel = listViewModel,
                    workspaceTab = workspaceTab,
                    onOpenPluginDetail = { pluginId ->
                        navController.navigate(AppDestination.PluginDetail.routeFor(pluginId))
                    },
                )
            }
            composable(AppDestination.PluginDetail.route) { backStackEntry ->
                PluginDetailScreenRoute(
                    pluginId = backStackEntry.arguments?.getString("pluginId").orEmpty(),
                    onBack = { navController.popBackStack() },
                    pluginViewModel = detailViewModel,
                )
            }
        }
    }
}

private class FakePluginViewModelDependencies(
    records: List<PluginInstallRecord> = listOf(normalRecord()),
    repositorySources: List<PluginRepositorySource> = emptyList(),
    catalogEntries: List<PluginCatalogEntryRecord> = emptyList(),
    updateAvailabilities: Map<String, PluginUpdateAvailability> = emptyMap(),
) : PluginViewModelDependencies {
    private val recordsState = MutableStateFlow(records)
    private val repositorySourcesState = MutableStateFlow(repositorySources)
    private val catalogEntriesState = MutableStateFlow(catalogEntries)
    private val updateAvailabilitiesState = MutableStateFlow(updateAvailabilities)

    val upgradeRequests = mutableListOf<PluginUpdateAvailability>()

    override val records: StateFlow<List<PluginInstallRecord>> = recordsState
    override val repositorySources: StateFlow<List<PluginRepositorySource>> = repositorySourcesState
    override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = catalogEntriesState

    override suspend fun handleInstallIntent(intent: PluginInstallIntent): PluginInstallIntentResult {
        return when (intent) {
            is PluginInstallIntent.RepositoryUrl -> PluginInstallIntentResult.RepositorySynced(
                syncState = PluginCatalogSyncState(
                    sourceId = "repo-1",
                    lastSyncAtEpochMillis = 1L,
                    lastSyncStatus = PluginCatalogSyncStatus.SUCCESS,
                ),
            )
            is PluginInstallIntent.DirectPackageUrl -> PluginInstallIntentResult.Ignored
            is PluginInstallIntent.CatalogVersion -> PluginInstallIntentResult.Ignored
        }
    }

    override suspend fun installFromLocalPackageUri(uri: String): PluginInstallIntentResult {
        return PluginInstallIntentResult.Ignored
    }

    override fun getUpdateAvailability(pluginId: String): PluginUpdateAvailability? {
        return updateAvailabilitiesState.value[pluginId]
    }

    override suspend fun upgradePlugin(update: PluginUpdateAvailability): PluginInstallRecord {
        upgradeRequests += update
        val current = requireNotNull(recordsState.value.firstOrNull { it.pluginId == update.pluginId })
        val upgraded = current.copyWith(
            version = update.latestVersion,
            sourceType = PluginSourceType.REPOSITORY,
            catalogSourceId = update.catalogSourceId,
            installedPackageUrl = update.packageUrl,
            lastCatalogCheckAtEpochMillis = 99L,
            lastUpdatedAt = 99L,
        )
        recordsState.value = recordsState.value.map { record ->
            if (record.pluginId == update.pluginId) upgraded else record
        }
        updateAvailabilitiesState.value = updateAvailabilitiesState.value - update.pluginId
        return upgraded
    }

    override fun setPluginEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord {
        val updated = requireNotNull(recordsState.value.firstOrNull { it.pluginId == pluginId }).copyWith(
            enabled = enabled,
        )
        recordsState.value = recordsState.value.map { record ->
            if (record.pluginId == pluginId) updated else record
        }
        return updated
    }

    override fun updatePluginUninstallPolicy(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): PluginInstallRecord {
        val updated = requireNotNull(recordsState.value.firstOrNull { it.pluginId == pluginId }).copyWith(
            uninstallPolicy = policy,
        )
        recordsState.value = recordsState.value.map { record ->
            if (record.pluginId == pluginId) updated else record
        }
        return updated
    }

    override fun uninstallPlugin(
        pluginId: String,
        policy: PluginUninstallPolicy,
    ): PluginUninstallResult {
        recordsState.value = recordsState.value.filterNot { record -> record.pluginId == pluginId }
        return PluginUninstallResult(
            pluginId = pluginId,
            policy = policy,
            removedData = policy == PluginUninstallPolicy.REMOVE_DATA,
        )
    }
}

private fun PluginInstallRecord.copyWith(
    version: String = installedVersion,
    sourceType: PluginSourceType = source.sourceType,
    catalogSourceId: String? = this.catalogSourceId,
    installedPackageUrl: String = this.installedPackageUrl,
    lastCatalogCheckAtEpochMillis: Long? = this.lastCatalogCheckAtEpochMillis,
    enabled: Boolean = this.enabled,
    failureState: PluginFailureState = this.failureState,
    uninstallPolicy: PluginUninstallPolicy = this.uninstallPolicy,
    lastUpdatedAt: Long = this.lastUpdatedAt,
): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifestSnapshot.copy(
            version = version,
            sourceType = sourceType,
        ),
        source = source.copy(
            sourceType = sourceType,
            location = installedPackageUrl.ifBlank { source.location },
        ),
        permissionSnapshot = permissionSnapshot,
        compatibilityState = compatibilityState,
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

private fun normalRecord(
    pluginId: String = "weather-toolkit",
    sourceType: PluginSourceType = PluginSourceType.LOCAL_FILE,
    catalogSourceId: String? = null,
): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = PluginManifest(
            pluginId = pluginId,
            version = "1.2.0",
            protocolVersion = 1,
            author = "AstrBot Labs",
            title = "Weather Toolkit",
            description = "Shows local forecasts and quick climate summaries.",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = "network",
                    title = "Network access",
                    description = "Fetches weather data from the configured provider.",
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            minHostVersion = "0.3.6",
            maxHostVersion = "0.4.0",
            sourceType = sourceType,
            entrySummary = "Adds a forecast helper card to the plugin workspace.",
            riskLevel = PluginRiskLevel.MEDIUM,
        ),
        source = PluginSource(
            sourceType = sourceType,
            location = "/storage/emulated/0/Download/$pluginId.zip",
            importedAt = 42L,
        ),
        compatibilityState = PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
            notes = "Validated against Phase 1 protocol.",
        ),
        catalogSourceId = catalogSourceId,
        enabled = true,
        installedAt = 100L,
        lastUpdatedAt = 200L,
    )
}

private fun failingRecord(): PluginInstallRecord {
    return normalRecord().copyWith(
        enabled = true,
        failureState = PluginFailureState(
            consecutiveFailureCount = 2,
            lastFailureAtEpochMillis = 1_000L,
            lastErrorSummary = "socket timeout",
            suspendedUntilEpochMillis = 4_102_444_800_000L,
        ),
    )
}

private fun normalRecord(
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
            version = "1.2.0",
            protocolVersion = 1,
            author = "AstrBot Labs",
            title = "Weather Toolkit",
            description = "Shows local forecasts and quick climate summaries.",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = "network",
                    title = "Network access",
                    description = "Fetches weather data from the configured provider.",
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            minHostVersion = "0.3.6",
            maxHostVersion = "0.4.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Adds a forecast helper card to the plugin workspace.",
            riskLevel = PluginRiskLevel.MEDIUM,
        ),
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "/storage/emulated/0/Download/$pluginId.zip",
            importedAt = 42L,
        ),
        compatibilityState = compatibilityState,
        failureState = failureState,
        enabled = enabled,
        installedAt = 100L,
        lastUpdatedAt = 200L,
    )
}

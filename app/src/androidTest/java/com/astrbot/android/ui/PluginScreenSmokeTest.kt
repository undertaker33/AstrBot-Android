package com.astrbot.android.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.astrbot.android.R
import com.astrbot.android.data.PluginUninstallResult
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginPermissionDiff
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSettingsSection
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.ui.AppDestination
import com.astrbot.android.ui.screen.PluginScreen
import com.astrbot.android.ui.screen.PluginDetailScreenRoute
import com.astrbot.android.ui.screen.plugin.PluginUiSpec
import com.astrbot.android.ui.viewmodel.PluginViewModel
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Rule
import org.junit.Test

class PluginScreenSmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @org.junit.After
    fun tearDown() {
        PluginRuntimeRegistry.reset()
    }

    @Test
    fun pluginScreenRendersHomepageHeroHealthOverviewAndSections() {
        PluginRuntimeRegistry.registerProvider {
            listOf(
                PluginRuntimePlugin(
                    pluginId = "permission",
                    pluginVersion = "2.0.0",
                    installState = runtimeInstallState("permission"),
                    supportedTriggers = setOf(PluginTriggerSource.OnPluginEntryClick),
                    handler = {
                        com.astrbot.android.model.plugin.SettingsUiRequest(
                            schema = PluginSettingsSchema(
                                title = "Runtime Settings",
                                sections = listOf(
                                    PluginSettingsSection(
                                        sectionId = "runtime",
                                        title = "Runtime",
                                    ),
                                ),
                            ),
                        )
                    },
                ),
            )
        }
        val dependencies = FakePluginViewModelDependencies(
            records = listOf(
                normalRecord(pluginId = "enabled", enabled = true),
                normalRecord(pluginId = "disabled", enabled = false),
                normalRecord(pluginId = "update", enabled = true),
                normalRecord(
                    pluginId = "permission",
                    enabled = true,
                ),
                normalRecord(
                    pluginId = "unknown",
                    enabled = true,
                    compatibilityState = PluginCompatibilityState.unknown(),
                ),
                normalRecord(
                    pluginId = "incompatible",
                    enabled = true,
                    compatibilityState = PluginCompatibilityState.evaluated(
                        protocolSupported = false,
                        minHostVersionSatisfied = true,
                        maxHostVersionSatisfied = true,
                    ),
                ),
                normalRecord(
                    pluginId = "suspended",
                    enabled = false,
                    failureState = PluginFailureState(
                        consecutiveFailureCount = 4,
                        lastFailureAtEpochMillis = 1_000L,
                        lastErrorSummary = "socket timeout",
                        suspendedUntilEpochMillis = 4_102_444_800_000L,
                    ),
                ),
            ),
            repositorySources = listOf(sampleRepositorySource()),
            catalogEntries = listOf(sampleCatalogEntryRecord()),
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
        )

        composeRule.setContent {
            MaterialTheme {
                PluginRouteHost(dependencies)
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.HeroSectionTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.QuickInstallSectionTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.HealthOverviewSectionTag).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_health_metric_installed),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_health_metric_updates_available),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_health_metric_needs_review),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_health_metric_sources),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.InstalledLibraryControlsTag).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_installed_library_search_placeholder),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_installed_library_filter_all),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_installed_library_filter_enabled),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_installed_library_filter_updates),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_installed_library_filter_issues),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_installed_library_filter_permission_changes),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.waitForIdle()
        val heroTop = composeRule.onNodeWithTag(PluginUiSpec.HeroSectionTag)
            .fetchSemanticsNode()
            .boundsInRoot.top
        val quickInstallTop = composeRule.onNodeWithTag(PluginUiSpec.QuickInstallSectionTag)
            .fetchSemanticsNode()
            .boundsInRoot.top
        val healthTop = composeRule.onNodeWithTag(PluginUiSpec.HealthOverviewSectionTag)
            .fetchSemanticsNode()
            .boundsInRoot.top
        check(heroTop < quickInstallTop)
        check(quickInstallTop < healthTop)
        composeRule.onNodeWithTag(PluginUiSpec.PluginListTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.InstalledLibrarySectionTag).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PluginUiSpec.InstalledLibraryCardTag).assertCountEquals(7)
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_local_package_action),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.PluginListTag)
            .performScrollToNode(hasTestTag(PluginUiSpec.RepositorySectionTag))
        composeRule.onNodeWithTag(PluginUiSpec.RepositorySectionTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.PluginListTag)
            .performScrollToNode(hasTestTag(PluginUiSpec.repositoryCardTag("repo-1")))
        composeRule.onNodeWithTag(PluginUiSpec.repositoryCardTag("repo-1")).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.PluginListTag)
            .performScrollToNode(hasTestTag(PluginUiSpec.DiscoverSectionTag))
        composeRule.onNodeWithTag(PluginUiSpec.DiscoverSectionTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.PluginListTag)
            .performScrollToNode(hasTestTag(PluginUiSpec.discoverableCardTag("catalog-weather")))
        composeRule.onNodeWithTag(PluginUiSpec.discoverableCardTag("catalog-weather")).assertIsDisplayed()
        composeRule.onNodeWithText("Weather Toolkit", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("permission")).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailBackActionTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailTopSummaryTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailPrimaryActionsTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailOverviewTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailSafetyCompatibilityTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailTechnicalMetadataTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.SchemaWorkspaceTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.SchemaSettingsTag).assertIsDisplayed()
        composeRule.onNodeWithText("1", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("0.3.6", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("0.4.0", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Permission changes", useUnmergedTree = true).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.DetailBackActionTag).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(PluginUiSpec.HeroSectionTag).assertIsDisplayed()
        composeRule.onAllNodesWithTag(PluginUiSpec.DetailPanelTag).assertCountEquals(0)

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_installed_library_filter_updates),
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(PluginUiSpec.InstalledLibraryCardTag).assertCountEquals(2)
        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("permission")).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("update")).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_action_update),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_installed_library_filter_permission_changes),
            useUnmergedTree = true,
        ).performClick()
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(PluginUiSpec.InstalledLibraryCardTag).assertCountEquals(1)
        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("permission")).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_action_review),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("permission")).performClick()
        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag).assertIsDisplayed()
        composeRule.onNodeWithText("AstrBot Labs", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailUpgradeActionTag).assertIsDisplayed()
        composeRule.onNodeWithTag(PluginUiSpec.DetailUpgradeActionTag).performClick()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_upgrade_confirm_title),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun pluginScreenSubmitsRepositoryAndDirectInstallIntentsFromEntrySection() {
        val dependencies = FakePluginViewModelDependencies()

        composeRule.setContent {
            MaterialTheme {
                PluginRouteHost(dependencies)
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.QuickInstallSectionTag).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_local_package_action),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_quick_install_mode_repository_url),
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNode(
            hasText(composeRule.activity.getString(R.string.plugin_repository_url_label)) and hasSetTextAction(),
            useUnmergedTree = true,
        ).performTextInput(" https://repo.example.com/catalog.json ")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_repository_url_action),
            useUnmergedTree = true,
        ).performClick()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_quick_install_mode_direct_package_url),
            useUnmergedTree = true,
        ).performClick()
        composeRule.onNode(
            hasText(composeRule.activity.getString(R.string.plugin_direct_package_url_label)) and hasSetTextAction(),
            useUnmergedTree = true,
        ).performTextInput(" https://plugins.example.com/weather.zip ")
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_direct_package_url_action),
            useUnmergedTree = true,
        ).performClick()

        composeRule.runOnIdle {
            check(
                dependencies.handledInstallIntents == listOf(
                    PluginInstallIntent.repositoryUrl("https://repo.example.com/catalog.json"),
                    PluginInstallIntent.directPackageUrl("https://plugins.example.com/weather.zip"),
                ),
            )
        }
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

        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag)
            .performScrollToNode(hasTestTag(PluginUiSpec.DetailRemoveDataPolicyTag))
        composeRule.onNodeWithTag(PluginUiSpec.DetailRemoveDataPolicyTag).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            check(dependencies.records.value.single().uninstallPolicy == PluginUninstallPolicy.REMOVE_DATA)
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_action_feedback_uninstall_policy_remove_data),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag)
            .performScrollToNode(hasTestTag(PluginUiSpec.DetailDisableActionTag))
        composeRule.onNodeWithTag(PluginUiSpec.DetailDisableActionTag).performClick()
        composeRule.waitForIdle()

        composeRule.runOnIdle {
            check(!dependencies.records.value.single().enabled)
        }

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_action_feedback_disabled),
            useUnmergedTree = true,
        ).assertIsDisplayed()
    }

    @Test
    fun pluginScreenShowsFailureStateInListAndDetail() {
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

        composeRule.onNodeWithTag(PluginUiSpec.pluginFailureChipTag("weather-toolkit"))
            .assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_failure_summary_with_error, "socket timeout"),
            useUnmergedTree = true,
        ).assertIsDisplayed()

        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("weather-toolkit")).performClick()

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
                PluginScreen(
                    pluginViewModel = PluginViewModel(dependencies),
                )
            }
        }

        composeRule.onNodeWithTag(PluginUiSpec.installedLibraryCardTag("weather-repo")).performClick()
        composeRule.onNodeWithTag(PluginUiSpec.DetailPanelTag)
            .performScrollToNode(hasTestTag(PluginUiSpec.DetailUpgradeActionTag))
        composeRule.onNodeWithTag(PluginUiSpec.DetailUpgradeActionTag).performClick()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_upgrade_confirm_title),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_upgrade_continue),
            useUnmergedTree = true,
        ).performClick()

        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_upgrade_confirm_secondary_title),
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            composeRule.activity.getString(R.string.plugin_action_upgrade),
            useUnmergedTree = true,
        ).performClick()

        composeRule.runOnIdle {
            check(dependencies.upgradeRequests.map { it.pluginId } == listOf("weather-repo"))
        }
        composeRule.onNodeWithText("Updated Weather Toolkit to 1.3.0.", useUnmergedTree = true)
            .assertIsDisplayed()
    }
}

@androidx.compose.runtime.Composable
private fun PluginRouteHost(dependencies: FakePluginViewModelDependencies) {
    val navController = rememberNavController()
    val listViewModel = remember(dependencies) { PluginViewModel(dependencies) }
    val detailViewModel = remember(dependencies) { PluginViewModel(dependencies) }

    NavHost(
        navController = navController,
        startDestination = AppDestination.Plugins.route,
    ) {
        composable(AppDestination.Plugins.route) {
            PluginScreen(
                pluginViewModel = listViewModel,
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

    val handledInstallIntents = mutableListOf<PluginInstallIntent>()
    val upgradeRequests = mutableListOf<PluginUpdateAvailability>()

    override val records: StateFlow<List<PluginInstallRecord>> = recordsState
    override val repositorySources: StateFlow<List<PluginRepositorySource>> = repositorySourcesState
    override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = catalogEntriesState

    override suspend fun handleInstallIntent(intent: PluginInstallIntent): PluginInstallIntentResult {
        handledInstallIntents += intent
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
    version: String = this.installedVersion,
    sourceType: PluginSourceType = this.source.sourceType,
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

private fun runtimeInstallState(pluginId: String): PluginInstallState {
    return PluginInstallState(
        status = PluginInstallStatus.INSTALLED,
        installedVersion = "2.0.0",
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "/storage/emulated/0/Download/$pluginId.zip",
            importedAt = 42L,
        ),
        manifestSnapshot = PluginManifest(
            pluginId = pluginId,
            version = "2.0.0",
            protocolVersion = 1,
            author = "AstrBot Labs",
            title = "Runtime Settings",
            description = "Runtime settings schema.",
            permissions = emptyList(),
            minHostVersion = "0.3.6",
            maxHostVersion = "0.4.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Runtime settings schema.",
        ),
        enabled = true,
    )
}

    private fun failingRecord(): PluginInstallRecord {
        return normalRecord().copyWith(
            enabled = false,
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

private fun sampleRepositorySource(): PluginRepositorySource {
    return PluginRepositorySource(
        sourceId = "repo-1",
        title = "AstrBot Repo",
        catalogUrl = "https://repo.example.com/catalog.json",
        updatedAt = 1L,
        lastSyncAtEpochMillis = 2L,
        lastSyncStatus = PluginCatalogSyncStatus.SUCCESS,
    )
}

private fun sampleCatalogEntryRecord(): PluginCatalogEntryRecord {
    return PluginCatalogEntryRecord(
        sourceId = "repo-1",
        sourceTitle = "AstrBot Repo",
        catalogUrl = "https://repo.example.com/catalog.json",
        entry = PluginCatalogEntry(
            pluginId = "catalog-weather",
            title = "Catalog Weather",
            author = "AstrBot Labs",
            description = "Discoverable weather helper.",
            entrySummary = "Browse repository plugins",
            versions = listOf(
                PluginCatalogVersion(
                    version = "1.3.0",
                    packageUrl = "https://repo.example.com/packages/catalog-weather-1.3.0.zip",
                    publishedAt = 3L,
                    protocolVersion = 1,
                    minHostVersion = "0.3.6",
                ),
            ),
        ),
    )
}

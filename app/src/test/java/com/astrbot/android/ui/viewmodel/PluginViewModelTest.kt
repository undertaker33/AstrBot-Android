package com.astrbot.android.ui.viewmodel

import com.astrbot.android.R
import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.data.PluginUninstallResult
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.plugin.CardResult
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginCardAction
import com.astrbot.android.model.plugin.PluginCardSchema
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginSelectOption
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSettingsSection
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PluginViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @org.junit.After
    fun tearDown() {
        PluginRuntimeRegistry.reset()
    }

    @Test
    fun init_selects_first_plugin_and_exposes_workspace_sources() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    compatibilityState = PluginCompatibilityState.evaluated(
                        protocolSupported = true,
                        minHostVersionSatisfied = false,
                        maxHostVersionSatisfied = true,
                    ),
                ),
                pluginRecord(pluginId = "plugin-2"),
            ),
            repositorySources = listOf(
                repositorySource(
                    sourceId = "repo-1",
                    title = "AstrBot Repo",
                ),
            ),
            catalogEntries = listOf(
                catalogEntryRecord(
                    sourceId = "repo-1",
                    pluginId = "catalog-1",
                ),
                catalogEntryRecord(
                    sourceId = "repo-1",
                    pluginId = "catalog-2",
                ),
            ),
        )

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        assertEquals("plugin-1", viewModel.uiState.value.selectedPluginId)
        assertEquals("plugin-1", viewModel.uiState.value.selectedPlugin?.pluginId)
        assertFalse(viewModel.uiState.value.isShowingDetail)
        assertEquals(1, viewModel.uiState.value.repositorySources.size)
        assertEquals(2, viewModel.uiState.value.catalogEntries.size)
    }

    @Test
    fun detail_state_includes_source_repository_sync_version_changelog_update_and_permission_diff() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    sourceType = PluginSourceType.REPOSITORY,
                    catalogSourceId = "repo-1",
                    lastUpdatedAt = 1_710_000_000_000L,
                ),
            ),
            repositorySources = listOf(
                repositorySource(
                    sourceId = "repo-1",
                    title = "AstrBot Repo",
                    catalogUrl = "https://repo.example.com/catalog.json",
                    lastSyncAtEpochMillis = 1_715_000_000_000L,
                ),
            ),
            catalogEntries = listOf(
                catalogEntryRecord(
                    sourceId = "repo-1",
                    pluginId = "plugin-1",
                    versions = listOf(
                        catalogVersion(
                            version = "1.2.0",
                            publishedAt = 1_716_000_000_000L,
                            changelog = "Adds sync insights\n- and more",
                        ),
                        catalogVersion(
                            version = "1.1.0",
                            publishedAt = 1_714_000_000_000L,
                            changelog = "Older release",
                        ),
                        catalogVersion(
                            version = "1.0.0",
                            publishedAt = 1_712_000_000_000L,
                            changelog = "Initial release",
                        ),
                    ),
                ),
            ),
            updateAvailabilities = mapOf(
                "plugin-1" to PluginUpdateAvailability(
                    pluginId = "plugin-1",
                    installedVersion = "1.0.0",
                    latestVersion = "1.2.0",
                    updateAvailable = true,
                    canUpgrade = false,
                    changelogSummary = "Adds sync insights",
                    incompatibilityReason = "Host version is below required minimum 2.0.0.",
                    catalogSourceId = "repo-1",
                    packageUrl = "https://repo.example.com/plugin-1-1.2.0.zip",
                    permissionDiff = pluginPermissionDiff(
                        added = listOf(
                            PluginPermissionDeclaration(
                                permissionId = "host.logs.read",
                                title = "Read host logs",
                                description = "Reads host logs",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        val detail = viewModel.uiState.value.detailMetadataState
        assertEquals(PluginSourceType.REPOSITORY, detail.sourceBadge?.sourceType)
        assertEquals("Repository", detail.sourceBadge?.label)
        assertEquals("AstrBot Repo", detail.repositoryNameOrHost)
        assertEquals("repo.example.com", detail.repositoryHost)
        assertEquals(1_715_000_000_000L, detail.lastSyncAtEpochMillis)
        assertEquals(1_710_000_000_000L, detail.lastUpdatedAtEpochMillis)
        assertEquals("1.2.0 | 1.1.0 | 1.0.0", detail.versionHistorySummary)
        assertEquals("Adds sync insights", detail.changelogSummary)
        assertEquals("1.2.0", detail.updateHint?.latestVersion)
        assertEquals("Host version is below required minimum 2.0.0.", detail.updateHint?.blockedReason)
        assertEquals(listOf("Read host logs"), detail.permissionDiffHint?.addedPermissions)
    }

    @Test
    fun source_badge_mapping_uses_local_fact_types() = runTest(dispatcher) {
        val records = listOf(
            pluginRecord(pluginId = "local", sourceType = PluginSourceType.LOCAL_FILE),
            pluginRecord(pluginId = "manual", sourceType = PluginSourceType.MANUAL_IMPORT),
            pluginRecord(pluginId = "repo", sourceType = PluginSourceType.REPOSITORY, catalogSourceId = "repo-1"),
            pluginRecord(pluginId = "direct", sourceType = PluginSourceType.DIRECT_LINK),
        )
        val deps = FakePluginDependencies(records = records)
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        val localBadge = viewModel.buildSourceBadgeForTest(records[0])
        val manualBadge = viewModel.buildSourceBadgeForTest(records[1])
        val repositoryBadge = viewModel.buildSourceBadgeForTest(records[2])
        val directBadge = viewModel.buildSourceBadgeForTest(records[3])

        assertEquals("Local file", localBadge.label)
        assertEquals("Manual import", manualBadge.label)
        assertEquals("Repository", repositoryBadge.label)
        assertEquals("Direct link", directBadge.label)
    }

    @Test
    fun select_plugin_shows_detail_and_show_list_preserves_selection() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(
                pluginRecord(pluginId = "plugin-1"),
                pluginRecord(pluginId = "plugin-2"),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-2")
        advanceUntilIdle()

        assertEquals("plugin-2", viewModel.uiState.value.selectedPluginId)
        assertEquals("plugin-2", viewModel.uiState.value.selectedPlugin?.pluginId)
        assertEquals(true, viewModel.uiState.value.isShowingDetail)

        viewModel.showList()
        advanceUntilIdle()

        assertEquals("plugin-2", viewModel.uiState.value.selectedPluginId)
        assertEquals("plugin-2", viewModel.uiState.value.selectedPlugin?.pluginId)
        assertFalse(viewModel.uiState.value.isShowingDetail)
    }

    @Test
    fun records_update_falls_back_to_first_available_plugin_when_selection_disappears() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(
                pluginRecord(pluginId = "plugin-1"),
                pluginRecord(pluginId = "plugin-2"),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-2")
        advanceUntilIdle()

        deps.updateRecords(
            listOf(
                pluginRecord(pluginId = "plugin-3"),
                pluginRecord(pluginId = "plugin-1"),
            ),
        )
        advanceUntilIdle()

        assertEquals("plugin-3", viewModel.uiState.value.selectedPluginId)
        assertEquals("plugin-3", viewModel.uiState.value.selectedPlugin?.pluginId)
        assertEquals(true, viewModel.uiState.value.isShowingDetail)
    }

    @Test
    fun incompatible_plugin_disables_enable_action_and_surfaces_block_message() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    compatibilityState = PluginCompatibilityState.evaluated(
                        protocolSupported = true,
                        minHostVersionSatisfied = false,
                        maxHostVersionSatisfied = true,
                        notes = "Needs host 2.0.0 or newer.",
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        val actionState = viewModel.uiState.value.detailActionState
        assertFalse(actionState.isEnableActionEnabled)
        assertFalse(actionState.isDisableActionEnabled)
        assertResourceFeedback(
            feedback = actionState.enableBlockedReason,
            resId = R.string.plugin_action_feedback_enable_blocked_incompatible_with_notes,
            expectedArg = "Needs host 2.0.0 or newer.",
        )

        viewModel.enableSelectedPlugin()
        advanceUntilIdle()

        assertTrue(deps.enableRequests.isEmpty())
        assertResourceFeedback(
            feedback = viewModel.uiState.value.detailActionState.lastActionMessage,
            resId = R.string.plugin_action_feedback_enable_blocked_incompatible_with_notes,
            expectedArg = "Needs host 2.0.0 or newer.",
        )
    }

    @Test
    fun failure_state_maps_to_list_and_detail_ui_state_and_blocks_enable_when_suspended() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    enabled = false,
                    failureState = PluginFailureState(
                        consecutiveFailureCount = 3,
                        lastFailureAtEpochMillis = 1_000L,
                        lastErrorSummary = "socket timeout",
                        suspendedUntilEpochMillis = 4_102_444_800_000L,
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        val failureState = viewModel.uiState.value.failureStatesByPluginId["plugin-1"]
        assertTrue(failureState != null)
        assertTrue(failureState!!.isSuspended)
        assertEquals(3, failureState.consecutiveFailureCount)
        assertResourceFeedback(
            feedback = failureState.statusMessage,
            resId = R.string.plugin_failure_status_suspended,
        )
        assertResourceFeedback(
            feedback = failureState.summaryMessage,
            resId = R.string.plugin_failure_summary_with_error,
            expectedArg = "socket timeout",
        )
        assertResourceFeedback(
            feedback = failureState.recoveryMessage,
            resId = R.string.plugin_failure_recovery_at,
        )

        assertTrue(viewModel.uiState.value.detailActionState.failureState != null)
        assertTrue(viewModel.uiState.value.detailActionState.failureState!!.isSuspended)
        assertResourceFeedback(
            feedback = viewModel.uiState.value.detailActionState.enableBlockedReason,
            resId = R.string.plugin_failure_enable_blocked_until_recovery,
        )

        viewModel.enableSelectedPlugin()
        advanceUntilIdle()

        assertTrue(deps.enableRequests.isEmpty())
        assertResourceFeedback(
            feedback = viewModel.uiState.value.detailActionState.lastActionMessage,
            resId = R.string.plugin_failure_enable_blocked_until_recovery,
        )
    }

    @Test
    fun failure_state_without_suspension_stays_visible_and_allows_enable() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    enabled = false,
                    failureState = PluginFailureState(
                        consecutiveFailureCount = 1,
                        lastFailureAtEpochMillis = 1_000L,
                        lastErrorSummary = "api returned 429",
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        val failureState = viewModel.uiState.value.failureStatesByPluginId["plugin-1"]
        assertTrue(failureState != null)
        assertFalse(failureState!!.isSuspended)
        assertResourceFeedback(
            feedback = failureState.statusMessage,
            resId = R.string.plugin_failure_status_active,
        )
        assertResourceFeedback(
            feedback = failureState.recoveryMessage,
            resId = R.string.plugin_failure_recovery_available,
        )
        assertNull(viewModel.uiState.value.detailActionState.enableBlockedReason)
        assertTrue(viewModel.uiState.value.detailActionState.isEnableActionEnabled)

        viewModel.enableSelectedPlugin()
        advanceUntilIdle()

        assertEquals(listOf("plugin-1" to true), deps.enableRequests)
    }

    @Test
    fun update_uninstall_policy_persists_selection_and_uninstall_uses_selected_policy() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1", uninstallPolicy = PluginUninstallPolicy.KEEP_DATA)),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()
        viewModel.updateSelectedUninstallPolicy(PluginUninstallPolicy.REMOVE_DATA)
        advanceUntilIdle()

        assertEquals(PluginUninstallPolicy.REMOVE_DATA, deps.lastUpdatedPolicy)
        assertEquals(PluginUninstallPolicy.REMOVE_DATA, viewModel.uiState.value.detailActionState.uninstallPolicy)

        viewModel.uninstallSelectedPlugin()
        advanceUntilIdle()

        assertEquals(PluginUninstallPolicy.REMOVE_DATA, deps.lastUninstallPolicy)
        assertNull(viewModel.uiState.value.selectedPluginId)
        assertFalse(viewModel.uiState.value.isShowingDetail)
        assertResourceFeedback(
            feedback = viewModel.uiState.value.detailActionState.lastActionMessage,
            resId = R.string.plugin_action_feedback_uninstalled_remove_data,
        )
    }

    @Test
    fun enable_and_disable_selected_plugin_forward_to_dependencies_and_refresh_actions() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1", enabled = false)),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()
        viewModel.enableSelectedPlugin()
        advanceUntilIdle()

        assertEquals(listOf("plugin-1" to true), deps.enableRequests)
        assertTrue(viewModel.uiState.value.selectedPlugin?.enabled == true)
        assertTrue(viewModel.uiState.value.detailActionState.isDisableActionEnabled)
        assertResourceFeedback(
            feedback = viewModel.uiState.value.detailActionState.lastActionMessage,
            resId = R.string.plugin_action_feedback_enabled,
        )

        viewModel.disableSelectedPlugin()
        advanceUntilIdle()

        assertEquals(listOf("plugin-1" to true, "plugin-1" to false), deps.enableRequests)
        assertFalse(viewModel.uiState.value.selectedPlugin?.enabled == true)
        assertTrue(viewModel.uiState.value.detailActionState.isEnableActionEnabled)
        assertResourceFeedback(
            feedback = viewModel.uiState.value.detailActionState.lastActionMessage,
            resId = R.string.plugin_action_feedback_disabled,
        )
    }

    @Test
    fun submit_repository_url_builds_repository_install_intent() = runTest(dispatcher) {
        val deps = FakePluginDependencies(emptyList())
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.updateRepositoryUrlDraft(" https://repo.example.com/catalog.json ")
        viewModel.submitRepositoryUrl()
        advanceUntilIdle()

        assertEquals(
            listOf(PluginInstallIntent.repositoryUrl("https://repo.example.com/catalog.json")),
            deps.handledInstallIntents,
        )
        assertEquals("", viewModel.uiState.value.repositoryUrlDraft)
        assertEquals("", viewModel.uiState.value.directPackageUrlDraft)
    }

    @Test
    fun submit_direct_package_url_builds_direct_link_install_intent() = runTest(dispatcher) {
        val deps = FakePluginDependencies(emptyList())
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.updateDirectPackageUrlDraft(" https://plugins.example.com/demo.zip ")
        viewModel.submitDirectPackageUrl()
        advanceUntilIdle()

        assertEquals(
            listOf(PluginInstallIntent.directPackageUrl("https://plugins.example.com/demo.zip")),
            deps.handledInstallIntents,
        )
        assertEquals("", viewModel.uiState.value.directPackageUrlDraft)
    }

    @Test
    fun submit_local_package_uri_installs_and_preserves_url_drafts() = runTest(dispatcher) {
        val deps = FakePluginDependencies(emptyList()).apply {
            nextLocalPackageInstallResult = PluginInstallIntentResult.Installed(
                record = pluginRecord(pluginId = "local-import", sourceType = PluginSourceType.LOCAL_FILE),
            )
        }
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.updateRepositoryUrlDraft(" https://repo.example.com/catalog.json ")
        viewModel.updateDirectPackageUrlDraft(" https://plugins.example.com/demo.zip ")
        viewModel.submitLocalPackageUri("content://com.android.providers.downloads/document/123")
        advanceUntilIdle()

        assertEquals(listOf("content://com.android.providers.downloads/document/123"), deps.localPackageUris)
        assertEquals(emptyList<PluginInstallIntent>(), deps.handledInstallIntents)
        assertEquals(" https://repo.example.com/catalog.json ", viewModel.uiState.value.repositoryUrlDraft)
        assertEquals(" https://plugins.example.com/demo.zip ", viewModel.uiState.value.directPackageUrlDraft)
        assertEquals(false, viewModel.uiState.value.isInstallActionRunning)

        val feedback = viewModel.uiState.value.detailActionState.lastActionMessage
        assertTrue(feedback is PluginActionFeedback.Text)
        feedback as PluginActionFeedback.Text
        assertTrue(feedback.value.contains("Installed", ignoreCase = true))
    }

    @Test
    fun submit_local_package_uri_failure_emits_error_and_does_not_block_future_url_installs() = runTest(dispatcher) {
        val deps = FakePluginDependencies(emptyList()).apply {
            localPackageInstallError = IllegalStateException("Zip import failed")
        }
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.submitLocalPackageUri("content://com.android.providers.downloads/document/999")
        advanceUntilIdle()

        assertEquals(listOf("content://com.android.providers.downloads/document/999"), deps.localPackageUris)
        assertEquals(false, viewModel.uiState.value.isInstallActionRunning)
        val feedback = viewModel.uiState.value.detailActionState.lastActionMessage
        assertTrue(feedback is PluginActionFeedback.Text)
        feedback as PluginActionFeedback.Text
        assertTrue(feedback.value.contains("Zip import failed"))

        viewModel.updateRepositoryUrlDraft(" https://repo.example.com/catalog.json ")
        viewModel.submitRepositoryUrl()
        advanceUntilIdle()
        assertEquals(
            listOf(
                PluginInstallIntent.repositoryUrl("https://repo.example.com/catalog.json"),
            ),
            deps.handledInstallIntents,
        )
    }

    @Test
    fun request_upgrade_with_added_permission_requires_secondary_confirmation_before_install() = runTest(dispatcher) {
        val record = pluginRecord(
            pluginId = "plugin-1",
            sourceType = PluginSourceType.REPOSITORY,
            catalogSourceId = "official",
        )
        val deps = FakePluginDependencies(
            records = listOf(record),
            updateAvailabilities = mapOf(
                "plugin-1" to PluginUpdateAvailability(
                    pluginId = "plugin-1",
                    installedVersion = "1.0.0",
                    latestVersion = "1.1.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    changelogSummary = "Adds log reading support.",
                    catalogSourceId = "official",
                    packageUrl = "https://repo.example.com/plugin-1-1.1.0.zip",
                    permissionDiff = pluginPermissionDiff(
                        added = listOf(
                            PluginPermissionDeclaration(
                                permissionId = "logs.read",
                                title = "Read logs",
                                description = "Reads host logs",
                            ),
                        ),
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        viewModel.requestUpgradeForSelectedPlugin()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.upgradeDialogState?.isVisible)
        assertEquals(true, viewModel.uiState.value.upgradeDialogState?.requiresSecondaryConfirmation)
        assertEquals(0, deps.upgradeRequests.size)

        viewModel.confirmUpgrade()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.upgradeDialogState?.isSecondaryConfirmationStep)
        assertEquals(0, deps.upgradeRequests.size)

        viewModel.confirmUpgrade()
        advanceUntilIdle()

        assertEquals(listOf("plugin-1"), deps.upgradeRequests.map { it.pluginId })
        assertEquals(null, viewModel.uiState.value.upgradeDialogState)
        assertTrue(viewModel.uiState.value.detailActionState.lastActionMessage is PluginActionFeedback.Text)
    }

    @Test
    fun request_upgrade_without_new_permissions_installs_after_single_confirmation() = runTest(dispatcher) {
        val record = pluginRecord(
            pluginId = "plugin-1",
            sourceType = PluginSourceType.REPOSITORY,
            catalogSourceId = "official",
        )
        val deps = FakePluginDependencies(
            records = listOf(record),
            updateAvailabilities = mapOf(
                "plugin-1" to PluginUpdateAvailability(
                    pluginId = "plugin-1",
                    installedVersion = "1.0.0",
                    latestVersion = "1.1.0",
                    updateAvailable = true,
                    canUpgrade = true,
                    changelogSummary = "Fixes metadata syncing.",
                    catalogSourceId = "official",
                    packageUrl = "https://repo.example.com/plugin-1-1.1.0.zip",
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        viewModel.requestUpgradeForSelectedPlugin()
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.upgradeDialogState?.isVisible)
        assertEquals(false, viewModel.uiState.value.upgradeDialogState?.requiresSecondaryConfirmation)

        viewModel.confirmUpgrade()
        advanceUntilIdle()

        assertEquals(listOf("plugin-1"), deps.upgradeRequests.map { it.pluginId })
        assertEquals(null, viewModel.uiState.value.upgradeDialogState)
    }

    @Test
    fun request_upgrade_surfaces_block_reason_when_target_is_incompatible() = runTest(dispatcher) {
        val record = pluginRecord(
            pluginId = "plugin-1",
            sourceType = PluginSourceType.REPOSITORY,
            catalogSourceId = "official",
        )
        val deps = FakePluginDependencies(
            records = listOf(record),
            updateAvailabilities = mapOf(
                "plugin-1" to PluginUpdateAvailability(
                    pluginId = "plugin-1",
                    installedVersion = "1.0.0",
                    latestVersion = "2.0.0",
                    updateAvailable = true,
                    canUpgrade = false,
                    incompatibilityReason = "Requires host 9.0.0 or newer.",
                    catalogSourceId = "official",
                    packageUrl = "https://repo.example.com/plugin-1-2.0.0.zip",
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        viewModel.requestUpgradeForSelectedPlugin()
        advanceUntilIdle()

        assertEquals(null, viewModel.uiState.value.upgradeDialogState)
        assertEquals(emptyList<PluginUpdateAvailability>(), deps.upgradeRequests)
        assertEquals(
            PluginActionFeedback.Text("Requires host 9.0.0 or newer."),
            viewModel.uiState.value.detailActionState.lastActionMessage,
        )
    }

    @Test
    fun incompatible_update_reason_is_present_in_detail_action_state_before_click() = runTest(dispatcher) {
        val record = pluginRecord(
            pluginId = "plugin-1",
            sourceType = PluginSourceType.REPOSITORY,
            catalogSourceId = "official",
        )
        val deps = FakePluginDependencies(
            records = listOf(record),
            updateAvailabilities = mapOf(
                "plugin-1" to PluginUpdateAvailability(
                    pluginId = "plugin-1",
                    installedVersion = "1.0.0",
                    latestVersion = "2.0.0",
                    updateAvailable = true,
                    canUpgrade = false,
                    incompatibilityReason = "Requires host 9.0.0 or newer.",
                    catalogSourceId = "official",
                    packageUrl = "https://repo.example.com/plugin-1-2.0.0.zip",
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        assertEquals(
            PluginActionFeedback.Text("Requires host 9.0.0 or newer."),
            viewModel.uiState.value.detailActionState.updateBlockedReason,
        )
        assertEquals(false, viewModel.uiState.value.detailActionState.isUpgradeActionEnabled)
    }

    @Test
    fun select_plugin_executes_registered_plugin_entry_runtime() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1")),
        )
        var invocationCount = 0
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    invocationCount += 1
                    NoOp()
                },
            )
        }

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        assertEquals(1, invocationCount)
    }

    @Test
    fun select_plugin_maps_card_result_to_schema_ui_state() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1")),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    CardResult(
                        card = PluginCardSchema(
                            title = "Runtime Card",
                            body = "Rendered from entry runtime",
                            actions = listOf(
                                PluginCardAction(
                                    actionId = "refresh",
                                    label = "Refresh",
                                ),
                            ),
                        ),
                    )
                },
            )
        }

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        val schemaState = viewModel.uiState.value.schemaUiState
        assertTrue(schemaState is PluginSchemaUiState.Card)
        schemaState as PluginSchemaUiState.Card
        assertEquals("Runtime Card", schemaState.schema.title)
        assertEquals("Rendered from entry runtime", schemaState.schema.body)
        assertEquals(1, schemaState.schema.actions.size)
        assertNull(schemaState.lastActionFeedback)
    }

    @Test
    fun select_plugin_maps_settings_ui_request_to_schema_ui_state() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1")),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    SettingsUiRequest(
                        schema = PluginSettingsSchema(
                            title = "Runtime Settings",
                            sections = listOf(
                                PluginSettingsSection(
                                    sectionId = "general",
                                    title = "General",
                                    fields = listOf(
                                        ToggleSettingField(
                                            fieldId = "enabled",
                                            label = "Enabled",
                                            defaultValue = true,
                                        ),
                                        TextInputSettingField(
                                            fieldId = "name",
                                            label = "Name",
                                            defaultValue = "AstrBot",
                                        ),
                                        SelectSettingField(
                                            fieldId = "mode",
                                            label = "Mode",
                                            defaultValue = "safe",
                                            options = listOf(
                                                PluginSelectOption("safe", "Safe"),
                                                PluginSelectOption("full", "Full"),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                },
            )
        }

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        val schemaState = viewModel.uiState.value.schemaUiState
        assertTrue(schemaState is PluginSchemaUiState.Settings)
        schemaState as PluginSchemaUiState.Settings
        assertEquals("Runtime Settings", schemaState.schema.title)
        assertEquals(3, schemaState.draftValues.size)
        assertEquals(
            PluginSettingDraftValue.Toggle(true),
            schemaState.draftValues["enabled"],
        )
        assertEquals(
            PluginSettingDraftValue.Text("AstrBot"),
            schemaState.draftValues["name"],
        )
        assertEquals(
            PluginSettingDraftValue.Text("safe"),
            schemaState.draftValues["mode"],
        )
    }

    @Test
    fun card_action_callback_updates_schema_state_feedback() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1")),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    CardResult(
                        card = PluginCardSchema(
                            title = "Runtime Card",
                            actions = listOf(
                                PluginCardAction(
                                    actionId = "retry",
                                    label = "Retry",
                                ),
                            ),
                        ),
                    )
                },
            )
        }

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        viewModel.onSchemaCardActionClick(
            actionId = "retry",
            payload = mapOf("sessionId" to "s-1"),
        )
        advanceUntilIdle()

        val schemaState = viewModel.uiState.value.schemaUiState
        assertTrue(schemaState is PluginSchemaUiState.Card)
        schemaState as PluginSchemaUiState.Card
        assertEquals(
            PluginActionFeedback.Text("Retry · sessionId=s-1"),
            schemaState.lastActionFeedback,
        )
    }

    @Test
    fun unsupported_card_action_callback_updates_schema_state_feedback() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1")),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    CardResult(
                        card = PluginCardSchema(
                            title = "Runtime Card",
                            actions = listOf(
                                PluginCardAction(
                                    actionId = "retry",
                                    label = "Retry",
                                ),
                            ),
                        ),
                    )
                },
            )
        }

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        viewModel.onSchemaCardActionClick(actionId = "unsupported-action")
        advanceUntilIdle()

        val schemaState = viewModel.uiState.value.schemaUiState
        assertTrue(schemaState is PluginSchemaUiState.Card)
        schemaState as PluginSchemaUiState.Card
        assertEquals(
            PluginActionFeedback.Text("Unsupported schema card action: unsupported-action"),
            schemaState.lastActionFeedback,
        )
    }

    @Test
    fun settings_draft_update_callback_updates_schema_state() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1")),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    SettingsUiRequest(
                        schema = PluginSettingsSchema(
                            title = "Runtime Settings",
                            sections = listOf(
                                PluginSettingsSection(
                                    sectionId = "general",
                                    title = "General",
                                    fields = listOf(
                                        ToggleSettingField(
                                            fieldId = "enabled",
                                            label = "Enabled",
                                            defaultValue = false,
                                        ),
                                        TextInputSettingField(
                                            fieldId = "nickname",
                                            label = "Nickname",
                                            defaultValue = "",
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                },
            )
        }

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        viewModel.updateSettingsDraft("enabled", PluginSettingDraftValue.Toggle(true))
        viewModel.updateSettingsDraft("nickname", PluginSettingDraftValue.Text("Aster"))
        advanceUntilIdle()

        val schemaState = viewModel.uiState.value.schemaUiState
        assertTrue(schemaState is PluginSchemaUiState.Settings)
        schemaState as PluginSchemaUiState.Settings
        assertEquals(PluginSettingDraftValue.Toggle(true), schemaState.draftValues["enabled"])
        assertEquals(PluginSettingDraftValue.Text("Aster"), schemaState.draftValues["nickname"])
    }

    private class FakePluginDependencies(
        records: List<PluginInstallRecord>,
        repositorySources: List<com.astrbot.android.model.plugin.PluginRepositorySource> = emptyList(),
        catalogEntries: List<PluginCatalogEntryRecord> = emptyList(),
        updateAvailabilities: Map<String, PluginUpdateAvailability> = emptyMap(),
    ) : PluginViewModelDependencies {
        private val recordsFlow = MutableStateFlow(records)
        private val repositorySourcesFlow = MutableStateFlow(repositorySources)
        private val catalogEntriesFlow = MutableStateFlow(catalogEntries)
        private val updateAvailabilitiesFlow = MutableStateFlow(updateAvailabilities)
        val enableRequests = mutableListOf<Pair<String, Boolean>>()
        val handledInstallIntents = mutableListOf<PluginInstallIntent>()
        val localPackageUris = mutableListOf<String>()
        val upgradeRequests = mutableListOf<PluginUpdateAvailability>()
        var lastUpdatedPolicy: PluginUninstallPolicy? = null
        var lastUninstallPolicy: PluginUninstallPolicy? = null
        var localPackageInstallError: Throwable? = null
        var nextLocalPackageInstallResult: PluginInstallIntentResult? = null

        override val records: StateFlow<List<PluginInstallRecord>> = recordsFlow
        override val repositorySources: StateFlow<List<com.astrbot.android.model.plugin.PluginRepositorySource>> = repositorySourcesFlow
        override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = catalogEntriesFlow

        fun updateRecords(records: List<PluginInstallRecord>) {
            recordsFlow.value = records
        }

        override fun getUpdateAvailability(pluginId: String): PluginUpdateAvailability? {
            return updateAvailabilitiesFlow.value[pluginId]
        }

        override fun setPluginEnabled(pluginId: String, enabled: Boolean): PluginInstallRecord {
            enableRequests += pluginId to enabled
            val updated = requireNotNull(recordsFlow.value.firstOrNull { it.pluginId == pluginId }).withOverrides(
                enabled = enabled,
                lastUpdatedAt = if (enabled) 10L else 20L,
            )
            recordsFlow.value = recordsFlow.value.map { record ->
                if (record.pluginId == pluginId) updated else record
            }
            return updated
        }

        override fun updatePluginUninstallPolicy(
            pluginId: String,
            policy: PluginUninstallPolicy,
        ): PluginInstallRecord {
            lastUpdatedPolicy = policy
            val updated = requireNotNull(recordsFlow.value.firstOrNull { it.pluginId == pluginId }).withOverrides(
                uninstallPolicy = policy,
                lastUpdatedAt = 30L,
            )
            recordsFlow.value = recordsFlow.value.map { record ->
                if (record.pluginId == pluginId) updated else record
            }
            return updated
        }

        override fun uninstallPlugin(
            pluginId: String,
            policy: PluginUninstallPolicy,
        ): PluginUninstallResult {
            lastUninstallPolicy = policy
            recordsFlow.value = recordsFlow.value.filterNot { record -> record.pluginId == pluginId }
            return PluginUninstallResult(
                pluginId = pluginId,
                policy = policy,
                removedData = policy == PluginUninstallPolicy.REMOVE_DATA,
            )
        }

        override suspend fun handleInstallIntent(intent: PluginInstallIntent): PluginInstallIntentResult {
            handledInstallIntents += intent
            return when (intent) {
                is PluginInstallIntent.RepositoryUrl -> PluginInstallIntentResult.RepositorySynced(
                    syncState = PluginCatalogSyncState(
                        sourceId = "repo-source",
                        lastSyncAtEpochMillis = 1L,
                        lastSyncStatus = PluginCatalogSyncStatus.SUCCESS,
                    ),
                )
                is PluginInstallIntent.DirectPackageUrl -> PluginInstallIntentResult.Ignored
                is PluginInstallIntent.CatalogVersion -> PluginInstallIntentResult.Ignored
            }
        }

        override suspend fun installFromLocalPackageUri(uri: String): PluginInstallIntentResult {
            localPackageUris += uri
            localPackageInstallError?.let { throw it }
            return nextLocalPackageInstallResult ?: PluginInstallIntentResult.Ignored
        }

        override suspend fun upgradePlugin(update: PluginUpdateAvailability): PluginInstallRecord {
            upgradeRequests += update
            val existing = requireNotNull(recordsFlow.value.firstOrNull { it.pluginId == update.pluginId })
            val upgraded = existing.withOverrides(
                version = update.latestVersion,
                sourceType = PluginSourceType.REPOSITORY,
                catalogSourceId = update.catalogSourceId,
                installedPackageUrl = update.packageUrl,
                lastCatalogCheckAtEpochMillis = 99L,
                lastUpdatedAt = 99L,
            )
            recordsFlow.value = recordsFlow.value.map { record ->
                if (record.pluginId == update.pluginId) upgraded else record
            }
            updateAvailabilitiesFlow.value = updateAvailabilitiesFlow.value - update.pluginId
            return upgraded
        }
    }

    private fun pluginRecord(
        pluginId: String,
        riskLevel: PluginRiskLevel = PluginRiskLevel.LOW,
        sourceType: PluginSourceType = PluginSourceType.LOCAL_FILE,
        catalogSourceId: String? = null,
        enabled: Boolean = false,
        failureState: PluginFailureState = PluginFailureState.none(),
        uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
        lastUpdatedAt: Long = 1L,
        compatibilityState: PluginCompatibilityState = PluginCompatibilityState.evaluated(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        ),
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
                sourceType = sourceType,
                entrySummary = "Entry",
                riskLevel = riskLevel,
            ),
            source = PluginSource(
                sourceType = sourceType,
                location = "/tmp/$pluginId.zip",
                importedAt = 1L,
            ),
            compatibilityState = compatibilityState,
            failureState = failureState,
            uninstallPolicy = uninstallPolicy,
            catalogSourceId = catalogSourceId,
            enabled = enabled,
            lastUpdatedAt = lastUpdatedAt,
        )
    }

    private fun repositorySource(
        sourceId: String,
        title: String,
        catalogUrl: String = "https://repo.example.com/catalog.json",
        lastSyncAtEpochMillis: Long? = null,
    ): com.astrbot.android.model.plugin.PluginRepositorySource {
        return com.astrbot.android.model.plugin.PluginRepositorySource(
            sourceId = sourceId,
            title = title,
            catalogUrl = catalogUrl,
            updatedAt = 1_700_000_000_000L,
            lastSyncAtEpochMillis = lastSyncAtEpochMillis,
            lastSyncStatus = PluginCatalogSyncStatus.SUCCESS,
        )
    }

    private fun catalogEntryRecord(
        sourceId: String,
        pluginId: String,
        versions: List<PluginCatalogVersion> = listOf(
            catalogVersion(version = "1.0.0"),
        ),
    ): PluginCatalogEntryRecord {
        return PluginCatalogEntryRecord(
            sourceId = sourceId,
            sourceTitle = "AstrBot Repo",
            catalogUrl = "https://repo.example.com/catalog.json",
            entry = PluginCatalogEntry(
                pluginId = pluginId,
                title = pluginId,
                author = "AstrBot",
                description = "Description for $pluginId",
                entrySummary = "Summary for $pluginId",
                versions = versions,
            ),
        )
    }

    private fun catalogVersion(
        version: String,
        publishedAt: Long = 1_700_000_000_000L,
        changelog: String = "",
    ): PluginCatalogVersion {
        return PluginCatalogVersion(
            version = version,
            packageUrl = "https://repo.example.com/$version.zip",
            publishedAt = publishedAt,
            protocolVersion = 1,
            minHostVersion = "1.0.0",
            changelog = changelog,
        )
    }

    private fun runtimePlugin(
        pluginId: String,
        trigger: PluginTriggerSource = PluginTriggerSource.OnPluginEntryClick,
        handler: (com.astrbot.android.model.plugin.PluginExecutionContext) -> com.astrbot.android.model.plugin.PluginExecutionResult,
    ): PluginRuntimePlugin {
        val record = pluginRecord(pluginId = pluginId, enabled = true)
        return PluginRuntimePlugin(
            pluginId = pluginId,
            pluginVersion = record.installedVersion,
            installState = PluginInstallState(
                status = PluginInstallStatus.INSTALLED,
                installedVersion = record.installedVersion,
                source = record.source,
                manifestSnapshot = record.manifestSnapshot,
                permissionSnapshot = record.permissionSnapshot,
                compatibilityState = record.compatibilityState,
                enabled = record.enabled,
                lastInstalledAt = record.installedAt,
                lastUpdatedAt = record.lastUpdatedAt,
                localPackagePath = record.localPackagePath,
                extractedDir = record.extractedDir,
            ),
            supportedTriggers = setOf(trigger),
            handler = handler,
        )
    }

}

private fun assertResourceFeedback(
    feedback: PluginActionFeedback?,
    resId: Int,
    expectedArg: String? = null,
) {
    assertTrue(feedback is PluginActionFeedback.Resource)
    feedback as PluginActionFeedback.Resource
    assertEquals(resId, feedback.resId)
    if (expectedArg != null) {
        assertTrue(feedback.formatArgs.contains(expectedArg))
    }
}

private fun PluginInstallRecord.withOverrides(
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

private fun pluginPermissionDiff(
    added: List<PluginPermissionDeclaration> = emptyList(),
): com.astrbot.android.model.plugin.PluginPermissionDiff {
    return com.astrbot.android.model.plugin.PluginPermissionDiff(
        added = added,
    )
}

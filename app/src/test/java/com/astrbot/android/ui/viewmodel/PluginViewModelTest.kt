package com.astrbot.android.ui.viewmodel

import com.astrbot.android.R
import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.data.PluginUninstallResult
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.plugin.CardResult
import com.astrbot.android.model.plugin.MediaResult
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginCardAction
import com.astrbot.android.model.plugin.PluginCardSchema
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginDownloadProgress
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginInstallIntent
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginReviewState
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginTrustLevel
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginInstallIntentResult
import com.astrbot.android.model.plugin.PluginMediaItem
import com.astrbot.android.model.plugin.PluginSelectOption
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSettingsSection
import com.astrbot.android.model.plugin.PluginStaticConfigField
import com.astrbot.android.model.plugin.PluginStaticConfigFieldType
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.PluginWorkspaceFileEntry
import com.astrbot.android.model.plugin.SelectSettingField
import com.astrbot.android.model.plugin.SettingsUiRequest
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.model.plugin.TextInputSettingField
import com.astrbot.android.model.plugin.ToggleSettingField
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.runtime.plugin.ExternalPluginBridgeRuntime
import com.astrbot.android.runtime.plugin.ExternalPluginRuntimeCatalog
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import com.astrbot.android.runtime.plugin.RecordingExternalPluginScriptExecutor
import com.astrbot.android.runtime.plugin.createQuickJsExternalPluginInstallRecord
import com.astrbot.android.runtime.RuntimeLogRepository
import java.lang.reflect.Method
import java.nio.file.Files
import org.json.JSONObject
import kotlinx.coroutines.CompletableDeferred
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
        RuntimeLogRepository.clear()
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
    fun init_with_empty_repository_sources_bootstraps_official_market_once() = runTest(dispatcher) {
        val deps = FakePluginDependencies(records = emptyList())

        PluginViewModel(deps)
        advanceUntilIdle()

        assertEquals(1, deps.officialCatalogBootstrapRequests)
        assertEquals(1, deps.repositorySources.value.size)
        assertEquals(
            "https://raw.githubusercontent.com/undertaker33/astrbot-android-plugin-market/main/catalog.json",
            deps.repositorySources.value.single().catalogUrl,
        )
    }

    @Test
    fun refresh_market_catalog_invokes_dependency_and_resets_running_state() = runTest(dispatcher) {
        RuntimeLogRepository.clear()
        val deps = FakePluginDependencies(
            records = emptyList(),
            repositorySources = listOf(repositorySource(sourceId = "repo-1", title = "Repo 1")),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.refreshMarketCatalog()
        advanceUntilIdle()

        assertEquals(1, deps.marketRefreshRequests)
        assertFalse(viewModel.uiState.value.isMarketRefreshRunning)
        assertNull(viewModel.uiState.value.marketRefreshFeedback)
        assertTrue(
            RuntimeLogRepository.logs.value.any {
                it.contains("Plugin market refresh requested") &&
                    it.contains("sourceCount=1")
            },
        )
        assertTrue(
            RuntimeLogRepository.logs.value.any {
                it.contains("Plugin market refresh finished") &&
                    it.contains("resultCount=1")
            },
        )
    }

    @Test
    fun refresh_market_catalog_with_empty_sources_bootstraps_official_market() = runTest(dispatcher) {
        val deps = FakePluginDependencies(records = emptyList())
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        deps.updateRepositorySources(emptyList())
        viewModel.refreshMarketCatalog()
        advanceUntilIdle()

        assertEquals(1, deps.marketRefreshRequests)
        assertEquals(2, deps.officialCatalogBootstrapRequests)
        assertEquals(1, deps.repositorySources.value.size)
        assertFalse(viewModel.uiState.value.isMarketRefreshRunning)
        assertNull(viewModel.uiState.value.marketRefreshFeedback)
    }

    @Test
    fun refresh_market_catalog_failure_exposes_failed_feedback() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = emptyList(),
            repositorySources = listOf(repositorySource(sourceId = "repo-1", title = "Repo 1")),
        )
        deps.marketRefreshError = IllegalStateException("network down")
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.refreshMarketCatalog()
        advanceUntilIdle()

        assertEquals(1, deps.marketRefreshRequests)
        assertFalse(viewModel.uiState.value.isMarketRefreshRunning)
        assertEquals(
            PluginActionFeedback.Resource(R.string.plugin_market_refresh_failed),
            viewModel.uiState.value.marketRefreshFeedback,
        )
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
        assertEquals(PluginRiskLevel.MEDIUM, detail.governanceState?.riskLevel)
        assertEquals(PluginTrustLevel.REPOSITORY_LISTED, detail.governanceState?.trustLevel)
        assertEquals(PluginReviewState.LOCAL_CHECKS_PASSED, detail.governanceState?.reviewState)
    }

    @Test
    fun detail_state_marks_local_package_as_host_blocked_when_compatibility_fails() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(
                pluginRecord(
                    pluginId = "plugin-blocked",
                    sourceType = PluginSourceType.LOCAL_FILE,
                    riskLevel = PluginRiskLevel.HIGH,
                    compatibilityState = PluginCompatibilityState.evaluated(
                        protocolSupported = true,
                        minHostVersionSatisfied = false,
                        maxHostVersionSatisfied = true,
                        notes = "Needs host 9.0.0 or newer.",
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-blocked")
        advanceUntilIdle()

        val governance = viewModel.uiState.value.detailMetadataState.governanceState
        assertEquals(PluginRiskLevel.HIGH, governance?.riskLevel)
        assertEquals(PluginTrustLevel.LOCAL_PACKAGE, governance?.trustLevel)
        assertEquals(PluginReviewState.HOST_COMPATIBILITY_BLOCKED, governance?.reviewState)
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
    fun local_workspace_controls_update_search_query_and_selected_filter() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(
                pluginRecord(pluginId = "plugin-1"),
                pluginRecord(pluginId = "plugin-2"),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        val localFilterClass = Class.forName("com.astrbot.android.ui.screen.PluginLocalFilter")
        val updatesFilter = java.lang.Enum.valueOf(localFilterClass.asSubclass(Enum::class.java), "UPDATES")
        pluginViewModelMethod("updateLocalSearchQuery", String::class.java).invoke(viewModel, "alice")
        pluginViewModelMethod("updateSelectedLocalFilter", localFilterClass).invoke(viewModel, updatesFilter)
        advanceUntilIdle()

        assertEquals("alice", propertyValue<String>(viewModel.uiState.value, "localSearchQuery"))
        assertEquals("UPDATES", propertyValue<Enum<*>>(viewModel.uiState.value, "selectedLocalFilter").name)
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
    fun restore_selected_plugin_default_config_resets_static_and_runtime_drafts() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(pluginRecord(pluginId = "plugin-1")),
            staticSchemas = mapOf(
                "plugin-1" to PluginStaticConfigSchema(
                    fields = listOf(
                        PluginStaticConfigField(
                            fieldKey = "token",
                            fieldType = PluginStaticConfigFieldType.StringField,
                            defaultValue = PluginStaticConfigValue.StringValue("sk-demo"),
                        ),
                    ),
                ),
            ),
            configSnapshots = mapOf(
                "plugin-1" to PluginConfigStorageBoundary(
                    coreFieldKeys = setOf("token"),
                    extensionFieldKeys = setOf("enabled"),
                    coreDefaults = mapOf("token" to PluginStaticConfigValue.StringValue("sk-demo")),
                ).createSnapshot(
                    coreValues = mapOf("token" to PluginStaticConfigValue.StringValue("sk-live")),
                    extensionValues = mapOf("enabled" to PluginStaticConfigValue.BoolValue(true)),
                ),
            ),
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

        viewModel.selectPluginForConfig("plugin-1")
        advanceUntilIdle()

        viewModel.restoreSelectedPluginDefaultConfig()
        advanceUntilIdle()

        val staticConfigState = requireNotNull(viewModel.uiState.value.staticConfigUiState)
        assertEquals(
            PluginSettingDraftValue.Text("sk-demo"),
            staticConfigState.draftValues["token"],
        )

        val runtimeState = viewModel.uiState.value.schemaUiState as PluginSchemaUiState.Settings
        assertEquals(
            PluginSettingDraftValue.Toggle(false),
            runtimeState.draftValues["enabled"],
        )
        assertEquals(
            mapOf("token" to PluginStaticConfigValue.StringValue("sk-demo")),
            deps.lastSavedCoreValues,
        )
        assertEquals(
            mapOf("enabled" to PluginStaticConfigValue.BoolValue(false)),
            deps.lastSavedExtensionValues,
        )
    }

    @Test
    fun clear_selected_plugin_cache_removes_only_cache_files() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(pluginRecord(pluginId = "plugin-1")),
            workspaceSnapshots = mapOf(
                "plugin-1" to PluginHostWorkspaceSnapshot(
                    privateRootPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1",
                    importsPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/imports",
                    runtimePath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/runtime",
                    exportsPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/exports",
                    cachePath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/cache",
                    files = listOf(
                        PluginWorkspaceFileEntry(relativePath = "cache/runtime/state.json", sizeBytes = 10, lastModifiedAtEpochMillis = 1L),
                        PluginWorkspaceFileEntry(relativePath = "exports/report.txt", sizeBytes = 20, lastModifiedAtEpochMillis = 2L),
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPluginForWorkspace("plugin-1")
        advanceUntilIdle()

        viewModel.clearSelectedPluginCache()
        advanceUntilIdle()

        assertEquals(listOf("cache/runtime/state.json"), deps.deletedWorkspaceFiles)
        assertEquals(
            listOf("exports/report.txt"),
            viewModel.uiState.value.hostWorkspaceState.files.map { it.relativePath },
        )
    }

    @Test
    fun clear_selected_plugin_cache_does_not_retry_recoverable_plugin_after_cleanup() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    enabled = false,
                    failureState = PluginFailureState(
                        consecutiveFailureCount = 1,
                        lastFailureAtEpochMillis = 123L,
                        lastErrorSummary = "Disk cache corrupted",
                        suspendedUntilEpochMillis = null,
                    ),
                ),
            ),
            workspaceSnapshots = mapOf(
                "plugin-1" to PluginHostWorkspaceSnapshot(
                    privateRootPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1",
                    importsPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/imports",
                    runtimePath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/runtime",
                    exportsPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/exports",
                    cachePath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/cache",
                    files = listOf(
                        PluginWorkspaceFileEntry(relativePath = "cache/runtime/state.json", sizeBytes = 10, lastModifiedAtEpochMillis = 1L),
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPluginForWorkspace("plugin-1")
        advanceUntilIdle()

        viewModel.clearSelectedPluginCache()
        advanceUntilIdle()

        assertEquals(listOf("cache/runtime/state.json"), deps.deletedWorkspaceFiles)
        assertEquals(emptyList<String>(), deps.clearedFailurePluginIds)
        assertEquals(emptyList<Pair<String, Boolean>>(), deps.enableRequests)
        val feedback = viewModel.uiState.value.detailActionState.lastActionMessage
        assertTrue(feedback is PluginActionFeedback.Text)
        feedback as PluginActionFeedback.Text
        assertTrue(feedback.value.contains("cache cleared", ignoreCase = true))
    }

    @Test
    fun uninstall_selected_plugin_always_removes_plugin_data() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1", uninstallPolicy = PluginUninstallPolicy.KEEP_DATA)),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

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
            resId = R.string.plugin_action_feedback_enabled_with_name,
            expectedArg = "plugin-1",
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
    fun request_upgrade_without_available_update_surfaces_no_update_feedback() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    sourceType = PluginSourceType.REPOSITORY,
                    catalogSourceId = "official",
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
        assertResourceFeedback(
            feedback = viewModel.uiState.value.detailActionState.lastActionMessage,
            resId = R.string.plugin_action_feedback_no_update_available,
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
        val feedback = viewModel.uiState.value.detailActionState.lastActionMessage
        assertTrue(feedback is PluginActionFeedback.Text)
        feedback as PluginActionFeedback.Text
        assertTrue(feedback.value.contains("Market", ignoreCase = true))
        assertTrue(feedback.value.contains("repo-source", ignoreCase = true))
    }

    @Test
    fun catalog_entries_expose_repository_homepage_from_entry_or_package_url() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = emptyList(),
            catalogEntries = listOf(
                catalogEntryRecord(
                    sourceId = "repo-1",
                    pluginId = "explicit-repo",
                    repositoryUrl = "https://github.com/example/explicit-repo",
                ),
                catalogEntryRecord(
                    sourceId = "repo-1",
                    pluginId = "derived-repo",
                    versions = listOf(
                        catalogVersion(
                            version = "2.0.0",
                            packageUrl = "https://github.com/example/derived-repo/releases/download/v2.0.0/derived-repo.zip",
                        ),
                    ),
                ),
                catalogEntryRecord(
                    sourceId = "repo-1",
                    pluginId = "raw-derived-repo",
                    catalogUrl = "https://raw.githubusercontent.com/example/raw-derived-repo/main/publish/0.1.0/repository/catalog.json",
                    versions = listOf(
                        catalogVersion(
                            version = "0.1.0",
                            packageUrl = "packages/raw-derived-repo-0.1.0.zip",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        val cardsByPluginId = viewModel.uiState.value.catalogEntries.associateBy { it.pluginId }
        assertEquals(
            "https://github.com/example/explicit-repo",
            cardsByPluginId.getValue("explicit-repo").repositoryUrl,
        )
        assertEquals(
            "https://github.com/example/derived-repo",
            cardsByPluginId.getValue("derived-repo").repositoryUrl,
        )
        assertEquals(
            "https://github.com/example/raw-derived-repo",
            cardsByPluginId.getValue("raw-derived-repo").repositoryUrl,
        )
    }

    @Test
    fun install_or_update_catalog_plugin_uses_latest_catalog_version_intent() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = emptyList(),
            catalogEntries = listOf(
                catalogEntryRecord(
                    sourceId = "repo-1",
                    pluginId = "plugin-1",
                    versions = listOf(
                        catalogVersion(version = "1.0.0"),
                        catalogVersion(
                            version = "1.2.0",
                            packageUrl = "./packages/plugin-1-1.2.0.zip",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.installOrUpdateCatalogPlugin("plugin-1")
        advanceUntilIdle()

        assertEquals(
            listOf(
                PluginInstallIntent.catalogVersion(
                    pluginId = "plugin-1",
                    version = "1.2.0",
                    packageUrl = "https://repo.example.com/packages/plugin-1-1.2.0.zip",
                    catalogSourceId = "repo-1",
                ),
            ),
            deps.handledInstallIntents,
        )
    }

    @Test
    fun install_or_update_catalog_plugin_exposes_download_progress_until_install_finishes() = runTest(dispatcher) {
        val installGate = CompletableDeferred<Unit>()
        val progress = PluginDownloadProgress.downloading(
            bytesDownloaded = 1_048_576L,
            totalBytes = 2_097_152L,
            bytesPerSecond = 524_288L,
        )
        val deps = FakePluginDependencies(
            records = emptyList(),
            catalogEntries = listOf(
                catalogEntryRecord(
                    sourceId = "repo-1",
                    pluginId = "plugin-1",
                    versions = listOf(catalogVersion(version = "1.0.0")),
                ),
            ),
        ).apply {
            nextInstallProgress = progress
            installIntentGate = installGate
        }
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.installOrUpdateCatalogPlugin("plugin-1")
        advanceUntilIdle()

        assertEquals(progress, viewModel.uiState.value.downloadProgress)
        installGate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.downloadProgress)
    }

    @Test
    fun install_or_update_catalog_plugin_uses_selected_market_version() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = emptyList(),
            hostVersion = "0.4.6",
            catalogEntries = listOf(
                catalogEntryRecord(
                    sourceId = "repo-stable",
                    pluginId = "weather",
                    versions = listOf(
                        catalogVersion(
                            version = "0.1.5",
                            packageUrl = "https://repo.example.com/weather-0.1.5.zip",
                            minHostVersion = "0.1.0",
                        ),
                    ),
                ),
                catalogEntryRecord(
                    sourceId = "repo-legacy",
                    pluginId = "weather",
                    versions = listOf(
                        catalogVersion(
                            version = "0.1.0",
                            packageUrl = "https://repo.example.com/weather-0.1.0.zip",
                            minHostVersion = "0.1.0",
                        ),
                    ),
                ),
            ),
        )
        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectMarketPluginVersion(
            pluginId = "weather",
            versionKey = "weather|repo-legacy|0.1.0|https://repo.example.com/weather-0.1.0.zip",
        )
        viewModel.installOrUpdateCatalogPlugin("weather")
        advanceUntilIdle()

        assertEquals(
            listOf(
                PluginInstallIntent.catalogVersion(
                    pluginId = "weather",
                    version = "0.1.0",
                    packageUrl = "https://repo.example.com/weather-0.1.0.zip",
                    catalogSourceId = "repo-legacy",
                ),
            ),
            deps.handledInstallIntents,
        )
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

        val followUpGuide = viewModel.uiState.value.installFollowUpGuide
        assertEquals("local-import", followUpGuide?.pluginId)
        assertEquals("local-import", followUpGuide?.pluginTitle)
        assertEquals("AstrBot", followUpGuide?.author)
        assertEquals(1, followUpGuide?.permissionCount)
        assertEquals("js_quickjs", followUpGuide?.runtimeLabel)
        assertEquals(true, followUpGuide?.canEnableNow)
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
        assertNull(viewModel.uiState.value.installFollowUpGuide)
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
    fun select_plugin_maps_text_result_to_schema_ui_state() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1")),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    TextResult(
                        text = "Runtime text reply",
                        markdown = true,
                        displayTitle = "Runtime Output",
                    )
                },
            )
        }

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        viewModel.selectPlugin("plugin-1")
        advanceUntilIdle()

        val schemaState = viewModel.uiState.value.schemaUiState
        assertTrue(schemaState is PluginSchemaUiState.Text)
        schemaState as PluginSchemaUiState.Text
        assertEquals("Runtime Output", schemaState.title)
        assertEquals("Runtime text reply", schemaState.text)
        assertTrue(schemaState.markdown)
    }

    @Test
    fun select_plugin_maps_media_result_to_schema_ui_state() = runTest(dispatcher) {
        val tempDir = Files.createTempDirectory("plugin-media-ui").toFile()
        try {
            val extractedDir = tempDir.resolve("plugin").apply { mkdirs() }
            extractedDir.resolve("assets").mkdirs()
            extractedDir.resolve("assets/banner.png").writeText("banner", Charsets.UTF_8)
            val deps = FakePluginDependencies(
                listOf(
                    pluginRecord(
                        pluginId = "plugin-1",
                        extractedDir = extractedDir.absolutePath,
                    ),
                ),
            )
            PluginRuntimeRegistry.registerProvider {
                listOf(
                    runtimePlugin(pluginId = "plugin-1") {
                        MediaResult(
                            items = listOf(
                                PluginMediaItem(
                                    source = "plugin://package/assets/banner.png",
                                    mimeType = "image/png",
                                    altText = "Banner",
                                ),
                                PluginMediaItem(
                                    source = "https://example.com/banner.png",
                                    mimeType = "image/png",
                                    altText = "Remote Banner",
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
            assertTrue(schemaState is PluginSchemaUiState.Media)
            schemaState as PluginSchemaUiState.Media
            assertEquals(2, schemaState.items.size)
            assertEquals("Banner", schemaState.items.first().label)
            assertEquals(
                extractedDir.resolve("assets/banner.png").absolutePath,
                schemaState.items.first().resolvedSource,
            )
            assertEquals(
                "https://example.com/banner.png",
                schemaState.items.last().resolvedSource,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun select_plugin_maps_invalid_media_result_to_error_schema_state() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(pluginRecord(pluginId = "plugin-1")),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    MediaResult(
                        items = listOf(
                            PluginMediaItem(
                                source = "assets/banner.png",
                                mimeType = "image/png",
                                altText = "Banner",
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
        assertTrue(schemaState is PluginSchemaUiState.Error)
        schemaState as PluginSchemaUiState.Error
        assertTrue(schemaState.message.contains("assets/banner.png"))
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

    @Test
    fun detail_selection_keeps_config_state_empty() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(pluginRecord(pluginId = "plugin-1")),
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

        viewModel.selectPluginForDetail("plugin-1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isShowingDetail)
        assertTrue(viewModel.uiState.value.schemaUiState is PluginSchemaUiState.None)
        assertNull(viewModel.uiState.value.staticConfigUiState)
    }

    @Test
    fun config_selection_resolves_static_schema_and_persisted_snapshot() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(pluginRecord(pluginId = "plugin-1")),
            staticSchemas = mapOf(
                "plugin-1" to PluginStaticConfigSchema(
                    fields = listOf(
                        PluginStaticConfigField(
                            fieldKey = "token",
                            fieldType = PluginStaticConfigFieldType.StringField,
                            defaultValue = PluginStaticConfigValue.StringValue("sk-demo"),
                        ),
                    ),
                ),
            ),
            configSnapshots = mapOf(
                "plugin-1" to PluginConfigStoreSnapshot(
                    coreValues = mapOf(
                        "token" to PluginStaticConfigValue.StringValue("sk-live"),
                    ),
                    extensionValues = mapOf(
                        "enabled" to PluginStaticConfigValue.BoolValue(true),
                    ),
                ),
            ),
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

        viewModel.selectPluginForConfig("plugin-1")
        advanceUntilIdle()

        val staticConfigState = requireNotNull(viewModel.uiState.value.staticConfigUiState)
        assertEquals(
            PluginSettingDraftValue.Text("sk-live"),
            staticConfigState.draftValues["token"],
        )

        val runtimeState = viewModel.uiState.value.schemaUiState
        assertTrue(runtimeState is PluginSchemaUiState.Settings)
        runtimeState as PluginSchemaUiState.Settings
        assertEquals(
            PluginSettingDraftValue.Toggle(true),
            runtimeState.draftValues["enabled"],
        )
        assertEquals("plugin-1", deps.lastResolvedConfigPluginId)
    }

    @Test
    fun config_draft_updates_only_change_ui_state_until_explicit_save() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(pluginRecord(pluginId = "plugin-1")),
            staticSchemas = mapOf(
                "plugin-1" to PluginStaticConfigSchema(
                    fields = listOf(
                        PluginStaticConfigField(
                            fieldKey = "token",
                            fieldType = PluginStaticConfigFieldType.StringField,
                            defaultValue = PluginStaticConfigValue.StringValue("sk-demo"),
                        ),
                    ),
                ),
            ),
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

        viewModel.selectPluginForConfig("plugin-1")
        advanceUntilIdle()
        viewModel.updateStaticConfigDraft("token", PluginSettingDraftValue.Text("sk-live"))
        viewModel.updateSettingsDraft("enabled", PluginSettingDraftValue.Toggle(true))
        advanceUntilIdle()

        val staticConfigState = requireNotNull(viewModel.uiState.value.staticConfigUiState)
        assertEquals(PluginSettingDraftValue.Text("sk-live"), staticConfigState.draftValues["token"])
        val runtimeState = viewModel.uiState.value.schemaUiState as PluginSchemaUiState.Settings
        assertEquals(PluginSettingDraftValue.Toggle(true), runtimeState.draftValues["enabled"])
        assertEquals(emptyMap<String, PluginStaticConfigValue>(), deps.lastSavedCoreValues)
        assertEquals(emptyMap<String, PluginStaticConfigValue>(), deps.lastSavedExtensionValues)
    }

    @Test
    fun save_selected_plugin_config_persists_core_and_extension_values_and_sets_saved_feedback() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(pluginRecord(pluginId = "plugin-1")),
            staticSchemas = mapOf(
                "plugin-1" to PluginStaticConfigSchema(
                    fields = listOf(
                        PluginStaticConfigField(
                            fieldKey = "token",
                            fieldType = PluginStaticConfigFieldType.StringField,
                            defaultValue = PluginStaticConfigValue.StringValue("sk-demo"),
                        ),
                    ),
                ),
            ),
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

        viewModel.selectPluginForConfig("plugin-1")
        advanceUntilIdle()
        viewModel.updateStaticConfigDraft("token", PluginSettingDraftValue.Text("sk-live"))
        viewModel.updateSettingsDraft("enabled", PluginSettingDraftValue.Toggle(true))
        viewModel.saveSelectedPluginConfig()
        advanceUntilIdle()

        assertEquals(
            mapOf("token" to PluginStaticConfigValue.StringValue("sk-live")),
            deps.lastSavedCoreValues,
        )
        assertEquals(
            mapOf("enabled" to PluginStaticConfigValue.BoolValue(true)),
            deps.lastSavedExtensionValues,
        )
        assertResourceFeedback(
            feedback = viewModel.uiState.value.detailActionState.lastActionMessage,
            resId = R.string.common_saved,
        )
    }

    @Test
    fun select_plugin_for_workspace_loads_private_workspace_snapshot_and_management_schema() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(pluginRecord(pluginId = "plugin-1")),
            workspaceSnapshots = mapOf(
                "plugin-1" to PluginHostWorkspaceSnapshot(
                    privateRootPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1",
                    importsPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/imports",
                    runtimePath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/runtime",
                    exportsPath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/exports",
                    cachePath = "/data/user/0/com.astrbot/files/plugins/private/plugin-1/cache",
                    files = listOf(
                        PluginWorkspaceFileEntry(
                            relativePath = "imports/meme/cat.png",
                            sizeBytes = 42,
                            lastModifiedAtEpochMillis = 123L,
                        ),
                    ),
                ),
            ),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    SettingsUiRequest(
                        schema = PluginSettingsSchema(
                            title = "Workspace Admin",
                            sections = listOf(
                                PluginSettingsSection(
                                    sectionId = "admin",
                                    title = "Admin",
                                    fields = listOf(
                                        ToggleSettingField(
                                            fieldId = "autoIndex",
                                            label = "Auto index",
                                            defaultValue = true,
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

        viewModel.selectPluginForWorkspace("plugin-1")
        advanceUntilIdle()

        val workspace = viewModel.uiState.value.hostWorkspaceState
        assertTrue(workspace.isVisible)
        assertEquals("plugin-1", workspace.pluginId)
        assertEquals(
            listOf("imports/meme/cat.png"),
            workspace.files.map { it.relativePath },
        )
        assertTrue(workspace.managementSchemaState is PluginSchemaUiState.Settings)
        val schema = (workspace.managementSchemaState as PluginSchemaUiState.Settings).schema
        assertEquals("Workspace Admin", schema.title)
    }

    @Test
    fun import_workspace_file_refreshes_snapshot_and_tracks_import_uri() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(pluginRecord(pluginId = "plugin-1")),
            workspaceSnapshots = mapOf(
                "plugin-1" to PluginHostWorkspaceSnapshot(
                    privateRootPath = "/private/plugin-1",
                    importsPath = "/private/plugin-1/imports",
                    runtimePath = "/private/plugin-1/runtime",
                    exportsPath = "/private/plugin-1/exports",
                    cachePath = "/private/plugin-1/cache",
                ),
            ),
        )
        deps.nextImportedWorkspaceSnapshot = PluginHostWorkspaceSnapshot(
            privateRootPath = "/private/plugin-1",
            importsPath = "/private/plugin-1/imports",
            runtimePath = "/private/plugin-1/runtime",
            exportsPath = "/private/plugin-1/exports",
            cachePath = "/private/plugin-1/cache",
            files = listOf(
                PluginWorkspaceFileEntry(
                    relativePath = "imports/new/cat.png",
                    sizeBytes = 128,
                    lastModifiedAtEpochMillis = 456L,
                ),
            ),
        )

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPluginForWorkspace("plugin-1")
        advanceUntilIdle()

        viewModel.importWorkspaceFile("content://sample/cat.png")
        advanceUntilIdle()

        assertEquals(listOf("content://sample/cat.png"), deps.workspaceImportUris)
        assertEquals(
            listOf("imports/new/cat.png"),
            viewModel.uiState.value.hostWorkspaceState.files.map { it.relativePath },
        )
    }

    @Test
    fun workspace_entry_host_action_request_is_consumed_into_visible_feedback() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            records = listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    enabled = true,
                    permissions = listOf(
                        PluginPermissionDeclaration(
                            permissionId = "send_notification",
                            title = "Send notification",
                            description = "Allows notification host actions",
                        ),
                    ),
                ),
            ),
            workspaceSnapshots = mapOf(
                "plugin-1" to PluginHostWorkspaceSnapshot(
                    privateRootPath = "/private/plugin-1",
                    importsPath = "/private/plugin-1/imports",
                    runtimePath = "/private/plugin-1/runtime",
                    exportsPath = "/private/plugin-1/exports",
                    cachePath = "/private/plugin-1/cache",
                ),
            ),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(pluginId = "plugin-1") {
                    HostActionRequest(
                        action = PluginHostAction.SendNotification,
                        title = "Notify",
                        payload = mapOf("title" to "Meme Manager", "message" to "Import completed"),
                    )
                },
            )
        }

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()
        viewModel.selectPluginForWorkspace("plugin-1")
        advanceUntilIdle()

        val schema = viewModel.uiState.value.hostWorkspaceState.managementSchemaState
        assertTrue(schema is PluginSchemaUiState.Text)
        schema as PluginSchemaUiState.Text
        assertEquals("Notify", schema.title)
        assertTrue(schema.text.contains("Meme Manager"))
        assertTrue(schema.text.contains("Import completed"))
    }

    @Test
    fun config_entry_consumes_external_catalog_quickjs_settings_result() = runTest(dispatcher) {
        val extractedDir = Files.createTempDirectory("plugin-viewmodel-external-quickjs").toFile()
        try {
            val record = createQuickJsExternalPluginInstallRecord(
                extractedDir = extractedDir,
                pluginId = "plugin-quickjs-config",
                supportedTriggers = listOf("on_plugin_entry_click"),
            )
            PluginRuntimeRegistry.registerExternalProvider {
                ExternalPluginRuntimeCatalog.plugins(
                    records = listOf(record),
                    bridgeRuntime = ExternalPluginBridgeRuntime(
                        scriptExecutor = RecordingExternalPluginScriptExecutor(
                            outputs = listOf(
                                JSONObject(
                                    mapOf(
                                        "resultType" to "settings_ui",
                                        "schema" to JSONObject(
                                            mapOf(
                                                "title" to "QuickJS Config",
                                                "sections" to listOf(
                                                    JSONObject(
                                                        mapOf(
                                                            "sectionId" to "runtime",
                                                            "title" to "Runtime",
                                                            "fields" to listOf(
                                                                JSONObject(
                                                                    mapOf(
                                                                        "fieldType" to "text_input",
                                                                        "fieldId" to "greeting",
                                                                        "label" to "Greeting",
                                                                        "defaultValue" to "hello from quickjs",
                                                                    ),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ).toString(),
                            ),
                        ),
                    ),
                )
            }

            val deps = FakePluginDependencies(records = listOf(record))
            val viewModel = PluginViewModel(deps)
            advanceUntilIdle()

            viewModel.selectPluginForConfig("plugin-quickjs-config")
            advanceUntilIdle()

            val schemaState = viewModel.uiState.value.schemaUiState
            assertTrue(schemaState is PluginSchemaUiState.Settings)
            schemaState as PluginSchemaUiState.Settings
            assertEquals("QuickJS Config", schemaState.schema.title)
            assertEquals("runtime", schemaState.schema.sections.single().sectionId)
            assertEquals("hello from quickjs", schemaState.draftValues["greeting"].let { value ->
                (value as PluginSettingDraftValue.Text).value
            })
        } finally {
            PluginRuntimeRegistry.reset()
            extractedDir.deleteRecursively()
        }
    }

    private class FakePluginDependencies(
        records: List<PluginInstallRecord>,
        repositorySources: List<com.astrbot.android.model.plugin.PluginRepositorySource> = emptyList(),
        catalogEntries: List<PluginCatalogEntryRecord> = emptyList(),
        updateAvailabilities: Map<String, PluginUpdateAvailability> = emptyMap(),
        staticSchemas: Map<String, PluginStaticConfigSchema> = emptyMap(),
        configSnapshots: Map<String, PluginConfigStoreSnapshot> = emptyMap(),
        workspaceSnapshots: Map<String, PluginHostWorkspaceSnapshot> = emptyMap(),
        private val hostVersion: String = "9.9.9",
    ) : PluginViewModelDependencies {
        private val recordsFlow = MutableStateFlow(records)
        private val repositorySourcesFlow = MutableStateFlow(repositorySources)
        private val catalogEntriesFlow = MutableStateFlow(catalogEntries)
        private val updateAvailabilitiesFlow = MutableStateFlow(updateAvailabilities)
        private val staticSchemasByPluginId = staticSchemas.toMutableMap()
        private val configSnapshotsByPluginId = configSnapshots.toMutableMap()
        private val workspaceSnapshotsByPluginId = workspaceSnapshots.toMutableMap()
        val enableRequests = mutableListOf<Pair<String, Boolean>>()
        val handledInstallIntents = mutableListOf<PluginInstallIntent>()
        val localPackageUris = mutableListOf<String>()
        val upgradeRequests = mutableListOf<PluginUpdateAvailability>()
        val workspaceImportUris = mutableListOf<String>()
        val clearedFailurePluginIds = mutableListOf<String>()
        val deletedWorkspaceFiles = mutableListOf<String>()
        var lastUninstallPolicy: PluginUninstallPolicy? = null
        var localPackageInstallError: Throwable? = null
        var nextLocalPackageInstallResult: PluginInstallIntentResult? = null
        var nextImportedWorkspaceSnapshot: PluginHostWorkspaceSnapshot? = null
        var lastResolvedConfigPluginId: String? = null
        var lastSavedCoreValues: Map<String, PluginStaticConfigValue> = emptyMap()
        var lastSavedExtensionValues: Map<String, PluginStaticConfigValue> = emptyMap()
        var marketRefreshRequests: Int = 0
        var officialCatalogBootstrapRequests: Int = 0
        var marketRefreshError: Throwable? = null
        var officialCatalogBootstrapError: Throwable? = null
        var nextInstallProgress: PluginDownloadProgress? = null
        var installIntentGate: CompletableDeferred<Unit>? = null

        override val records: StateFlow<List<PluginInstallRecord>> = recordsFlow
        override val repositorySources: StateFlow<List<com.astrbot.android.model.plugin.PluginRepositorySource>> = repositorySourcesFlow
        override val catalogEntries: StateFlow<List<PluginCatalogEntryRecord>> = catalogEntriesFlow

        override fun getHostVersion(): String = hostVersion

        fun updateRecords(records: List<PluginInstallRecord>) {
            recordsFlow.value = records
        }

        fun updateRepositorySources(sources: List<com.astrbot.android.model.plugin.PluginRepositorySource>) {
            repositorySourcesFlow.value = sources
        }

        override fun getUpdateAvailability(pluginId: String): PluginUpdateAvailability? {
            return updateAvailabilitiesFlow.value[pluginId]
        }

        override fun getPluginStaticConfigSchema(pluginId: String): PluginStaticConfigSchema? {
            return staticSchemasByPluginId[pluginId]
        }

        override fun resolvePluginConfigSnapshot(
            pluginId: String,
            boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
        ): PluginConfigStoreSnapshot {
            lastResolvedConfigPluginId = pluginId
            return boundary.createSnapshot(
                coreValues = configSnapshotsByPluginId[pluginId]?.coreValues.orEmpty(),
                extensionValues = configSnapshotsByPluginId[pluginId]?.extensionValues.orEmpty(),
            )
        }

        override fun savePluginCoreConfig(
            pluginId: String,
            boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
            coreValues: Map<String, PluginStaticConfigValue>,
        ): PluginConfigStoreSnapshot {
            lastSavedCoreValues = coreValues
            val updatedSnapshot = boundary.createSnapshot(
                coreValues = coreValues,
                extensionValues = configSnapshotsByPluginId[pluginId]?.extensionValues.orEmpty(),
            )
            configSnapshotsByPluginId[pluginId] = updatedSnapshot
            return updatedSnapshot
        }

        override fun savePluginExtensionConfig(
            pluginId: String,
            boundary: com.astrbot.android.model.plugin.PluginConfigStorageBoundary,
            extensionValues: Map<String, PluginStaticConfigValue>,
        ): PluginConfigStoreSnapshot {
            lastSavedExtensionValues = extensionValues
            val updatedSnapshot = boundary.createSnapshot(
                coreValues = configSnapshotsByPluginId[pluginId]?.coreValues.orEmpty(),
                extensionValues = extensionValues,
            )
            configSnapshotsByPluginId[pluginId] = updatedSnapshot
            return updatedSnapshot
        }

        override fun resolvePluginWorkspaceSnapshot(pluginId: String): PluginHostWorkspaceSnapshot {
            return workspaceSnapshotsByPluginId[pluginId] ?: PluginHostWorkspaceSnapshot()
        }

        override suspend fun importPluginWorkspaceFile(
            pluginId: String,
            uri: String,
        ): PluginHostWorkspaceSnapshot {
            workspaceImportUris += uri
            val updated = nextImportedWorkspaceSnapshot ?: workspaceSnapshotsByPluginId[pluginId]
                ?: PluginHostWorkspaceSnapshot()
            workspaceSnapshotsByPluginId[pluginId] = updated
            return updated
        }

        override fun deletePluginWorkspaceFile(
            pluginId: String,
            relativePath: String,
        ): PluginHostWorkspaceSnapshot {
            deletedWorkspaceFiles += relativePath
            val current = workspaceSnapshotsByPluginId[pluginId] ?: PluginHostWorkspaceSnapshot()
            val updated = current.copy(
                files = current.files.filterNot { it.relativePath == relativePath },
            )
            workspaceSnapshotsByPluginId[pluginId] = updated
            return updated
        }

        override fun clearPluginFailureState(pluginId: String): PluginInstallRecord {
            clearedFailurePluginIds += pluginId
            val updated = requireNotNull(recordsFlow.value.firstOrNull { it.pluginId == pluginId }).withOverrides(
                failureState = PluginFailureState.none(),
                lastUpdatedAt = 88L,
            )
            recordsFlow.value = recordsFlow.value.map { record ->
                if (record.pluginId == pluginId) updated else record
            }
            return updated
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

        override suspend fun handleInstallIntent(
            intent: PluginInstallIntent,
            onDownloadProgress: (PluginDownloadProgress) -> Unit,
        ): PluginInstallIntentResult {
            handledInstallIntents += intent
            nextInstallProgress?.let(onDownloadProgress)
            installIntentGate?.await()
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

        override suspend fun ensureOfficialMarketCatalogSubscribed(): PluginCatalogSyncState {
            officialCatalogBootstrapRequests += 1
            officialCatalogBootstrapError?.let { throw it }
            if (repositorySourcesFlow.value.isEmpty()) {
                repositorySourcesFlow.value = listOf(
                    com.astrbot.android.model.plugin.PluginRepositorySource(
                        sourceId = "official-market",
                        title = "AstrBot Android Plugin Market",
                        catalogUrl = "https://raw.githubusercontent.com/undertaker33/astrbot-android-plugin-market/main/catalog.json",
                        updatedAt = 1_700_000_000_000L,
                    ),
                )
            }
            return PluginCatalogSyncState(
                sourceId = repositorySourcesFlow.value.first().sourceId,
                lastSyncAtEpochMillis = 1L,
                lastSyncStatus = PluginCatalogSyncStatus.SUCCESS,
            )
        }

        override suspend fun refreshMarketCatalog(): List<PluginCatalogSyncState> {
            marketRefreshRequests += 1
            marketRefreshError?.let { throw it }
            if (repositorySourcesFlow.value.isEmpty()) {
                return listOf(ensureOfficialMarketCatalogSubscribed())
            }
            return repositorySourcesFlow.value.map { source ->
                PluginCatalogSyncState(
                    sourceId = source.sourceId,
                    lastSyncAtEpochMillis = 1L,
                    lastSyncStatus = PluginCatalogSyncStatus.SUCCESS,
                )
            }
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
        extractedDir: String = "",
        failureState: PluginFailureState = PluginFailureState.none(),
        uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
        lastUpdatedAt: Long = 1L,
        permissions: List<PluginPermissionDeclaration> = listOf(
            PluginPermissionDeclaration(
                permissionId = "$pluginId.permission",
                title = "Permission",
                description = "Permission for $pluginId",
            ),
        ),
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
                permissions = permissions,
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
            extractedDir = extractedDir,
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
        repositoryUrl: String = "",
        catalogUrl: String = "https://repo.example.com/catalog.json",
        versions: List<PluginCatalogVersion> = listOf(
            catalogVersion(version = "1.0.0"),
        ),
    ): PluginCatalogEntryRecord {
        return PluginCatalogEntryRecord(
            sourceId = sourceId,
            sourceTitle = "AstrBot Repo",
            catalogUrl = catalogUrl,
            entry = PluginCatalogEntry(
                pluginId = pluginId,
                title = pluginId,
                author = "AstrBot",
                repositoryUrl = repositoryUrl,
                description = "Description for $pluginId",
                entrySummary = "Summary for $pluginId",
                versions = versions,
            ),
        )
    }

    private fun catalogVersion(
        version: String,
        packageUrl: String = "https://repo.example.com/$version.zip",
        publishedAt: Long = 1_700_000_000_000L,
        changelog: String = "",
        minHostVersion: String = "1.0.0",
        maxHostVersion: String = "",
    ): PluginCatalogVersion {
        return PluginCatalogVersion(
            version = version,
            packageUrl = packageUrl,
            publishedAt = publishedAt,
            protocolVersion = 1,
            minHostVersion = minHostVersion,
            maxHostVersion = maxHostVersion,
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

    private fun pluginViewModelMethod(name: String, vararg parameterTypes: Class<*>): Method {
        return PluginViewModel::class.java.getMethod(name, *parameterTypes)
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

@Suppress("UNCHECKED_CAST")
private fun <T> propertyValue(target: Any?, propertyName: String): T {
    val getter = target!!.javaClass.getMethod("get${propertyName.replaceFirstChar(Char::titlecase)}")
    return getter.invoke(target) as T
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

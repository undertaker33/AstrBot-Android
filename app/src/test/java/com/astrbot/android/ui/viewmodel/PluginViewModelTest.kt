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
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSelectOption
import com.astrbot.android.model.plugin.PluginSettingsSchema
import com.astrbot.android.model.plugin.PluginSettingsSection
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginTriggerSource
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
    fun init_selects_first_plugin_and_computes_summary_metrics() = runTest(dispatcher) {
        val deps = FakePluginDependencies(
            listOf(
                pluginRecord(
                    pluginId = "plugin-1",
                    riskLevel = PluginRiskLevel.HIGH,
                    compatibilityState = PluginCompatibilityState.evaluated(
                        protocolSupported = true,
                        minHostVersionSatisfied = false,
                        maxHostVersionSatisfied = true,
                    ),
                ),
                pluginRecord(pluginId = "plugin-2"),
            ),
        )

        val viewModel = PluginViewModel(deps)
        advanceUntilIdle()

        assertEquals("plugin-1", viewModel.uiState.value.selectedPluginId)
        assertEquals("plugin-1", viewModel.uiState.value.selectedPlugin?.pluginId)
        assertFalse(viewModel.uiState.value.isShowingDetail)
        assertEquals(2, viewModel.uiState.value.summaryMetrics.totalInstalled)
        assertEquals(1, viewModel.uiState.value.summaryMetrics.highRisk)
        assertEquals(1, viewModel.uiState.value.summaryMetrics.incompatible)
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
    ) : PluginViewModelDependencies {
        private val recordsFlow = MutableStateFlow(records)
        val enableRequests = mutableListOf<Pair<String, Boolean>>()
        var lastUpdatedPolicy: PluginUninstallPolicy? = null
        var lastUninstallPolicy: PluginUninstallPolicy? = null

        override val records: StateFlow<List<PluginInstallRecord>> = recordsFlow

        fun updateRecords(records: List<PluginInstallRecord>) {
            recordsFlow.value = records
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
    }

    private fun pluginRecord(
        pluginId: String,
        riskLevel: PluginRiskLevel = PluginRiskLevel.LOW,
        enabled: Boolean = false,
        failureState: PluginFailureState = PluginFailureState.none(),
        uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
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
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "Entry",
                riskLevel = riskLevel,
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/tmp/$pluginId.zip",
                importedAt = 1L,
            ),
            compatibilityState = compatibilityState,
            failureState = failureState,
            uninstallPolicy = uninstallPolicy,
            enabled = enabled,
            lastUpdatedAt = 1L,
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
    enabled: Boolean = this.enabled,
    failureState: PluginFailureState = this.failureState,
    uninstallPolicy: PluginUninstallPolicy = this.uninstallPolicy,
    lastUpdatedAt: Long = this.lastUpdatedAt,
): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifestSnapshot,
        source = source,
        permissionSnapshot = permissionSnapshot,
        compatibilityState = compatibilityState,
        failureState = failureState,
        uninstallPolicy = uninstallPolicy,
        enabled = enabled,
        installedAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = localPackagePath,
        extractedDir = extractedDir,
    )
}

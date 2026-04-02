package com.astrbot.android.ui.viewmodel

import com.astrbot.android.R
import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.data.PluginUninstallResult
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy
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
                uninstallPolicy = uninstallPolicy,
                enabled = enabled,
                lastUpdatedAt = 1L,
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
    uninstallPolicy: PluginUninstallPolicy = this.uninstallPolicy,
    lastUpdatedAt: Long = this.lastUpdatedAt,
): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifestSnapshot,
        source = source,
        permissionSnapshot = permissionSnapshot,
        compatibilityState = compatibilityState,
        uninstallPolicy = uninstallPolicy,
        enabled = enabled,
        installedAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = localPackagePath,
        extractedDir = extractedDir,
    )
}

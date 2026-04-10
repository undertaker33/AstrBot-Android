package com.astrbot.android.ui.screen

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginTriggerManagementPresentationTest {

    @Test
    fun build_trigger_management_state_marks_legacy_v1_plugin_as_unsupported() {
        val state = buildPluginTriggerManagementState(
            record = pluginRecord(
                pluginId = "plugin.legacy",
                protocolVersion = 1,
                packageContractSnapshot = null,
                compatibilityState = PluginCompatibilityState.fromChecks(
                    protocolSupported = false,
                    minHostVersionSatisfied = true,
                    maxHostVersionSatisfied = true,
                    notes = "Protocol version 1 is not supported.",
                ),
            ),
        )

        assertEquals(PluginTriggerManagementStatus.Unsupported, state.status)
        assertEquals("Protocol version 1 is not supported.", state.statusDetail)
        assertEquals("-", state.runtimeLabel)
        assertEquals("-", state.contractVersion)
        assertEquals("-", state.entryPath)
        assertTrue(state.openTriggers.isEmpty())
        assertTrue(state.closedTriggers.isEmpty())
        assertTrue(!state.canManualOpen)
    }

    @Test
    fun build_trigger_management_state_marks_v2_plugin_as_not_ready_until_runtime_chain_exists() {
        val state = buildPluginTriggerManagementState(
            record = pluginRecord(
                pluginId = "plugin.v2",
                protocolVersion = 2,
                packageContractSnapshot = PluginPackageContractSnapshot(
                    protocolVersion = 2,
                    runtime = PluginRuntimeDeclarationSnapshot(
                        kind = "js_quickjs",
                        bootstrap = "runtime/index.js",
                        apiVersion = 1,
                    ),
                ),
                compatibilityState = PluginCompatibilityState.evaluated(
                    protocolSupported = true,
                    minHostVersionSatisfied = true,
                    maxHostVersionSatisfied = true,
                ),
            ),
        )

        assertEquals(PluginTriggerManagementStatus.NotReady, state.status)
        assertEquals("JavaScript (QuickJS)", state.runtimeLabel)
        assertEquals("2", state.contractVersion)
        assertEquals("runtime/index.js", state.entryPath)
        assertEquals("This legacy trigger page is not ready for protocol v2 runtime execution in phase 1.", state.statusDetail)
        assertTrue(state.openTriggers.isEmpty())
        assertTrue(state.closedTriggers.isEmpty())
    }

    @Test
    fun build_trigger_management_state_marks_missing_contract_snapshot_as_invalid_without_quickjs_runtime_label() {
        val state = buildPluginTriggerManagementState(
            record = pluginRecord(
                pluginId = "plugin.missing.contract",
                protocolVersion = 2,
                packageContractSnapshot = null,
                compatibilityState = PluginCompatibilityState.evaluated(
                    protocolSupported = true,
                    minHostVersionSatisfied = true,
                    maxHostVersionSatisfied = true,
                ),
            ),
        )

        assertEquals(PluginTriggerManagementStatus.InvalidContract, state.status)
        assertEquals("-", state.runtimeLabel)
        assertEquals("-", state.contractVersion)
        assertEquals("-", state.entryPath)
        assertEquals(
            "This plugin is missing a valid protocol v2 package contract snapshot.",
            state.statusDetail,
        )
    }

    @Test
    fun build_trigger_management_state_marks_blank_runtime_kind_as_invalid_contract() {
        val state = buildPluginTriggerManagementState(
            record = pluginRecord(
                pluginId = "plugin.blank.kind",
                protocolVersion = 2,
                packageContractSnapshot = PluginPackageContractSnapshot(
                    protocolVersion = 2,
                    runtime = PluginRuntimeDeclarationSnapshot(
                        kind = "",
                        bootstrap = "runtime/index.js",
                        apiVersion = 1,
                    ),
                ),
                compatibilityState = PluginCompatibilityState.evaluated(
                    protocolSupported = true,
                    minHostVersionSatisfied = true,
                    maxHostVersionSatisfied = true,
                ),
            ),
        )

        assertEquals(PluginTriggerManagementStatus.InvalidContract, state.status)
        assertEquals("-", state.runtimeLabel)
        assertEquals("-", state.contractVersion)
        assertEquals("-", state.entryPath)
    }

    @Test
    fun build_trigger_management_state_marks_blank_runtime_bootstrap_as_invalid_contract() {
        val state = buildPluginTriggerManagementState(
            record = pluginRecord(
                pluginId = "plugin.blank.bootstrap",
                protocolVersion = 2,
                packageContractSnapshot = PluginPackageContractSnapshot(
                    protocolVersion = 2,
                    runtime = PluginRuntimeDeclarationSnapshot(
                        kind = "js_quickjs",
                        bootstrap = "",
                        apiVersion = 1,
                    ),
                ),
                compatibilityState = PluginCompatibilityState.evaluated(
                    protocolSupported = true,
                    minHostVersionSatisfied = true,
                    maxHostVersionSatisfied = true,
                ),
            ),
        )

        assertEquals(PluginTriggerManagementStatus.InvalidContract, state.status)
        assertEquals("-", state.runtimeLabel)
        assertEquals("-", state.contractVersion)
        assertEquals("-", state.entryPath)
    }
}

private fun pluginRecord(
    pluginId: String,
    protocolVersion: Int,
    packageContractSnapshot: PluginPackageContractSnapshot?,
    compatibilityState: PluginCompatibilityState,
): PluginInstallRecord {
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = PluginManifest(
            pluginId = pluginId,
            version = "1.0.0",
            protocolVersion = protocolVersion,
            author = "AstrBot",
            title = "Demo",
            description = "Demo plugin",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = "send_message",
                    title = "Send message",
                    description = "Send messages to chat",
                ),
            ),
            minHostVersion = "1.0.0",
            maxHostVersion = "2.0.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Entry summary",
            riskLevel = PluginRiskLevel.MEDIUM,
        ),
        source = PluginSource(
            sourceType = PluginSourceType.LOCAL_FILE,
            location = "C:/plugins/demo.zip",
            importedAt = 1L,
        ),
        packageContractSnapshot = packageContractSnapshot,
        compatibilityState = compatibilityState,
        extractedDir = "C:/plugins/demo",
    )
}

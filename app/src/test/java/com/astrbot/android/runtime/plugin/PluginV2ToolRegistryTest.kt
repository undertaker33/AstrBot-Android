package com.astrbot.android.runtime.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ToolRegistryTest {
    @Test
    fun active_tool_names_stay_unique_across_plugins() {
        val registry = PluginV2ToolRegistry()
        val result = registry.compile(
            listOf(
                rawRegistryWithTool(
                    pluginId = "com.example.alpha",
                    toolName = "summarize",
                ),
                rawRegistryWithTool(
                    pluginId = "com.example.beta",
                    toolName = "lookup",
                ),
            ),
        )

        assertTrue(result.diagnostics.isEmpty())
        assertTrue(result.activeRegistry != null)
        assertEquals(
            listOf("summarize", "lookup"),
            result.activeRegistry!!.activeEntries.map { it.name },
        )
    }

    @Test
    fun same_plugin_duplicate_public_tool_name_is_a_compile_error() {
        val registry = PluginV2ToolRegistry()
        val rawRegistry = rawRegistryWithTool(
            pluginId = "com.example.alpha",
            toolName = "summarize",
            duplicateToolName = "summarize",
        )

        val result = registry.compile(listOf(rawRegistry))

        assertNull(result.activeRegistry)
        assertTrue(result.diagnostics.any { it.code == "duplicate_public_tool_name" })
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun different_plugins_cannot_register_the_same_public_tool_name() {
        val registry = PluginV2ToolRegistry()
        val result = registry.compile(
            listOf(
                rawRegistryWithTool(
                    pluginId = "com.example.alpha",
                    toolName = "shared",
                ),
                rawRegistryWithTool(
                    pluginId = "com.example.beta",
                    toolName = "shared",
                ),
            ),
        )

        assertNull(result.activeRegistry)
        assertTrue(result.diagnostics.any { it.code == "duplicate_public_tool_name" })
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun reserved_source_kind_is_rejected_before_it_can_enter_the_active_table() {
        val registry = PluginV2ToolRegistry()
        val result = registry.compileCandidates(
            listOf(
                PluginToolDescriptor(
                    pluginId = "com.example.alpha",
                    name = "future_mcp_tool",
                    sourceKind = PluginToolSourceKind.MCP,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
            ),
        )

        assertNull(result.activeRegistry)
        assertTrue(result.diagnostics.any { it.code == "tool_source_kind_reserved" })
        assertTrue(result.diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun reserved_source_kind_is_reported_even_when_public_name_duplicates_an_active_tool() {
        val registry = PluginV2ToolRegistry()
        val result = registry.compileCandidates(
            listOf(
                PluginToolDescriptor(
                    pluginId = "com.example.active",
                    name = "shared",
                    sourceKind = PluginToolSourceKind.PLUGIN_V2,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
                PluginToolDescriptor(
                    pluginId = "com.example.future",
                    name = "shared",
                    sourceKind = PluginToolSourceKind.MCP,
                    inputSchema = linkedMapOf("type" to "object"),
                ),
            ),
        )

        assertNull(result.activeRegistry)
        assertTrue(result.diagnostics.any { it.code == "tool_source_kind_reserved" })
    }

    @Test
    fun registry_and_gateway_share_the_same_phase5_source_kind_rule() {
        val registry = PluginV2ToolRegistry()
        val gateway = PluginV2ToolSourceGateway()

        PluginToolSourceKind.entries.forEach { sourceKind ->
            val toolName = "tool_${sourceKind.name.lowercase()}"
            val compileResult = registry.compileCandidates(
                listOf(
                    PluginToolDescriptor(
                        pluginId = "com.example.alpha",
                        name = toolName,
                        sourceKind = sourceKind,
                        inputSchema = linkedMapOf("type" to "object"),
                    ),
                ),
            )
            val gatewayResult = gateway.resolve(
                PluginV2ToolRegistryEntry(
                    pluginId = "com.example.alpha",
                    name = toolName,
                    toolId = "com.example.alpha:$toolName",
                    description = "",
                    visibility = PluginToolVisibility.LLM_VISIBLE,
                    sourceKind = sourceKind,
                    inputSchema = linkedMapOf("type" to "object"),
                    metadata = null,
                    sourceOrder = 0,
                ),
            )

            if (isPhase5ActiveToolSourceKind(sourceKind)) {
                assertTrue(compileResult.diagnostics.none { it.code == "tool_source_kind_reserved" })
                assertTrue(gatewayResult is PluginV2ToolSourceGatewayResult.ActiveEntry)
            } else {
                assertTrue(compileResult.diagnostics.any { it.code == "tool_source_kind_reserved" })
                assertTrue(gatewayResult is PluginV2ToolSourceGatewayResult.ReservedSourceUnavailable)
            }
        }
    }

    private fun rawRegistryWithTool(
        pluginId: String,
        toolName: String,
        duplicateToolName: String? = null,
    ): PluginV2RawRegistry {
        val session = bootstrappedSession(pluginId)
        val rawRegistry = PluginV2RawRegistry(pluginId)
        val handler = PluginV2CallbackHandle {}

        rawRegistry.appendTool(
            callbackToken = session.allocateCallbackToken(handler),
            descriptor = ToolRegistrationInput(
                registrationKey = "tool.$toolName",
                toolDescriptor = PluginV2ToolDescriptor(name = toolName),
                handler = handler,
            ),
        )

        duplicateToolName?.let { duplicateName ->
            rawRegistry.appendTool(
                callbackToken = session.allocateCallbackToken(handler),
                descriptor = ToolRegistrationInput(
                    registrationKey = "tool.$duplicateName.duplicate",
                    toolDescriptor = PluginV2ToolDescriptor(name = duplicateName),
                    handler = handler,
                ),
            )
        }

        return rawRegistry
    }

    private fun bootstrappedSession(pluginId: String): PluginV2RuntimeSession {
        return PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        ).also { session ->
            session.transitionTo(PluginV2RuntimeSessionState.Loading)
            session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }
    }
}

package com.elymbot.android.feature.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2AgentToolPolicyTest {

    @Test
    fun policy_allows_only_declared_and_available_tools() {
        val policy = PluginV2AgentToolPolicy()
        val snapshot = snapshotWithTools(
            pluginTool("com.example.agent.run", "own"),
            tool(PluginExecutionHostApi.HostBuiltinPluginId, "host.notify", PluginToolSourceKind.HOST_BUILTIN),
            pluginTool("other.plugin", "other"),
        )

        val allowed = policy.resolveAllowedTools(
            pluginId = "com.example.agent.run",
            declaredTools = listOf("own", "host.notify", "other", "missing"),
            snapshot = snapshot,
        )

        assertEquals(listOf("own", "host.notify"), allowed.allowed.map { it.name })
        assertEquals(
            mapOf(
                "other" to "agent_tool_not_owned",
                "missing" to "agent_tool_unavailable",
            ),
            allowed.rejections,
        )
    }

    @Test
    fun reserved_source_kind_cannot_be_spoofed_by_plugin_tool_name() {
        val policy = PluginV2AgentToolPolicy()
        val snapshot = snapshotWithTools(
            PluginV2ToolRegistryEntry(
                pluginId = "com.example.agent.run",
                name = "web.search",
                toolId = "com.example.agent.run:web.search",
                description = "spoof",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.PLUGIN_V2,
                inputSchema = linkedMapOf("type" to "object"),
                metadata = null,
                sourceOrder = 0,
            ),
        )

        val allowed = policy.resolveAllowedTools(
            pluginId = "com.example.agent.run",
            declaredTools = listOf("web.search"),
            snapshot = snapshot,
        )

        assertTrue(allowed.allowed.isEmpty())
        assertEquals("agent_reserved_source_spoof", allowed.rejections["web.search"])
    }

    @Test
    fun future_toolsource_must_be_available() {
        val policy = PluginV2AgentToolPolicy()
        val snapshot = snapshotWithTools(
            tool("web", "web.search", PluginToolSourceKind.WEB_SEARCH),
            availability = mapOf(
                "web.search" to PluginV2ToolAvailabilitySnapshot(
                    toolName = "web.search",
                    toolId = "web:web.search",
                    pluginId = "web",
                    sourceKind = PluginToolSourceKind.WEB_SEARCH,
                    registryActive = true,
                    personaEnabled = true,
                    capabilityAllowed = true,
                    sourceProviderAvailable = false,
                    available = false,
                    firstFailureReason = PluginV2ToolAvailabilityFailureReason.SourceUnavailable,
                ),
            ),
        )

        val allowed = policy.resolveAllowedTools(
            pluginId = "com.example.agent.run",
            declaredTools = listOf("web.search"),
            snapshot = snapshot,
        )

        assertFalse(allowed.allowed.any { it.name == "web.search" })
        assertEquals("agent_tool_unavailable", allowed.rejections["web.search"])
    }
}

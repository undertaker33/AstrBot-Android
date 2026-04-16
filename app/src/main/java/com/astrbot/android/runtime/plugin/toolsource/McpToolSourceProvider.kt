package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolResult
import com.astrbot.android.runtime.plugin.PluginToolResultStatus
import com.astrbot.android.runtime.plugin.PluginToolSourceKind
import com.astrbot.android.runtime.plugin.PluginToolVisibility

/**
 * MCP (Model Context Protocol) tool source provider.
 *
 * Reads per-config MCP server entries and converts their advertised tools into
 * [ToolSourceDescriptorBinding]s that feed into the centralized registry.
 *
 * On Android, only HTTP-based MCP transports (SSE / streamable_http) are supported.
 * stdio-based MCP is not feasible without a local subprocess runtime.
 */
class McpToolSourceProvider : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.MCP

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        val activeServers = configProfile.mcpServers.filter { it.active }
        return activeServers.flatMap { server -> buildBindingsForServer(server) }
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        val server = configProfile.mcpServers.firstOrNull { "mcp.${it.serverId}" == identity.ownerId }
        return if (server != null && server.active) {
            ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = true,
            )
        } else {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = true,
                detailCode = "mcp_server_inactive",
                detailMessage = "MCP server is not configured or inactive.",
            )
        }
    }

    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        // TODO: Phase 6 — implement actual MCP HTTP/SSE client invocation.
        // For now, return an error indicating the MCP runtime is not yet connected.
        return ToolSourceInvokeResult(
            result = PluginToolResult(
                toolCallId = request.args.toolCallId,
                requestId = request.args.requestId,
                toolId = request.args.toolId,
                status = PluginToolResultStatus.ERROR,
                errorCode = "mcp_not_connected",
                text = "MCP invocation is not yet implemented on Android.",
            ),
        )
    }

    private fun buildBindingsForServer(server: McpServerEntry): List<ToolSourceDescriptorBinding> {
        // MCP tool list comes from server discovery at connect time.
        // Until the MCP client runtime is connected, produce a placeholder descriptor
        // that lets the tool appear in the registry (compile-time visible, invoke-time stub).
        val ownerId = "mcp.${server.serverId}"
        val identity = ToolSourceIdentity(
            sourceKind = PluginToolSourceKind.MCP,
            ownerId = ownerId,
            sourceRef = server.url.ifBlank { server.command },
            displayName = server.name.ifBlank { server.serverId },
        )
        val descriptor = PluginToolDescriptor(
            pluginId = ownerId,
            name = "${server.serverId}_proxy",
            description = "MCP proxy tool for server: ${server.name.ifBlank { server.serverId }}",
            visibility = PluginToolVisibility.LLM_VISIBLE,
            sourceKind = PluginToolSourceKind.MCP,
            inputSchema = mapOf("type" to "object" as Any),
        )
        return listOf(
            ToolSourceDescriptorBinding(
                identity = identity,
                descriptor = descriptor,
            ),
        )
    }
}

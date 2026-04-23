package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.core.common.logging.AppLogger
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility
import com.astrbot.android.feature.plugin.runtime.mcp.McpSessionManager
import javax.inject.Inject

/**
 * MCP (Model Context Protocol) tool source provider.
 *
 * Reads per-config MCP server entries and converts their advertised tools into
 * [ToolSourceDescriptorBinding]s that feed into the centralized registry.
 *
 * On Android, only remote streamable_http MCP transports are supported.
 * stdio-based MCP is not feasible without a local subprocess runtime.
 */
class McpToolSourceProvider @Inject constructor(
    override val contextResolver: FutureToolSourceContextResolver,
) : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.MCP

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        val activeServers = context.toolSourceContext.mcpServers.filter { it.active }
        return activeServers.flatMap { server ->
            if (!isStreamableHttp(server.transport)) {
                AppLogger.append(
                    "MCP tools discovery skipped: server=${server.serverId} unsupported transport=${server.transport}",
                )
                return@flatMap emptyList()
            }
            runCatching {
                val tools = sessionManager.discoverTools(context.configProfileId, server)
                buildBindingsForServer(server, tools)
            }.onFailure { error ->
                AppLogger.append(
                    "MCP tools discovery failed: server=${server.serverId} reason=${error.message ?: error.javaClass.simpleName}",
                )
            }.getOrDefault(emptyList())
        }
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val server = context.toolSourceContext.mcpServers.firstOrNull { "mcp.${it.serverId}" == identity.ownerId }
        return if (server == null || !server.active) {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = true,
                detailCode = "mcp_server_inactive",
                detailMessage = "MCP server is not configured or inactive.",
            )
        } else if (!isStreamableHttp(server.transport)) {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = false,
                detailCode = "mcp_transport_unsupported",
                detailMessage = "Only streamable_http MCP transport is supported.",
            )
        } else {
            runCatching {
                sessionManager.discoverTools(context.configProfileId, server)
            }.fold(
                onSuccess = {
                    ToolSourceAvailability(
                        providerReachable = true,
                        permissionGranted = true,
                        capabilityAllowed = true,
                    )
                },
                onFailure = { error ->
                    ToolSourceAvailability(
                        providerReachable = false,
                        permissionGranted = true,
                        capabilityAllowed = true,
                        detailCode = "mcp_server_unreachable",
                        detailMessage = error.message ?: "MCP tools discovery failed",
                    )
                },
            )
        }
    }

    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        val configProfileId = request.configProfileId?.takeIf { it.isNotBlank() }
            ?: request.toolSourceContext?.configProfileId?.takeIf { it.isNotBlank() }
            ?: extractConfigProfileId(request)
            ?: return ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "mcp_config_profile_missing",
                    text = "Missing configProfileId for MCP tool invocation.",
                ),
            )
        val ownerId = request.identity.ownerId
        val server = resolveServerByOwnerId(request.toolSourceContext, configProfileId, ownerId)
            ?: return ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "mcp_server_not_found",
                    text = "MCP server not found for ownerId=$ownerId",
                ),
            )

        val encodedToolName = request.args.toolId.substringAfter(":")
        val actualToolName = decodeToolName(server.serverId, encodedToolName)
        return runCatching {
            val output = sessionManager.callTool(
                configProfileId = configProfileId,
                server = server,
                toolName = actualToolName,
                arguments = request.args.payload,
            )
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = if (output.isError) PluginToolResultStatus.ERROR else PluginToolResultStatus.SUCCESS,
                    errorCode = if (output.isError) "mcp_tool_error" else null,
                    text = output.text,
                    structuredContent = output.structuredContent,
                ),
            )
        }.getOrElse { error ->
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "mcp_invoke_failed",
                    text = error.message ?: error.javaClass.simpleName,
                ),
            )
        }
    }

    private fun buildBindingsForServer(
        server: McpServerEntry,
        tools: List<com.astrbot.android.feature.plugin.runtime.mcp.McpDiscoveredTool>,
    ): List<ToolSourceDescriptorBinding> {
        val ownerId = "mcp.${server.serverId}"
        return tools.map { tool ->
            val encodedToolName = encodeToolName(server.serverId, tool.name)
            ToolSourceDescriptorBinding(
                identity = ToolSourceIdentity(
                    sourceKind = PluginToolSourceKind.MCP,
                    ownerId = ownerId,
                    sourceRef = tool.name,
                    displayName = "${server.name.ifBlank { server.serverId }} / ${tool.name}",
                ),
                descriptor = PluginToolDescriptor(
                    pluginId = ownerId,
                    name = encodedToolName,
                    description = tool.description,
                    visibility = PluginToolVisibility.LLM_VISIBLE,
                    sourceKind = PluginToolSourceKind.MCP,
                    inputSchema = if (tool.inputSchema.isEmpty()) {
                        mapOf("type" to "object" as Any)
                    } else {
                        tool.inputSchema
                    },
                ),
            )
        }
    }

    private fun resolveServerByOwnerId(
        toolSourceContext: ToolSourceContext?,
        configProfileId: String?,
        ownerId: String,
    ): McpServerEntry? {
        val resolvedConfigId = configProfileId?.takeIf { it.isNotBlank() } ?: return null
        val context = toolSourceContext ?: contextResolver.resolveForConfig(resolvedConfigId)
        return context.mcpServers.firstOrNull { server -> "mcp.${server.serverId}" == ownerId && server.active }
    }

    private fun encodeToolName(serverId: String, toolName: String): String {
        return "mcp_${encodeSegment(serverId)}_${encodeSegment(toolName)}"
    }

    private fun decodeToolName(serverId: String, encodedToolName: String): String {
        val prefix = "mcp_${encodeSegment(serverId)}_"
        val encodedSegment = if (encodedToolName.startsWith(prefix)) {
            encodedToolName.removePrefix(prefix)
        } else {
            return encodedToolName
        }
        return decodeSegment(encodedSegment)
    }

    private fun encodeSegment(raw: String): String {
        return raw.toByteArray(Charsets.UTF_8).joinToString(separator = "") { byte ->
            "%02x".format(byte.toInt() and 0xff)
        }
    }

    private fun decodeSegment(encoded: String): String {
        require(encoded.length % 2 == 0) { "Invalid MCP encoded tool segment." }
        val bytes = ByteArray(encoded.length / 2)
        var cursor = 0
        while (cursor < encoded.length) {
            bytes[cursor / 2] = encoded.substring(cursor, cursor + 2).toInt(16).toByte()
            cursor += 2
        }
        return bytes.toString(Charsets.UTF_8)
    }

    private fun extractConfigProfileId(request: ToolSourceInvokeRequest): String? {
        val hostMetadata = request.args.metadata?.get("__host") as? Map<*, *> ?: return null
        val eventExtras = hostMetadata["eventExtras"] as? Map<*, *> ?: return null
        return (eventExtras["configProfileId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun isStreamableHttp(transport: String): Boolean {
        return transport.trim().lowercase().replace('-', '_').ifBlank { "streamable_http" } == "streamable_http"
    }

    private companion object {
        private val sessionManager = McpSessionManager()
    }
}

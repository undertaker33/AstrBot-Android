package com.astrbot.android.runtime.plugin

import com.astrbot.android.data.PluginRepository
import com.astrbot.android.data.plugin.PluginStoragePaths
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.plugin.ExternalPluginRuntimeKind
import com.astrbot.android.model.plugin.ExternalPluginWorkspacePolicy
import com.astrbot.android.model.plugin.PluginConfigStorageBoundary
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginConfigStorageJson
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginHostWorkspaceSnapshot
import com.astrbot.android.model.plugin.toStorageBoundary
import org.json.JSONArray
import org.json.JSONObject

data class PluginExecutionHostSnapshot(
    val runtimeKind: ExternalPluginRuntimeKind = ExternalPluginRuntimeKind.JsQuickJs,
    val bridgeMode: String = "compatibility_only",
    val workspaceSnapshot: PluginHostWorkspaceSnapshot = PluginHostWorkspaceSnapshot(),
    val configBoundary: PluginConfigStorageBoundary? = null,
    val configSnapshot: PluginConfigStoreSnapshot = PluginConfigStoreSnapshot(),
)

data class PluginExecutionHostToolHandlers(
    val sendMessageHandler: ((String) -> Unit)? = null,
    val sendNotificationHandler: ((String, String) -> Unit)? = null,
    val openHostPageHandler: ((String) -> Unit)? = null,
)

object PluginExecutionHostApi {
    const val WorkspaceApiKey = "host.workspace_api"
    const val ConfigBoundaryKey = "host.config_boundary"
    const val ConfigSnapshotKey = "host.config_snapshot"
    const val RuntimeKindKey = "host.runtime_kind"
    const val BridgeModeKey = "host.bridge_mode"
    const val ResultMergeModeKey = "host.result_merge_mode"
    const val HostBuiltinPluginId = "__host_builtin__"
    const val HostSendMessageToolName = "send_message"
    const val HostSendNotificationToolName = "send_notification"
    const val HostOpenHostPageToolName = "open_host_page"

    fun resolve(pluginId: String): PluginExecutionHostSnapshot {
        val workspaceSnapshot = runCatching {
            val appContext = PluginRepository.requireAppContext()
            ExternalPluginWorkspacePolicy.snapshot(
                storagePaths = PluginStoragePaths.fromFilesDir(appContext.filesDir),
                pluginId = pluginId,
            )
        }.getOrDefault(PluginHostWorkspaceSnapshot())
        val boundary = runCatching {
            PluginRepository.getInstalledStaticConfigSchema(pluginId)?.toStorageBoundary()
        }.getOrNull()
        val configSnapshot = boundary?.let { resolvedBoundary ->
            runCatching {
                PluginRepository.resolveConfigSnapshot(
                    pluginId = pluginId,
                    boundary = resolvedBoundary,
                )
            }.getOrDefault(PluginConfigStoreSnapshot())
        } ?: PluginConfigStoreSnapshot()
        return PluginExecutionHostSnapshot(
            workspaceSnapshot = workspaceSnapshot,
            configBoundary = boundary,
            configSnapshot = configSnapshot,
        )
    }

    fun inject(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext {
        val configExtras = linkedMapOf<String, String>().apply {
            putAll(context.config.extras)
            put(WorkspaceApiKey, encodeWorkspace(hostSnapshot.workspaceSnapshot))
            hostSnapshot.configBoundary?.let { boundary ->
                put(ConfigBoundaryKey, encodeBoundary(boundary))
            }
            if (
                hostSnapshot.configSnapshot.coreValues.isNotEmpty() ||
                hostSnapshot.configSnapshot.extensionValues.isNotEmpty()
            ) {
                put(ConfigSnapshotKey, encodeConfigSnapshot(hostSnapshot.configSnapshot))
            }
        }
        val triggerExtras = linkedMapOf<String, String>().apply {
            putAll(context.triggerMetadata.extras)
            put(RuntimeKindKey, hostSnapshot.runtimeKind.wireValue)
            put(BridgeModeKey, hostSnapshot.bridgeMode)
            put(ResultMergeModeKey, "ordered_chain_v1")
        }
        return context.copy(
            config = context.config.copy(extras = configExtras),
            triggerMetadata = context.triggerMetadata.copy(extras = triggerExtras),
        )
    }

    fun registeredHostToolDescriptors(
        handlers: PluginExecutionHostToolHandlers = PluginExecutionHostToolHandlers(),
    ): List<PluginToolDescriptor> {
        return hostBuiltinToolDescriptors().filter { descriptor ->
            when (descriptor.name) {
                HostSendMessageToolName -> handlers.sendMessageHandler != null
                HostSendNotificationToolName -> handlers.sendNotificationHandler != null
                HostOpenHostPageToolName -> handlers.openHostPageHandler != null
                else -> false
            }
        }
    }

    fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        handlers: PluginExecutionHostToolHandlers = PluginExecutionHostToolHandlers(),
        personaSnapshot: PersonaToolEnablementSnapshot? = null,
        capabilityGateway: PluginV2ToolCapabilityGateway = PluginV2ToolCapabilityGateway { true },
    ): PluginV2ActiveRuntimeSnapshot {
        val descriptors = registeredHostToolDescriptors(handlers)
        val toolState = compileCentralizedToolState(
            sessionsByPluginId = snapshot.toolSourceSessionsByPluginId(),
            additionalToolDescriptors = descriptors,
            personaSnapshot = personaSnapshot,
            capabilityGateway = capabilityGateway,
        )
        return snapshot.copy(
            toolRegistrySnapshot = toolState.activeRegistry,
            toolRegistryDiagnostics = toolState.diagnostics,
            toolAvailabilityByName = toolState.availabilityByName,
        )
    }

    fun executeHostBuiltinTool(
        args: PluginToolArgs,
        handlers: PluginExecutionHostToolHandlers = PluginExecutionHostToolHandlers(),
    ): PluginToolResult? {
        val descriptor = hostBuiltinToolDescriptors().firstOrNull { candidate ->
            candidate.toolId == args.toolId
        } ?: return null

        return runCatching {
            when (descriptor.name) {
                HostSendMessageToolName -> {
                    val handler = requireNotNull(handlers.sendMessageHandler) {
                        "Host builtin tool $HostSendMessageToolName is unavailable."
                    }
                    val text = requirePayloadValue(args.payload, "text")
                    handler(text)
                    successToolResult(args, text)
                }

                HostSendNotificationToolName -> {
                    val handler = requireNotNull(handlers.sendNotificationHandler) {
                        "Host builtin tool $HostSendNotificationToolName is unavailable."
                    }
                    val title = (args.payload["title"] as? String).orEmpty().trim().ifBlank { "ElymBot" }
                    val message = requirePayloadValue(
                        payload = args.payload,
                        primaryKey = "message",
                        fallbackKey = "text",
                    )
                    handler(title, message)
                    successToolResult(args, "$title: $message")
                }

                HostOpenHostPageToolName -> {
                    val handler = requireNotNull(handlers.openHostPageHandler) {
                        "Host builtin tool $HostOpenHostPageToolName is unavailable."
                    }
                    val route = requirePayloadValue(args.payload, "route")
                    handler(route)
                    successToolResult(args, route)
                }

                else -> errorToolResult(
                    args = args,
                    errorCode = "host_builtin_unknown_tool",
                    message = "Unknown host builtin tool: ${descriptor.name}",
                )
            }
        }.getOrElse { error ->
            val message = error.message ?: "Host builtin tool execution failed."
            errorToolResult(
                args = args,
                errorCode = when {
                    message.contains("payload", ignoreCase = true) -> "host_builtin_invalid_payload"
                    message.contains("unavailable", ignoreCase = true) -> "host_builtin_unavailable"
                    else -> "host_builtin_execution_failed"
                },
                message = message,
            )
        }
    }

    private fun encodeWorkspace(snapshot: PluginHostWorkspaceSnapshot): String {
        return JSONObject().apply {
            put("privateRootPath", snapshot.privateRootPath)
            put("importsPath", snapshot.importsPath)
            put("runtimePath", snapshot.runtimePath)
            put("exportsPath", snapshot.exportsPath)
            put("cachePath", snapshot.cachePath)
            put(
                "files",
                JSONArray().apply {
                    snapshot.files.forEach { file ->
                        put(
                            JSONObject().apply {
                                put("relativePath", file.relativePath)
                                put("sizeBytes", file.sizeBytes)
                                put("lastModifiedAtEpochMillis", file.lastModifiedAtEpochMillis)
                            },
                        )
                    }
                },
            )
        }.toString()
    }

    private fun encodeBoundary(boundary: PluginConfigStorageBoundary): String {
        return JSONObject().apply {
            put(
                "coreFieldKeys",
                JSONArray().apply {
                    boundary.coreFieldKeys.sorted().forEach(::put)
                },
            )
            put(
                "extensionFieldKeys",
                JSONArray().apply {
                    boundary.extensionFieldKeys.sorted().forEach(::put)
                },
            )
        }.toString()
    }

    private fun encodeConfigSnapshot(snapshot: PluginConfigStoreSnapshot): String {
        return JSONObject().apply {
            put("coreValues", JSONObject(PluginConfigStorageJson.encodeValues(snapshot.coreValues)))
            put("extensionValues", JSONObject(PluginConfigStorageJson.encodeValues(snapshot.extensionValues)))
        }.toString()
    }

    private fun hostBuiltinToolDescriptors(): List<PluginToolDescriptor> {
        return listOf(
            PluginToolDescriptor(
                pluginId = HostBuiltinPluginId,
                name = HostSendMessageToolName,
                description = "Send a host message through the current adapter.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.HOST_BUILTIN,
                inputSchema = linkedMapOf(
                    "type" to "object",
                    "properties" to linkedMapOf(
                        "text" to linkedMapOf("type" to "string"),
                    ),
                ),
            ),
            PluginToolDescriptor(
                pluginId = HostBuiltinPluginId,
                name = HostSendNotificationToolName,
                description = "Post a host notification.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.HOST_BUILTIN,
                inputSchema = linkedMapOf(
                    "type" to "object",
                    "properties" to linkedMapOf(
                        "title" to linkedMapOf("type" to "string"),
                        "message" to linkedMapOf("type" to "string"),
                        "text" to linkedMapOf("type" to "string"),
                    ),
                ),
            ),
            PluginToolDescriptor(
                pluginId = HostBuiltinPluginId,
                name = HostOpenHostPageToolName,
                description = "Open a host page route.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.HOST_BUILTIN,
                inputSchema = linkedMapOf(
                    "type" to "object",
                    "properties" to linkedMapOf(
                        "route" to linkedMapOf("type" to "string"),
                    ),
                ),
            ),
        )
    }

    private fun requirePayloadValue(
        payload: JsonLikeMap,
        primaryKey: String,
        fallbackKey: String? = null,
    ): String {
        val primaryValue = (payload[primaryKey] as? String).orEmpty().trim()
        if (primaryValue.isNotBlank()) {
            return primaryValue
        }
        val fallbackValue = fallbackKey
            ?.let { key -> (payload[key] as? String).orEmpty().trim() }
            .orEmpty()
        if (fallbackValue.isNotBlank()) {
            return fallbackValue
        }
        error("Host builtin tool requires payload.$primaryKey")
    }

    private fun successToolResult(
        args: PluginToolArgs,
        text: String,
    ): PluginToolResult {
        return PluginToolResult(
            toolCallId = args.toolCallId,
            requestId = args.requestId,
            toolId = args.toolId,
            status = PluginToolResultStatus.SUCCESS,
            text = text,
        )
    }

    private fun errorToolResult(
        args: PluginToolArgs,
        errorCode: String,
        message: String,
    ): PluginToolResult {
        return PluginToolResult(
            toolCallId = args.toolCallId,
            requestId = args.requestId,
            toolId = args.toolId,
            status = PluginToolResultStatus.ERROR,
            errorCode = errorCode,
            text = message,
        )
    }
}

private fun PluginV2ActiveRuntimeSnapshot.toolSourceSessionsByPluginId(): Map<String, PluginV2RuntimeSession> {
    return LinkedHashMap<String, PluginV2RuntimeSession>().apply {
        putAll(activeSessionsByPluginId)
        activeRuntimeEntriesByPluginId.forEach { (pluginId, entry) ->
            putIfAbsent(pluginId, entry.session)
        }
    }
}

package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.feature.plugin.runtime.toolsource.FutureToolSourceRegistry
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceIdentity
import com.elymbot.android.feature.plugin.runtime.toolsource.ToolSourceInvokeRequest
import com.elymbot.android.model.plugin.PluginRuntimeLogCategory
import com.elymbot.android.model.plugin.PluginRuntimeLogLevel
import com.elymbot.android.model.plugin.PluginRuntimeLogRecord
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class AgentRunLimits(
    val maxToolCalls: Int = DEFAULT_MAX_TOOL_CALLS,
    val maxDepth: Int = DEFAULT_MAX_DEPTH,
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    val maxTokens: Int = DEFAULT_MAX_TOKENS,
    val maxCostMicros: Long = DEFAULT_MAX_COST_MICROS,
) {
    init {
        require(maxToolCalls >= 0) { "maxToolCalls must not be negative." }
        require(maxDepth > 0) { "maxDepth must be greater than zero." }
        require(timeoutMs > 0L) { "timeoutMs must be greater than zero." }
        require(maxTokens > 0) { "maxTokens must be greater than zero." }
        require(maxCostMicros >= 0L) { "maxCostMicros must not be negative." }
    }

    companion object {
        const val DEFAULT_MAX_TOOL_CALLS: Int = 8
        const val DEFAULT_MAX_DEPTH: Int = 8
        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
        const val DEFAULT_MAX_TOKENS: Int = 32_768
        const val DEFAULT_MAX_COST_MICROS: Long = 5_000_000L
    }
}

data class AgentRunRequest(
    val context: PluginV2HostApiRequestContext,
    val agent: PluginV2CompiledAgentHandler,
    val input: String,
    val snapshot: PluginV2ActiveRuntimeSnapshot,
    val limits: AgentRunLimits = AgentRunLimits(),
)

data class AgentRunResult(
    val succeeded: Boolean,
    val text: String,
    val providerId: String,
    val modelId: String,
    val usage: PluginLlmUsageSnapshot?,
    val toolCallCount: Int,
    val durationMs: Long,
    val failureCode: String = "",
)

data class PluginV2AgentInvocationRequest(
    val context: PluginV2HostApiRequestContext,
    val pluginId: String,
    val agentKey: String,
    val input: String,
    val limits: AgentRunLimits = AgentRunLimits(),
)

interface PluginV2AgentCallbackHandle : PluginV2CallbackHandle {
    suspend fun handleAgent(event: PluginV2AgentInvocationEvent): Any?
}

interface PluginV2ToolCallbackHandle : PluginV2CallbackHandle {
    suspend fun handleTool(args: PluginToolArgs): Any?
}

class PluginV2AgentInvocationEvent internal constructor(
    val input: String,
    internal val request: PluginV2AgentInvocationRequest,
    internal val agentDescriptor: PluginV2CompiledAgentHandler,
    internal val snapshot: PluginV2ActiveRuntimeSnapshot,
    internal val runner: PluginV2AgentRunner,
) : PluginErrorEventPayload {
    val agent: PluginV2AgentContext = PluginV2AgentContext(this)
}

class PluginV2AgentContext internal constructor(
    private val event: PluginV2AgentInvocationEvent,
) {
    suspend fun run(input: String = event.input): AgentRunResult {
        return event.runner.run(
            AgentRunRequest(
                context = event.request.context,
                agent = event.agentDescriptor,
                input = input,
                snapshot = event.snapshot,
                limits = event.request.limits,
            ),
        )
    }
}

class PluginV2AgentInvoker(
    private val store: PluginV2ActiveRuntimeStore,
    private val agentRunner: PluginV2AgentRunner,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun invoke(request: PluginV2AgentInvocationRequest): PluginV2HostApiResult {
        val snapshot = store.snapshot()
        val session = snapshot.activeSessionsByPluginId[request.pluginId]
            ?: return failure(
                code = "agent_plugin_not_active",
                message = "Plugin runtime is not active.",
            )
        if (session.state != PluginV2RuntimeSessionState.Active) {
            return failure(
                code = "agent_plugin_not_active",
                message = "Plugin runtime is not active.",
            )
        }
        val registry = snapshot.compiledRegistriesByPluginId[request.pluginId]
            ?: return failure(
                code = "agent_registry_missing",
                message = "Plugin registry snapshot is missing.",
            )
        val agent = registry.handlerRegistry.agentHandlers.firstOrNull { handler ->
            handler.agentKey == request.agentKey.trim()
        } ?: return failure(
            code = "agent_handler_not_found",
            message = "Agent handler not found.",
            details = mapOf("agentKey" to request.agentKey),
        )
        val handle = session.requireCallbackHandle(agent.callbackToken)
        val event = PluginV2AgentInvocationEvent(
            input = request.input,
            request = request,
            agentDescriptor = agent,
            snapshot = snapshot,
            runner = agentRunner,
        )
        val value = when (handle) {
            is PluginV2AgentCallbackHandle -> handle.handleAgent(event)
            is PluginV2EventAwareCallbackHandle -> {
                handle.handleEvent(event)
                mapOf("ok" to true)
            }
            else -> {
                handle.invoke()
                mapOf("ok" to true)
            }
        }
        return PluginV2HostApiResult.Success(
            requestId = request.context.requestId,
            api = PluginV2AgentRunner.HOST_API_AGENT_RUN,
            value = value ?: mapOf("ok" to true),
        )
    }

    private fun failure(
        code: String,
        message: String,
        details: Map<String, String> = emptyMap(),
    ): PluginV2HostApiResult.Failure {
        return PluginV2HostApiResult.Failure(
            requestId = "",
            api = PluginV2AgentRunner.HOST_API_AGENT_RUN,
            error = PluginV2HostApiError(
                code = code,
                message = message,
                details = details + mapOf("occurredAtEpochMillis" to clock().toString()),
            ),
        )
    }
}

data class PluginV2AgentToolPolicyResult(
    val allowed: List<PluginV2ToolRegistryEntry>,
    val rejections: Map<String, String>,
)

class PluginV2AgentToolPolicy {
    fun resolveAllowedTools(
        pluginId: String,
        declaredTools: List<String>,
        snapshot: PluginV2ActiveRuntimeSnapshot,
    ): PluginV2AgentToolPolicyResult {
        val registry = snapshot.toolRegistrySnapshot
        val allowed = mutableListOf<PluginV2ToolRegistryEntry>()
        val rejections = linkedMapOf<String, String>()
        declaredTools.map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .forEach { toolName ->
                val entry = registry?.activeEntriesByName?.get(toolName)
                val availability = snapshot.toolAvailabilityByName[toolName]
                when {
                    entry == null || availability?.available != true -> {
                        rejections[toolName] = AGENT_TOOL_UNAVAILABLE
                    }

                    entry.sourceKind == PluginToolSourceKind.PLUGIN_V2 &&
                        entry.pluginId != pluginId -> {
                        rejections[toolName] = AGENT_TOOL_NOT_OWNED
                    }

                    entry.sourceKind == PluginToolSourceKind.PLUGIN_V2 &&
                        usesReservedSourceName(toolName) -> {
                        rejections[toolName] = AGENT_RESERVED_SOURCE_SPOOF
                    }

                    entry.sourceKind.reservedOnly && !availability.sourceProviderAvailable -> {
                        rejections[toolName] = AGENT_TOOL_UNAVAILABLE
                    }

                    else -> allowed += entry
                }
            }
        return PluginV2AgentToolPolicyResult(
            allowed = allowed.toList(),
            rejections = rejections.toMap(),
        )
    }

    fun requireAllowedTool(
        pluginId: String,
        toolName: String,
        snapshot: PluginV2ActiveRuntimeSnapshot,
    ): PluginV2ToolRegistryEntry? {
        return resolveAllowedTools(
            pluginId = pluginId,
            declaredTools = listOf(toolName),
            snapshot = snapshot,
        ).allowed.singleOrNull()
    }

    private fun usesReservedSourceName(toolName: String): Boolean {
        val normalized = toolName.trim().lowercase()
        return RESERVED_SOURCE_NAME_PREFIXES.any { prefix -> normalized.startsWith(prefix) }
    }

    companion object {
        const val AGENT_TOOL_UNAVAILABLE = "agent_tool_unavailable"
        const val AGENT_TOOL_NOT_OWNED = "agent_tool_not_owned"
        const val AGENT_RESERVED_SOURCE_SPOOF = "agent_reserved_source_spoof"

        private val RESERVED_SOURCE_NAME_PREFIXES = listOf(
            "mcp.",
            "skill.",
            "web.",
            "active.",
            "ctx.",
            "context.",
        )
    }
}

internal class PluginV2AgentRuntimeToolExecutor(
    private val store: PluginV2ActiveRuntimeStore,
    private val hostOperations: PluginExecutionHostOperations = DefaultPluginExecutionHostOperations(),
    private val futureToolSourceRegistry: FutureToolSourceRegistry? = null,
) : PluginV2ToolExecutor {
    override suspend fun execute(args: PluginToolArgs): PluginToolResult {
        val snapshot = store.snapshot()
        val entry = snapshot.toolRegistrySnapshot
            ?.activeEntriesByToolId
            ?.get(args.toolId)
            ?: return errorResult(
                args = args,
                errorCode = "agent_tool_descriptor_not_found",
                text = "Tool descriptor not found for toolId=${args.toolId}.",
            )
        return when (entry.sourceKind) {
            PluginToolSourceKind.PLUGIN_V2 -> executePluginTool(snapshot, entry, args)
            PluginToolSourceKind.HOST_BUILTIN -> hostOperations.executeHostBuiltinTool(args)
                ?: errorResult(
                    args = args,
                    errorCode = "agent_host_builtin_unavailable",
                    text = "Host builtin tool is unavailable.",
                )

            PluginToolSourceKind.MCP,
            PluginToolSourceKind.SKILL,
            PluginToolSourceKind.ACTIVE_CAPABILITY,
            PluginToolSourceKind.CONTEXT_STRATEGY,
            PluginToolSourceKind.WEB_SEARCH,
            -> executeFutureToolSource(entry, args)
        }
    }

    private suspend fun executePluginTool(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        entry: PluginV2ToolRegistryEntry,
        args: PluginToolArgs,
    ): PluginToolResult {
        val session = snapshot.activeSessionsByPluginId[entry.pluginId]
            ?: return errorResult(
                args = args,
                errorCode = "agent_tool_plugin_not_active",
                text = "Plugin tool owner is not active.",
            )
        val rawTool = session.rawRegistry?.tools?.firstOrNull { registration ->
            registration.descriptor.toolId == entry.toolId
        } ?: return errorResult(
            args = args,
            errorCode = "agent_tool_callback_not_found",
            text = "Plugin tool callback was not found.",
        )
        val callback = session.requireCallbackHandle(rawTool.callbackToken)
        val value = when (callback) {
            is PluginV2ToolCallbackHandle -> callback.handleTool(args)
            else -> {
                callback.invoke()
                null
            }
        }
        return value.toToolResult(args)
    }

    private suspend fun executeFutureToolSource(
        entry: PluginV2ToolRegistryEntry,
        args: PluginToolArgs,
    ): PluginToolResult {
        val registry = futureToolSourceRegistry
            ?: return errorResult(
                args = args,
                errorCode = "agent_future_toolsource_unavailable",
                text = "Future ToolSource registry is unavailable.",
            )
        val result = registry.invoke(
            ToolSourceInvokeRequest(
                identity = ToolSourceIdentity(
                    sourceKind = entry.sourceKind,
                    ownerId = entry.pluginId,
                    sourceRef = entry.name,
                    displayName = entry.name,
                ),
                args = args,
                timeoutMs = 60_000L,
                configProfileId = args.configProfileId(),
                toolSourceContext = null,
            ),
        )
        return result?.result ?: errorResult(
            args = args,
            errorCode = "agent_future_toolsource_unavailable",
            text = "Future ToolSource invocation returned no result.",
        )
    }

    private fun Any?.toToolResult(args: PluginToolArgs): PluginToolResult {
        return when (this) {
            is PluginToolResult -> this
            is Map<*, *> -> {
                val values = entries.associate { (key, value) -> key.toString() to value }
                val status = values["status"]?.toString()?.trim()?.lowercase().orEmpty()
                PluginToolResult(
                    toolCallId = values["toolCallId"]?.toString()?.takeIf(String::isNotBlank) ?: args.toolCallId,
                    requestId = values["requestId"]?.toString()?.takeIf(String::isNotBlank) ?: args.requestId,
                    toolId = values["toolId"]?.toString()?.takeIf(String::isNotBlank) ?: args.toolId,
                    status = if (status == "error" || status == "failed" || status == "failure") {
                        PluginToolResultStatus.ERROR
                    } else {
                        PluginToolResultStatus.SUCCESS
                    },
                    errorCode = values["errorCode"]?.toString()?.takeIf(String::isNotBlank),
                    text = values["text"]?.toString(),
                    structuredContent = (values["structuredContent"] as? Map<*, *>)?.jsonLikeMap(),
                    metadata = (values["metadata"] as? Map<*, *>)?.jsonLikeMap(),
                )
            }

            is String -> PluginToolResult(
                toolCallId = args.toolCallId,
                requestId = args.requestId,
                toolId = args.toolId,
                status = PluginToolResultStatus.SUCCESS,
                text = this,
            )

            null -> PluginToolResult(
                toolCallId = args.toolCallId,
                requestId = args.requestId,
                toolId = args.toolId,
                status = PluginToolResultStatus.SUCCESS,
                text = "",
            )

            else -> PluginToolResult(
                toolCallId = args.toolCallId,
                requestId = args.requestId,
                toolId = args.toolId,
                status = PluginToolResultStatus.SUCCESS,
                text = toString(),
            )
        }
    }

    private fun Map<*, *>.jsonLikeMap(): JsonLikeMap {
        return entries.associate { (key, value) -> key.toString() to value }
    }

    private fun PluginToolArgs.configProfileId(): String? {
        val host = metadata?.get("__host") as? Map<*, *> ?: return null
        return host["configProfileId"]?.toString()?.trim()?.takeIf(String::isNotBlank)
            ?: (host["eventExtras"] as? Map<*, *>)?.get("configProfileId")?.toString()?.trim()?.takeIf(String::isNotBlank)
    }

    private fun errorResult(
        args: PluginToolArgs,
        errorCode: String,
        text: String,
    ): PluginToolResult {
        return PluginToolResult(
            toolCallId = args.toolCallId,
            requestId = args.requestId,
            toolId = args.toolId,
            status = PluginToolResultStatus.ERROR,
            errorCode = errorCode,
            text = text,
        )
    }
}

class PluginV2AgentRunner internal constructor(
    private val llmPort: PluginV2HostLlmPort,
    private val toolExecutor: PluginV2ToolExecutor = PluginV2ToolExecutor { args ->
        PluginToolResult(
            toolCallId = args.toolCallId,
            requestId = args.requestId,
            toolId = args.toolId,
            status = PluginToolResultStatus.ERROR,
            errorCode = "tool_executor_unavailable",
            text = "Tool executor is unavailable.",
        )
    },
    private val toolPolicy: PluginV2AgentToolPolicy = PluginV2AgentToolPolicy(),
    private val logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
    private val clock: () -> Long = System::currentTimeMillis,
    private val toolCallIdFactory: () -> String = { "agent-tool-${System.currentTimeMillis()}-${System.nanoTime()}" },
) {
    suspend fun run(request: AgentRunRequest): AgentRunResult {
        val startedAt = clock()
        val permissionDecision = PluginV2HostApiPermissionPolicy().evaluate(
            context = request.context,
            api = HOST_API_AGENT_RUN,
            permissionId = PluginV2HostApiPermissions.AGENT_RUN,
        )
        if (!permissionDecision.allowed) {
            return resultFailure(
                request = request,
                startedAt = startedAt,
                failureCode = permissionDecision.error?.code ?: PluginV2HostApiErrorCodes.PERMISSION_DENIED,
                toolCallCount = 0,
            )
        }
        return try {
            withTimeout(request.limits.timeoutMs) {
                runInternal(request = request, startedAt = startedAt)
            }
        } catch (error: TimeoutCancellationException) {
            resultFailure(
                request = request,
                startedAt = startedAt,
                failureCode = AGENT_TIMEOUT,
                toolCallCount = 0,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            resultFailure(
                request = request,
                startedAt = startedAt,
                failureCode = AGENT_EXECUTION_FAILED,
                toolCallCount = 0,
            )
        }
    }

    private suspend fun runInternal(
        request: AgentRunRequest,
        startedAt: Long,
    ): AgentRunResult {
        val allowedToolResolution = toolPolicy.resolveAllowedTools(
            pluginId = request.context.pluginId,
            declaredTools = request.agent.tools,
            snapshot = request.snapshot,
        )
        if (allowedToolResolution.rejections.isNotEmpty()) {
            return resultFailure(
                request = request,
                startedAt = startedAt,
                failureCode = allowedToolResolution.rejections.values.first(),
                toolCallCount = 0,
            )
        }

        val messages = mutableListOf(
            PluginV2HostLlmMessage(role = "user", text = request.input.trim()),
        )
        if (messages.single().text.isBlank()) {
            return resultFailure(
                request = request,
                startedAt = startedAt,
                failureCode = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                toolCallCount = 0,
            )
        }

        var toolCallCount = 0
        var usage = PluginLlmUsageSnapshot()
        var lastProviderId = request.agent.model.providerId
        var lastModelId = request.agent.model.modelId
        repeat(request.limits.maxDepth) { depthIndex ->
            val llmResult = llmPort.generate(
                PluginV2HostLlmPortRequest(
                    pluginId = request.context.pluginId,
                    requestId = "${request.context.requestId}:agent:${request.agent.agentKey}:${depthIndex + 1}",
                    conversationId = request.context.conversationId,
                    providerId = request.agent.model.providerId,
                    modelId = request.agent.model.modelId,
                    messages = messages.toList(),
                    systemPrompt = request.agent.systemPrompt,
                    temperature = null,
                    topP = null,
                    maxTokens = request.limits.maxTokens,
                    tools = allowedToolResolution.allowed.map { entry ->
                        PluginProviderToolDefinition(
                            name = entry.name,
                            description = entry.description,
                            inputSchema = entry.inputSchema,
                        )
                    },
                    bypassPluginLlmHooks = true,
                ),
            )
            lastProviderId = llmResult.providerId
            lastModelId = llmResult.modelId
            usage = usage + llmResult.usage
            val guardFailure = evaluateUsageGuards(usage, request.limits)
            if (guardFailure != null) {
                return resultFailure(request, startedAt, guardFailure, toolCallCount, usage, lastProviderId, lastModelId)
            }
            if (llmResult.toolCalls.isEmpty()) {
                return resultSuccess(
                    request = request,
                    startedAt = startedAt,
                    text = llmResult.text,
                    providerId = lastProviderId,
                    modelId = lastModelId,
                    usage = usage,
                    toolCallCount = toolCallCount,
                )
            }
            messages += PluginV2HostLlmMessage(role = "assistant", text = llmResult.text.ifBlank { "Tool call requested." })
            for (toolCall in llmResult.toolCalls) {
                if (toolCallCount >= request.limits.maxToolCalls) {
                    return resultFailure(
                        request = request,
                        startedAt = startedAt,
                        failureCode = AGENT_MAX_TOOL_CALLS_EXCEEDED,
                        toolCallCount = toolCallCount,
                        usage = usage,
                        providerId = lastProviderId,
                        modelId = lastModelId,
                    )
                }
                val entry = toolPolicy.requireAllowedTool(
                    pluginId = request.context.pluginId,
                    toolName = toolCall.normalizedToolName,
                    snapshot = request.snapshot,
                ) ?: return resultFailure(
                    request = request,
                    startedAt = startedAt,
                    failureCode = PluginV2AgentToolPolicy.AGENT_TOOL_UNAVAILABLE,
                    toolCallCount = toolCallCount,
                    usage = usage,
                    providerId = lastProviderId,
                    modelId = lastModelId,
                )
                val toolCallId = toolCall.normalizedToolCallId ?: toolCallIdFactory()
                val toolResult = toolExecutor.execute(
                    PluginToolArgs(
                        toolCallId = toolCallId,
                        requestId = request.context.requestId,
                        toolId = entry.toolId,
                        attemptIndex = 0,
                        payload = toolCall.normalizedArguments,
                        metadata = linkedMapOf(
                            "__host" to linkedMapOf(
                                "conversationId" to request.context.conversationId,
                                "platformAdapterType" to request.context.platformAdapterType,
                                "providerId" to request.agent.model.providerId,
                                "modelId" to request.agent.model.modelId,
                                "agentKey" to request.agent.agentKey,
                                "triggerEventId" to request.context.triggerMetadata.eventId,
                                "eventExtras" to request.context.triggerMetadata.extras,
                            ),
                        ),
                    ),
                )
                toolCallCount++
                messages += PluginV2HostLlmMessage(
                    role = "tool",
                    text = toolResult.toAgentToolMessage(entry.name),
                )
            }
        }
        return resultFailure(
            request = request,
            startedAt = startedAt,
            failureCode = AGENT_MAX_DEPTH_EXCEEDED,
            toolCallCount = toolCallCount,
            usage = usage,
            providerId = lastProviderId,
            modelId = lastModelId,
        )
    }

    private fun evaluateUsageGuards(
        usage: PluginLlmUsageSnapshot,
        limits: AgentRunLimits,
    ): String? {
        if ((usage.totalTokens ?: 0) > limits.maxTokens) {
            return AGENT_MAX_TOKENS_EXCEEDED
        }
        val totalCost = (usage.inputCostMicros ?: 0L) + (usage.outputCostMicros ?: 0L)
        if (limits.maxCostMicros > 0L && totalCost > limits.maxCostMicros) {
            return AGENT_MAX_COST_EXCEEDED
        }
        return null
    }

    private fun PluginToolResult.toAgentToolMessage(toolName: String): String {
        return if (status == PluginToolResultStatus.SUCCESS) {
            text.orEmpty().ifBlank { structuredContent?.toString().orEmpty() }
        } else {
            "Tool $toolName failed with ${errorCode.orEmpty()}: ${text.orEmpty()}"
        }
    }

    private fun resultSuccess(
        request: AgentRunRequest,
        startedAt: Long,
        text: String,
        providerId: String,
        modelId: String,
        usage: PluginLlmUsageSnapshot,
        toolCallCount: Int,
    ): AgentRunResult {
        val result = AgentRunResult(
            succeeded = true,
            text = text,
            providerId = providerId,
            modelId = modelId,
            usage = usage,
            toolCallCount = toolCallCount,
            durationMs = durationSince(startedAt),
        )
        publishAgentAudit(request, result)
        return result
    }

    private fun resultFailure(
        request: AgentRunRequest,
        startedAt: Long,
        failureCode: String,
        toolCallCount: Int,
        usage: PluginLlmUsageSnapshot? = null,
        providerId: String = request.agent.model.providerId,
        modelId: String = request.agent.model.modelId,
    ): AgentRunResult {
        val result = AgentRunResult(
            succeeded = false,
            text = "",
            providerId = providerId,
            modelId = modelId,
            usage = usage,
            toolCallCount = toolCallCount,
            durationMs = durationSince(startedAt),
            failureCode = failureCode,
        )
        publishAgentAudit(request, result)
        return result
    }

    private fun publishAgentAudit(
        request: AgentRunRequest,
        result: AgentRunResult,
    ) {
        logBus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = clock(),
                pluginId = request.context.pluginId,
                pluginVersion = request.context.pluginVersion,
                category = PluginRuntimeLogCategory.HostAction,
                level = if (result.succeeded) PluginRuntimeLogLevel.Info else PluginRuntimeLogLevel.Warning,
                code = if (result.succeeded) "plugin_v2_agent_run_succeeded" else "plugin_v2_agent_run_failed",
                message = if (result.succeeded) "Plugin V2 agent run succeeded." else "Plugin V2 agent run failed.",
                succeeded = result.succeeded,
                durationMillis = result.durationMs,
                metadata = linkedMapOf(
                    "api" to HOST_API_AGENT_RUN,
                    "permissionId" to PluginV2HostApiPermissions.AGENT_RUN,
                    "agentKey" to request.agent.agentKey,
                    "toolCallCount" to result.toolCallCount.toString(),
                    "providerId" to result.providerId,
                    "modelId" to result.modelId,
                    "promptTokens" to result.usage?.promptTokens?.toString().orEmpty(),
                    "completionTokens" to result.usage?.completionTokens?.toString().orEmpty(),
                    "totalTokens" to result.usage?.totalTokens?.toString().orEmpty(),
                    "inputCostMicros" to result.usage?.inputCostMicros?.toString().orEmpty(),
                    "outputCostMicros" to result.usage?.outputCostMicros?.toString().orEmpty(),
                    "durationMs" to result.durationMs.toString(),
                    "failureCode" to result.failureCode,
                ).filterValues { it.isNotBlank() },
            ),
        )
    }

    private fun durationSince(startedAt: Long): Long = (clock() - startedAt).coerceAtLeast(0L)

    private operator fun PluginLlmUsageSnapshot.plus(other: PluginLlmUsageSnapshot?): PluginLlmUsageSnapshot {
        if (other == null) return this
        return PluginLlmUsageSnapshot(
            promptTokens = promptTokens.plusNullable(other.promptTokens),
            completionTokens = completionTokens.plusNullable(other.completionTokens),
            totalTokens = totalTokens.plusNullable(other.totalTokens),
            inputCostMicros = inputCostMicros.plusNullable(other.inputCostMicros),
            outputCostMicros = outputCostMicros.plusNullable(other.outputCostMicros),
            currencyCode = normalizedCurrencyCode ?: other.normalizedCurrencyCode,
        )
    }

    private fun Int?.plusNullable(other: Int?): Int? = when {
        this == null -> other
        other == null -> this
        else -> this + other
    }

    private fun Long?.plusNullable(other: Long?): Long? = when {
        this == null -> other
        other == null -> this
        else -> this + other
    }

    companion object {
        const val HOST_API_AGENT_RUN = "hostApi.agent.run"
        const val AGENT_TIMEOUT = "agent_timeout"
        const val AGENT_EXECUTION_FAILED = "agent_execution_failed"
        const val AGENT_MAX_TOOL_CALLS_EXCEEDED = "agent_max_tool_calls_exceeded"
        const val AGENT_MAX_DEPTH_EXCEEDED = "agent_max_depth_exceeded"
        const val AGENT_MAX_TOKENS_EXCEEDED = "agent_max_tokens_exceeded"
        const val AGENT_MAX_COST_EXCEEDED = "agent_max_cost_exceeded"
    }
}

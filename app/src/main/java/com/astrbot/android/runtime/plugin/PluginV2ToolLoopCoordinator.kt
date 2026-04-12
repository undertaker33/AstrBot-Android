package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginExecutionProtocolJson
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginV2ToolDiagnosticCodes
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException

internal fun interface PluginV2ToolExecutor {
    suspend fun execute(args: PluginToolArgs): PluginToolResult
}

internal data class PluginV2ToolLoopRunResult(
    val nextRequest: PluginProviderRequest,
    val executedToolNames: List<String>,
    val executedToolCallIds: List<String>,
)

internal class PluginV2ToolLoopCoordinator(
    private val dispatchEngine: PluginV2DispatchEngine = PluginV2DispatchEngineProvider.engine(),
    private val lifecycleManager: PluginV2LifecycleManager = PluginV2LifecycleManagerProvider.manager(),
    private val toolExecutor: PluginV2ToolExecutor = PluginV2ToolExecutor { args ->
        PluginToolResult(
            toolCallId = args.toolCallId,
            requestId = args.requestId,
            toolId = args.toolId,
            status = PluginToolResultStatus.ERROR,
            errorCode = "tool_executor_unavailable",
            text = "Tool executor is not wired yet.",
        )
    },
    private val toolCallIdFactory: () -> String = {
        "tool-call-${System.currentTimeMillis()}-${System.nanoTime()}"
    },
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun runToolLoop(
        event: PluginMessageEvent,
        baseRequest: PluginProviderRequest,
        toolCalls: List<PluginLlmToolCall>,
        snapshot: PluginV2ActiveRuntimeSnapshot,
    ): PluginV2ToolLoopRunResult {
        var nextRequest = baseRequest
        val executedToolNames = mutableListOf<String>()
        val executedToolCallIds = mutableListOf<String>()

        toolCalls.forEach { toolCall ->
            val toolName = toolCall.normalizedToolName
            val toolCallId = toolCallIdFactory()
            val descriptor = resolveToolDescriptorByName(
                toolName = toolName,
                snapshot = snapshot,
            )
            if (descriptor == null) {
                // Unknown tool call: no lifecycle payload can be emitted without a real descriptor.
                executedToolNames += toolName
                executedToolCallIds += toolCallId
                val errorResult = PluginToolResult(
                    toolCallId = toolCallId,
                    requestId = baseRequest.requestId,
                    toolId = "tool:$toolName",
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "tool_descriptor_not_found",
                    text = "Tool descriptor not found for toolName=$toolName",
                ).freeze()
                logBus.publishToolDiagnosticRecord(
                    pluginId = "__host__",
                    occurredAtEpochMillis = clock(),
                    level = PluginRuntimeLogLevel.Error,
                    code = PluginV2ToolDiagnosticCodes.LLM_TOOL_FAILED,
                    requestId = baseRequest.requestId,
                    stage = PluginV2InternalStage.ToolExecution.name,
                    outcome = "FAILED",
                    toolId = errorResult.toolId,
                    toolCallId = toolCallId,
                    metadata = mapOf(
                        "toolName" to toolName,
                        "errorCode" to errorResult.errorCode.orEmpty(),
                    ),
                )
                nextRequest = reinjectToolResult(
                    request = nextRequest,
                    descriptorName = toolName,
                    result = errorResult,
                )
                return@forEach
            }

            val initialArgs = PluginToolArgs(
                toolCallId = toolCallId,
                requestId = baseRequest.requestId,
                toolId = descriptor.toolId,
                attemptIndex = 0,
                payload = toolCall.normalizedArguments,
                metadata = null,
            ).freeze()
            publishToolDiagnostic(
                code = PluginV2ToolDiagnosticCodes.LLM_TOOL_SELECTED,
                requestId = baseRequest.requestId,
                stage = PluginV2InternalStage.LlmResponse,
                outcome = "SELECTED",
                descriptor = descriptor,
                toolCallId = toolCallId,
            )

            val usingPayload = PluginV2UsingLlmToolPayload(
                event = event,
                descriptor = descriptor,
                args = initialArgs,
            )

            var frozenArgs = initialArgs
            publishToolDiagnostic(
                code = PluginV2ToolDiagnosticCodes.TOOL_PRE_HOOK_EMITTED,
                requestId = initialArgs.requestId,
                stage = PluginV2InternalStage.UsingLlmTool,
                outcome = "EMITTED",
                descriptor = descriptor,
                toolCallId = toolCallId,
            )
            dispatchEngine.dispatchLlmStage(
                stage = PluginV2InternalStage.UsingLlmTool,
                payload = usingPayload,
                snapshot = snapshot,
                onHandlerCompleted = {
                    frozenArgs = sanitizeAndFreezeToolArgs(previousFrozen = frozenArgs, candidate = usingPayload.args)
                    usingPayload.replaceArgs(frozenArgs)
                },
            )
            frozenArgs = sanitizeAndFreezeToolArgs(previousFrozen = frozenArgs, candidate = usingPayload.args)
            usingPayload.replaceArgs(frozenArgs)

            dispatchEngine.dispatchLlmStage(
                stage = PluginV2InternalStage.ToolExecution,
                payload = PluginV2ToolExecutionPayload(
                    event = event,
                    descriptor = descriptor,
                    args = frozenArgs,
                ),
                snapshot = snapshot,
            )
            publishToolDiagnostic(
                code = PluginV2ToolDiagnosticCodes.LLM_TOOL_STARTED,
                requestId = frozenArgs.requestId,
                stage = PluginV2InternalStage.ToolExecution,
                outcome = "STARTED",
                descriptor = descriptor,
                toolCallId = frozenArgs.toolCallId,
            )

            val executionResult = runCatching {
                toolExecutor.execute(frozenArgs)
            }.getOrElse { error ->
                error.rethrowIfCancellation()
                PluginToolResult(
                    toolCallId = frozenArgs.toolCallId,
                    requestId = frozenArgs.requestId,
                    toolId = frozenArgs.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "tool_execution_failed",
                    text = error.message ?: error.javaClass.simpleName,
                )
            }

            val respondPayload = PluginV2LlmToolRespondPayload(
                event = event,
                descriptor = descriptor,
                args = frozenArgs,
                result = executionResult.freeze(),
            )

            var frozenResult = respondPayload.result
            publishToolDiagnostic(
                code = PluginV2ToolDiagnosticCodes.TOOL_POST_HOOK_EMITTED,
                requestId = frozenArgs.requestId,
                stage = PluginV2InternalStage.LlmToolRespond,
                outcome = "EMITTED",
                descriptor = descriptor,
                toolCallId = frozenArgs.toolCallId,
            )
            dispatchEngine.dispatchLlmStage(
                stage = PluginV2InternalStage.LlmToolRespond,
                payload = respondPayload,
                snapshot = snapshot,
                onHandlerCompleted = {
                    frozenResult = sanitizeAndFreezeToolResult(
                        previousFrozen = frozenResult,
                        candidate = respondPayload.result,
                    )
                    respondPayload.replaceResult(frozenResult)
                },
            )
            frozenResult = sanitizeAndFreezeToolResult(previousFrozen = frozenResult, candidate = respondPayload.result)
            respondPayload.replaceResult(frozenResult)
            publishToolDiagnostic(
                code = if (frozenResult.status == PluginToolResultStatus.SUCCESS) {
                    PluginV2ToolDiagnosticCodes.LLM_TOOL_COMPLETED
                } else {
                    PluginV2ToolDiagnosticCodes.LLM_TOOL_FAILED
                },
                requestId = frozenResult.requestId,
                stage = PluginV2InternalStage.LlmToolRespond,
                outcome = if (frozenResult.status == PluginToolResultStatus.SUCCESS) "COMPLETED" else "FAILED",
                descriptor = descriptor,
                toolCallId = frozenResult.toolCallId,
                level = if (frozenResult.status == PluginToolResultStatus.SUCCESS) {
                    PluginRuntimeLogLevel.Info
                } else {
                    PluginRuntimeLogLevel.Error
                },
            )

            executedToolNames += toolName
            executedToolCallIds += frozenArgs.toolCallId

            nextRequest = reinjectToolResult(
                request = nextRequest,
                descriptorName = descriptor.name,
                result = frozenResult,
            )
            publishToolDiagnostic(
                code = PluginV2ToolDiagnosticCodes.TOOL_CALL_LOOP_RESUMED,
                requestId = frozenResult.requestId,
                stage = PluginV2InternalStage.LlmRequest,
                outcome = "RESUMED",
                descriptor = descriptor,
                toolCallId = frozenResult.toolCallId,
            )
        }

        return PluginV2ToolLoopRunResult(
            nextRequest = nextRequest,
            executedToolNames = executedToolNames.toList(),
            executedToolCallIds = executedToolCallIds.toList(),
        )
    }

    private fun reinjectToolResult(
        request: PluginProviderRequest,
        descriptorName: String,
        result: PluginToolResult,
    ): PluginProviderRequest {
        val parts = buildList<PluginProviderMessagePartDto> {
            result.text?.takeIf { it.isNotEmpty() }?.let { text ->
                add(PluginProviderMessagePartDto.TextPart(text))
            }
            result.structuredContent?.takeIf { it.isNotEmpty() }?.let { structured ->
                add(PluginProviderMessagePartDto.TextPart(PluginExecutionProtocolJson.canonicalJson(structured)))
            }
            if (isEmpty()) {
                add(PluginProviderMessagePartDto.TextPart(""))
            }
        }

        val toolMessage = PluginProviderMessageDto(
            role = PluginProviderMessageRole.TOOL,
            name = descriptorName,
            parts = parts,
            metadata = linkedMapOf(
                "__host" to linkedMapOf(
                    "toolCallId" to result.toolCallId,
                ),
            ),
        )

        return PluginProviderRequest(
            requestId = request.requestId,
            availableProviderIds = request.availableProviderIds,
            availableModelIdsByProvider = request.availableModelIdsByProvider,
            conversationId = request.conversationId,
            messageIds = request.messageIds,
            llmInputSnapshot = request.llmInputSnapshot,
            selectedProviderId = request.selectedProviderId,
            selectedModelId = request.selectedModelId,
            systemPrompt = request.systemPrompt,
            messages = request.messages + toolMessage,
            temperature = request.temperature,
            topP = request.topP,
            maxTokens = request.maxTokens,
            streamingEnabled = request.streamingEnabled,
            metadata = request.metadata,
            allowHostToolMessages = true,
        )
    }

    private fun sanitizeAndFreezeToolArgs(
        previousFrozen: PluginToolArgs,
        candidate: PluginToolArgs,
    ): PluginToolArgs {
        require(candidate.requestId == previousFrozen.requestId) { "pre-call hook cannot change requestId." }
        require(candidate.toolCallId == previousFrozen.toolCallId) { "pre-call hook cannot change toolCallId." }
        require(candidate.toolId == previousFrozen.toolId) { "pre-call hook cannot change toolId." }
        require(candidate.attemptIndex == 0) { "attemptIndex must stay 0 in Phase 5 tool loop." }

        requireHostMetadataUnchanged(previousFrozen.metadata, candidate.metadata)

        return PluginToolArgs(
            toolCallId = previousFrozen.toolCallId,
            requestId = previousFrozen.requestId,
            toolId = previousFrozen.toolId,
            attemptIndex = 0,
            payload = freezeAllowedMap(candidate.payload),
            metadata = candidate.metadata?.let(::freezeAllowedMap),
        )
    }

    private fun requireHostMetadataUnchanged(
        previousMetadata: JsonLikeMap?,
        candidateMetadata: JsonLikeMap?,
    ) {
        val previousHasHostMetadata = previousMetadata?.containsKey("__host") == true
        val candidateHasHostMetadata = candidateMetadata?.containsKey("__host") == true
        require(candidateHasHostMetadata == previousHasHostMetadata) {
            "pre-call hook cannot change metadata.__host.*"
        }
        if (!previousHasHostMetadata) {
            return
        }
        val previousHostMetadata = previousMetadata?.get("__host")
        val candidateHostMetadata = candidateMetadata?.get("__host")
        require(previousHostMetadata is Map<*, *> && candidateHostMetadata is Map<*, *>) {
            "pre-call hook cannot change metadata.__host.*"
        }
        require(candidateHostMetadata == previousHostMetadata) {
            "pre-call hook cannot change metadata.__host.*"
        }
    }

    private fun sanitizeAndFreezeToolResult(
        previousFrozen: PluginToolResult,
        candidate: PluginToolResult,
    ): PluginToolResult {
        require(candidate.requestId == previousFrozen.requestId) { "post-call hook cannot change requestId." }
        require(candidate.toolCallId == previousFrozen.toolCallId) { "post-call hook cannot change toolCallId." }
        require(candidate.toolId == previousFrozen.toolId) { "post-call hook cannot change toolId." }
        require(candidate.status == previousFrozen.status) { "post-call hook cannot change status." }
        require(previousFrozen.status != PluginToolResultStatus.ERROR || candidate.status == PluginToolResultStatus.ERROR) {
            "post-call hook cannot turn a failure into success."
        }

        return PluginToolResult(
            toolCallId = previousFrozen.toolCallId,
            requestId = previousFrozen.requestId,
            toolId = previousFrozen.toolId,
            status = previousFrozen.status,
            errorCode = previousFrozen.errorCode,
            text = candidate.text,
            structuredContent = candidate.structuredContent?.let(::freezeAllowedMap),
            metadata = candidate.metadata?.let(::freezeAllowedMap),
        )
    }

    private fun freezeAllowedMap(values: JsonLikeMap): JsonLikeMap {
        return Collections.unmodifiableMap(LinkedHashMap(PluginV2ValueSanitizer.requireAllowedMap(values)))
    }

    private fun PluginToolArgs.freeze(): PluginToolArgs {
        return PluginToolArgs(
            toolCallId = toolCallId,
            requestId = requestId,
            toolId = toolId,
            attemptIndex = attemptIndex,
            payload = freezeAllowedMap(payload),
            metadata = metadata?.let(::freezeAllowedMap),
        )
    }

    private fun PluginToolResult.freeze(): PluginToolResult {
        return PluginToolResult(
            toolCallId = toolCallId,
            requestId = requestId,
            toolId = toolId,
            status = status,
            errorCode = errorCode,
            text = text,
            structuredContent = structuredContent?.let(::freezeAllowedMap),
            metadata = metadata?.let(::freezeAllowedMap),
        )
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private fun resolveToolDescriptorByName(
        toolName: String,
        snapshot: PluginV2ActiveRuntimeSnapshot,
    ): PluginToolDescriptor? {
        val normalized = toolName.trim()
        if (normalized.isBlank()) return null
        val availability = snapshot.toolAvailabilityByName[normalized]
            ?.takeIf(PluginV2ToolAvailabilitySnapshot::available)
            ?: return null
        val registryEntry = snapshot.toolRegistrySnapshot
            ?.activeEntriesByName
            ?.get(normalized)
            ?.takeIf { entry -> entry.toolId == availability.toolId }
            ?: return null
        return registryEntry.toDescriptor()
    }

    private fun publishToolDiagnostic(
        code: String,
        requestId: String,
        stage: PluginV2InternalStage,
        outcome: String,
        descriptor: PluginToolDescriptor,
        toolCallId: String,
        level: PluginRuntimeLogLevel = PluginRuntimeLogLevel.Info,
    ) {
        logBus.publishToolDiagnosticRecord(
            pluginId = descriptor.pluginId,
            occurredAtEpochMillis = clock(),
            level = level,
            code = code,
            requestId = requestId,
            stage = stage.name,
            outcome = outcome,
            toolId = descriptor.toolId,
            toolCallId = toolCallId,
            sourceKind = descriptor.sourceKind,
        )
    }

    private fun PluginV2ToolRegistryEntry.toDescriptor(): PluginToolDescriptor {
        return PluginToolDescriptor(
            pluginId = pluginId,
            name = name,
            description = description,
            visibility = visibility,
            sourceKind = sourceKind,
            inputSchema = inputSchema,
            metadata = metadata,
        )
    }
}

internal class PluginV2UsingLlmToolPayload(
    val event: PluginMessageEvent,
    val descriptor: PluginToolDescriptor,
    args: PluginToolArgs,
) : PluginErrorEventPayload {
    var args: PluginToolArgs = args
        private set

    fun replaceArgs(next: PluginToolArgs) {
        args = next
    }

    fun replaceArgsPayload(payload: JsonLikeMap) {
        replaceArgs(
            PluginToolArgs(
                toolCallId = args.toolCallId,
                requestId = args.requestId,
                toolId = args.toolId,
                attemptIndex = 0,
                payload = payload,
                metadata = args.metadata,
            ),
        )
    }
}

internal data class PluginV2ToolExecutionPayload(
    val event: PluginMessageEvent,
    val descriptor: PluginToolDescriptor,
    val args: PluginToolArgs,
) : PluginErrorEventPayload

internal class PluginV2LlmToolRespondPayload(
    val event: PluginMessageEvent,
    val descriptor: PluginToolDescriptor,
    val args: PluginToolArgs,
    result: PluginToolResult,
) : PluginErrorEventPayload {
    var result: PluginToolResult = result
        private set

    fun replaceResult(next: PluginToolResult) {
        result = next
    }
}

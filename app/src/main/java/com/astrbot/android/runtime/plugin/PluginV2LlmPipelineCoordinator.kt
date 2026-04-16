package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.AppChatLlm
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException

internal data class PluginV2LlmPipelineInput(
    val event: PluginMessageEvent,
    val messageIds: List<String>,
    val routingTarget: AppChatLlm = AppChatLlm.AppChat,
    val streamingMode: PluginV2StreamingMode,
    val availableProviderIds: List<String>,
    val availableModelIdsByProvider: Map<String, List<String>>,
    val selectedProviderId: String = "",
    val selectedModelId: String = "",
    val systemPrompt: String? = null,
    val messages: List<PluginProviderMessageDto> = emptyList(),
    val temperature: Double? = null,
    val topP: Double? = null,
    val maxTokens: Int? = null,
    val streamingEnabled: Boolean = streamingMode == PluginV2StreamingMode.NATIVE_STREAM,
    val metadata: JsonLikeMap? = null,
    val personaToolEnablementSnapshot: PersonaToolEnablementSnapshot? = null,
    val configProfileId: String? = null,
    val invokeProvider: suspend (
        request: PluginProviderRequest,
        streamingMode: PluginV2StreamingMode,
    ) -> PluginV2ProviderInvocationResult,
)

internal sealed interface PluginV2ProviderInvocationResult {
    data class NonStreaming(
        val response: PluginLlmResponse,
    ) : PluginV2ProviderInvocationResult

    data class Streaming(
        val events: List<PluginV2ProviderStreamChunk>,
    ) : PluginV2ProviderInvocationResult
}

internal data class PluginV2ProviderStreamChunk(
    val deltaText: String = "",
    val toolCallDeltas: List<PluginLlmToolCallDelta> = emptyList(),
    val isCompletion: Boolean = false,
    val finishReason: String? = null,
    val usage: PluginLlmUsageSnapshot? = null,
    val metadata: JsonLikeMap? = null,
)

internal data class PluginV2LlmWaitingPayload(
    val eventId: String,
    val platformAdapterType: String,
    val messageType: String,
    val conversationId: String,
    val senderId: String,
    val timestampEpochMillis: Long,
    val rawText: String,
    val workingText: String,
    val rawMentions: List<String>,
    val normalizedMentions: List<String>,
    val extras: Map<String, AllowedValue>,
) : PluginErrorEventPayload

internal class PluginV2LlmRequestPayload(
    val event: PluginMessageEvent,
    request: PluginProviderRequest,
) : PluginErrorEventPayload {
    var request: PluginProviderRequest = request
        private set

    fun replaceRequest(next: PluginProviderRequest) {
        request = next
    }
}

internal class PluginV2LlmResponsePayload(
    val event: PluginMessageEvent,
    response: PluginLlmResponse,
) : PluginErrorEventPayload {
    var response: PluginLlmResponse = response
        private set

    fun replaceResponse(next: PluginLlmResponse) {
        response = next
    }
}

internal data class PluginV2LlmResultDecoratingPayload(
    val event: PluginMessageEvent,
    val result: PluginMessageEventResult,
) : PluginErrorEventPayload

internal data class PluginV2LlmAfterSentPayload(
    val event: PluginMessageEvent,
    val view: PluginV2AfterSentView,
    val followupSender: PluginV2FollowupSender? = null,
) : PluginErrorEventPayload

internal data class PluginV2LlmPipelineResult(
    val admission: LlmPipelineAdmission,
    val finalRequest: PluginProviderRequest,
    val finalResponse: PluginLlmResponse,
    val sendableResult: PluginMessageEventResult,
    val hookInvocationTrace: List<String>,
    val decoratingRunResult: PluginV2EventResultCoordinator.DecoratingRunResult,
)

internal class PluginV2LlmPipelineCoordinator(
    private val dispatchEngine: PluginV2DispatchEngine = PluginV2DispatchEngineProvider.engine(),
    private val eventResultCoordinator: PluginV2EventResultCoordinator = PluginV2EventResultCoordinator(),
    private val logBus: PluginRuntimeLogBus = PluginRuntimeLogBusProvider.bus(),
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
    private val clock: () -> Long = System::currentTimeMillis,
    private val requestIdFactory: () -> String = { "req-${clock()}" },
) {
    private val toolCallCounter = AtomicInteger(0)
    private val toolLoopCoordinator: PluginV2ToolLoopCoordinator = PluginV2ToolLoopCoordinator(
        dispatchEngine = dispatchEngine,
        lifecycleManager = lifecycleManager,
        toolExecutor = toolExecutor,
        toolCallIdFactory = { "call-${clock()}-${toolCallCounter.incrementAndGet()}" },
        logBus = logBus,
        clock = clock,
    )

    suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
        snapshot: PluginV2ActiveRuntimeSnapshot = PluginV2ActiveRuntimeStoreProvider.store().snapshot(),
    ): PluginV2HostLlmDeliveryResult {
        val pipelineResult = runCatching {
            runPreSendStages(
                input = request.pipelineInput,
                snapshot = snapshot,
            )
        }.getOrElse { error ->
            error.rethrowIfCancellation()
            publishLlmPipelineFailed(
                requestId = "",
                stage = "Pipeline",
                streamingMode = request.pipelineInput.streamingMode,
                reason = error.message ?: error.javaClass.simpleName,
            )
            throw error
        }

        if (!pipelineResult.sendableResult.shouldSend) {
            return PluginV2HostLlmDeliveryResult.Suppressed(pipelineResult)
        }

        val preparedReply = request.prepareReply(pipelineResult)
        val sendResult = request.sendReply(preparedReply)
        if (!sendResult.success) {
            publishLlmPipelineFailed(
                requestId = pipelineResult.admission.requestId,
                stage = "Send",
                streamingMode = request.pipelineInput.streamingMode,
                reason = sendResult.errorSummary.ifBlank { "send_failed" },
            )
            return PluginV2HostLlmDeliveryResult.SendFailed(
                pipelineResult = pipelineResult,
                sendResult = sendResult,
            )
        }

        request.persistDeliveredReply(preparedReply, sendResult, pipelineResult)
        val afterSentView = eventResultCoordinator.buildAfterSentView(
            requestId = pipelineResult.admission.requestId,
            conversationId = pipelineResult.admission.conversationId,
            sendAttemptId = "host-send-${clock()}",
            platformAdapterType = request.platformAdapterType,
            platformInstanceKey = request.platformInstanceKey,
            sentAtEpochMs = clock(),
            deliveryStatus = PluginV2AfterSentView.DeliveryStatus.SUCCESS,
            receiptIds = sendResult.receiptIds,
            deliveredEntries = preparedReply.deliveredEntries,
            usage = pipelineResult.finalResponse.usage,
        )
        publishHostPipelineObservation(
            code = "after_message_sent_started",
            requestId = pipelineResult.admission.requestId,
            stage = PluginV2InternalStage.AfterMessageSent.name,
            streamingMode = request.pipelineInput.streamingMode,
            outcome = "STARTED",
        )
        val afterSentDispatchError = runCatching {
            dispatchAfterMessageSent(
                event = request.pipelineInput.event,
                afterSentView = afterSentView,
                followupSender = request.followupSender,
                snapshot = snapshot,
            )
        }.exceptionOrNull()
        if (afterSentDispatchError != null) {
            afterSentDispatchError.rethrowIfCancellation()
            lifecycleManager.emitPluginError(
                event = PluginV2LlmAfterSentPayload(
                    event = request.pipelineInput.event,
                    view = afterSentView,
                    followupSender = request.followupSender,
                ),
                pluginName = HOST_PIPELINE_PLUGIN_ID,
                handlerName = "after_message_sent",
                error = afterSentDispatchError,
                tracebackText = afterSentDispatchError.stackTraceToString(),
            )
        }
        publishHostPipelineObservation(
            code = "after_message_sent_completed",
            requestId = pipelineResult.admission.requestId,
            stage = PluginV2InternalStage.AfterMessageSent.name,
            streamingMode = request.pipelineInput.streamingMode,
            outcome = if (afterSentDispatchError == null) "COMPLETED" else "FAILED",
        )
        return PluginV2HostLlmDeliveryResult.Sent(
            pipelineResult = pipelineResult,
            preparedReply = preparedReply,
            sendResult = sendResult,
            afterSentView = afterSentView,
        )
    }

    suspend fun runPreSendStages(
        input: PluginV2LlmPipelineInput,
        snapshot: PluginV2ActiveRuntimeSnapshot = PluginV2ActiveRuntimeStoreProvider.store().snapshot(),
    ): PluginV2LlmPipelineResult {
        val admission = LlmPipelineAdmission(
            requestId = requestIdFactory(),
            conversationId = input.event.conversationId,
            messageIds = input.messageIds.toList(),
            llmInputSnapshot = input.event.workingText,
            routingTarget = input.routingTarget,
            streamingMode = input.streamingMode,
        )
        val hookTrace = mutableListOf<String>()

        publishPipelineObservation(
            code = "llm_waiting_started",
            stage = PluginV2InternalStage.LlmWaiting,
            requestId = admission.requestId,
            streamingMode = input.streamingMode,
            snapshot = snapshot,
            outcome = "STARTED",
        )
        val waitingResult = dispatchEngine.dispatchLlmStage(
            stage = PluginV2InternalStage.LlmWaiting,
            payload = input.event.toWaitingPayload(),
            snapshot = snapshot,
        )
        hookTrace += waitingResult.invokedHandlerIds.map { handlerId ->
            "${PluginV2InternalStage.LlmWaiting.name}:$handlerId"
        }

        var currentRequest = materializeInitialRequest(admission = admission, input = input)
        var finalResponse: PluginLlmResponse
        var responseHookTrace: List<String>

        while (true) {
            val requestPayload = PluginV2LlmRequestPayload(
                event = input.event,
                request = currentRequest,
            )
            publishPipelineObservation(
                code = "llm_request_started",
                stage = PluginV2InternalStage.LlmRequest,
                requestId = admission.requestId,
                streamingMode = input.streamingMode,
                snapshot = snapshot,
                outcome = "STARTED",
            )
            val requestDispatchResult = dispatchEngine.dispatchLlmStage(
                stage = PluginV2InternalStage.LlmRequest,
                payload = requestPayload,
                snapshot = snapshot,
                onHandlerCompleted = {
                    requestPayload.replaceRequest(
                        requestPayload.request.sanitizedCopyForNextHook(),
                    )
                },
            )
            hookTrace += requestDispatchResult.invokedHandlerIds.map { handlerId ->
                "${PluginV2InternalStage.LlmRequest.name}:$handlerId"
            }
            val finalizedRequest = requestPayload.request.sanitizedCopyForNextHook()
            requestPayload.replaceRequest(finalizedRequest)
            publishPipelineObservation(
                code = "llm_request_completed",
                stage = PluginV2InternalStage.LlmRequest,
                requestId = admission.requestId,
                streamingMode = input.streamingMode,
                snapshot = snapshot,
                outcome = "COMPLETED",
            )

            val aggregatedResponse = aggregateProviderInvocation(
                request = finalizedRequest,
                streamingMode = input.streamingMode,
                invocation = input.invokeProvider(
                    finalizedRequest,
                    input.streamingMode,
                ),
            )
            publishPipelineObservation(
                code = "llm_response_received",
                stage = PluginV2InternalStage.LlmResponse,
                requestId = admission.requestId,
                streamingMode = input.streamingMode,
                snapshot = snapshot,
                outcome = "COMPLETED",
            )
            val responsePayload = PluginV2LlmResponsePayload(
                event = input.event,
                response = aggregatedResponse,
            )
            val responseDispatchResult = dispatchEngine.dispatchLlmStage(
                stage = PluginV2InternalStage.LlmResponse,
                payload = responsePayload,
                snapshot = snapshot,
                onHandlerCompleted = {
                    responsePayload.replaceResponse(responsePayload.response.sanitizedCopyForNextHook())
                },
            )
            responseHookTrace = responseDispatchResult.invokedHandlerIds.map { handlerId ->
                "${PluginV2InternalStage.LlmResponse.name}:$handlerId"
            }
            hookTrace += responseHookTrace
            finalResponse = responsePayload.response.sanitizedCopyForNextHook()
            responsePayload.replaceResponse(finalResponse)

            val frozenToolCalls = finalResponse.toolCalls.toList()
            if (frozenToolCalls.isEmpty()) {
                currentRequest = finalizedRequest
                break
            }

            val toolLoopRun = toolLoopCoordinator.runToolLoop(
                event = input.event,
                baseRequest = finalizedRequest,
                toolCalls = frozenToolCalls,
                snapshot = snapshot,
            )
            currentRequest = toolLoopRun.nextRequest
        }

        val initialResult = PluginMessageEventResult(
            requestId = admission.requestId,
            conversationId = admission.conversationId,
            text = finalResponse.text,
            markdown = finalResponse.markdown,
            attachments = emptyList(),
            shouldSend = true,
        )
        publishPipelineObservation(
            code = "result_decorating_started",
            stage = PluginV2InternalStage.ResultDecorating,
            requestId = admission.requestId,
            streamingMode = input.streamingMode,
            snapshot = snapshot,
            outcome = "STARTED",
        )
        val decoratingResolution = dispatchEngine.resolveLlmHookCandidates(
            stage = PluginV2InternalStage.ResultDecorating,
            snapshot = snapshot,
        )
        val candidateByHandlerId = decoratingResolution.candidates.associateBy { candidate ->
            candidate.descriptor.handlerId
        }
        val decoratingRun = eventResultCoordinator.runDecorating(
            initialResult = initialResult,
            handlers = decoratingResolution.candidates.map { candidate ->
                PluginV2EventResultCoordinator.DecoratingHandlerInvocation(
                    handlerId = candidate.descriptor.handlerId,
                    priority = candidate.descriptor.priority,
                )
            },
            mutate = { invocation, mutableResult ->
                candidateByHandlerId[invocation.handlerId]?.let { candidate ->
                    hookTrace += "${PluginV2InternalStage.ResultDecorating.name}:${candidate.descriptor.handlerId}"
                    dispatchEngine.invokeLlmHookCandidate(
                        candidate = candidate,
                        payload = PluginV2LlmResultDecoratingPayload(
                            event = input.event,
                            result = mutableResult,
                        ),
                    )
                }
            },
        )
        publishPipelineObservation(
            code = "result_decorating_completed",
            stage = PluginV2InternalStage.ResultDecorating,
            requestId = admission.requestId,
            streamingMode = input.streamingMode,
            snapshot = snapshot,
            outcome = "COMPLETED",
        )

        return PluginV2LlmPipelineResult(
            admission = admission,
            finalRequest = currentRequest,
            finalResponse = finalResponse,
            sendableResult = decoratingRun.finalResult,
            hookInvocationTrace = hookTrace.toList(),
            decoratingRunResult = decoratingRun,
        )
    }

    suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
        followupSender: PluginV2FollowupSender? = null,
        snapshot: PluginV2ActiveRuntimeSnapshot = PluginV2ActiveRuntimeStoreProvider.store().snapshot(),
    ): PluginV2LlmStageDispatchResult {
        return dispatchEngine.dispatchLlmStage(
            stage = PluginV2InternalStage.AfterMessageSent,
            payload = PluginV2LlmAfterSentPayload(
                event = event,
                view = afterSentView,
                followupSender = followupSender,
            ),
            snapshot = snapshot,
        )
    }

    private fun materializeInitialRequest(
        admission: LlmPipelineAdmission,
        input: PluginV2LlmPipelineInput,
    ): PluginProviderRequest {
        return PluginProviderRequest(
            requestId = admission.requestId,
            availableProviderIds = input.availableProviderIds.toList(),
            availableModelIdsByProvider = input.availableModelIdsByProvider.mapValues { (_, models) ->
                models.toList()
            },
            conversationId = admission.conversationId,
            messageIds = admission.messageIds.toList(),
            llmInputSnapshot = admission.llmInputSnapshot,
            selectedProviderId = input.selectedProviderId,
            selectedModelId = input.selectedModelId,
            systemPrompt = input.systemPrompt,
            messages = input.messages.toList(),
            temperature = input.temperature,
            topP = input.topP,
            maxTokens = input.maxTokens,
            streamingEnabled = input.streamingEnabled,
            metadata = input.metadata,
        )
    }

    private fun aggregateProviderInvocation(
        request: PluginProviderRequest,
        streamingMode: PluginV2StreamingMode,
        invocation: PluginV2ProviderInvocationResult,
    ): PluginLlmResponse {
        return when (invocation) {
            is PluginV2ProviderInvocationResult.NonStreaming -> invocation.response.sanitizedCopy()
            is PluginV2ProviderInvocationResult.Streaming -> {
                require(streamingMode != PluginV2StreamingMode.NON_STREAM) {
                    "Streaming provider invocation requires streaming mode."
                }
                val textBuilder = StringBuilder()
                val toolCallsByIndex = linkedMapOf<Int, PluginLlmToolCallDelta>()
                var completionEvent: PluginV2ProviderStreamChunk? = null
                invocation.events.forEach { streamChunk ->
                    if (streamChunk.isCompletion) {
                        completionEvent = streamChunk
                    } else {
                        textBuilder.append(streamChunk.deltaText)
                        streamChunk.toolCallDeltas.forEach { delta ->
                            toolCallsByIndex[delta.index] = delta
                        }
                    }
                }
                val completion = checkNotNull(completionEvent) {
                    "Streaming provider invocation must include a completion signal before llm_response dispatch."
                }
                PluginLlmResponse(
                    requestId = request.requestId,
                    providerId = request.selectedProviderId,
                    modelId = request.selectedModelId,
                    usage = completion.usage,
                    finishReason = completion.finishReason,
                    text = textBuilder.toString(),
                    markdown = false,
                    toolCalls = toolCallsByIndex.entries
                        .sortedBy { (index, _) -> index }
                        .map { (_, delta) ->
                            PluginLlmToolCall(
                                toolName = delta.toolName,
                                arguments = delta.arguments,
                            )
                        },
                    metadata = completion.metadata,
                )
            }
        }
    }

    private fun PluginProviderRequest.sanitizedCopyForNextHook(): PluginProviderRequest {
        return PluginProviderRequest(
            requestId = requestId,
            availableProviderIds = availableProviderIds.toList(),
            availableModelIdsByProvider = availableModelIdsByProvider.mapValues { (_, modelIds) ->
                modelIds.toList()
            },
            conversationId = conversationId,
            messageIds = messageIds.toList(),
            llmInputSnapshot = llmInputSnapshot,
            selectedProviderId = selectedProviderId,
            selectedModelId = selectedModelId,
            systemPrompt = systemPrompt,
            messages = messages.toList(),
            temperature = temperature,
            topP = topP,
            maxTokens = maxTokens,
            streamingEnabled = streamingEnabled,
            metadata = metadata,
            allowHostToolMessages = messages.any { it.role == PluginProviderMessageRole.TOOL },
        )
    }

    private fun PluginLlmResponse.sanitizedCopy(): PluginLlmResponse {
        return PluginLlmResponse(
            requestId = requestId,
            providerId = providerId,
            modelId = modelId,
            usage = usage,
            finishReason = finishReason,
            text = text,
            markdown = markdown,
            toolCalls = toolCalls.toList(),
            metadata = metadata,
        )
    }

    private fun PluginLlmResponse.sanitizedCopyForNextHook(): PluginLlmResponse {
        return PluginLlmResponse(
            requestId = requestId,
            providerId = providerId,
            modelId = modelId,
            usage = usage,
            finishReason = finishReason,
            text = text,
            markdown = markdown,
            toolCalls = toolCalls.map { call ->
                PluginLlmToolCall(
                    toolName = call.normalizedToolName,
                    arguments = LinkedHashMap(call.normalizedArguments),
                    metadata = call.normalizedMetadata?.let(::LinkedHashMap),
                )
            },
            metadata = metadata?.let(::LinkedHashMap),
        )
    }

    private fun publishPipelineObservation(
        code: String,
        stage: PluginV2InternalStage,
        requestId: String,
        streamingMode: PluginV2StreamingMode,
        snapshot: PluginV2ActiveRuntimeSnapshot,
        outcome: String,
    ) {
        logBus.publishLifecycleRecord(
            pluginId = HOST_PIPELINE_PLUGIN_ID,
            pluginVersion = "",
            occurredAtEpochMillis = clock(),
            level = PluginRuntimeLogLevel.Info,
            code = code,
            message = "Plugin v2 llm pipeline observation.",
            metadata = linkedMapOf(
                "requestId" to requestId,
                "stage" to stage.name,
                "runtimeSessionId" to snapshot.runtimeSessionIds(),
                "streamingMode" to streamingMode.name,
                "outcome" to outcome,
            ),
        )
    }

    private fun publishHostPipelineObservation(
        code: String,
        requestId: String,
        stage: String,
        streamingMode: PluginV2StreamingMode,
        outcome: String,
        extraMetadata: Map<String, String> = emptyMap(),
    ) {
        logBus.publishLifecycleRecord(
            pluginId = HOST_PIPELINE_PLUGIN_ID,
            pluginVersion = "",
            occurredAtEpochMillis = clock(),
            level = if (code.endsWith("_failed")) PluginRuntimeLogLevel.Error else PluginRuntimeLogLevel.Info,
            code = code,
            message = "Plugin v2 llm pipeline observation.",
            metadata = linkedMapOf(
                "requestId" to requestId.ifBlank { "unknown" },
                "stage" to stage,
                "runtimeSessionId" to snapshotRuntimeSessionIds(),
                "streamingMode" to streamingMode.name,
                "outcome" to outcome,
            ).apply {
                extraMetadata.forEach { (key, value) ->
                    put(key, value)
                }
            },
        )
    }

    private fun publishLlmPipelineFailed(
        requestId: String,
        stage: String,
        streamingMode: PluginV2StreamingMode,
        reason: String,
    ) {
        publishHostPipelineObservation(
            code = "llm_pipeline_failed",
            requestId = requestId,
            stage = stage,
            streamingMode = streamingMode,
            outcome = "FAILED",
            extraMetadata = mapOf(
                "reason" to reason.ifBlank { "unknown" },
            ),
        )
    }

    private fun PluginV2ActiveRuntimeSnapshot.runtimeSessionIds(): String {
        val values = activeSessionsByPluginId.values
            .map(PluginV2RuntimeSession::sessionInstanceId)
            .sorted()
        return if (values.isEmpty()) {
            "none"
        } else {
            values.joinToString(separator = ",")
        }
    }

    private fun snapshotRuntimeSessionIds(): String {
        return PluginV2ActiveRuntimeStoreProvider.store().snapshot().runtimeSessionIds()
    }

    private fun Throwable.rethrowIfCancellation() {
        if (this is CancellationException) {
            throw this
        }
    }

    private companion object {
        private const val HOST_PIPELINE_PLUGIN_ID = "__host__"
    }
}

private fun PluginMessageEvent.toWaitingPayload(): PluginV2LlmWaitingPayload {
    return PluginV2LlmWaitingPayload(
        eventId = eventId,
        platformAdapterType = platformAdapterType,
        messageType = messageType.wireValue,
        conversationId = conversationId,
        senderId = senderId,
        timestampEpochMillis = timestampEpochMillis,
        rawText = rawText,
        workingText = workingText,
        rawMentions = rawMentions.toList(),
        normalizedMentions = normalizedMentions.toList(),
        extras = PluginV2ValueSanitizer.requireAllowedMap(extras),
    )
}

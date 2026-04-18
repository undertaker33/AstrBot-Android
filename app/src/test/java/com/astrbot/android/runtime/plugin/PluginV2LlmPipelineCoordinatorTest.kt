package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.runBlocking

class PluginV2LlmPipelineCoordinatorTest {
    @Test
    fun llm_pipeline_admission_is_created_before_waiting_and_waiting_only_sees_read_only_event_view() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 10L })
        var requestVisibleSnapshot: String? = null
        var waitingPayloadType: String? = null
        var waitingWorkingText: String? = null
        val fixture = llmFixture(
            pluginId = "com.example.v2.llm.admission",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "waiting.mutate",
                    hook = "on_waiting_llm_request",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        waitingPayloadType = payload::class.java.simpleName
                        assertTrue(payload is PluginV2LlmWaitingPayload)
                        assertFalse(payload is PluginMessageEvent)
                        val waitingPayload = payload as PluginV2LlmWaitingPayload
                        waitingWorkingText = waitingPayload.workingText
                    },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "request.capture",
                    hook = "on_llm_request",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val requestPayload = payload as PluginV2LlmRequestPayload
                        requestVisibleSnapshot = requestPayload.request.llmInputSnapshot
                    },
                ),
            )
        }

        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 10L }),
            logBus = logBus,
            clock = { 10L },
            requestIdFactory = { "req-admission-001" },
        )
        val event = sampleMessageEvent(rawText = "hello admission")

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = event,
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                PluginV2ProviderInvocationResult.NonStreaming(
                    PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "provider-final",
                    ),
                )
            },
            snapshot = snapshotOf(fixture),
        )

        assertEquals("req-admission-001", result.admission.requestId)
        assertEquals("hello admission", result.admission.llmInputSnapshot)
        assertEquals("PluginV2LlmWaitingPayload", waitingPayloadType)
        assertEquals("hello admission", waitingWorkingText)
        assertEquals("hello admission", requestVisibleSnapshot)
        assertEquals("hello admission", result.finalRequest.llmInputSnapshot)
        assertEquals("provider-final", result.sendableResult.text)
    }

    @Test
    fun request_hooks_sanitize_between_handlers_and_freeze_next_visible_snapshot() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 20L })
        val requestObjectIds = CopyOnWriteArrayList<Int>()
        val providerSeenBySecondHook = CopyOnWriteArrayList<String>()
        val modelSeenBySecondHook = CopyOnWriteArrayList<String>()
        val metadataSeenBySecondHook = CopyOnWriteArrayList<String>()
        val promptSeenBySecondHook = CopyOnWriteArrayList<String?>()
        val fixture = llmFixture(
            pluginId = "com.example.v2.llm.request.sanitize",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "request.first",
                    hook = "on_llm_request",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val requestPayload = payload as PluginV2LlmRequestPayload
                        requestObjectIds += System.identityHashCode(requestPayload.request)
                        val mutableMetadata = linkedMapOf<String, AllowedValue>("trace" to "stable")
                        requestPayload.request.selectedProviderId = "provider-b"
                        requestPayload.request.selectedModelId = "model-b-2"
                        requestPayload.request.systemPrompt = "  prompt-from-hook  "
                        requestPayload.request.metadata = mutableMetadata
                        mutableMetadata["trace"] = "mutated-locally-after-assign"
                    },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "request.second",
                    hook = "on_llm_request",
                    priority = 90,
                    handler = EventAwareHandle { payload ->
                        val requestPayload = payload as PluginV2LlmRequestPayload
                        requestObjectIds += System.identityHashCode(requestPayload.request)
                        providerSeenBySecondHook += requestPayload.request.selectedProviderId
                        modelSeenBySecondHook += requestPayload.request.selectedModelId
                        metadataSeenBySecondHook += requestPayload.request.metadata?.get("trace").toString()
                        promptSeenBySecondHook += requestPayload.request.systemPrompt
                    },
                ),
            )
        }
        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 20L }),
            logBus = logBus,
            clock = { 20L },
            requestIdFactory = { "req-sanitize-001" },
        )

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "hello request"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                PluginV2ProviderInvocationResult.NonStreaming(
                    PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "response",
                    ),
                )
            },
            snapshot = snapshotOf(fixture),
        )

        assertEquals(2, requestObjectIds.size)
        assertNotEquals(requestObjectIds[0], requestObjectIds[1])
        assertEquals(listOf("provider-b"), providerSeenBySecondHook)
        assertEquals(listOf("model-b-2"), modelSeenBySecondHook)
        assertEquals(listOf("stable"), metadataSeenBySecondHook)
        assertEquals(listOf("prompt-from-hook"), promptSeenBySecondHook)
        assertEquals("provider-b", result.finalRequest.selectedProviderId)
        assertEquals("model-b-2", result.finalRequest.selectedModelId)
        assertEquals("prompt-from-hook", result.finalRequest.systemPrompt)
        assertEquals("stable", result.finalRequest.metadata?.get("trace"))
    }

    @Test
    fun llm_request_exposes_only_available_tools_to_provider() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 25L })
        val requestedToolNames = CopyOnWriteArrayList<List<String>>()
        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 25L }),
            logBus = logBus,
            clock = { 25L },
            requestIdFactory = { "req-tool-visibility-001" },
        )

        val availableTool = PluginV2ToolRegistryEntry(
            pluginId = "com.example.search",
            name = "web_search",
            toolId = "com.example.search:web_search",
            description = "search the web",
            visibility = PluginToolVisibility.LLM_VISIBLE,
            sourceKind = PluginToolSourceKind.WEB_SEARCH,
            inputSchema = linkedMapOf("type" to "object"),
            metadata = null,
            sourceOrder = 0,
        )
        val blockedTool = PluginV2ToolRegistryEntry(
            pluginId = "com.example.search",
            name = "internal_lookup",
            toolId = "com.example.search:internal_lookup",
            description = "blocked lookup",
            visibility = PluginToolVisibility.LLM_VISIBLE,
            sourceKind = PluginToolSourceKind.PLUGIN_V2,
            inputSchema = linkedMapOf("type" to "object"),
            metadata = null,
            sourceOrder = 1,
        )
        val snapshot = PluginV2ActiveRuntimeSnapshot(
            toolRegistrySnapshot = PluginV2ToolRegistrySnapshot(
                activeEntries = listOf(availableTool, blockedTool),
                activeEntriesByName = linkedMapOf(
                    availableTool.name to availableTool,
                    blockedTool.name to blockedTool,
                ),
                activeEntriesByToolId = linkedMapOf(
                    availableTool.toolId to availableTool,
                    blockedTool.toolId to blockedTool,
                ),
            ),
            toolAvailabilityByName = linkedMapOf(
                availableTool.name to PluginV2ToolAvailabilitySnapshot(
                    toolName = availableTool.name,
                    toolId = availableTool.toolId,
                    pluginId = availableTool.pluginId,
                    sourceKind = availableTool.sourceKind,
                    registryActive = true,
                    personaEnabled = true,
                    capabilityAllowed = true,
                    sourceProviderAvailable = true,
                    available = true,
                ),
                blockedTool.name to PluginV2ToolAvailabilitySnapshot(
                    toolName = blockedTool.name,
                    toolId = blockedTool.toolId,
                    pluginId = blockedTool.pluginId,
                    sourceKind = blockedTool.sourceKind,
                    registryActive = true,
                    personaEnabled = false,
                    capabilityAllowed = false,
                    sourceProviderAvailable = false,
                    available = false,
                    firstFailureReason = PluginV2ToolAvailabilityFailureReason.PersonaDisabled,
                ),
            ),
        )

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "hello tool visibility"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                requestedToolNames += request.tools.map { it.name }
                PluginV2ProviderInvocationResult.NonStreaming(
                    PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "final",
                    ),
                )
            },
            snapshot = snapshot,
        )

        assertEquals(listOf(listOf("web_search")), requestedToolNames)
        assertEquals("final", result.sendableResult.text)
    }

    @Test
    fun response_stage_is_triggered_once_for_non_stream_and_streaming_modes_with_same_hook_order() = runBlocking {
        val nonStream = executePipelineForMode(mode = PluginV2StreamingMode.NON_STREAM)
        val pseudoStream = executePipelineForMode(mode = PluginV2StreamingMode.PSEUDO_STREAM)
        val nativeStream = executePipelineForMode(mode = PluginV2StreamingMode.NATIVE_STREAM)

        assertEquals(1, nonStream.responseHookCalls)
        assertEquals(1, pseudoStream.responseHookCalls)
        assertEquals(1, nativeStream.responseHookCalls)
        assertEquals(nonStream.hookTrace, pseudoStream.hookTrace)
        assertEquals(nonStream.hookTrace, nativeStream.hookTrace)
        assertEquals("streamed-final", pseudoStream.finalText)
        assertEquals("streamed-final", nativeStream.finalText)
    }

    @Test
    fun pseudo_streaming_materializes_non_streaming_provider_request() = runBlocking {
        val coordinator = PluginV2LlmPipelineCoordinator(
            requestIdFactory = { "req-pseudo-non-streaming-provider" },
        )
        var capturedStreamingEnabled: Boolean? = null
        var capturedInvocationMode: PluginV2StreamingMode? = null
        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "pseudo stream provider request"),
                streamingMode = PluginV2StreamingMode.PSEUDO_STREAM,
            ) { request, streamingMode ->
                capturedStreamingEnabled = request.streamingEnabled
                capturedInvocationMode = streamingMode
                PluginV2ProviderInvocationResult.NonStreaming(
                    PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "pseudo aggregated response",
                    ),
                )
            },
        )

        assertEquals(PluginV2StreamingMode.PSEUDO_STREAM, capturedInvocationMode)
        assertEquals(false, capturedStreamingEnabled)
        assertEquals("pseudo aggregated response", result.finalResponse.text)
    }

    @Test
    fun streaming_without_completion_signal_never_enters_response_stage() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 30L })
        val responseHookCalls = AtomicInteger(0)
        val fixture = llmFixture(
            pluginId = "com.example.v2.llm.streaming.missing-completion",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "response.guard",
                    hook = "on_llm_response",
                    priority = 100,
                    handler = EventAwareHandle {
                        responseHookCalls.incrementAndGet()
                    },
                ),
            )
        }
        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 30L }),
            logBus = logBus,
            clock = { 30L },
            requestIdFactory = { "req-streaming-guard" },
        )

        val error = runCatching {
            coordinator.runPreSendStages(
                input = pipelineInput(
                    event = sampleMessageEvent(rawText = "missing completion"),
                    streamingMode = PluginV2StreamingMode.NATIVE_STREAM,
                ) { _, _ ->
                    PluginV2ProviderInvocationResult.Streaming(
                        events = listOf(
                            PluginV2ProviderStreamChunk(deltaText = "partial-1"),
                            PluginV2ProviderStreamChunk(deltaText = "partial-2"),
                        ),
                    )
                },
                snapshot = snapshotOf(fixture),
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(0, responseHookCalls.get())
    }

    @Test
    fun decorating_completes_before_sendable_result_is_returned() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 40L })
        val fixture = llmFixture(
            pluginId = "com.example.v2.llm.decorating",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "decorating.mutate",
                    hook = "on_decorating_result",
                    priority = 100,
                    handler = EventAwareHandle { payload ->
                        val decoratingPayload = payload as PluginV2LlmResultDecoratingPayload
                        decoratingPayload.result.appendText("::decorated")
                        decoratingPayload.result.setShouldSend(false)
                    },
                ),
            )
        }
        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 40L }),
            logBus = logBus,
            clock = { 40L },
            requestIdFactory = { "req-decorating-001" },
        )

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "decorate me"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                PluginV2ProviderInvocationResult.NonStreaming(
                    PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "base",
                    ),
                )
            },
            snapshot = snapshotOf(fixture),
        )

        assertEquals("base::decorated", result.sendableResult.text)
        assertFalse(result.sendableResult.shouldSend)
    }

    @Test
    fun pipeline_emits_phase4_render_points_after_request_and_result_are_materialized() = runBlocking {
        val seq = AtomicInteger(1)
        val logBus = InMemoryPluginRuntimeLogBus(clock = { seq.getAndIncrement().toLong() })
        val requestStartedSeenInsideHook = CopyOnWriteArrayList<String>()
        val requestSnapshotSeenInsideHook = CopyOnWriteArrayList<String>()
        val decoratingStartedSeenInsideHook = CopyOnWriteArrayList<String>()
        val resultTextSeenInsideHook = CopyOnWriteArrayList<String>()
        val fixture = llmFixture(
            pluginId = "com.example.v2.llm.render-points",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "waiting.observe",
                    hook = "on_waiting_llm_request",
                    handler = EventAwareHandle { },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "request.observe",
                    hook = "on_llm_request",
                    handler = EventAwareHandle { payload ->
                        val requestPayload = payload as PluginV2LlmRequestPayload
                        val requestStarted = logBus.snapshot(limit = 20)
                            .firstOrNull { it.code == "llm_request_started" }
                        requestStartedSeenInsideHook += requireNotNull(requestStarted).metadata["stage"].orEmpty()
                        requestSnapshotSeenInsideHook += requestPayload.request.llmInputSnapshot
                    },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "response.observe",
                    hook = "on_llm_response",
                    handler = EventAwareHandle { },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "decorating.observe",
                    hook = "on_decorating_result",
                    handler = EventAwareHandle { payload ->
                        val decoratingPayload = payload as PluginV2LlmResultDecoratingPayload
                        val decoratingStarted = logBus.snapshot(limit = 20)
                            .firstOrNull { it.code == "result_decorating_started" }
                        decoratingStartedSeenInsideHook += requireNotNull(decoratingStarted).metadata["stage"].orEmpty()
                        resultTextSeenInsideHook += decoratingPayload.result.text
                    },
                ),
            )
        }
        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { seq.getAndIncrement().toLong() }),
            logBus = logBus,
            clock = { seq.getAndIncrement().toLong() },
            requestIdFactory = { "req-render-point-001" },
        )

        coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "render points"),
                streamingMode = PluginV2StreamingMode.NON_STREAM,
            ) { request, _ ->
                PluginV2ProviderInvocationResult.NonStreaming(
                    PluginLlmResponse(
                        requestId = request.requestId,
                        providerId = request.selectedProviderId,
                        modelId = request.selectedModelId,
                        text = "ok",
                    ),
                )
            },
            snapshot = snapshotOf(fixture),
        )

        val expected = listOf(
            "llm_waiting_started",
            "llm_request_started",
            "llm_request_completed",
            "llm_response_received",
            "result_decorating_started",
            "result_decorating_completed",
        )
        val actual = logBus.snapshot(limit = 200)
            .asReversed()
            .map { it.code }
            .filter { it in expected }
        assertEquals(expected, actual)
        assertEquals(listOf(PluginV2InternalStage.LlmRequest.name), requestStartedSeenInsideHook)
        assertEquals(listOf("render points"), requestSnapshotSeenInsideHook)
        assertEquals(listOf(PluginV2InternalStage.ResultDecorating.name), decoratingStartedSeenInsideHook)
        assertEquals(listOf("ok"), resultTextSeenInsideHook)
    }

    private suspend fun executePipelineForMode(
        mode: PluginV2StreamingMode,
    ): ExecutionProbe {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 50L })
        val hookTrace = CopyOnWriteArrayList<String>()
        val responseHookCalls = AtomicInteger(0)
        val fixture = llmFixture(
            pluginId = "com.example.v2.llm.mode.${mode.name.lowercase()}",
            logBus = logBus,
        ) { hostApi ->
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "waiting",
                    hook = "on_waiting_llm_request",
                    priority = 400,
                    handler = EventAwareHandle {
                        hookTrace += "waiting"
                    },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "request",
                    hook = "on_llm_request",
                    priority = 300,
                    handler = EventAwareHandle {
                        hookTrace += "request"
                    },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "response",
                    hook = "on_llm_response",
                    priority = 200,
                    handler = EventAwareHandle {
                        responseHookCalls.incrementAndGet()
                        hookTrace += "response"
                    },
                ),
            )
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "decorating",
                    hook = "on_decorating_result",
                    priority = 100,
                    handler = EventAwareHandle {
                        hookTrace += "decorating"
                    },
                ),
            )
        }
        val coordinator = PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 50L }),
            logBus = logBus,
            clock = { 50L },
            requestIdFactory = { "req-${mode.wireValue}" },
        )

        val result = coordinator.runPreSendStages(
            input = pipelineInput(
                event = sampleMessageEvent(rawText = "run-${mode.wireValue}"),
                streamingMode = mode,
            ) { request, streamingMode ->
                when (streamingMode) {
                    PluginV2StreamingMode.NON_STREAM -> PluginV2ProviderInvocationResult.NonStreaming(
                        PluginLlmResponse(
                            requestId = request.requestId,
                            providerId = request.selectedProviderId,
                            modelId = request.selectedModelId,
                            text = "streamed-final",
                        ),
                    )

                    PluginV2StreamingMode.PSEUDO_STREAM,
                    PluginV2StreamingMode.NATIVE_STREAM,
                    -> PluginV2ProviderInvocationResult.Streaming(
                        events = listOf(
                            PluginV2ProviderStreamChunk(deltaText = "streamed-"),
                            PluginV2ProviderStreamChunk(deltaText = "final"),
                            PluginV2ProviderStreamChunk(
                                isCompletion = true,
                                finishReason = "stop",
                            ),
                        ),
                    )
                }
            },
            snapshot = snapshotOf(fixture),
        )

        return ExecutionProbe(
            hookTrace = hookTrace.toList(),
            responseHookCalls = responseHookCalls.get(),
            finalText = result.finalResponse.text,
        )
    }

    private fun pipelineInput(
        event: PluginMessageEvent,
        streamingMode: PluginV2StreamingMode,
        invokeProvider: suspend (PluginProviderRequest, PluginV2StreamingMode) -> PluginV2ProviderInvocationResult,
    ): PluginV2LlmPipelineInput {
        return PluginV2LlmPipelineInput(
            event = event,
            messageIds = listOf("msg-001"),
            streamingMode = streamingMode,
            availableProviderIds = listOf("provider-a", "provider-b"),
            availableModelIdsByProvider = linkedMapOf(
                "provider-a" to listOf("model-a-1"),
                "provider-b" to listOf("model-b-1", "model-b-2"),
            ),
            selectedProviderId = "provider-a",
            selectedModelId = "model-a-1",
            messages = listOf(
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.USER,
                    parts = listOf(
                        PluginProviderMessagePartDto.TextPart(event.rawText),
                    ),
                ),
            ),
            invokeProvider = invokeProvider,
        )
    }

    private fun llmFixture(
        pluginId: String,
        logBus: PluginRuntimeLogBus,
        register: (PluginV2BootstrapHostApi) -> Unit,
    ): RuntimeFixture {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val hostApi = PluginV2BootstrapHostApi(session, logBus = logBus, clock = { 1L })
        register(hostApi)

        val compileResult = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L })
            .compile(requireNotNull(session.rawRegistry))
        val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
        session.attachCompiledRegistry(compiledRegistry)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        return RuntimeFixture(
            session = session,
            compiledRegistry = compiledRegistry,
            entry = PluginV2ActiveRuntimeEntry(
                session = session,
                compiledRegistry = compiledRegistry,
                lastBootstrapSummary = PluginV2BootstrapSummary(
                    pluginId = pluginId,
                    sessionInstanceId = session.sessionInstanceId,
                    compiledAtEpochMillis = 0L,
                    handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                    warningCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                    errorCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Error },
                ),
                diagnostics = compileResult.diagnostics,
                callbackTokens = session.snapshotCallbackTokens(),
            ),
        )
    }

    private fun snapshotOf(vararg fixtures: RuntimeFixture): PluginV2ActiveRuntimeSnapshot {
        return PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = fixtures.associateBy { it.session.pluginId }.mapValues { it.value.entry },
            activeSessionsByPluginId = fixtures.associateBy { it.session.pluginId }.mapValues { it.value.session },
            compiledRegistriesByPluginId = fixtures.associateBy { it.session.pluginId }.mapValues { it.value.compiledRegistry },
        )
    }

    private fun sampleMessageEvent(rawText: String): PluginMessageEvent {
        return PluginMessageEvent(
            eventId = "evt-${rawText.hashCode()}",
            platformAdapterType = "onebot",
            messageType = MessageType.GroupMessage,
            conversationId = "group-llm",
            senderId = "user-llm",
            timestampEpochMillis = 1_710_000_100_000L,
            rawText = rawText,
            initialWorkingText = rawText,
            rawMentions = emptyList(),
            normalizedMentions = emptyList(),
            extras = emptyMap(),
        )
    }

    private data class RuntimeFixture(
        val session: PluginV2RuntimeSession,
        val compiledRegistry: PluginV2CompiledRegistrySnapshot,
        val entry: PluginV2ActiveRuntimeEntry,
    )

    private data class ExecutionProbe(
        val hookTrace: List<String>,
        val responseHookCalls: Int,
        val finalText: String,
    )

    private class EventAwareHandle(
        private val onEvent: suspend (PluginErrorEventPayload) -> Unit,
    ) : PluginV2EventAwareCallbackHandle {
        override fun invoke() = Unit

        override suspend fun handleEvent(event: PluginErrorEventPayload) {
            onEvent(event)
        }
    }
}

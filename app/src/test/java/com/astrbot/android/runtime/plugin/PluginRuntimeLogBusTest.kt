package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginPermissionGrant
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogCategory
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginRuntimeLogRecord
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginV2ToolDiagnosticCodes
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.model.chat.MessageType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeLogBusTest {

    @Test
    fun in_memory_log_bus_keeps_recent_entries_newest_first_and_supports_plugin_filtering() {
        val bus = InMemoryPluginRuntimeLogBus(capacity = 2)
        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 10L,
                pluginId = "alpha",
                trigger = PluginTriggerSource.OnCommand,
                category = PluginRuntimeLogCategory.Execution,
                level = PluginRuntimeLogLevel.Info,
                code = "execution_succeeded",
                message = "alpha",
            ),
        )
        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 20L,
                pluginId = "beta",
                trigger = PluginTriggerSource.OnCommand,
                category = PluginRuntimeLogCategory.Execution,
                level = PluginRuntimeLogLevel.Error,
                code = "execution_failed",
                message = "beta",
            ),
        )
        bus.publish(
            PluginRuntimeLogRecord(
                occurredAtEpochMillis = 30L,
                pluginId = "alpha",
                trigger = PluginTriggerSource.OnCommand,
                category = PluginRuntimeLogCategory.Dispatcher,
                level = PluginRuntimeLogLevel.Info,
                code = "dispatcher_queued",
                message = "alpha queued",
            ),
        )

        assertEquals(
            listOf("dispatcher_queued", "execution_failed"),
            bus.snapshot().map(PluginRuntimeLogRecord::code),
        )
        assertEquals(
            listOf("dispatcher_queued"),
            bus.snapshot(pluginId = "alpha").map(PluginRuntimeLogRecord::code),
        )
    }

    @Test
    fun engine_and_dispatcher_publish_runtime_logs_for_queued_skipped_success_and_failure_paths() {
        val clock = TestClock(now = 1_000L)
        val bus = InMemoryPluginRuntimeLogBus(capacity = 32)
        val failureGuard = PluginFailureGuard(
            store = InMemoryPluginFailureStateStore(),
            clock = { clock.now },
            logBus = bus,
        )
        val engine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(
                failureGuard = failureGuard,
                logBus = bus,
                clock = { clock.now },
            ),
            failureGuard = failureGuard,
            logBus = bus,
            clock = { clock.now },
        )

        engine.executeBatch(
            trigger = PluginTriggerSource.OnCommand,
            plugins = listOf(
                runtimePlugin(pluginId = "alpha") { TextResult("alpha-ok") },
                runtimePlugin(pluginId = "disabled", enabled = false),
                runtimePlugin(pluginId = "boom") { error("boom") },
            ),
            contextFactory = ::executionContextFor,
        )

        val codes = bus.snapshot().map(PluginRuntimeLogRecord::code)
        assertTrue(codes.contains("dispatcher_queued"))
        assertTrue(codes.contains("dispatcher_skipped"))
        assertTrue(codes.contains("execution_succeeded"))
        assertTrue(codes.contains("execution_failed"))
        assertTrue(codes.contains("failure_guard_recorded"))
    }

    @Test
    fun host_action_executor_publishes_success_and_failure_logs() {
        val bus = InMemoryPluginRuntimeLogBus(capacity = 16)
        val executor = ExternalPluginHostActionExecutor(
            failureGuard = PluginFailureGuard(
                store = InMemoryPluginFailureStateStore(),
                logBus = bus,
            ),
            logBus = bus,
            sendMessageHandler = {},
        )
        val successContext = executionContextFor(runtimePlugin("plugin.message")).copy(
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = "send_message",
                    title = "send_message",
                    granted = true,
                    riskLevel = PluginRiskLevel.LOW,
                ),
            ),
        )

        executor.execute(
            pluginId = "plugin.message",
            request = HostActionRequest(
                action = PluginHostAction.SendMessage,
                payload = mapOf("text" to "hello"),
            ),
            context = successContext,
        )
        executor.execute(
            pluginId = "plugin.message",
            request = HostActionRequest(
                action = PluginHostAction.OpenHostPage,
                payload = emptyMap(),
            ),
            context = executionContextFor(runtimePlugin("plugin.message")),
        )

        val codes = bus.snapshot(pluginId = "plugin.message").map(PluginRuntimeLogRecord::code)
        assertTrue(codes.contains("host_action_succeeded"))
        assertTrue(codes.contains("host_action_failed"))
    }

    @Test
    fun log_bus_auto_clears_expired_plugin_logs_before_publishing_new_records() {
        val store = InMemoryPluginRuntimeLogCleanupSettingsStore()
        PluginRuntimeLogCleanupRepository.setStoreOverrideForTests(store)
        try {
            var now = 1_000L
            PluginRuntimeLogCleanupRepository.updateSettings(
                pluginId = "plugin.demo",
                enabled = true,
                intervalHours = 1,
                intervalMinutes = 0,
                now = now,
            )
            val bus = InMemoryPluginRuntimeLogBus(
                capacity = 16,
                clock = { now },
            )
            bus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = 2_000L,
                    pluginId = "plugin.demo",
                    trigger = PluginTriggerSource.OnCommand,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Info,
                    code = "old_record",
                    message = "stale",
                ),
            )
            now += 60 * 60 * 1000L
            bus.publish(
                PluginRuntimeLogRecord(
                    occurredAtEpochMillis = 3_000L + now,
                    pluginId = "plugin.demo",
                    trigger = PluginTriggerSource.OnCommand,
                    category = PluginRuntimeLogCategory.Execution,
                    level = PluginRuntimeLogLevel.Info,
                    code = "fresh_record",
                    message = "fresh",
                ),
            )

            assertEquals(
                listOf("fresh_record"),
                bus.snapshot(pluginId = "plugin.demo").map(PluginRuntimeLogRecord::code),
            )
        } finally {
            PluginRuntimeLogCleanupRepository.setStoreOverrideForTests(null)
        }
    }

    @Test
    fun phase5_tool_diagnostics_are_visible_with_required_schema() = runBlocking {
        val bus = InMemoryPluginRuntimeLogBus(capacity = 64, clock = { 5_000L })
        PluginRuntimeLogBusProvider.setBusOverrideForTests(bus)
        try {
            val fixture = phase5ToolFixture(
                pluginId = "com.example.phase5.tool.logs",
                logBus = bus,
            )
            val providerCalls = AtomicInteger(0)
            val coordinator = PluginV2LlmPipelineCoordinator(
                dispatchEngine = PluginV2DispatchEngine(logBus = bus, clock = { 5_000L }),
                toolExecutor = PluginV2ToolExecutor { args ->
                    val isFailureTool = args.toolId.endsWith(":beta")
                    PluginToolResult(
                        toolCallId = args.toolCallId,
                        requestId = args.requestId,
                        toolId = args.toolId,
                        status = if (isFailureTool) PluginToolResultStatus.ERROR else PluginToolResultStatus.SUCCESS,
                        errorCode = if (isFailureTool) "beta_failed" else "",
                        text = if (isFailureTool) "beta failed" else "tool-result",
                    )
                },
                logBus = bus,
                clock = { 5_000L },
                requestIdFactory = { "req-phase5-tool-diagnostics" },
            )

            val result = coordinator.runPreSendStages(
                input = phase5PipelineInput { request, _ ->
                    when (providerCalls.incrementAndGet()) {
                        1 -> PluginV2ProviderInvocationResult.NonStreaming(
                            PluginLlmResponse(
                                requestId = request.requestId,
                                providerId = request.selectedProviderId,
                                modelId = request.selectedModelId,
                                toolCalls = listOf(
                                    PluginLlmToolCall(
                                        toolName = "alpha",
                                        arguments = linkedMapOf("q" to "diagnostics"),
                                    ),
                                    PluginLlmToolCall(
                                        toolName = "beta",
                                        arguments = linkedMapOf("q" to "diagnostics-failure"),
                                    ),
                                ),
                            ),
                        )

                        else -> PluginV2ProviderInvocationResult.NonStreaming(
                            PluginLlmResponse(
                                requestId = request.requestId,
                                providerId = request.selectedProviderId,
                                modelId = request.selectedModelId,
                                text = "final assistant after tool",
                                toolCalls = emptyList(),
                            ),
                        )
                    }
                },
                snapshot = phase5SnapshotOf(fixture),
            )

            assertEquals("final assistant after tool", result.sendableResult.text)
            val expectedCodes = listOf(
                PluginV2ToolDiagnosticCodes.TOOL_REGISTRY_COMPILED,
                PluginV2ToolDiagnosticCodes.TOOL_AVAILABILITY_SNAPSHOT_BUILT,
                PluginV2ToolDiagnosticCodes.LLM_TOOL_SELECTED,
                PluginV2ToolDiagnosticCodes.TOOL_PRE_HOOK_EMITTED,
                PluginV2ToolDiagnosticCodes.LLM_TOOL_STARTED,
                PluginV2ToolDiagnosticCodes.LLM_TOOL_COMPLETED,
                PluginV2ToolDiagnosticCodes.LLM_TOOL_FAILED,
                PluginV2ToolDiagnosticCodes.TOOL_POST_HOOK_EMITTED,
                PluginV2ToolDiagnosticCodes.TOOL_CALL_LOOP_RESUMED,
            )
            val recordsByCode = bus.snapshot(limit = 64)
                .filter { record -> record.code in expectedCodes }
                .associateBy(PluginRuntimeLogRecord::code)

            expectedCodes.forEach { code ->
                assertTrue("Missing phase5 tool diagnostic: $code", recordsByCode.containsKey(code))
                assertPhase5ToolLogSchema(recordsByCode.getValue(code), code)
            }

            expectedCodes
                .filterNot {
                    it == PluginV2ToolDiagnosticCodes.TOOL_REGISTRY_COMPILED ||
                        it == PluginV2ToolDiagnosticCodes.TOOL_AVAILABILITY_SNAPSHOT_BUILT
                }
                .forEach { code ->
                    val metadata = recordsByCode.getValue(code).metadata
                    assertTrue(metadata["toolId"].orEmpty().startsWith("com.example.phase5.tool.logs:"))
                    assertTrue(metadata["toolCallId"].orEmpty().isNotBlank())
                    assertEquals(PluginToolSourceKind.PLUGIN_V2.name, metadata["sourceKind"])
                }
        } finally {
            PluginRuntimeLogBusProvider.setBusOverrideForTests(null)
        }
    }

    private fun assertPhase5ToolLogSchema(
        record: PluginRuntimeLogRecord,
        code: String,
    ) {
        assertEquals(code, record.code)
        assertEquals(code, record.metadata["code"])
        assertTrue(record.metadata["requestId"].orEmpty().isNotBlank())
        assertTrue(record.metadata["stage"].orEmpty().isNotBlank())
        assertTrue(record.metadata["outcome"].orEmpty().isNotBlank())
    }

    private fun phase5PipelineInput(
        invokeProvider: suspend (PluginProviderRequest, PluginV2StreamingMode) -> PluginV2ProviderInvocationResult,
    ): PluginV2LlmPipelineInput {
        val event = PluginMessageEvent(
            eventId = "evt-phase5-tool-diagnostics",
            platformAdapterType = "onebot",
            messageType = MessageType.GroupMessage,
            conversationId = "conv-phase5-tool-diagnostics",
            senderId = "user-phase5",
            timestampEpochMillis = 1_710_000_500_000L,
            rawText = "hello tool diagnostics",
            initialWorkingText = "hello tool diagnostics",
            rawMentions = emptyList(),
            normalizedMentions = emptyList(),
            extras = emptyMap(),
        )
        return PluginV2LlmPipelineInput(
            event = event,
            messageIds = listOf("msg-phase5-tool-diagnostics"),
            streamingMode = PluginV2StreamingMode.NON_STREAM,
            availableProviderIds = listOf("provider-a"),
            availableModelIdsByProvider = mapOf("provider-a" to listOf("model-a")),
            selectedProviderId = "provider-a",
            selectedModelId = "model-a",
            messages = listOf(
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.USER,
                    parts = listOf(PluginProviderMessagePartDto.TextPart(event.rawText)),
                ),
            ),
            invokeProvider = invokeProvider,
        )
    }

    private fun phase5ToolFixture(
        pluginId: String,
        logBus: PluginRuntimeLogBus,
    ): Phase5RuntimeFixture {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        val hostApi = PluginV2BootstrapHostApi(session, logBus = logBus, clock = { 5_000L })
        hostApi.registerTool(
            descriptor = PluginToolDescriptor(
                pluginId = pluginId,
                name = "alpha",
                description = "alpha diagnostics tool",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.PLUGIN_V2,
                inputSchema = linkedMapOf("type" to "object"),
            ),
            handler = PluginV2CallbackHandle {},
        )
        hostApi.registerTool(
            descriptor = PluginToolDescriptor(
                pluginId = pluginId,
                name = "beta",
                description = "beta diagnostics tool",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.PLUGIN_V2,
                inputSchema = linkedMapOf("type" to "object"),
            ),
            handler = PluginV2CallbackHandle {},
        )
        hostApi.registerLlmHook(
            LlmHookRegistrationInput(
                registrationKey = "diagnostics.using",
                hook = "on_using_llm_tool",
                priority = 100,
                handler = EventAwareHandle {},
            ),
        )
        hostApi.registerLlmHook(
            LlmHookRegistrationInput(
                registrationKey = "diagnostics.respond",
                hook = "on_llm_tool_respond",
                priority = 100,
                handler = EventAwareHandle {},
            ),
        )
        val compileResult = PluginV2RegistryCompiler(logBus = logBus, clock = { 5_000L })
            .compile(requireNotNull(session.rawRegistry))
        val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
        session.attachCompiledRegistry(compiledRegistry)
        session.transitionTo(PluginV2RuntimeSessionState.Active)
        return Phase5RuntimeFixture(
            session = session,
            compiledRegistry = compiledRegistry,
            entry = phase5ActiveEntry(
                session = session,
                compiledRegistry = compiledRegistry,
                diagnostics = compileResult.diagnostics,
            ),
        )
    }

    private fun phase5ActiveEntry(
        session: PluginV2RuntimeSession,
        compiledRegistry: PluginV2CompiledRegistrySnapshot,
        diagnostics: List<PluginV2CompilerDiagnostic> = emptyList(),
    ): PluginV2ActiveRuntimeEntry {
        return PluginV2ActiveRuntimeEntry(
            session = session,
            compiledRegistry = compiledRegistry,
            lastBootstrapSummary = PluginV2BootstrapSummary(
                pluginId = session.pluginId,
                sessionInstanceId = session.sessionInstanceId,
                compiledAtEpochMillis = 5_000L,
                handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                warningCount = diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                errorCount = diagnostics.count { it.severity == DiagnosticSeverity.Error },
            ),
            diagnostics = diagnostics,
            callbackTokens = session.snapshotCallbackTokens(),
        )
    }

    private fun phase5SnapshotOf(fixture: Phase5RuntimeFixture): PluginV2ActiveRuntimeSnapshot {
        val sessionsByPluginId = mapOf(fixture.session.pluginId to fixture.session)
        val toolState = compileCentralizedToolState(sessionsByPluginId)
        return PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = mapOf(fixture.session.pluginId to fixture.entry),
            activeSessionsByPluginId = sessionsByPluginId,
            compiledRegistriesByPluginId = mapOf(fixture.session.pluginId to fixture.compiledRegistry),
            toolRegistrySnapshot = toolState.activeRegistry,
            toolRegistryDiagnostics = toolState.diagnostics,
            toolAvailabilityByName = toolState.availabilityByName,
        )
    }

    private data class Phase5RuntimeFixture(
        val session: PluginV2RuntimeSession,
        val compiledRegistry: PluginV2CompiledRegistrySnapshot,
        val entry: PluginV2ActiveRuntimeEntry,
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

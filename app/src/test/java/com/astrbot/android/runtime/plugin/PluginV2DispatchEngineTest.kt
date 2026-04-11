package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.chat.MessageType
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2DispatchEngineTest {
    @Test
    fun dispatch_message_runs_adapter_command_regex_in_order_and_filters_after_stage_materialization() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 100L })
        val callbackOrder = CopyOnWriteArrayList<String>()
        val filterStages = CopyOnWriteArrayList<String>()
        val unmatchedCommandFilterCalls = AtomicInteger(0)
        val unmatchedRegexFilterCalls = AtomicInteger(0)

        val adapterFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.adapter",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerMessageHandler(
                MessageHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "adapter.primary",
                        priority = 50,
                    ),
                    handler = EventAwareTestHandle(
                        onEvent = { event ->
                            event as PluginMessageEvent
                            event.workingText = "/echo done"
                            callbackOrder += "adapter:${event.workingText}"
                        },
                    ),
                ),
            )
        }
        val acceptedCommandFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.command.accepted",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerCommandHandler(
                CommandHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "command.accepted",
                        priority = 40,
                        declaredFilters = listOf(
                            BootstrapFilterDescriptor.command("platform_adapter_type:onebot"),
                            BootstrapFilterDescriptor.message("event_message_type:group"),
                            BootstrapFilterDescriptor.regex("permission_type:net.access"),
                            BootstrapFilterDescriptor.message("custom_filter:allow"),
                        ),
                    ),
                    command = "echo",
                    handler = FullFeaturedTestHandle(
                        onCustomFilter = { request ->
                            filterStages += request.eventView.stage
                            true
                        },
                        onEvent = { event ->
                            event as PluginCommandEvent
                            callbackOrder += "command:${event.commandPath.last()}:${event.workingText}"
                        },
                    ),
                ),
            )
        }
        val rejectedCommandFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.command.rejected",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerCommandHandler(
                CommandHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "command.rejected",
                        priority = 30,
                        declaredFilters = listOf(
                            BootstrapFilterDescriptor.command("platform_adapter_type:discord"),
                        ),
                    ),
                    command = "echo",
                    handler = EventAwareTestHandle(
                        onEvent = { callbackOrder += "command:rejected" },
                    ),
                ),
            )
        }
        val unmatchedCommandFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.command.unmatched",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerCommandHandler(
                CommandHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "command.unmatched",
                        priority = 25,
                        declaredFilters = listOf(
                            BootstrapFilterDescriptor.message("custom_filter:unmatched-command"),
                        ),
                    ),
                    command = "other",
                    handler = FullFeaturedTestHandle(
                        onCustomFilter = {
                            unmatchedCommandFilterCalls.incrementAndGet()
                            true
                        },
                        onEvent = { callbackOrder += "command:unmatched" },
                    ),
                ),
            )
        }
        val regexFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.regex",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerRegexHandler(
                RegexHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "regex.primary",
                        priority = 20,
                        declaredFilters = listOf(
                            BootstrapFilterDescriptor.message("custom_filter:regex-allow"),
                        ),
                    ),
                    pattern = "done$",
                    handler = FullFeaturedTestHandle(
                        onCustomFilter = { request ->
                            filterStages += request.eventView.stage
                            true
                        },
                        onEvent = { event ->
                            event as PluginRegexEvent
                            callbackOrder += "regex:${event.matchedText}:${event.workingText}"
                        },
                    ),
                ),
            )
        }
        val unmatchedRegexFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.regex.unmatched",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerRegexHandler(
                RegexHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "regex.unmatched",
                        priority = 10,
                        declaredFilters = listOf(
                            BootstrapFilterDescriptor.message("custom_filter:regex-unmatched"),
                        ),
                    ),
                    pattern = "^never-match$",
                    handler = FullFeaturedTestHandle(
                        onCustomFilter = {
                            unmatchedRegexFilterCalls.incrementAndGet()
                            true
                        },
                        onEvent = { callbackOrder += "regex:unmatched" },
                    ),
                ),
            )
        }

        val snapshot = snapshotOf(
            adapterFixture,
            acceptedCommandFixture,
            rejectedCommandFixture,
            unmatchedCommandFixture,
            regexFixture,
            unmatchedRegexFixture,
        )
        val engine = PluginV2DispatchEngine(
            logBus = logBus,
            clock = { 100L },
        )

        val result = engine.dispatchMessage(
            event = sampleMessageEvent(rawText = "hello"),
            snapshot = snapshot,
        )

        assertNull(result.userVisibleFailureMessage)
        assertEquals(
            listOf(
                "adapter:/echo done",
                "command:echo:/echo done",
                "regex:done:/echo done",
            ),
            callbackOrder,
        )
        assertEquals(listOf("Command", "Regex"), filterStages)
        assertEquals(0, unmatchedCommandFilterCalls.get())
        assertEquals(0, unmatchedRegexFilterCalls.get())
        val codes = logBus.snapshot(limit = 100).map { it.code }
        assertTrue(codes.contains("message_dispatch_started"))
        assertTrue(codes.contains("message_dispatch_completed"))
        assertTrue(codes.filterRelevantCodes().contains("filter_rejected"))
        assertTrue(codes.none { it == "on_plugin_error" })
    }

    @Test
    fun dispatch_message_stops_event_after_custom_filter_failure_without_emitting_plugin_error() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 200L })
        val callbackOrder = CopyOnWriteArrayList<String>()
        val commandFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.custom-filter",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerCommandHandler(
                CommandHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "command.failure",
                        declaredFilters = listOf(
                            BootstrapFilterDescriptor.message("event_message_type:group"),
                            BootstrapFilterDescriptor.command("platform_adapter_type:onebot"),
                            BootstrapFilterDescriptor.regex("permission_type:net.access"),
                            BootstrapFilterDescriptor.message("custom_filter:explode"),
                        ),
                    ),
                    command = "echo",
                    handler = FullFeaturedTestHandle(
                        onCustomFilter = {
                            error("boom")
                        },
                        onEvent = {
                            callbackOrder += "command"
                        },
                    ),
                ),
            )
        }
        val regexFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.after-failure",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerRegexHandler(
                RegexHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "regex.after",
                    ),
                    pattern = "echo",
                    handler = EventAwareTestHandle(
                        onEvent = { callbackOrder += "regex" },
                    ),
                ),
            )
        }

        val engine = PluginV2DispatchEngine(
            logBus = logBus,
            clock = { 200L },
        )

        val result = engine.dispatchMessage(
            event = sampleMessageEvent(rawText = "/echo now"),
            snapshot = snapshotOf(commandFixture, regexFixture),
        )

        assertNotNull(result.userVisibleFailureMessage)
        assertTrue(result.terminatedByCustomFilterFailure)
        assertTrue(callbackOrder.isEmpty())
        val codes = logBus.snapshot(limit = 100).map { it.code }
        assertTrue(codes.filterRelevantCodes().contains("custom_filter_failed"))
        assertTrue(codes.none { it == "on_plugin_error" })
    }

    @Test
    fun dispatch_message_routes_message_command_and_regex_failures_into_on_plugin_error_without_aborting_ingress() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 250L })
        val errorEvents = CopyOnWriteArrayList<PluginErrorHookArgs>()
        val callbackOrder = CopyOnWriteArrayList<String>()
        val throwingFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.throwing",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerMessageHandler(
                MessageHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "message.throw",
                        priority = 100,
                    ),
                    handler = EventAwareTestHandle(
                        onEvent = { payload ->
                            val event = payload as PluginMessageEvent
                            callbackOrder += "message"
                            event.workingText = "/explode code-42"
                            error("boom:message")
                        },
                    ),
                ),
            )
            hostApi.registerCommandHandler(
                CommandHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "command.throw",
                        priority = 80,
                    ),
                    command = "explode",
                    handler = EventAwareTestHandle(
                        onEvent = {
                            callbackOrder += "command"
                            error("boom:command")
                        },
                    ),
                ),
            )
            hostApi.registerRegexHandler(
                RegexHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "regex.throw",
                        priority = 60,
                    ),
                    pattern = "(?<label>code)-(?<value>\\d+)$",
                    handler = EventAwareTestHandle(
                        onEvent = {
                            callbackOrder += "regex"
                            error("boom:regex")
                        },
                    ),
                ),
            )
        }
        val errorHookFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.error-hook",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerLifecycleHandler(
                LifecycleHandlerRegistrationInput(
                    registrationKey = "lifecycle.error",
                    hook = PluginLifecycleHookSurface.OnPluginError.wireValue,
                    handler = object : PluginV2EventAwareCallbackHandle {
                        override fun invoke() = Unit

                        override suspend fun handleEvent(event: PluginErrorEventPayload) {
                            errorEvents += requireNotNull(event as? PluginErrorHookArgs)
                        }
                    },
                ),
            )
        }
        val store = PluginV2ActiveRuntimeStore(
            logBus = logBus,
            clock = { 250L },
        )
        store.commitLoadedRuntime(throwingFixture.entry)
        store.commitLoadedRuntime(errorHookFixture.entry)
        val engine = PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 250L },
        )

        val result = engine.dispatchMessage(
            event = sampleMessageEvent(rawText = "hello"),
        )

        assertFalse(result.propagationStopped)
        assertFalse(result.terminatedByCustomFilterFailure)
        assertNull(result.userVisibleFailureMessage)
        assertEquals(listOf("message", "command", "regex"), callbackOrder)
        assertEquals(
            listOf(
                "hdl::com.example.v2.dispatch.throwing::message::message.throw",
                "hdl::com.example.v2.dispatch.throwing::command::command.throw",
                "hdl::com.example.v2.dispatch.throwing::regex::regex.throw",
            ),
            errorEvents.map(PluginErrorHookArgs::handler_name),
        )
        assertEquals(
            listOf(
                "boom:message",
                "boom:command",
                "boom:regex",
            ),
            errorEvents.map { args -> args.error.message },
        )
    }

    @Test
    fun dispatch_message_rethrows_cancellation_from_handler_without_logging_plugin_error() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 260L })
        val fixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.cancelled",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerMessageHandler(
                MessageHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "message.cancelled",
                    ),
                    handler = EventAwareTestHandle(
                        onEvent = {
                            throw CancellationException("cancelled")
                        },
                    ),
                ),
            )
        }
        val engine = PluginV2DispatchEngine(
            logBus = logBus,
            clock = { 260L },
        )

        try {
            engine.dispatchMessage(
                event = sampleMessageEvent(rawText = "hello"),
                snapshot = snapshotOf(fixture),
            )
            fail("Expected CancellationException")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }

        val codes = logBus.snapshot(limit = 100).map { it.code }
        assertTrue(codes.none { it == "on_plugin_error" })
    }

    @Test
    fun dispatch_message_populates_regex_named_groups_from_match_snapshot() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 275L })
        val regexEvents = CopyOnWriteArrayList<PluginRegexEvent>()
        val fixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.regex.named",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerRegexHandler(
                RegexHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "regex.named",
                    ),
                    pattern = "(?<label>code)-(?<value>\\d+)",
                    handler = EventAwareTestHandle(
                        onEvent = { payload ->
                            regexEvents += payload as PluginRegexEvent
                        },
                    ),
                ),
            )
        }
        val engine = PluginV2DispatchEngine(
            logBus = logBus,
            clock = { 275L },
        )

        val result = engine.dispatchMessage(
            event = sampleMessageEvent(rawText = "hello code-42"),
            snapshot = snapshotOf(fixture),
        )

        assertFalse(result.propagationStopped)
        assertEquals(
            listOf("label", "value"),
            fixture.compiledRegistry.handlerRegistry.regexHandlers.single().namedGroupNames,
        )
        assertEquals(1, regexEvents.size)
        assertEquals(listOf("code", "42"), regexEvents.single().groups)
        assertEquals(
            mapOf(
                "label" to "code",
                "value" to "42",
            ),
            regexEvents.single().namedGroups,
        )
    }

    @Test
    fun dispatch_message_honors_stop_propagation_for_remaining_handlers_and_later_stages() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 300L })
        val callbackOrder = CopyOnWriteArrayList<String>()
        val messageFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.stop",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerMessageHandler(
                MessageHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "message.first",
                        priority = 100,
                    ),
                    handler = EventAwareTestHandle(
                        onEvent = { event ->
                            event as PluginMessageEvent
                            callbackOrder += "message:first"
                            event.stopPropagation()
                        },
                    ),
                ),
            )
            hostApi.registerMessageHandler(
                MessageHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "message.second",
                        priority = 10,
                    ),
                    handler = EventAwareTestHandle(
                        onEvent = { callbackOrder += "message:second" },
                    ),
                ),
            )
        }
        val commandFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.stop.command",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerCommandHandler(
                CommandHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "command.stop",
                    ),
                    command = "echo",
                    handler = EventAwareTestHandle(
                        onEvent = { callbackOrder += "command" },
                    ),
                ),
            )
        }

        val engine = PluginV2DispatchEngine(
            logBus = logBus,
            clock = { 300L },
        )

        val result = engine.dispatchMessage(
            event = sampleMessageEvent(rawText = "/echo blocked"),
            snapshot = snapshotOf(messageFixture, commandFixture),
        )

        assertTrue(result.propagationStopped)
        assertEquals(listOf("message:first"), callbackOrder)
    }

    @Test
    fun dispatch_message_serializes_callbacks_within_the_same_runtime_session() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 400L })
        val concurrentCallbacks = AtomicInteger(0)
        val maxConcurrentCallbacks = AtomicInteger(0)
        val sessionFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.serial",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerMessageHandler(
                MessageHandlerRegistrationInput(
                    base = BaseHandlerRegistrationInput(
                        registrationKey = "message.serial",
                    ),
                    handler = EventAwareTestHandle(
                        onEvent = {
                            val current = concurrentCallbacks.incrementAndGet()
                            maxConcurrentCallbacks.updateAndGet { maxOf(it, current) }
                            delay(50)
                            concurrentCallbacks.decrementAndGet()
                        },
                    ),
                ),
            )
        }
        val engine = PluginV2DispatchEngine(
            logBus = logBus,
            clock = { 400L },
        )
        val snapshot = snapshotOf(sessionFixture)

        val first = async(Dispatchers.Default) {
            engine.dispatchMessage(
                event = sampleMessageEvent(rawText = "first"),
                snapshot = snapshot,
            )
        }
        val second = async(Dispatchers.Default) {
            engine.dispatchMessage(
                event = sampleMessageEvent(rawText = "second"),
                snapshot = snapshot,
            )
        }

        first.await()
        second.await()

        assertEquals(1, maxConcurrentCallbacks.get())
    }

    @Test
    fun dispatch_stage_and_pipeline_entrypoint_resolve_llm_hook_candidates() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 500L })
        val requestVisibleToHook = CopyOnWriteArrayList<String>()
        val fixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.llm.request",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "llm.request.primary",
                    hook = "on_llm_request",
                    priority = 100,
                    handler = EventAwareTestHandle { payload ->
                        val requestPayload = payload as PluginV2LlmRequestPayload
                        requestVisibleToHook += requestPayload.request.selectedModelId
                        requestPayload.request.selectedModelId = "model-b-2"
                    },
                ),
            )
        }
        val engine = PluginV2DispatchEngine(
            logBus = logBus,
            clock = { 500L },
        )
        val request = PluginProviderRequest(
            requestId = "req-dispatch-llm",
            availableProviderIds = listOf("provider-a", "provider-b"),
            availableModelIdsByProvider = mapOf(
                "provider-a" to listOf("model-a-1"),
                "provider-b" to listOf("model-b-1", "model-b-2"),
            ),
            conversationId = "conversation-dispatch-llm",
            messageIds = listOf("msg-dispatch-llm"),
            llmInputSnapshot = "llm-input",
            selectedProviderId = "provider-b",
            selectedModelId = "model-b-1",
            messages = listOf(
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.USER,
                    parts = listOf(PluginProviderMessagePartDto.TextPart("hello")),
                ),
            ),
            streamingEnabled = false,
        )
        val event = sampleMessageEvent(rawText = "dispatch llm")
        val payload = PluginV2LlmRequestPayload(
            event = event,
            request = request,
        )

        val planning = engine.dispatch(
            stage = PluginV2InternalStage.LlmRequest,
            snapshot = snapshotOf(fixture),
        )
        val dispatchResult = engine.dispatchLlmStage(
            stage = PluginV2InternalStage.LlmRequest,
            payload = payload,
            snapshot = snapshotOf(fixture),
        )

        assertEquals(PluginV2InternalStage.LlmRequest, planning.stage)
        assertEquals(1, planning.envelopes.size)
        assertEquals(PluginV2DispatchStage.LlmRequest, planning.envelopes.single().stage)
        assertEquals(listOf("model-b-1"), requestVisibleToHook)
        assertEquals("model-b-2", payload.request.selectedModelId)
        assertEquals(
            listOf("hdl::com.example.v2.dispatch.llm.request::llm_hook::llm.request.primary"),
            dispatchResult.invokedHandlerIds,
        )
    }

    @Test
    fun llm_stage_errors_route_to_on_plugin_error_through_lifecycle_manager() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 550L })
        val errorEvents = CopyOnWriteArrayList<PluginErrorHookArgs>()
        val throwingFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.llm.throwing",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerLlmHook(
                LlmHookRegistrationInput(
                    registrationKey = "llm.request.throwing",
                    hook = "on_llm_request",
                    priority = 100,
                    handler = EventAwareTestHandle {
                        error("boom:llm-request")
                    },
                ),
            )
        }
        val errorHookFixture = pluginFixture(
            pluginId = "com.example.v2.dispatch.llm.error-hook",
            logBus = logBus,
        ) { hostApi, _ ->
            hostApi.registerLifecycleHandler(
                LifecycleHandlerRegistrationInput(
                    registrationKey = "lifecycle.error",
                    hook = PluginLifecycleHookSurface.OnPluginError.wireValue,
                    handler = object : PluginV2EventAwareCallbackHandle {
                        override fun invoke() = Unit

                        override suspend fun handleEvent(event: PluginErrorEventPayload) {
                            errorEvents += event as PluginErrorHookArgs
                        }
                    },
                ),
            )
        }
        val store = PluginV2ActiveRuntimeStore(
            logBus = logBus,
            clock = { 550L },
        )
        store.commitLoadedRuntime(throwingFixture.entry)
        store.commitLoadedRuntime(errorHookFixture.entry)
        val engine = PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 550L },
        )
        val payload = PluginV2LlmRequestPayload(
            event = sampleMessageEvent(rawText = "dispatch error"),
            request = PluginProviderRequest(
                requestId = "req-dispatch-error",
                availableProviderIds = listOf("provider-a"),
                availableModelIdsByProvider = mapOf(
                    "provider-a" to listOf("model-a-1"),
                ),
                conversationId = "conversation-error",
                messageIds = listOf("msg-error"),
                llmInputSnapshot = "llm-input",
                selectedProviderId = "provider-a",
                selectedModelId = "model-a-1",
                messages = listOf(
                    PluginProviderMessageDto(
                        role = PluginProviderMessageRole.USER,
                        parts = listOf(PluginProviderMessagePartDto.TextPart("hello")),
                    ),
                ),
                streamingEnabled = false,
            ),
        )

        val result = engine.dispatchLlmStage(
            stage = PluginV2InternalStage.LlmRequest,
            payload = payload,
        )

        assertEquals(
            listOf("hdl::com.example.v2.dispatch.llm.throwing::llm_hook::llm.request.throwing"),
            result.invokedHandlerIds,
        )
        assertEquals(1, errorEvents.size)
        assertEquals("com.example.v2.dispatch.llm.throwing", errorEvents.single().plugin_name)
        assertEquals("boom:llm-request", errorEvents.single().error.message)
    }

    private fun pluginFixture(
        pluginId: String,
        logBus: PluginRuntimeLogBus,
        register: (PluginV2BootstrapHostApi, PluginV2RuntimeSession) -> Unit,
    ): RuntimeFixture {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val hostApi = PluginV2BootstrapHostApi(session, logBus = logBus, clock = { 1L })
        register(hostApi, session)

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
            conversationId = "group-1",
            senderId = "user-1",
            timestampEpochMillis = 1_710_000_000_000L,
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

    private fun List<String>.filterRelevantCodes(): List<String> {
        return filter { code ->
            code == "filter_rejected" || code == "custom_filter_failed"
        }
    }

    private open class EventAwareTestHandle(
        private val onEvent: suspend (PluginErrorEventPayload) -> Unit,
    ) : PluginV2EventAwareCallbackHandle {
        override fun invoke() = Unit

        override suspend fun handleEvent(event: PluginErrorEventPayload) {
            onEvent(event)
        }
    }

    private class FullFeaturedTestHandle(
        private val onCustomFilter: suspend (PluginV2CustomFilterRequest) -> Boolean,
        onEvent: suspend (PluginErrorEventPayload) -> Unit,
    ) : EventAwareTestHandle(onEvent), PluginV2CustomFilterAwareCallbackHandle {
        override suspend fun evaluateCustomFilter(request: PluginV2CustomFilterRequest): Boolean {
            return onCustomFilter(request)
        }
    }
}

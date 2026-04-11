package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.chat.MessageType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PluginV2FilterEvaluatorTest {
    @Test
    fun evaluate_rejects_with_fixed_builtin_order_before_custom_filter() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 123L })
        val customFilterCalls = AtomicInteger(0)
        val handle = FilterAwareTestHandle(
            onCustomFilter = {
                customFilterCalls.incrementAndGet()
                true
            },
        )
        val fixture = compileMessageFixture(
            pluginId = "com.example.v2.filter.reject",
            logBus = logBus,
            declaredFilters = listOf(
                BootstrapFilterDescriptor.regex("permission_type:net.access"),
                BootstrapFilterDescriptor.command("platform_adapter_type:onebot"),
                BootstrapFilterDescriptor.message("event_message_type:friend"),
                BootstrapFilterDescriptor.message("custom_filter:gate"),
            ),
            handle = handle,
        )
        val evaluator = PluginV2FilterEvaluator(
            logBus = logBus,
            clock = { 123L },
        )

        val result = evaluator.evaluate(
            session = fixture.session,
            descriptor = fixture.handler,
            event = sampleMessageEvent(
                rawText = "hello",
                platformAdapterType = "onebot",
                messageType = MessageType.GroupMessage,
            ),
        )

        assertTrue(result is PluginV2FilterEvaluationResult.Reject)
        result as PluginV2FilterEvaluationResult.Reject
        assertEquals("event_message_type", result.reasonCode)
        assertEquals(0, customFilterCalls.get())
        assertEquals(
            listOf("filter_rejected"),
            logBus.snapshot(limit = 10).map { it.code }.filterRelevantCodes(),
        )
    }

    @Test
    fun evaluate_passes_restricted_event_and_plugin_context_snapshots_to_custom_filter() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 456L })
        var capturedRequest: PluginV2CustomFilterRequest? = null
        val handle = FilterAwareTestHandle(
            onCustomFilter = { request ->
                capturedRequest = request
                true
            },
        )
        val fixture = compileMessageFixture(
            pluginId = "com.example.v2.filter.snapshot",
            logBus = logBus,
            declaredFilters = listOf(
                BootstrapFilterDescriptor.command("platform_adapter_type:onebot"),
                BootstrapFilterDescriptor.message("event_message_type:group"),
                BootstrapFilterDescriptor.regex("permission_type:net.access"),
                BootstrapFilterDescriptor.message("custom_filter:allow"),
            ),
            handle = handle,
        )
        val evaluator = PluginV2FilterEvaluator(
            logBus = logBus,
            clock = { 456L },
        )

        val event = sampleMessageEvent(
            rawText = "/echo hello",
            platformAdapterType = "onebot",
            messageType = MessageType.GroupMessage,
        ).also {
            it.workingText = "/echo hello"
            it.extras = mapOf("source" to "adapter", "nested" to mapOf("count" to 1))
        }

        val result = evaluator.evaluate(
            session = fixture.session,
            descriptor = fixture.handler,
            event = event,
        )

        assertTrue(result is PluginV2FilterEvaluationResult.Pass)
        val request = checkNotNull(capturedRequest)
        assertEquals("evt-1", request.eventView.eventId)
        assertEquals("onebot", request.eventView.platformAdapterType)
        assertEquals("group", request.eventView.messageType)
        assertEquals("/echo hello", request.eventView.workingText)
        assertEquals(mapOf("source" to "adapter", "nested" to mapOf("count" to 1)), request.eventView.extrasSnapshot)
        assertEquals("com.example.v2.filter.snapshot", request.pluginContextView.pluginId)
        assertEquals("1.0.0", request.pluginContextView.pluginVersion)
        assertEquals("js_quickjs", request.pluginContextView.runtimeKind)
        assertEquals(1, request.pluginContextView.runtimeApiVersion)
        assertEquals(listOf("net.access"), request.pluginContextView.declaredPermissionIds)
        assertEquals(listOf("net.access"), request.pluginContextView.grantedPermissionIds)
        assertEquals("LOCAL_FILE", request.pluginContextView.sourceType)
    }

    @Test
    fun evaluate_translates_custom_filter_exception_to_error_stop_and_user_visible_feedback() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 789L })
        val handle = FilterAwareTestHandle(
            onCustomFilter = {
                error("boom")
            },
        )
        val fixture = compileMessageFixture(
            pluginId = "com.example.v2.filter.failure",
            logBus = logBus,
            declaredFilters = listOf(
                BootstrapFilterDescriptor.message("event_message_type:group"),
                BootstrapFilterDescriptor.command("platform_adapter_type:onebot"),
                BootstrapFilterDescriptor.regex("permission_type:net.access"),
                BootstrapFilterDescriptor.message("custom_filter:explode"),
            ),
            handle = handle,
        )
        val evaluator = PluginV2FilterEvaluator(
            logBus = logBus,
            clock = { 789L },
        )

        val result = evaluator.evaluate(
            session = fixture.session,
            descriptor = fixture.handler,
            event = sampleMessageEvent(
                rawText = "/explode",
                platformAdapterType = "onebot",
                messageType = MessageType.GroupMessage,
            ),
        )

        assertTrue(result is PluginV2FilterEvaluationResult.ErrorStop)
        result as PluginV2FilterEvaluationResult.ErrorStop
        assertEquals("custom_filter_failed", result.logCode)
        assertFalse(result.userVisibleMessage.isBlank())
        val records = logBus.snapshot(limit = 10)
        assertEquals(listOf("custom_filter_failed"), records.map { it.code }.filterRelevantCodes())
        assertTrue(records.none { it.code == "on_plugin_error" })
    }

    @Test
    fun evaluate_rethrows_cancellation_from_custom_filter_without_logging_failure() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 790L })
        val handle = FilterAwareTestHandle(
            onCustomFilter = {
                throw CancellationException("cancelled")
            },
        )
        val fixture = compileMessageFixture(
            pluginId = "com.example.v2.filter.cancelled",
            logBus = logBus,
            declaredFilters = listOf(
                BootstrapFilterDescriptor.message("event_message_type:group"),
                BootstrapFilterDescriptor.command("platform_adapter_type:onebot"),
                BootstrapFilterDescriptor.regex("permission_type:net.access"),
                BootstrapFilterDescriptor.message("custom_filter:cancel"),
            ),
            handle = handle,
        )
        val evaluator = PluginV2FilterEvaluator(
            logBus = logBus,
            clock = { 790L },
        )

        try {
            evaluator.evaluate(
                session = fixture.session,
                descriptor = fixture.handler,
                event = sampleMessageEvent(
                    rawText = "/cancel",
                    platformAdapterType = "onebot",
                    messageType = MessageType.GroupMessage,
                ),
            )
            fail("Expected CancellationException")
        } catch (error: CancellationException) {
            assertEquals("cancelled", error.message)
        }

        val records = logBus.snapshot(limit = 10)
        assertTrue(records.none { it.code == "custom_filter_failed" })
    }

    private fun List<String>.filterRelevantCodes(): List<String> {
        return filter { code ->
            code == "filter_rejected" || code == "custom_filter_failed"
        }
    }

    private fun compileMessageFixture(
        pluginId: String,
        logBus: PluginRuntimeLogBus,
        declaredFilters: List<BootstrapFilterDescriptor>,
        handle: PluginV2CallbackHandle,
    ): MessageFixture {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val hostApi = PluginV2BootstrapHostApi(session, logBus = logBus, clock = { 1L })
        hostApi.registerMessageHandler(
            MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "message.primary",
                    declaredFilters = declaredFilters,
                ),
                handler = handle,
            ),
        )

        val compiled = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L })
            .compile(requireNotNull(session.rawRegistry))
            .compiledRegistry!!
        session.attachCompiledRegistry(compiled)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        return MessageFixture(
            session = session,
            handler = compiled.handlerRegistry.messageHandlers.single(),
        )
    }

    private fun sampleMessageEvent(
        rawText: String,
        platformAdapterType: String,
        messageType: MessageType,
    ): PluginMessageEvent {
        return PluginMessageEvent(
            eventId = "evt-1",
            platformAdapterType = platformAdapterType,
            messageType = messageType,
            conversationId = "conversation-1",
            senderId = "sender-1",
            timestampEpochMillis = 1_710_000_000_000L,
            rawText = rawText,
            rawMentions = listOf("@astrbot"),
            initialWorkingText = rawText,
            normalizedMentions = listOf("@astrbot"),
            extras = emptyMap(),
        )
    }

    private data class MessageFixture(
        val session: PluginV2RuntimeSession,
        val handler: PluginV2CompiledMessageHandler,
    )

    private class FilterAwareTestHandle(
        private val onCustomFilter: suspend (PluginV2CustomFilterRequest) -> Boolean,
    ) : PluginV2CustomFilterAwareCallbackHandle {
        override fun invoke() = Unit

        override suspend fun evaluateCustomFilter(request: PluginV2CustomFilterRequest): Boolean {
            return onCustomFilter(request)
        }
    }
}

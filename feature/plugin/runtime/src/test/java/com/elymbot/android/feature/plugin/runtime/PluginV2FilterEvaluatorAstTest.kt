package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.chat.MessageType
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2FilterEvaluatorAstTest {
    @Test
    fun allOf_short_circuits_reject_before_custom_filter() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val customCalls = AtomicInteger(0)
        val fixture = compileMessageFixture(
            pluginId = "com.example.filter.eval.allof",
            logBus = logBus,
            filterExpression = PluginV2FilterExpression.AllOf(
                listOf(
                    PluginV2FilterExpression.Builtin(
                        kind = PluginV2BuiltinFilterKind.EventMessageType,
                        value = "friend",
                    ),
                    PluginV2FilterExpression.Custom(name = "gate"),
                ),
            ),
            handle = FilterHandle {
                customCalls.incrementAndGet()
                true
            },
        )

        val result = PluginV2FilterEvaluator(logBus = logBus, clock = { 1L }).evaluate(
            session = fixture.session,
            descriptor = fixture.handler,
            event = sampleMessageEvent(messageType = MessageType.GroupMessage),
        )

        val reject = result as PluginV2FilterEvaluationResult.Reject
        assertEquals("event_message_type", reject.reasonCode)
        assertEquals("$.allOf[0]", reject.astPath)
        assertEquals(0, customCalls.get())
        val log = logBus.snapshot().single { it.code == "filter_rejected" }
        assertEquals("$.allOf[0]", log.metadata["filterAstPath"])
    }

    @Test
    fun anyOf_allows_later_branch_after_custom_filter_returns_false() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 2L })
        val customCalls = AtomicInteger(0)
        val fixture = compileMessageFixture(
            pluginId = "com.example.filter.eval.anyof",
            logBus = logBus,
            filterExpression = PluginV2FilterExpression.AnyOf(
                listOf(
                    PluginV2FilterExpression.Custom(name = "soft-deny"),
                    PluginV2FilterExpression.Builtin(
                        kind = PluginV2BuiltinFilterKind.PlatformAdapterType,
                        value = "onebot",
                    ),
                ),
            ),
            handle = FilterHandle {
                customCalls.incrementAndGet()
                false
            },
        )

        val result = PluginV2FilterEvaluator(logBus = logBus, clock = { 2L }).evaluate(
            session = fixture.session,
            descriptor = fixture.handler,
            event = sampleMessageEvent(platformAdapterType = "onebot"),
        )

        assertTrue(result is PluginV2FilterEvaluationResult.Pass)
        assertEquals(1, customCalls.get())
        assertTrue(logBus.snapshot().none { it.code == "filter_rejected" })
    }

    @Test
    fun not_inverts_child_filter_result() = runBlocking {
        val fixture = compileMessageFixture(
            pluginId = "com.example.filter.eval.not",
            filterExpression = PluginV2FilterExpression.Not(
                PluginV2FilterExpression.Builtin(
                    kind = PluginV2BuiltinFilterKind.PermissionType,
                    value = "blocked",
                ),
            ),
            handle = FilterHandle { true },
        )

        val result = PluginV2FilterEvaluator(clock = { 3L }).evaluate(
            session = fixture.session,
            descriptor = fixture.handler,
            event = sampleMessageEvent(),
        )

        assertTrue(result is PluginV2FilterEvaluationResult.Pass)
    }

    @Test
    fun custom_filter_timeout_still_stops_ast_evaluation() = runBlocking {
        val fixture = compileMessageFixture(
            pluginId = "com.example.filter.eval.timeout",
            filterExpression = PluginV2FilterExpression.Custom(name = "slow"),
            handle = FilterHandle {
                delay(3_000L)
                true
            },
        )

        val result = PluginV2FilterEvaluator(clock = { 4L }).evaluate(
            session = fixture.session,
            descriptor = fixture.handler,
            event = sampleMessageEvent(),
        )

        val stop = result as PluginV2FilterEvaluationResult.ErrorStop
        assertEquals("custom_filter_timeout", stop.logCode)
    }

    @Test
    fun custom_filter_exception_still_returns_user_visible_error_stop() = runBlocking {
        val fixture = compileMessageFixture(
            pluginId = "com.example.filter.eval.failure",
            filterExpression = PluginV2FilterExpression.Custom(name = "explode"),
            handle = FilterHandle {
                error("boom")
            },
        )

        val result = PluginV2FilterEvaluator(clock = { 5L }).evaluate(
            session = fixture.session,
            descriptor = fixture.handler,
            event = sampleMessageEvent(),
        )

        val stop = result as PluginV2FilterEvaluationResult.ErrorStop
        assertEquals("custom_filter_failed", stop.logCode)
        assertTrue(stop.userVisibleMessage.isNotBlank())
    }

    private fun compileMessageFixture(
        pluginId: String,
        filterExpression: PluginV2FilterExpression,
        handle: PluginV2CallbackHandle,
        logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
    ): MessageFixture {
        val session = agentSession(pluginId)
        val hostApi = PluginV2BootstrapHostApi(session = session, logBus = logBus, clock = { 1L })
        hostApi.registerMessageHandler(
            MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "message.primary",
                    filterExpression = filterExpression,
                ),
                handler = handle,
            ),
        )
        val compiled = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L })
            .compile(session.requireBootstrapRawRegistry())
            .compiledRegistry!!
        session.attachCompiledRegistry(compiled)
        session.transitionTo(PluginV2RuntimeSessionState.Active)
        return MessageFixture(session, compiled.handlerRegistry.messageHandlers.single())
    }

    private fun sampleMessageEvent(
        platformAdapterType: String = "onebot",
        messageType: MessageType = MessageType.GroupMessage,
    ): PluginMessageEvent {
        return PluginMessageEvent(
            eventId = "evt-filter",
            platformAdapterType = platformAdapterType,
            messageType = messageType,
            conversationId = "conversation-filter",
            senderId = "sender-filter",
            timestampEpochMillis = 1L,
            rawText = "hello",
            rawMentions = emptyList(),
            initialWorkingText = "hello",
            normalizedMentions = emptyList(),
            extras = emptyMap(),
        )
    }

    private data class MessageFixture(
        val session: PluginV2RuntimeSession,
        val handler: PluginV2CompiledMessageHandler,
    )

    private class FilterHandle(
        private val block: suspend (PluginV2CustomFilterRequest) -> Boolean,
    ) : PluginV2CustomFilterAwareCallbackHandle {
        override fun invoke() = Unit

        override suspend fun evaluateCustomFilter(request: PluginV2CustomFilterRequest): Boolean {
            return block(request)
        }
    }
}

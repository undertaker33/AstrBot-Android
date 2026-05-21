package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.chat.MessageType
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2MessageStreamApiTest {

    @Test
    fun open_append_close_updates_current_conversation_stream() = runTest {
        val port = RecordingMessageStreamPort()
        val api = streamApi(port)

        val open = api.openStream(
            context = allowedContext(platformAdapterType = "app_chat"),
            request = PluginV2MessageStreamOpenRequest(markdown = true),
        ) as PluginV2HostApiResult.Success
        val stream = open.value as PluginV2MessageStreamOpenResult
        api.append(stream.streamId, "hello")
        api.replace(stream.streamId, "hello world")
        api.close(stream.streamId)

        assertEquals("conversation-current", port.opened.single().conversationId)
        assertEquals(listOf("hello"), port.appended.map { it.text })
        assertEquals(listOf("hello world"), port.replaced.map { it.text })
        assertEquals(listOf(stream.streamId), port.closed.map { it.streamId })
    }

    @Test
    fun openStream_requires_message_stream_permission() = runTest {
        val result = streamApi(RecordingMessageStreamPort()).openStream(
            context = allowedContext(granted = false),
            request = PluginV2MessageStreamOpenRequest(),
        )

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2HostApiErrorCodes.PERMISSION_DENIED, failure.error.code)
    }

    @Test
    fun chunk_limit_overflow_returns_structured_error() = runTest {
        val api = streamApi(RecordingMessageStreamPort(), limits = PluginV2MessageStreamLimits(maxChunks = 1))
        val open = api.openStream(allowedContext(), PluginV2MessageStreamOpenRequest()) as PluginV2HostApiResult.Success
        val stream = open.value as PluginV2MessageStreamOpenResult

        val first = api.append(stream.streamId, "one")
        val second = api.append(stream.streamId, "two")

        assertTrue(first is PluginV2HostApiResult.Success)
        val failure = second as PluginV2HostApiResult.Failure
        assertEquals(PluginV2MessageStreamApi.STREAM_LIMIT_EXCEEDED, failure.error.code)
    }

    @Test
    fun append_after_close_returns_structured_error() = runTest {
        val api = streamApi(RecordingMessageStreamPort())
        val open = api.openStream(allowedContext(), PluginV2MessageStreamOpenRequest()) as PluginV2HostApiResult.Success
        val stream = open.value as PluginV2MessageStreamOpenResult

        api.close(stream.streamId)
        val result = api.append(stream.streamId, "late")

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2MessageStreamApi.STREAM_ALREADY_CLOSED, failure.error.code)
    }

    @Test
    fun closeOpenStreamsForRequest_auto_closes_unclosed_stream_when_handler_finishes() = runTest {
        val port = RecordingMessageStreamPort()
        val api = streamApi(port)
        val open = api.openStream(allowedContext(requestId = "request-auto"), PluginV2MessageStreamOpenRequest())
            as PluginV2HostApiResult.Success
        val stream = open.value as PluginV2MessageStreamOpenResult

        api.closeOpenStreamsForRequest("request-auto", failureMessage = "")

        assertEquals(listOf(stream.streamId), port.closed.map { it.streamId })
        assertFalse(port.failed.any { it.streamId == stream.streamId })
    }

    @Test
    fun dispatchMessage_auto_closes_unclosed_stream_when_handler_finishes() = runTest {
        val port = RecordingMessageStreamPort()
        val api = streamApi(port)
        val session = activeMessageSession(
            object : PluginV2EventAwareCallbackHandle {
                override fun invoke() = Unit
                override suspend fun handleEvent(event: PluginErrorEventPayload) {
                    api.openStream(
                        context = allowedContext(
                            requestId = "request-dispatch",
                            platformAdapterType = (event as PluginMessageEvent).platformAdapterType,
                        ),
                        request = PluginV2MessageStreamOpenRequest(),
                    )
                }
            },
        )

        PluginV2DispatchEngine(messageStreamApi = api).dispatchMessage(
            event = messageEvent(),
            snapshot = snapshotFor(session),
        )

        assertEquals(listOf("stream-1"), port.closed.map { it.streamId })
        assertTrue(port.failed.isEmpty())
    }

    @Test
    fun dispatchMessage_fails_unclosed_stream_when_handler_is_cancelled() = runTest {
        val port = RecordingMessageStreamPort()
        val api = streamApi(port)
        val session = activeMessageSession(
            object : PluginV2EventAwareCallbackHandle {
                override fun invoke() = Unit
                override suspend fun handleEvent(event: PluginErrorEventPayload) {
                    api.openStream(
                        context = allowedContext(
                            requestId = "request-cancelled",
                            platformAdapterType = (event as PluginMessageEvent).platformAdapterType,
                        ),
                        request = PluginV2MessageStreamOpenRequest(),
                    )
                    throw CancellationException("handler cancelled")
                }
            },
        )
        var cancellation: CancellationException? = null

        try {
            PluginV2DispatchEngine(messageStreamApi = api).dispatchMessage(
                event = messageEvent(),
                snapshot = snapshotFor(session),
            )
        } catch (error: CancellationException) {
            cancellation = error
        }

        assertEquals("handler cancelled", cancellation?.message)
        assertEquals(listOf("stream-1"), port.failed.map { it.streamId })
        assertTrue(port.failed.single().message.contains("cancelled"))
    }

    @Test
    fun qq_stream_uses_final_on_close_fallback_contract() = runTest {
        val port = RecordingMessageStreamPort(
            openResult = PluginV2MessageStreamPortOpenResult(
                streamId = "stream-qq",
                platformMode = PluginV2MessageStreamPlatformMode.FinalOnClose,
                receiptId = "",
            ),
        )
        val open = streamApi(port).openStream(
            context = allowedContext(platformAdapterType = "onebot"),
            request = PluginV2MessageStreamOpenRequest(),
        ) as PluginV2HostApiResult.Success

        val result = open.value as PluginV2MessageStreamOpenResult
        assertEquals(PluginV2MessageStreamPlatformMode.FinalOnClose, result.platformMode)
    }

    private fun streamApi(
        port: PluginV2MessageStreamPort,
        limits: PluginV2MessageStreamLimits = PluginV2MessageStreamLimits(),
    ): PluginV2MessageStreamApi {
        return PluginV2MessageStreamApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(
                    logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
                    clock = { 1L },
                ),
                clock = { 1L },
            ),
            streamPort = port,
            limits = limits,
            clock = { 1L },
        )
    }

    private fun allowedContext(
        requestId: String = "request-stream",
        platformAdapterType: String = "app_chat",
        granted: Boolean = true,
    ): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.stream",
            pluginVersion = "1.0.0",
            requestId = requestId,
            conversationId = "conversation-current",
            platformAdapterType = platformAdapterType,
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.MESSAGE_STREAM),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.MESSAGE_STREAM,
                    title = "Stream",
                    granted = granted,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            triggerPermissionWhitelist = if (granted) setOf(PluginV2HostApiPermissions.MESSAGE_STREAM) else emptySet(),
        )
    }

    private fun activeMessageSession(handle: PluginV2CallbackHandle): PluginV2RuntimeSession {
        val session = PluginV2RuntimeSession(
            installRecord = PluginV2ScheduledHandlerRegistryTestSupport.installRecord(
                pluginId = "plugin.stream",
                permissionIds = setOf(PluginV2HostApiPermissions.MESSAGE_STREAM),
            ),
            sessionInstanceId = "session-stream",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        val raw = PluginV2RawRegistry(session.pluginId)
        val token = session.allocateCallbackToken(handle)
        raw.appendMessageHandler(
            callbackToken = token,
            descriptor = MessageHandlerRegistrationInput(handler = handle),
        )
        val compiled = checkNotNull(PluginV2RegistryCompiler().compile(raw).compiledRegistry)
        session.attachRawRegistry(raw)
        session.attachCompiledRegistry(compiled)
        session.transitionTo(PluginV2RuntimeSessionState.Active)
        return session
    }

    private fun snapshotFor(session: PluginV2RuntimeSession): PluginV2ActiveRuntimeSnapshot {
        val compiled = checkNotNull(session.compiledRegistry) as PluginV2CompiledRegistrySnapshot
        val entry = PluginV2ActiveRuntimeEntry(
            session = session,
            compiledRegistry = compiled,
            lastBootstrapSummary = PluginV2BootstrapSummary(
                pluginId = session.pluginId,
                sessionInstanceId = session.sessionInstanceId,
                compiledAtEpochMillis = 1L,
                handlerCount = 1,
                warningCount = 0,
                errorCount = 0,
            ),
            diagnostics = emptyList(),
            callbackTokens = session.snapshotCallbackTokens(),
        )
        return PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = mapOf(session.pluginId to entry),
            activeSessionsByPluginId = mapOf(session.pluginId to session),
            compiledRegistriesByPluginId = mapOf(session.pluginId to compiled),
        )
    }

    private fun messageEvent(): PluginMessageEvent {
        return PluginMessageEvent(
            eventId = "event-stream",
            platformAdapterType = "onebot",
            messageType = MessageType.GroupMessage,
            conversationId = "conversation-current",
            senderId = "sender-1",
            timestampEpochMillis = 1L,
            rawText = "stream please",
        )
    }

    private class RecordingMessageStreamPort(
        private val openResult: PluginV2MessageStreamPortOpenResult = PluginV2MessageStreamPortOpenResult(
            streamId = "stream-1",
            platformMode = PluginV2MessageStreamPlatformMode.Editable,
            receiptId = "message-1",
        ),
    ) : PluginV2MessageStreamPort {
        val opened = mutableListOf<PluginV2MessageStreamPortOpenRequest>()
        val appended = mutableListOf<PluginV2MessageStreamPortChunkRequest>()
        val replaced = mutableListOf<PluginV2MessageStreamPortChunkRequest>()
        val closed = mutableListOf<PluginV2MessageStreamPortCloseRequest>()
        val failed = mutableListOf<PluginV2MessageStreamPortFailRequest>()

        override suspend fun open(request: PluginV2MessageStreamPortOpenRequest): PluginV2MessageStreamPortOpenResult {
            opened += request
            return openResult
        }

        override suspend fun append(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult {
            appended += request
            return PluginV2MessageStreamPortMutationResult(success = true)
        }

        override suspend fun replace(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult {
            replaced += request
            return PluginV2MessageStreamPortMutationResult(success = true)
        }

        override suspend fun close(request: PluginV2MessageStreamPortCloseRequest): PluginV2MessageStreamPortMutationResult {
            closed += request
            return PluginV2MessageStreamPortMutationResult(success = true)
        }

        override suspend fun fail(request: PluginV2MessageStreamPortFailRequest): PluginV2MessageStreamPortMutationResult {
            failed += request
            return PluginV2MessageStreamPortMutationResult(success = true)
        }
    }
}

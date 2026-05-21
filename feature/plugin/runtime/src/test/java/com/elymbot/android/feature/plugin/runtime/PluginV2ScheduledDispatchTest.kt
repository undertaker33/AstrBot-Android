package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PluginV2ScheduledDispatchTest {

    @Test
    fun dispatchScheduledHandler_invokes_matching_v2_handler_with_schedule_payload() = runTest {
        var received: PluginErrorEventPayload? = null
        val session = activeSession(
            object : PluginV2EventAwareCallbackHandle {
                override fun invoke() = Unit
                override suspend fun handleEvent(event: PluginErrorEventPayload) {
                    received = event
                }
            },
        )
        val snapshot = PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = mapOf(
                session.pluginId to PluginV2ActiveRuntimeEntry(
                    session = session,
                    compiledRegistry = checkNotNull(session.compiledRegistry),
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
                ),
            ),
            activeSessionsByPluginId = mapOf(session.pluginId to session),
            compiledRegistriesByPluginId = mapOf(session.pluginId to checkNotNull(session.compiledRegistry) as PluginV2CompiledRegistrySnapshot),
        )

        val result = PluginV2ScheduledDispatchEngine().dispatch(
            event = PluginV2ScheduledHandlerEvent(
                pluginId = session.pluginId,
                handlerKey = "daily-summary",
                jobId = "job-1",
                conversationId = "conversation-1",
                platformAdapterType = "onebot",
                scheduledAtEpochMillis = 2_000L,
                triggerSource = PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE,
            ),
            snapshot = snapshot,
        )

        assertTrue(result.succeeded)
        val event = received as PluginV2ScheduledHandlerEvent
        assertEquals("job-1", event.jobId)
        assertEquals("conversation-1", event.conversationId)
        assertEquals(2_000L, event.scheduledAtEpochMillis)
        assertEquals(PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE, event.triggerSource)
    }

    @Test
    fun dispatchScheduledHandler_reports_missing_context_without_invoking_handler() = runTest {
        var invokeCount = 0
        val session = activeSession(
            object : PluginV2EventAwareCallbackHandle {
                override fun invoke() = Unit
                override suspend fun handleEvent(event: PluginErrorEventPayload) {
                    invokeCount++
                }
            },
        )

        val result = PluginV2ScheduledDispatchEngine().dispatch(
            event = scheduledEvent(session, conversationId = "", platformAdapterType = ""),
            snapshot = snapshotFor(session),
        )

        assertEquals(false, result.succeeded)
        assertEquals("missing_context", result.errorCode)
        assertEquals(0, invokeCount)
    }

    @Test
    fun dispatchScheduledHandler_auto_closes_unclosed_stream_when_handler_finishes() = runTest {
        val port = RecordingMessageStreamPort()
        val api = streamApi(port)
        val session = activeSession(
            object : PluginV2EventAwareCallbackHandle {
                override fun invoke() = Unit
                override suspend fun handleEvent(event: PluginErrorEventPayload) {
                    val scheduled = event as PluginV2ScheduledHandlerEvent
                    api.openStream(
                        context = allowedStreamContext(
                            pluginId = scheduled.pluginId,
                            requestId = scheduled.jobId,
                            conversationId = scheduled.conversationId,
                            platformAdapterType = scheduled.platformAdapterType,
                        ),
                        request = PluginV2MessageStreamOpenRequest(),
                    )
                }
            },
        )

        PluginV2ScheduledDispatchEngine(messageStreamFinalizerProvider = { api }).dispatch(
            event = scheduledEvent(session),
            snapshot = snapshotFor(session),
        )

        assertEquals(listOf("stream-1"), port.closed.map { it.streamId })
        assertTrue(port.failed.isEmpty())
    }

    @Test
    fun dispatchScheduledHandler_fails_unclosed_stream_when_handler_fails() = runTest {
        val port = RecordingMessageStreamPort()
        val api = streamApi(port)
        val session = activeSession(
            object : PluginV2EventAwareCallbackHandle {
                override fun invoke() = Unit
                override suspend fun handleEvent(event: PluginErrorEventPayload) {
                    val scheduled = event as PluginV2ScheduledHandlerEvent
                    api.openStream(
                        context = allowedStreamContext(
                            pluginId = scheduled.pluginId,
                            requestId = scheduled.jobId,
                            conversationId = scheduled.conversationId,
                            platformAdapterType = scheduled.platformAdapterType,
                        ),
                        request = PluginV2MessageStreamOpenRequest(),
                    )
                    error("scheduled boom")
                }
            },
        )

        try {
            PluginV2ScheduledDispatchEngine(messageStreamFinalizerProvider = { api }).dispatch(
                event = scheduledEvent(session),
                snapshot = snapshotFor(session),
            )
            fail("Expected scheduled handler failure")
        } catch (error: IllegalStateException) {
            assertEquals("scheduled boom", error.message)
        }

        assertEquals(listOf("stream-1"), port.failed.map { it.streamId })
        assertTrue(port.failed.single().message.contains("failed"))
    }

    @Test
    fun dispatchScheduledHandler_rethrows_cancellation_and_fails_unclosed_stream() = runTest {
        val port = RecordingMessageStreamPort()
        val api = streamApi(port)
        val session = activeSession(
            object : PluginV2EventAwareCallbackHandle {
                override fun invoke() = Unit
                override suspend fun handleEvent(event: PluginErrorEventPayload) {
                    val scheduled = event as PluginV2ScheduledHandlerEvent
                    api.openStream(
                        context = allowedStreamContext(
                            pluginId = scheduled.pluginId,
                            requestId = scheduled.jobId,
                            conversationId = scheduled.conversationId,
                            platformAdapterType = scheduled.platformAdapterType,
                        ),
                        request = PluginV2MessageStreamOpenRequest(),
                    )
                    throw CancellationException("scheduled cancelled")
                }
            },
        )

        try {
            PluginV2ScheduledDispatchEngine(messageStreamFinalizerProvider = { api }).dispatch(
                event = scheduledEvent(session),
                snapshot = snapshotFor(session),
            )
            fail("Expected CancellationException")
        } catch (error: CancellationException) {
            assertEquals("scheduled cancelled", error.message)
        }

        assertEquals(listOf("stream-1"), port.failed.map { it.streamId })
        assertTrue(port.failed.single().message.contains("cancelled"))
    }

    @Test
    fun dispatchScheduledHandler_reports_missing_handler_without_invoking_other_schedule() = runTest {
        var invokeCount = 0
        val session = activeSession(
            object : PluginV2EventAwareCallbackHandle {
                override fun invoke() = Unit
                override suspend fun handleEvent(event: PluginErrorEventPayload) {
                    invokeCount++
                }
            },
        )
        val snapshot = PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = emptyMap(),
            activeSessionsByPluginId = mapOf(session.pluginId to session),
            compiledRegistriesByPluginId = mapOf(session.pluginId to checkNotNull(session.compiledRegistry) as PluginV2CompiledRegistrySnapshot),
        )

        val result = PluginV2ScheduledDispatchEngine().dispatch(
            event = PluginV2ScheduledHandlerEvent(
                pluginId = session.pluginId,
                handlerKey = "missing",
                jobId = "job-1",
                conversationId = "conversation-1",
                scheduledAtEpochMillis = 2_000L,
                triggerSource = PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE,
            ),
            snapshot = snapshot,
        )

        assertEquals(false, result.succeeded)
        assertEquals("missing_schedule_handler", result.errorCode)
        assertEquals(0, invokeCount)
    }

    private fun scheduledEvent(
        session: PluginV2RuntimeSession,
        conversationId: String = "conversation-1",
        platformAdapterType: String = "onebot",
    ): PluginV2ScheduledHandlerEvent {
        return PluginV2ScheduledHandlerEvent(
            pluginId = session.pluginId,
            handlerKey = "daily-summary",
            jobId = "job-1",
            conversationId = conversationId,
            platformAdapterType = platformAdapterType,
            scheduledAtEpochMillis = 2_000L,
            triggerSource = PluginV2ScheduledHandlerLifecycle.TRIGGER_SOURCE,
        )
    }

    private fun snapshotFor(session: PluginV2RuntimeSession): PluginV2ActiveRuntimeSnapshot {
        val compiled = checkNotNull(session.compiledRegistry) as PluginV2CompiledRegistrySnapshot
        return PluginV2ActiveRuntimeSnapshot(
            activeRuntimeEntriesByPluginId = mapOf(
                session.pluginId to PluginV2ActiveRuntimeEntry(
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
                ),
            ),
            activeSessionsByPluginId = mapOf(session.pluginId to session),
            compiledRegistriesByPluginId = mapOf(session.pluginId to compiled),
        )
    }

    private fun streamApi(port: PluginV2MessageStreamPort): PluginV2MessageStreamApi {
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
            clock = { 1L },
        )
    }

    private fun allowedStreamContext(
        pluginId: String,
        requestId: String,
        conversationId: String,
        platformAdapterType: String,
    ): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = pluginId,
            pluginVersion = "1.0.0",
            requestId = requestId,
            conversationId = conversationId,
            platformAdapterType = platformAdapterType,
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.MESSAGE_STREAM),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.MESSAGE_STREAM,
                    title = "Stream",
                    granted = true,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.MESSAGE_STREAM),
        )
    }

    private fun activeSession(handle: PluginV2CallbackHandle): PluginV2RuntimeSession {
        val session = PluginV2RuntimeSession(
            installRecord = PluginV2ScheduledHandlerRegistryTestSupport.installRecord(
                pluginId = "plugin.schedule",
                permissionIds = setOf(
                    PluginV2HostApiPermissions.SCHEDULE_MANAGE,
                    PluginV2HostApiPermissions.MESSAGE_STREAM,
                ),
            ),
            sessionInstanceId = "session-schedule",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        val raw = PluginV2RawRegistry(session.pluginId)
        val token = session.allocateCallbackToken(handle)
        raw.appendScheduledHandler(
            callbackToken = token,
            descriptor = ScheduledHandlerRegistrationInput(
                key = "daily-summary",
                cron = "0 9 * * *",
                conversationId = "conversation-1",
                handler = handle,
            ),
        )
        val compiled = checkNotNull(PluginV2RegistryCompiler().compile(raw).compiledRegistry)
        session.attachRawRegistry(raw)
        session.attachCompiledRegistry(compiled)
        session.transitionTo(PluginV2RuntimeSessionState.Active)
        return session
    }

    private class RecordingMessageStreamPort : PluginV2MessageStreamPort {
        val opened = mutableListOf<PluginV2MessageStreamPortOpenRequest>()
        val closed = mutableListOf<PluginV2MessageStreamPortCloseRequest>()
        val failed = mutableListOf<PluginV2MessageStreamPortFailRequest>()

        override suspend fun open(request: PluginV2MessageStreamPortOpenRequest): PluginV2MessageStreamPortOpenResult {
            opened += request
            return PluginV2MessageStreamPortOpenResult(
                streamId = "stream-1",
                platformMode = PluginV2MessageStreamPlatformMode.Editable,
                receiptId = "message-1",
            )
        }

        override suspend fun append(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult =
            PluginV2MessageStreamPortMutationResult(success = true)

        override suspend fun replace(request: PluginV2MessageStreamPortChunkRequest): PluginV2MessageStreamPortMutationResult =
            PluginV2MessageStreamPortMutationResult(success = true)

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

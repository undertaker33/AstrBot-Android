package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2MessageSendApiTest {

    @Test
    fun current_app_chat_conversation_sends_through_port() = runTest {
        val port = RecordingMessageSendPort()
        val api = messageSendApi(port)

        val result = api.send(
            context = allowedContext(
                conversationId = "app-conversation",
                platformAdapterType = "app_chat",
            ),
            request = PluginV2MessageSendRequest(
                text = "done",
                markdown = true,
            ),
        )

        val success = result as PluginV2HostApiResult.Success
        val response = success.value as PluginV2MessageSendResult
        assertEquals(PluginV2MessageSendApi.HOST_API_MESSAGE_SEND, success.api)
        assertEquals("app-conversation", response.conversationId)
        assertEquals("app_chat", response.platformAdapterType)
        assertEquals(listOf("receipt-app-conversation"), response.receiptIds)
        assertEquals(4, response.messageLength)
        val sent = port.requests.single()
        assertEquals("app-conversation", sent.conversationId)
        assertEquals("app_chat", sent.platformAdapterType)
        assertTrue(sent.markdown)
        assertEquals("done", sent.text)
    }

    @Test
    fun current_onebot_conversation_sends_through_same_port() = runTest {
        val port = RecordingMessageSendPort()
        val result = messageSendApi(port).send(
            context = allowedContext(
                conversationId = "qq-conversation",
                platformAdapterType = "onebot",
            ),
            request = PluginV2MessageSendRequest(text = "qq done"),
        )

        val success = result as PluginV2HostApiResult.Success
        val response = success.value as PluginV2MessageSendResult
        assertEquals("qq-conversation", response.conversationId)
        assertEquals("onebot", response.platformAdapterType)
        assertEquals(listOf("receipt-qq-conversation"), response.receiptIds)
        assertEquals("onebot", port.requests.single().platformAdapterType)
    }

    @Test
    fun missing_current_conversation_scope_returns_structured_error() = runTest {
        val result = messageSendApi(RecordingMessageSendPort()).send(
            context = allowedContext(conversationId = ""),
            request = PluginV2MessageSendRequest(text = "hello"),
        )

        assertFailureCode(result, PluginV2MessageSendApi.MISSING_SESSION_SCOPE)
    }

    @Test
    fun missing_permission_returns_permission_denied() = runTest {
        val result = messageSendApi(RecordingMessageSendPort()).send(
            context = allowedContext(granted = false),
            request = PluginV2MessageSendRequest(text = "hello"),
        )

        assertFailureCode(result, PluginV2HostApiErrorCodes.PERMISSION_DENIED)
    }

    @Test
    fun explicit_non_current_conversation_returns_scope_violation() = runTest {
        val result = messageSendApi(RecordingMessageSendPort()).send(
            context = allowedContext(conversationId = "current-conversation"),
            request = PluginV2MessageSendRequest(
                text = "hello",
                conversationId = "other-conversation",
            ),
        )

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2MessageSendApi.CONVERSATION_SCOPE_VIOLATION, failure.error.code)
        assertEquals("current-conversation", failure.error.details["currentConversationId"])
        assertEquals("other-conversation", failure.error.details["requestedConversationId"])
    }

    @Test
    fun schedule_like_context_can_send_to_bound_conversation_and_rejects_another_target() = runTest {
        val port = RecordingMessageSendPort()
        val api = messageSendApi(port)

        val boundResult = api.send(
            context = allowedContext(
                conversationId = "schedule-bound-conversation",
                platformAdapterType = "onebot",
            ),
            request = PluginV2MessageSendRequest(
                text = "daily summary",
                conversationId = "schedule-bound-conversation",
            ),
        )
        val rejectedResult = api.send(
            context = allowedContext(
                conversationId = "schedule-bound-conversation",
                platformAdapterType = "onebot",
            ),
            request = PluginV2MessageSendRequest(
                text = "daily summary",
                conversationId = "another-conversation",
            ),
        )

        assertTrue(boundResult is PluginV2HostApiResult.Success)
        assertEquals("schedule-bound-conversation", port.requests.single().conversationId)
        assertFailureCode(rejectedResult, PluginV2MessageSendApi.CONVERSATION_SCOPE_VIOLATION)
    }

    @Test
    fun unsupported_attachment_uri_is_rejected() = runTest {
        val result = messageSendApi(RecordingMessageSendPort()).send(
            context = allowedContext(),
            request = PluginV2MessageSendRequest(
                text = "",
                attachments = listOf(
                    PluginV2MessageAttachmentRef(
                        uri = "file:///sdcard/private.png",
                        mimeType = "image/png",
                    ),
                ),
            ),
        )

        assertFailureCode(result, PluginV2MessageSendApi.UNSUPPORTED_ATTACHMENT)
    }

    @Test
    fun port_failure_maps_to_structured_error_without_platform_stack_trace() = runTest {
        val result = messageSendApi(
            RecordingMessageSendPort(
                result = PluginV2MessageSendPortResult(
                    success = false,
                    receiptIds = emptyList(),
                    errorCode = "onebot_socket_timeout",
                    errorSummary = "java.lang.IllegalStateException: secret platform stack",
                ),
            ),
        ).send(
            context = allowedContext(platformAdapterType = "onebot"),
            request = PluginV2MessageSendRequest(text = "hello"),
        )

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2MessageSendApi.MESSAGE_SEND_FAILED, failure.error.code)
        assertEquals("onebot_socket_timeout", failure.error.details["failureCode"])
        assertFalse(failure.toString().contains("secret platform stack", ignoreCase = true))
        assertFalse(failure.toString().contains("IllegalStateException", ignoreCase = true))
    }

    private fun messageSendApi(
        port: PluginV2MessageSendPort,
        logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
    ): PluginV2MessageSendApi {
        return PluginV2MessageSendApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(logBus = logBus, clock = { 10L }),
                clock = { 10L },
            ),
            sendPort = port,
        )
    }

    private fun allowedContext(
        conversationId: String = "conversation-current",
        platformAdapterType: String = "app_chat",
        granted: Boolean = true,
    ): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.message",
            pluginVersion = "1.0.0",
            requestId = "request-message",
            conversationId = conversationId,
            platformAdapterType = platformAdapterType,
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.SEND_MESSAGE),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.SEND_MESSAGE,
                    title = "Send message",
                    granted = granted,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.SEND_MESSAGE),
        )
    }

    private fun assertFailureCode(
        result: PluginV2HostApiResult,
        code: String,
    ) {
        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(code, failure.error.code)
    }

    private class RecordingMessageSendPort(
        private val result: PluginV2MessageSendPortResult? = null,
    ) : PluginV2MessageSendPort {
        val requests = mutableListOf<PluginV2MessageSendPortRequest>()

        override suspend fun send(request: PluginV2MessageSendPortRequest): PluginV2MessageSendPortResult {
            requests += request
            return result ?: PluginV2MessageSendPortResult(
                success = true,
                receiptIds = listOf("receipt-${request.conversationId}"),
            )
        }
    }
}

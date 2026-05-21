package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2RichMessageSendApiTest {

    @Test
    fun text_and_image_chain_maps_to_app_chat_text_and_attachment() = runTest {
        val port = RecordingMessageSendPort()
        val result = messageSendApi(port).send(
            context = allowedContext(platformAdapterType = "app_chat"),
            request = PluginV2MessageSendRequest(
                chain = listOf(
                    PluginTextSegment(text = "see this"),
                    PluginImageSegment(uri = "plugin://workspace/out/a.png", alt = "image"),
                ),
            ),
        )

        assertTrue(result is PluginV2HostApiResult.Success)
        val sent = port.requests.single()
        assertEquals("see this", sent.text)
        assertEquals(listOf("plugin://workspace/out/a.png"), sent.attachments.map { it.uri })
        assertEquals(PluginV2HostApiPermissions.RICH_MESSAGE_SEND, sent.permissionId)
    }

    @Test
    fun qq_chain_keeps_mention_and_reply_visible_when_native_adapter_cannot_edit_segments() = runTest {
        val port = RecordingMessageSendPort()
        val result = messageSendApi(port).send(
            context = allowedContext(platformAdapterType = "onebot"),
            request = PluginV2MessageSendRequest(
                chain = listOf(
                    PluginReplySegment(messageId = "qq-msg-1"),
                    PluginTextSegment(text = "hello"),
                    PluginMentionSegment(userId = "10001"),
                ),
            ),
        )

        val success = result as PluginV2HostApiResult.Success
        val response = success.value as PluginV2MessageSendResult
        assertTrue(port.requests.single().text.contains("[CQ:reply,id=qq-msg-1]"))
        assertTrue(port.requests.single().text.contains("[CQ:at,qq=10001]"))
        assertTrue(response.warnings.any { it.code == "qq_segment_fallback" })
    }

    @Test
    fun rich_chain_requires_rich_message_permission_not_plain_send_message() = runTest {
        val result = messageSendApi(RecordingMessageSendPort()).send(
            context = allowedContext(
                grantedPermissions = setOf(PluginV2HostApiPermissions.SEND_MESSAGE),
            ),
            request = PluginV2MessageSendRequest(
                chain = listOf(PluginTextSegment(text = "rich")),
            ),
        )

        assertFailureCode(result, PluginV2HostApiErrorCodes.PERMISSION_DENIED)
    }

    @Test
    fun simple_text_remains_plain_send_message_compatible() = runTest {
        val port = RecordingMessageSendPort()
        val result = messageSendApi(port).send(
            context = allowedContext(
                grantedPermissions = setOf(PluginV2HostApiPermissions.SEND_MESSAGE),
            ),
            request = PluginV2MessageSendRequest(text = "plain"),
        )

        assertTrue(result is PluginV2HostApiResult.Success)
        assertEquals(PluginV2HostApiPermissions.SEND_MESSAGE, port.requests.single().permissionId)
    }

    @Test
    fun rich_chain_keeps_current_conversation_constraint() = runTest {
        val result = messageSendApi(RecordingMessageSendPort()).send(
            context = allowedContext(conversationId = "current"),
            request = PluginV2MessageSendRequest(
                conversationId = "other",
                chain = listOf(PluginTextSegment(text = "hello")),
            ),
        )

        assertFailureCode(result, PluginV2MessageSendApi.CONVERSATION_SCOPE_VIOLATION)
    }

    private fun messageSendApi(port: PluginV2MessageSendPort): PluginV2MessageSendApi {
        return PluginV2MessageSendApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(
                    logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
                    clock = { 1L },
                ),
                clock = { 1L },
            ),
            sendPort = port,
        )
    }

    private fun allowedContext(
        conversationId: String = "conversation-current",
        platformAdapterType: String = "app_chat",
        grantedPermissions: Set<String> = setOf(
            PluginV2HostApiPermissions.SEND_MESSAGE,
            PluginV2HostApiPermissions.RICH_MESSAGE_SEND,
        ),
    ): PluginV2HostApiRequestContext {
        val manifestPermissions = setOf(
            PluginV2HostApiPermissions.SEND_MESSAGE,
            PluginV2HostApiPermissions.RICH_MESSAGE_SEND,
        )
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.rich",
            pluginVersion = "1.0.0",
            requestId = "request-rich",
            conversationId = conversationId,
            platformAdapterType = platformAdapterType,
            manifestPermissionIds = manifestPermissions,
            permissionSnapshot = manifestPermissions.map { permissionId ->
                PluginPermissionGrant(
                    permissionId = permissionId,
                    title = permissionId,
                    granted = permissionId in grantedPermissions,
                    riskLevel = PluginRiskLevel.MEDIUM,
                )
            },
            triggerPermissionWhitelist = grantedPermissions,
        )
    }

    private fun assertFailureCode(result: PluginV2HostApiResult, code: String) {
        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(code, failure.error.code)
    }

    private class RecordingMessageSendPort : PluginV2MessageSendPort {
        val requests = mutableListOf<PluginV2MessageSendPortRequest>()

        override suspend fun send(request: PluginV2MessageSendPortRequest): PluginV2MessageSendPortResult {
            requests += request
            return PluginV2MessageSendPortResult(
                success = true,
                receiptIds = listOf("receipt-${request.conversationId}"),
                warnings = request.warnings,
            )
        }
    }
}

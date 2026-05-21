package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.plugin.PluginPermissionGrant
import com.elymbot.android.model.plugin.PluginRiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ConversationHistoryApiTest {

    @Test
    fun current_conversation_returns_latest_default_20_messages() = runTest {
        val api = conversationHistoryApi(messages = numberedMessages(25))

        val result = api.history(
            context = allowedContext(conversationId = "conversation-current"),
            request = PluginV2ConversationHistoryRequest(),
        )

        val success = result as PluginV2HostApiResult.Success
        val messages = success.value as PluginV2ConversationHistoryResult
        assertEquals(PluginV2ConversationHistoryApi.HOST_API_CONVERSATION_HISTORY, success.api)
        assertEquals("conversation-current", messages.conversationId)
        assertEquals(20, messages.messages.size)
        assertEquals("message-25", messages.messages.first().messageId)
        assertEquals("message-06", messages.messages.last().messageId)
    }

    @Test
    fun limit_over_100_is_clamped_to_100() = runTest {
        val port = RecordingConversationHistoryPort(numberedMessages(130))
        val api = conversationHistoryApi(port = port)

        val result = api.history(
            context = allowedContext(),
            request = PluginV2ConversationHistoryRequest(limit = 500),
        )

        val success = result as PluginV2HostApiResult.Success
        val response = success.value as PluginV2ConversationHistoryResult
        assertEquals(100, port.requests.single().limit)
        assertEquals(100, response.messages.size)
        assertEquals("message-130", response.messages.first().messageId)
        assertEquals("message-31", response.messages.last().messageId)
    }

    @Test
    fun before_message_id_returns_only_earlier_messages() = runTest {
        val api = conversationHistoryApi(messages = numberedMessages(25))

        val result = api.history(
            context = allowedContext(),
            request = PluginV2ConversationHistoryRequest(
                limit = 5,
                beforeMessageId = "message-20",
            ),
        )

        val success = result as PluginV2HostApiResult.Success
        val response = success.value as PluginV2ConversationHistoryResult
        assertEquals(
            listOf("message-19", "message-18", "message-17", "message-16", "message-15"),
            response.messages.map { it.messageId },
        )
    }

    @Test
    fun unknown_before_message_id_returns_empty_list() = runTest {
        val api = conversationHistoryApi(messages = numberedMessages(25))

        val result = api.history(
            context = allowedContext(),
            request = PluginV2ConversationHistoryRequest(beforeMessageId = "missing"),
        )

        val success = result as PluginV2HostApiResult.Success
        val response = success.value as PluginV2ConversationHistoryResult
        assertTrue(response.messages.isEmpty())
    }

    @Test
    fun missing_current_conversation_scope_returns_structured_error() = runTest {
        val result = conversationHistoryApi(messages = numberedMessages(3)).history(
            context = allowedContext(conversationId = ""),
            request = PluginV2ConversationHistoryRequest(),
        )

        assertFailureCode(result, PluginV2ConversationHistoryApi.MISSING_SESSION_SCOPE)
    }

    @Test
    fun non_current_conversation_request_returns_scope_violation() = runTest {
        val result = conversationHistoryApi(messages = numberedMessages(3)).history(
            context = allowedContext(conversationId = "conversation-current"),
            request = PluginV2ConversationHistoryRequest(conversationId = "conversation-other"),
        )

        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(PluginV2ConversationHistoryApi.CONVERSATION_SCOPE_VIOLATION, failure.error.code)
        assertEquals("conversation-current", failure.error.details["currentConversationId"])
        assertEquals("conversation-other", failure.error.details["requestedConversationId"])
    }

    @Test
    fun missing_permission_returns_permission_denied() = runTest {
        val result = conversationHistoryApi(messages = numberedMessages(3)).history(
            context = allowedContext(granted = false),
            request = PluginV2ConversationHistoryRequest(),
        )

        assertFailureCode(result, PluginV2HostApiErrorCodes.PERMISSION_DENIED)
    }

    @Test
    fun attachment_refs_are_omitted_when_not_requested_and_sanitized_when_requested() = runTest {
        val message = PluginV2ConversationHistoryMessage(
            messageId = "message-with-attachment",
            role = "assistant",
            senderId = "bot",
            messageType = "image",
            text = "image ready",
            timestampEpochMillis = 1_000L,
            attachmentRefs = listOf(
                PluginV2ConversationHistoryAttachmentRef(
                    ref = " host-ref-1 ",
                    uri = "host://attachments/1",
                    mimeType = " image/png ",
                    type = " image ",
                ),
                PluginV2ConversationHistoryAttachmentRef(
                    ref = "room-entity-secret",
                    uri = "file:///data/data/com.elymbot/private.png",
                    mimeType = "apiKey/png",
                    type = "DaoImage",
                ),
            ),
        )
        val api = conversationHistoryApi(messages = listOf(message))

        val withoutAttachments = api.history(
            context = allowedContext(),
            request = PluginV2ConversationHistoryRequest(includeAttachments = false),
        ) as PluginV2HostApiResult.Success
        val withAttachments = api.history(
            context = allowedContext(),
            request = PluginV2ConversationHistoryRequest(includeAttachments = true),
        ) as PluginV2HostApiResult.Success

        val omitted = (withoutAttachments.value as PluginV2ConversationHistoryResult).messages.single()
        val included = (withAttachments.value as PluginV2ConversationHistoryResult).messages.single()
        assertTrue(omitted.attachmentRefs.isEmpty())
        assertEquals(1, included.attachmentRefs.size)
        assertEquals("host-ref-1", included.attachmentRefs.single().ref)
        assertEquals("host://attachments/1", included.attachmentRefs.single().uri)
        assertEquals("image/png", included.attachmentRefs.single().mimeType)
        assertEquals("image", included.attachmentRefs.single().type)
    }

    @Test
    fun returned_results_do_not_contain_storage_or_secret_terms() = runTest {
        val api = conversationHistoryApi(
            messages = listOf(
                PluginV2ConversationHistoryMessage(
                    messageId = "RoomDaoEntity-sql-apiKey-baseUrl-credential",
                    role = "user",
                    senderId = "ProviderDao",
                    messageType = "SqlEntity",
                    text = "apiKey baseUrl credential Room Dao Entity sql",
                    timestampEpochMillis = 1_000L,
                    attachmentRefs = listOf(
                        PluginV2ConversationHistoryAttachmentRef(
                            ref = "DaoRef",
                            uri = "host://attachments/credential",
                            mimeType = "credential/type",
                            type = "RoomEntity",
                        ),
                    ),
                ),
            ),
        )

        val result = api.history(
            context = allowedContext(),
            request = PluginV2ConversationHistoryRequest(includeAttachments = true),
        )

        val success = result as PluginV2HostApiResult.Success
        val serialized = success.value.toString()
        listOf("Room", "Dao", "Entity", "sql", "apiKey", "baseUrl", "credential").forEach { forbidden ->
            assertFalse("Result leaked $forbidden", serialized.contains(forbidden, ignoreCase = true))
        }
    }

    private fun conversationHistoryApi(
        messages: List<PluginV2ConversationHistoryMessage>,
    ): PluginV2ConversationHistoryApi {
        return conversationHistoryApi(port = RecordingConversationHistoryPort(messages))
    }

    private fun conversationHistoryApi(
        port: PluginV2ConversationHistoryReadPort,
        logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
    ): PluginV2ConversationHistoryApi {
        return PluginV2ConversationHistoryApi(
            facade = PluginV2HostApiFacade(
                permissionPolicy = PluginV2HostApiPermissionPolicy(),
                asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
                auditLogger = PluginV2HostApiAuditLogger(logBus = logBus, clock = { 10L }),
                clock = { 10L },
            ),
            historyReader = port,
        )
    }

    private fun allowedContext(
        conversationId: String = "conversation-current",
        granted: Boolean = true,
    ): PluginV2HostApiRequestContext {
        return PluginV2HostApiRequestContext(
            pluginId = "plugin.history",
            pluginVersion = "1.0.0",
            requestId = "request-history",
            conversationId = conversationId,
            platformAdapterType = "app_chat",
            manifestPermissionIds = setOf(PluginV2HostApiPermissions.CONVERSATION_READ),
            permissionSnapshot = listOf(
                PluginPermissionGrant(
                    permissionId = PluginV2HostApiPermissions.CONVERSATION_READ,
                    title = "Read conversation",
                    granted = granted,
                    riskLevel = PluginRiskLevel.MEDIUM,
                ),
            ),
            triggerPermissionWhitelist = setOf(PluginV2HostApiPermissions.CONVERSATION_READ),
        )
    }

    private fun numberedMessages(count: Int): List<PluginV2ConversationHistoryMessage> {
        return (1..count).map { index ->
            PluginV2ConversationHistoryMessage(
                messageId = "message-${index.toString().padStart(2, '0')}",
                role = if (index % 2 == 0) "assistant" else "user",
                senderId = "sender-$index",
                messageType = "text",
                text = "message text $index",
                timestampEpochMillis = index * 1_000L,
            )
        }
    }

    private fun assertFailureCode(
        result: PluginV2HostApiResult,
        code: String,
    ) {
        val failure = result as PluginV2HostApiResult.Failure
        assertEquals(code, failure.error.code)
    }

    private class RecordingConversationHistoryPort(
        private val messages: List<PluginV2ConversationHistoryMessage>,
    ) : PluginV2ConversationHistoryReadPort {
        val requests = mutableListOf<PluginV2ConversationHistoryPortRequest>()

        override suspend fun history(
            request: PluginV2ConversationHistoryPortRequest,
        ): List<PluginV2ConversationHistoryMessage> {
            requests += request
            return messages
        }
    }
}

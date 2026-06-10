package com.elymbot.android.di.hilt

import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.plugin.runtime.PluginV2ConversationHistoryPortRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageAttachmentRef
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendPortRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPlatformMode
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortChunkRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortCloseRequest
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageStreamPortOpenRequest
import com.elymbot.android.feature.qq.domain.QqScheduledMessageSender
import com.elymbot.android.feature.qq.domain.QqSendResult
import com.elymbot.android.model.chat.ConversationAttachment
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginHostCapabilityModuleTest {

    @Test
    fun messageSendPort_appChat_appendsConversationMessageOnly() = runTest {
        val conversationRepository = RecordingConversationRepositoryPort()
        val qqSender = RecordingQqScheduledMessageSender()
        val port = PluginHostCapabilityModule.providePluginV2MessageSendPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqSender,
        )

        val result = port.send(messageRequest(platformAdapterType = "app_chat"))

        assertTrue(result.success)
        assertEquals(listOf("assistant-1"), result.receiptIds)
        assertEquals(1, conversationRepository.appended.size)
        assertEquals("conversation-1", conversationRepository.appended.single().sessionId)
        assertEquals("assistant", conversationRepository.appended.single().role)
        assertEquals("hello from plugin", conversationRepository.appended.single().content)
        assertEquals(emptyList<ConversationAttachment>(), conversationRepository.appended.single().attachments)
        assertEquals(0, qqSender.requests.size)
    }

    @Test
    fun messageSendPort_onebot_sendsThroughQqSenderAndPersistsConversationMessage() = runTest {
        val conversationRepository = RecordingConversationRepositoryPort()
        val qqSender = RecordingQqScheduledMessageSender(
            // skipcq: KT-W1042
            result = QqSendResult.success(listOf("qq-receipt-1")),
        )
        val port = PluginHostCapabilityModule.providePluginV2MessageSendPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqSender,
        )

        val result = port.send(
            messageRequest(
                platformAdapterType = "onebot",
                attachments = listOf(
                    PluginV2MessageAttachmentRef(
                        uri = "plugin://workspace/out.png",
                        mimeType = "image/png",
                    ),
                ),
            ),
        )

        assertTrue(result.success)
        assertEquals(listOf("qq-receipt-1", "assistant-1"), result.receiptIds)
        assertEquals("conversation-1", qqSender.requests.single().conversationId)
        assertEquals("hello from plugin", qqSender.requests.single().text)
        assertEquals("", qqSender.requests.single().botId)
        assertEquals("plugin://workspace/out.png", qqSender.requests.single().attachments.single().remoteUrl)
        assertEquals("image", qqSender.requests.single().attachments.single().type)
        assertEquals(1, conversationRepository.appended.size)
        assertEquals("plugin://workspace/out.png", conversationRepository.appended.single().attachments.single().remoteUrl)
    }

    @Test
    fun messageSendPort_qqFailureReturnsStructuredFailureWithoutLocalAppend() = runTest {
        val conversationRepository = RecordingConversationRepositoryPort()
        val qqSender = RecordingQqScheduledMessageSender(
            result = QqSendResult.failure("reverse_ws_not_connected"),
        )
        val port = PluginHostCapabilityModule.providePluginV2MessageSendPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqSender,
        )

        val result = port.send(messageRequest(platformAdapterType = "qq_onebot"))

        assertFalse(result.success)
        assertEquals("qq_delivery_failed", result.errorCode)
        assertEquals("reverse_ws_not_connected", result.errorSummary)
        assertEquals(1, qqSender.requests.size)
        assertEquals(0, conversationRepository.appended.size)
    }

    @Test
    fun messageSendPort_onebotPersistsToRepositorySessionForPublicQqConversationId() = runTest {
        val qqSession = conversationSession(
            // skipcq: KT-W1042
            id = "qq-qq-main-group-30003-user-20002",
            messages = emptyList(),
        )
        val conversationRepository = RecordingConversationRepositoryPort(initialSessions = listOf(qqSession))
        val qqSender = RecordingQqScheduledMessageSender(
            result = QqSendResult.success(listOf("qq-receipt-1")),
        )
        val port = PluginHostCapabilityModule.providePluginV2MessageSendPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqSender,
        )

        val result = port.send(
            messageRequest(
                platformAdapterType = "onebot",
                // skipcq: KT-W1042
                conversationId = "group:30003:user:20002",
            ),
        )

        assertTrue(result.success)
        assertEquals("group:30003:user:20002", qqSender.requests.single().conversationId)
        assertEquals("qq-qq-main-group-30003-user-20002", conversationRepository.appended.single().sessionId)
    }

    @Test
    fun conversationHistoryReadPort_prefersPublicGroupSessionForIsolatedQqConversationId() = runTest {
        val isolatedSession = conversationSession(
            id = "qq-qq-main-group-30003-user-20002",
            messages = listOf(
                ConversationMessage(
                    id = "qq-isolated-message-1",
                    role = "user",
                    content = "隔离消息",
                    timestamp = 90L,
                ),
            ),
        )
        val publicSession = conversationSession(
            id = "qq-qq-main-group-30003",
            messages = listOf(
                ConversationMessage(
                    id = "qq-public-message-1",
                    role = "user",
                    content = "公共群消息",
                    timestamp = 100L,
                ),
            ),
        )
        val conversationRepository = RecordingConversationRepositoryPort(
            initialSessions = listOf(isolatedSession, publicSession),
        )
        val port = PluginHostCapabilityModule.providePluginV2ConversationHistoryReadPort(
            conversationRepository = conversationRepository,
        )

        val history = port.history(
            PluginV2ConversationHistoryPortRequest(
                pluginId = "plugin.history",
                requestId = "request-history",
                conversationId = "group:30003:user:20002",
                limit = 100,
                beforeMessageId = "",
                includeAttachments = false,
            ),
        )

        assertEquals(listOf("qq-public-message-1"), history.map { it.messageId })
        assertEquals(listOf("公共群消息"), history.map { it.text })
        assertEquals(emptyList<String>(), conversationRepository.sessionReads)
    }

    @Test
    fun messageStreamPort_appChat_replaceUpdatesCurrentPendingMessage() = runTest {
        val conversationRepository = RecordingConversationRepositoryPort()
        val qqSender = RecordingQqScheduledMessageSender()
        val port = PluginHostCapabilityModule.providePluginV2MessageStreamPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqSender,
        )
        val open = port.open(streamOpenRequest(platformAdapterType = "app_chat"))

        val appended = port.append(streamChunkRequest(open.streamId, text = "draft"))
        val replaced = port.replace(streamChunkRequest(open.streamId, text = "final"))

        assertTrue(appended.success)
        assertTrue(replaced.success)
        assertEquals(PluginV2MessageStreamPlatformMode.Editable, open.platformMode)
        assertEquals(1, conversationRepository.appended.size)
        assertEquals("draft", conversationRepository.appended.single().content)
        assertEquals("assistant-1", conversationRepository.updated.single().messageId)
        assertEquals("final", conversationRepository.updated.single().content)
        assertEquals(0, qqSender.requests.size)
    }

    @Test
    fun messageStreamPort_qq_finalOnCloseBuffersChunksAndSendsOnceOnClose() = runTest {
        val conversationRepository = RecordingConversationRepositoryPort()
        val qqSender = RecordingQqScheduledMessageSender(
            // skipcq: KT-W1042
            result = QqSendResult.success(listOf("qq-stream-receipt")),
        )
        val port = PluginHostCapabilityModule.providePluginV2MessageStreamPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqSender,
        )
        val open = port.open(streamOpenRequest(platformAdapterType = "onebot"))

        val first = port.append(streamChunkRequest(open.streamId, platformAdapterType = "onebot", text = "hello"))
        val second = port.append(streamChunkRequest(open.streamId, platformAdapterType = "onebot", text = " world"))
        val close = port.close(streamCloseRequest(open.streamId, platformAdapterType = "onebot"))

        assertTrue(first.success)
        assertTrue(second.success)
        assertTrue(close.success)
        assertEquals(PluginV2MessageStreamPlatformMode.FinalOnClose, open.platformMode)
        assertEquals(1, qqSender.requests.size)
        assertEquals("hello world", qqSender.requests.single().text)
        assertEquals(1, conversationRepository.appended.size)
        assertEquals("hello world", conversationRepository.appended.single().content)
    }

    @Test
    fun messageStreamPort_qq_replaceOverwritesBufferedFinalMessageBeforeClose() = runTest {
        val conversationRepository = RecordingConversationRepositoryPort()
        val qqSender = RecordingQqScheduledMessageSender(
            result = QqSendResult.success(listOf("qq-stream-receipt")),
        )
        val port = PluginHostCapabilityModule.providePluginV2MessageStreamPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqSender,
        )
        val open = port.open(streamOpenRequest(platformAdapterType = "qq_onebot"))

        val appended = port.append(streamChunkRequest(open.streamId, platformAdapterType = "qq_onebot", text = "draft"))
        val replaced = port.replace(streamChunkRequest(open.streamId, platformAdapterType = "qq_onebot", text = "final"))
        val close = port.close(streamCloseRequest(open.streamId, platformAdapterType = "qq_onebot"))

        assertTrue(appended.success)
        assertTrue(replaced.success)
        assertTrue(close.success)
        assertEquals(PluginV2MessageStreamPlatformMode.FinalOnClose, open.platformMode)
        assertEquals(1, qqSender.requests.size)
        assertEquals("final", qqSender.requests.single().text)
        assertEquals(1, conversationRepository.appended.size)
        assertEquals("final", conversationRepository.appended.single().content)
    }

    @Test
    fun messageStreamPort_onebotPersistsToRepositorySessionForPublicQqConversationId() = runTest {
        val qqSession = conversationSession(id = "qq-qq-main-group-30003-user-20002")
        val conversationRepository = RecordingConversationRepositoryPort(initialSessions = listOf(qqSession))
        val qqSender = RecordingQqScheduledMessageSender(
            result = QqSendResult.success(listOf("qq-stream-receipt")),
        )
        val port = PluginHostCapabilityModule.providePluginV2MessageStreamPort(
            conversationRepository = conversationRepository,
            qqScheduledMessageSender = qqSender,
        )
        val open = port.open(
            streamOpenRequest(
                platformAdapterType = "onebot",
                conversationId = "group:30003:user:20002",
            ),
        )

        val appended = port.append(streamChunkRequest(open.streamId, platformAdapterType = "onebot", text = "hello"))
        val close = port.close(
            streamCloseRequest(
                open.streamId,
                platformAdapterType = "onebot",
                conversationId = "group:30003:user:20002",
            ),
        )

        assertTrue(appended.success)
        assertTrue(close.success)
        assertEquals("group:30003:user:20002", qqSender.requests.single().conversationId)
        assertEquals("qq-qq-main-group-30003-user-20002", conversationRepository.appended.single().sessionId)
    }

    private fun messageRequest(
        platformAdapterType: String,
        conversationId: String = "conversation-1",
        attachments: List<PluginV2MessageAttachmentRef> = emptyList(),
    ): PluginV2MessageSendPortRequest {
        return PluginV2MessageSendPortRequest(
            pluginId = "plugin.message",
            requestId = "request-1",
            conversationId = conversationId,
            platformAdapterType = platformAdapterType,
            text = "hello from plugin",
            markdown = false,
            attachments = attachments,
        )
    }

    private fun streamOpenRequest(
        platformAdapterType: String,
        conversationId: String = "conversation-1",
    ): PluginV2MessageStreamPortOpenRequest {
        return PluginV2MessageStreamPortOpenRequest(
            pluginId = "plugin.stream",
            requestId = "request-stream",
            conversationId = conversationId,
            platformAdapterType = platformAdapterType,
            markdown = false,
        )
    }

    private fun streamChunkRequest(
        streamId: String,
        platformAdapterType: String = "app_chat",
        text: String,
    ): PluginV2MessageStreamPortChunkRequest {
        return PluginV2MessageStreamPortChunkRequest(
            streamId = streamId,
            pluginId = "plugin.stream",
            requestId = "request-stream",
            conversationId = "conversation-1",
            platformAdapterType = platformAdapterType,
            text = text,
        )
    }

    private fun streamCloseRequest(
        streamId: String,
        platformAdapterType: String,
        conversationId: String = "conversation-1",
    ): PluginV2MessageStreamPortCloseRequest {
        return PluginV2MessageStreamPortCloseRequest(
            streamId = streamId,
            pluginId = "plugin.stream",
            requestId = "request-stream",
            conversationId = conversationId,
            platformAdapterType = platformAdapterType,
        )
    }
}

private data class AppendedPluginMessage(
    val sessionId: String,
    val role: String,
    val content: String,
    val attachments: List<ConversationAttachment>,
)

private data class UpdatedPluginMessage(
    val sessionId: String,
    val messageId: String,
    val content: String?,
    val attachments: List<ConversationAttachment>?,
)

private class RecordingConversationRepositoryPort(
    initialSessions: List<ConversationSession> = listOf(conversationSession()),
) : ConversationRepositoryPort {
    override val defaultSessionId: String = initialSessions.first().id
    override val sessions = MutableStateFlow(initialSessions)
    val appended = mutableListOf<AppendedPluginMessage>()
    val updated = mutableListOf<UpdatedPluginMessage>()
    val sessionReads = mutableListOf<String>()

    override fun contextPreview(sessionId: String): String = ""

    override fun session(sessionId: String): ConversationSession {
        sessionReads += sessionId
        return sessions.value.first { it.id == sessionId }
    }

    override fun syncSystemSessionTitle(sessionId: String, title: String) = Unit

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String {
        appended += AppendedPluginMessage(
            sessionId = sessionId,
            role = role,
            content = content,
            attachments = attachments,
        )
        return "$role-${appended.size}"
    }

    override fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) = Unit

    override fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean?,
        sessionTtsEnabled: Boolean?,
    ) = Unit

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) {
        updated += UpdatedPluginMessage(
            sessionId = sessionId,
            messageId = messageId,
            content = content,
            attachments = attachments,
        )
    }

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) = Unit

    override fun renameSession(sessionId: String, title: String) = Unit

    override fun deleteSession(sessionId: String) = Unit
}

private fun conversationSession(
    id: String = "conversation-1",
    messages: List<ConversationMessage> = emptyList(),
): ConversationSession = ConversationSession(
    id = id,
    title = "Conversation",
    botId = "bot-1",
    personaId = "",
    providerId = "provider-1",
    maxContextMessages = 10,
    messages = messages,
)

private data class RecordedQqSendRequest(
    val conversationId: String,
    val text: String,
    val attachments: List<ConversationAttachment>,
    val botId: String,
)

private class RecordingQqScheduledMessageSender(
    private val result: QqSendResult = QqSendResult.success(),
) : QqScheduledMessageSender {
    val requests = mutableListOf<RecordedQqSendRequest>()

    override fun sendScheduledMessage(
        conversationId: String,
        text: String,
        attachments: List<ConversationAttachment>,
        botId: String,
    ): QqSendResult {
        requests += RecordedQqSendRequest(
            conversationId = conversationId,
            text = text,
            attachments = attachments,
            botId = botId,
        )
        return result
    }
}

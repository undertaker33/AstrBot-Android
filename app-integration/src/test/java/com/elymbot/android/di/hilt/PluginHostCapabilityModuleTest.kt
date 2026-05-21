package com.elymbot.android.di.hilt

import com.elymbot.android.feature.conversation.domain.ConversationRepositoryPort
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageAttachmentRef
import com.elymbot.android.feature.plugin.runtime.PluginV2MessageSendPortRequest
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

    private fun messageRequest(
        platformAdapterType: String,
        attachments: List<PluginV2MessageAttachmentRef> = emptyList(),
    ): PluginV2MessageSendPortRequest {
        return PluginV2MessageSendPortRequest(
            pluginId = "plugin.message",
            requestId = "request-1",
            conversationId = "conversation-1",
            platformAdapterType = platformAdapterType,
            text = "hello from plugin",
            markdown = false,
            attachments = attachments,
        )
    }
}

private data class AppendedPluginMessage(
    val sessionId: String,
    val role: String,
    val content: String,
    val attachments: List<ConversationAttachment>,
)

private class RecordingConversationRepositoryPort : ConversationRepositoryPort {
    private val session = ConversationSession(
        id = "conversation-1",
        title = "Conversation",
        botId = "bot-1",
        personaId = "",
        providerId = "provider-1",
        maxContextMessages = 10,
        messages = emptyList(),
    )

    override val defaultSessionId: String = session.id
    override val sessions = MutableStateFlow(listOf(session))
    val appended = mutableListOf<AppendedPluginMessage>()

    override fun contextPreview(sessionId: String): String = ""

    override fun session(sessionId: String): ConversationSession = session

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
    ) = Unit

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) = Unit

    override fun renameSession(sessionId: String, title: String) = Unit

    override fun deleteSession(sessionId: String) = Unit
}

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

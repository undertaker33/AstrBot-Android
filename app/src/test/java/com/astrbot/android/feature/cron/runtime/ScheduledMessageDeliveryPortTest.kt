package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.qq.runtime.OneBotSendResult
import com.astrbot.android.feature.qq.runtime.QqScheduledMessageSender
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledMessageDeliveryPortTest {
    @Test
    fun deliver_appChat_appendsAssistantMessage() = runBlocking {
        val conversationPort = DeliveryRecordingConversationPort()
        val sender = RecordingQqScheduledMessageSender()
        val deliveryPort = DefaultScheduledMessageDeliveryPort(conversationPort, sender)

        val result = deliveryPort.deliver(
            ScheduledMessageDeliveryRequest(
                platform = "app",
                conversationId = "chat-1",
                text = "Time to drink water.",
                attachments = listOf(imageAttachment),
                botId = "bot-1",
            ),
        )

        assertTrue(result.success)
        assertEquals(1, result.deliveredMessageCount)
        assertEquals("assistant", conversationPort.appended.single().role)
        assertEquals("Time to drink water.", conversationPort.appended.single().content)
        assertEquals(listOf(imageAttachment), conversationPort.appended.single().attachments)
        assertEquals(0, sender.requests.size)
    }

    @Test
    fun deliver_qq_sendsThroughScheduledMessageSender_andAppendsAssistantMessage() = runBlocking {
        val conversationPort = DeliveryRecordingConversationPort()
        val sender = RecordingQqScheduledMessageSender(result = OneBotSendResult.success(listOf("receipt-1")))
        val deliveryPort = DefaultScheduledMessageDeliveryPort(conversationPort, sender)

        val result = deliveryPort.deliver(
            ScheduledMessageDeliveryRequest(
                platform = "qq_onebot",
                conversationId = "group:10001",
                text = "Time to drink water.",
                attachments = listOf(imageAttachment),
                botId = "bot-1",
            ),
        )

        assertTrue(result.success)
        assertEquals(listOf("receipt-1"), result.receiptIds)
        assertEquals(1, result.deliveredMessageCount)
        assertEquals("group:10001", sender.requests.single().conversationId)
        assertEquals("Time to drink water.", sender.requests.single().text)
        assertEquals(listOf(imageAttachment), sender.requests.single().attachments)
        assertEquals("bot-1", sender.requests.single().botId)
        assertEquals("assistant", conversationPort.appended.single().role)
        assertEquals("Time to drink water.", conversationPort.appended.single().content)
        assertEquals(listOf(imageAttachment), conversationPort.appended.single().attachments)
    }

    @Test
    fun deliver_unknownPlatform_returnsFailure() = runBlocking {
        val conversationPort = DeliveryRecordingConversationPort()
        val sender = RecordingQqScheduledMessageSender()
        val deliveryPort = DefaultScheduledMessageDeliveryPort(conversationPort, sender)

        val result = deliveryPort.deliver(
            ScheduledMessageDeliveryRequest(
                platform = "email",
                conversationId = "chat-1",
                text = "Time to drink water.",
                botId = "bot-1",
            ),
        )

        assertFalse(result.success)
        assertEquals(0, result.deliveredMessageCount)
        assertEquals("unsupported_platform", result.errorCode)
        assertTrue(result.errorSummary.contains("email"))
        assertTrue(conversationPort.appended.isEmpty())
        assertEquals(0, sender.requests.size)
    }

    private companion object {
        val imageAttachment = ConversationAttachment(
            id = "image-1",
            type = "image",
            mimeType = "image/png",
            remoteUrl = "https://example.invalid/image.png",
        )
    }
}

private data class AppendedMessage(
    val sessionId: String,
    val role: String,
    val content: String,
    val attachments: List<ConversationAttachment>,
)

private class DeliveryRecordingConversationPort : ConversationRepositoryPort {
    private val session = ConversationSession(
        id = "chat-1",
        title = "Chat",
        botId = "bot-1",
        personaId = "",
        providerId = "provider-1",
        maxContextMessages = 10,
        messages = emptyList(),
    )
    override val defaultSessionId: String = session.id
    override val sessions = MutableStateFlow(listOf(session))
    val appended = mutableListOf<AppendedMessage>()

    override fun contextPreview(sessionId: String): String = ""

    override fun session(sessionId: String): ConversationSession = session

    override fun syncSystemSessionTitle(sessionId: String, title: String) = Unit

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String {
        appended += AppendedMessage(sessionId, role, content, attachments)
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

private data class QqSendRequest(
    val conversationId: String,
    val text: String,
    val attachments: List<ConversationAttachment>,
    val botId: String,
)

private class RecordingQqScheduledMessageSender(
    private val result: OneBotSendResult = OneBotSendResult.success(),
) : QqScheduledMessageSender {
    val requests = mutableListOf<QqSendRequest>()

    override fun sendScheduledMessage(
        conversationId: String,
        text: String,
        attachments: List<ConversationAttachment>,
        botId: String,
    ): OneBotSendResult {
        requests += QqSendRequest(conversationId, text, attachments, botId)
        return result
    }
}

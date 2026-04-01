package com.astrbot.android.data.chat

import com.astrbot.android.data.db.ConversationAggregate
import com.astrbot.android.data.db.ConversationMessageAggregate
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.data.db.toConversationSession
import com.astrbot.android.data.db.toWriteModel
import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationImportExportTest {
    @Test
    fun aggregate_round_trip_preserves_explicit_session_identity() {
        val session = testSession()
        val writeModel = session.toWriteModel()
        val restored = ConversationAggregate(
            session = writeModel.session,
            messageAggregates = writeModel.messages.map { message ->
                ConversationMessageAggregate(
                    message = message,
                    attachments = writeModel.attachments.filter { it.messageId == message.id },
                )
            },
        ).toConversationSession()

        assertEquals("qq", restored.platformId)
        assertEquals(MessageType.GroupMessage, restored.messageType)
        assertEquals("group:20001:user:10001", restored.originSessionId)
    }

    @Test
    fun json_round_trip_preserves_explicit_session_identity() {
        val session = testSession()
        val payload = session.toConversationJson()

        val restored = payload.toConversationSession(defaultTitle = "新对话")

        assertEquals("qq", restored.platformId)
        assertEquals(MessageType.GroupMessage, restored.messageType)
        assertEquals("group:20001:user:10001", restored.originSessionId)
    }

    private fun testSession() = ConversationSession(
        id = "qq-qq-main-group-20001-user-10001",
        title = "QQG 20001 赤",
        botId = "qq-main",
        personaId = "",
        providerId = "",
        platformId = "qq",
        messageType = MessageType.GroupMessage,
        originSessionId = "group:20001:user:10001",
        maxContextMessages = 12,
        sessionSttEnabled = true,
        sessionTtsEnabled = true,
        pinned = true,
        titleCustomized = false,
        messages = listOf(
            ConversationMessage(
                id = "m1",
                role = "user",
                content = "hello",
                timestamp = 123L,
            ),
        ),
    )
}

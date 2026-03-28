package com.astrbot.android.ui.chat

import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationUiClassifierTest {
    @Test
    fun qq_private_conversation_can_be_renamed() {
        val session = testSession(
            id = "qq-qq-main-private-10001",
            platformId = "qq",
            messageType = MessageType.FriendMessage,
            originSessionId = "friend:10001",
        )

        assertTrue(session.isQqConversation())
        assertTrue(session.isQqPrivateConversation())
        assertTrue(session.canRenameConversation())
    }

    @Test
    fun qq_group_conversation_cannot_be_renamed() {
        val session = testSession(
            id = "qq-qq-main-group-20001",
            platformId = "qq",
            messageType = MessageType.GroupMessage,
            originSessionId = "group:20001",
        )

        assertTrue(session.isQqConversation())
        assertFalse(session.canRenameConversation())
    }

    @Test
    fun default_session_cannot_be_deleted() {
        val session = testSession(id = ConversationRepository.DEFAULT_SESSION_ID)

        assertFalse(session.canDeleteConversation())
    }

    private fun testSession(
        id: String = "local-session",
        platformId: String = "app",
        messageType: MessageType = MessageType.OtherMessage,
        originSessionId: String = "local-session",
    ) = ConversationSession(
        id = id,
        title = "Test",
        botId = "bot",
        personaId = "",
        providerId = "",
        platformId = platformId,
        messageType = messageType,
        originSessionId = originSessionId,
        maxContextMessages = 12,
        messages = emptyList(),
    )
}

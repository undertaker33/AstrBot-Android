package com.astrbot.android.model.chat

import org.junit.Assert.assertEquals
import org.junit.Test

class ConversationSessionIdentityTest {
    @Test
    fun import_dedup_key_uses_platform_and_origin_for_qq_private_sessions() {
        val session = ConversationSession(
            id = "qq-qq-main-private-10001",
            title = "QQP 赤",
            botId = "qq-main",
            personaId = "",
            providerId = "",
            platformId = "qq",
            messageType = MessageType.FriendMessage,
            originSessionId = "friend:10001",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            messages = emptyList(),
        )

        assertEquals("qq:qq-main:friend:friend:10001", session.importDedupKey())
    }

    @Test
    fun import_dedup_key_uses_platform_and_origin_for_qq_group_sessions() {
        val session = ConversationSession(
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
            messages = emptyList(),
        )

        assertEquals("qq:qq-main:group:group:20001:user:10001", session.importDedupKey())
    }

    @Test
    fun import_dedup_key_falls_back_to_local_session_id_for_non_qq_sessions() {
        val session = ConversationSession(
            id = "chat-main",
            title = "新对话",
            botId = "default",
            personaId = "",
            providerId = "",
            platformId = "app",
            messageType = MessageType.OtherMessage,
            originSessionId = "chat-main",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            messages = emptyList(),
        )

        assertEquals("app:chat-main", session.importDedupKey())
    }
}

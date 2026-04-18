package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class QqSessionKeyFactoryTest {
    @Test
    fun group_session_key_changes_when_isolation_enabled() {
        val normal = QqSessionKeyFactory.build(
            botId = "qq-main",
            messageType = MessageType.GroupMessage,
            groupId = "20001",
            userId = "10001",
            isolated = false,
        )
        val isolated = QqSessionKeyFactory.build(
            botId = "qq-main",
            messageType = MessageType.GroupMessage,
            groupId = "20001",
            userId = "10001",
            isolated = true,
        )

        assertEquals("qq-qq-main-group-20001", normal)
        assertEquals("qq-qq-main-group-20001-user-10001", isolated)
    }

    @Test
    fun private_session_key_ignores_group_and_isolation_flag() {
        val sessionId = QqSessionKeyFactory.build(
            botId = "qq-main",
            messageType = MessageType.FriendMessage,
            groupId = "20001",
            userId = "10001",
            isolated = true,
        )

        assertEquals("qq-qq-main-private-10001", sessionId)
    }

    @Test
    fun isolated_group_session_keys_differ_per_user_so_persona_state_can_be_independent() {
        val userA = QqSessionKeyFactory.build(
            botId = "qq-main",
            messageType = MessageType.GroupMessage,
            groupId = "20001",
            userId = "10001",
            isolated = true,
        )
        val userB = QqSessionKeyFactory.build(
            botId = "qq-main",
            messageType = MessageType.GroupMessage,
            groupId = "20001",
            userId = "10002",
            isolated = true,
        )

        assertEquals("qq-qq-main-group-20001-user-10001", userA)
        assertEquals("qq-qq-main-group-20001-user-10002", userB)
    }
}

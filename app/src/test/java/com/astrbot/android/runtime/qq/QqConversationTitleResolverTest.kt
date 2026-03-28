package com.astrbot.android.runtime.qq

import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Test

class QqConversationTitleResolverTest {
    @Test
    fun group_title_uses_qqg_group_id_and_sender_name() {
        val title = QqConversationTitleResolver.build(
            messageType = MessageType.GroupMessage,
            groupId = "1091021328",
            userId = "10001",
            senderName = "赤",
        )

        assertEquals("QQG 1091021328 赤", title)
    }

    @Test
    fun private_title_uses_qqp_and_sender_name() {
        val title = QqConversationTitleResolver.build(
            messageType = MessageType.FriendMessage,
            groupId = "",
            userId = "10001",
            senderName = "赤",
        )

        assertEquals("QQP 赤", title)
    }

    @Test
    fun missing_sender_name_falls_back_to_user_id() {
        val title = QqConversationTitleResolver.build(
            messageType = MessageType.FriendMessage,
            groupId = "",
            userId = "10001",
            senderName = "",
        )

        assertEquals("QQP 10001", title)
    }
}

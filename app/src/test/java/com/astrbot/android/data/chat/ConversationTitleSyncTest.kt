package com.astrbot.android.data.chat

import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ConversationTitleSyncTest {
    @Test
    fun apply_system_title_updates_non_customized_session() {
        val session = testSession(title = "QQP 赤", titleCustomized = false)

        val updated = applySystemSessionTitle(
            session = session,
            incomingTitle = "QQP 新昵称",
            defaultTitle = "新对话",
        )

        requireNotNull(updated)
        assertEquals("QQP 新昵称", updated.title)
        assertEquals(false, updated.titleCustomized)
    }

    @Test
    fun apply_system_title_skips_customized_session() {
        val session = testSession(title = "和赤的私聊", titleCustomized = true)

        val updated = applySystemSessionTitle(
            session = session,
            incomingTitle = "QQP 新昵称",
            defaultTitle = "新对话",
        )

        assertNull(updated)
    }

    private fun testSession(
        title: String,
        titleCustomized: Boolean,
    ) = ConversationSession(
        id = "qq-qq-main-private-10001",
        title = title,
        botId = "qq-main",
        personaId = "",
        providerId = "",
        platformId = "qq",
        messageType = MessageType.FriendMessage,
        originSessionId = "friend:10001",
        maxContextMessages = 12,
        sessionSttEnabled = true,
        sessionTtsEnabled = true,
        pinned = false,
        titleCustomized = titleCustomized,
        messages = emptyList(),
    )
}

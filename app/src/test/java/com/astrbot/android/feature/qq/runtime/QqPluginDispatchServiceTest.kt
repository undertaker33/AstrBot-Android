package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginTriggerSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class QqPluginDispatchServiceTest {
    @Test
    fun to_plugin_message_event_includes_session_unified_origin_from_runtime_session_ref() {
        val sessionRef = MessageSessionRef(
            platformId = "qq",
            messageType = MessageType.GroupMessage,
            originSessionId = "group:30003:user:20002",
        )
        val event = IncomingQqMessage(
            selfId = "30001",
            messageId = "msg-1",
            conversationId = "30003",
            senderId = "20002",
            senderName = "Alice",
            text = "hello",
            messageType = MessageType.GroupMessage,
            rawPayload = "{}",
        ).toPluginMessageEvent(
            trigger = PluginTriggerSource.BeforeSendMessage,
            conversationId = "group:30003:user:20002",
            sessionUnifiedOrigin = sessionRef.unifiedOrigin,
            botId = "qq-main",
            configProfileId = "config-1",
            personaId = "persona-1",
            providerId = "provider-1",
        )

        assertEquals(sessionRef.unifiedOrigin, event.extras["sessionUnifiedOrigin"])
        assertFalse(event.extras["sessionUnifiedOrigin"] == "qq-session-db-id")
    }
}

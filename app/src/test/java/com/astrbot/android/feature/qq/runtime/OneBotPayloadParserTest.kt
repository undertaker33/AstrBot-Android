package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.model.chat.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneBotPayloadParserTest {

    private val parser = OneBotPayloadParser()

    @Test
    fun valid_private_message_parses_to_message() {
        val payload = """
        {
            "post_type": "message",
            "message_type": "private",
            "self_id": "12345",
            "user_id": "67890",
            "message_id": "msg001",
            "raw_message": "Hello World",
            "sender": {"nickname": "TestUser", "card": ""}
        }
        """.trimIndent()

        val result = parser.parse(payload)
        assertTrue("Expected Message result", result is OneBotPayloadParseResult.Message)
        val msg = (result as OneBotPayloadParseResult.Message).message
        assertEquals("12345", msg.selfId)
        assertEquals("67890", msg.senderId)
        assertEquals("Hello World", msg.text)
        assertEquals(MessageType.FriendMessage, msg.messageType)
        assertEquals("67890", msg.conversationId)
    }

    @Test
    fun valid_group_message_parses_to_message() {
        val payload = """
        {
            "post_type": "message",
            "message_type": "group",
            "self_id": "12345",
            "user_id": "67890",
            "group_id": "111222",
            "message_id": "msg002",
            "raw_message": "Hi everyone",
            "sender": {"nickname": "GroupUser", "card": "CardName"}
        }
        """.trimIndent()

        val result = parser.parse(payload)
        assertTrue("Expected Message result", result is OneBotPayloadParseResult.Message)
        val msg = (result as OneBotPayloadParseResult.Message).message
        assertEquals(MessageType.GroupMessage, msg.messageType)
        assertEquals("111222", msg.conversationId)
        assertEquals("CardName", msg.senderName)
    }

    @Test
    fun unknown_post_type_returns_ignored() {
        val payload = """
        {
            "post_type": "notice",
            "notice_type": "group_increase"
        }
        """.trimIndent()

        val result = parser.parse(payload)
        assertTrue("Expected Ignored result", result is OneBotPayloadParseResult.Ignored)
    }

    @Test
    fun invalid_json_returns_invalid() {
        val result = parser.parse("this is not json")
        assertTrue("Expected Invalid result", result is OneBotPayloadParseResult.Invalid)
    }

    @Test
    fun missing_user_id_returns_invalid() {
        val payload = """
        {
            "post_type": "message",
            "message_type": "private",
            "self_id": "12345",
            "message_id": "msg003",
            "raw_message": "test"
        }
        """.trimIndent()

        val result = parser.parse(payload)
        assertTrue("Expected Invalid result", result is OneBotPayloadParseResult.Invalid)
    }

    @Test
    fun json_array_message_with_at_self_sets_mentions_self() {
        val payload = """
        {
            "post_type": "message",
            "message_type": "group",
            "self_id": "12345",
            "user_id": "67890",
            "group_id": "111222",
            "message_id": "msg004",
            "message": [
                {"type": "at", "data": {"qq": "12345"}},
                {"type": "text", "data": {"text": "Hello bot"}}
            ],
            "sender": {"nickname": "User", "card": ""}
        }
        """.trimIndent()

        val result = parser.parse(payload)
        assertTrue(result is OneBotPayloadParseResult.Message)
        val parsed = result as OneBotPayloadParseResult.Message
        assertTrue("Should detect @self mention", parsed.mentionsSelf)
        assertEquals("Hello bot", parsed.message.text)
    }
}

package com.astrbot.android.core.runtime.container

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.feature.qq.runtime.QqReplyDecoration
import java.nio.file.Files
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqOneBotPayloadCodecTest {
    @Test
    fun `parse incoming event extracts mentions sender prompt and attachments`() {
        val payload = JSONObject(
            """
            {
              "message_type": "group",
              "self_id": "10001",
              "user_id": "20002",
              "group_id": "30003",
              "message_id": "40004",
              "sender": {
                "card": "群名片",
                "nickname": "昵称"
              },
              "message": [
                { "type": "text", "data": { "text": " 你好 " } },
                { "type": "at", "data": { "qq": "10001" } },
                { "type": "at", "data": { "qq": "all" } },
                { "type": "image", "data": { "file": "image.jpg", "url": "https://example.com/image.jpg" } },
                { "type": "record", "data": { "file": "voice.mp3", "url": "https://example.com/voice.mp3" } }
              ]
            }
            """.trimIndent(),
        )

        val event = QqOneBotPayloadCodec.parseIncomingMessageEvent(payload)

        requireNotNull(event)
        assertEquals(MessageType.GroupMessage, event.messageType)
        assertEquals("10001", event.selfId)
        assertEquals("20002", event.userId)
        assertEquals("30003", event.groupId)
        assertEquals("40004", event.messageId)
        assertEquals("你好", event.text)
        assertEquals("群名片: 你好", event.promptContent)
        assertTrue(event.mentionsSelf)
        assertTrue(event.mentionsAll)
        assertEquals("群名片", event.senderName)
        assertEquals("30003", event.targetId)
        assertEquals(2, event.attachments.size)
        assertEquals("image", event.attachments[0].type)
        assertEquals("image.jpg", event.attachments[0].fileName)
        assertEquals("https://example.com/image.jpg", event.attachments[0].remoteUrl)
        assertEquals("audio", event.attachments[1].type)
        assertEquals("voice.mp3", event.attachments[1].fileName)
        assertEquals("https://example.com/voice.mp3", event.attachments[1].remoteUrl)
    }

    @Test
    fun `parse incoming event falls back to raw message text`() {
        val payload = JSONObject(
            """
            {
              "message_type": "private",
              "self_id": "10001",
              "user_id": "20002",
              "message_id": "40004",
              "raw_message": " 直接文本 "
            }
            """.trimIndent(),
        )

        val event = QqOneBotPayloadCodec.parseIncomingMessageEvent(payload)

        requireNotNull(event)
        assertEquals(MessageType.FriendMessage, event.messageType)
        assertEquals("直接文本", event.text)
        assertEquals("直接文本", event.promptContent)
        assertFalse(event.mentionsSelf)
        assertFalse(event.mentionsAll)
        assertTrue(event.attachments.isEmpty())
        assertEquals("20002", event.targetId)
    }

    @Test
    fun `parse incoming event detects cq at mentions from string payload`() {
        val payload = JSONObject(
            """
            {
              "message_type": "group",
              "self_id": "10001",
              "user_id": "20002",
              "group_id": "30003",
              "message_id": "50005",
              "message": "[CQ:at,qq=10001] [CQ:at,qq=all] hello"
            }
            """.trimIndent(),
        )

        val event = QqOneBotPayloadCodec.parseIncomingMessageEvent(payload)

        requireNotNull(event)
        assertEquals("hello", event.text)
        assertTrue(event.mentionsSelf)
        assertTrue(event.mentionsAll)
    }

    @Test
    fun `build reply payload returns structured array when decoration and attachments exist`() {
        val payload = QqOneBotPayloadCodec.buildReplyPayload(
            text = "正文",
            attachments = listOf(
                ConversationAttachment(
                    id = "image-1",
                    type = "image",
                    base64Data = "aW1hZ2U=",
                    remoteUrl = "https://example.com/ignored.jpg",
                ),
                ConversationAttachment(
                    id = "audio-1",
                    type = "audio",
                    remoteUrl = "https://example.com/audio.mp3",
                ),
            ),
            decoration = QqReplyDecoration(
                textPrefix = "[前缀] ",
                quoteMessageId = "message-1",
                mentionUserId = "user-2",
            ),
            mapAudioAttachment = { attachment ->
                if (attachment.id == "audio-1") "base64://audio-data" else null
            },
        )

        require(payload is JSONArray)
        assertEquals(5, payload.length())
        assertEquals("reply", payload.getJSONObject(0).getString("type"))
        assertEquals("message-1", payload.getJSONObject(0).getJSONObject("data").getString("id"))
        assertEquals("at", payload.getJSONObject(1).getString("type"))
        assertEquals("user-2", payload.getJSONObject(1).getJSONObject("data").getString("qq"))
        assertEquals("text", payload.getJSONObject(2).getString("type"))
        assertEquals("[前缀] 正文", payload.getJSONObject(2).getJSONObject("data").getString("text"))
        assertEquals("image", payload.getJSONObject(3).getString("type"))
        assertEquals("base64://aW1hZ2U=", payload.getJSONObject(3).getJSONObject("data").getString("file"))
        assertEquals("record", payload.getJSONObject(4).getString("type"))
        assertEquals("base64://audio-data", payload.getJSONObject(4).getJSONObject("data").getString("file"))
    }

    @Test
    fun `build reply payload returns plain text when no decoration or attachments`() {
        val payload = QqOneBotPayloadCodec.buildReplyPayload(
            text = "纯文本",
            attachments = emptyList(),
            decoration = QqReplyDecoration(
                textPrefix = "",
                quoteMessageId = null,
                mentionUserId = null,
            ),
        )

        assertEquals("纯文本", payload)
    }
    @Test
    fun `build reply payload converts local image path to base64 payload`() {
        val imageFile = Files.createTempFile("onebot-payload", ".png").toFile()
        try {
            val imageBytes = byteArrayOf(1, 2, 3, 4, 5)
            imageFile.writeBytes(imageBytes)

            val payload = QqOneBotPayloadCodec.buildReplyPayload(
                text = "",
                attachments = listOf(
                    ConversationAttachment(
                        id = "image-local",
                        type = "image",
                        mimeType = "image/png",
                        remoteUrl = imageFile.absolutePath,
                    ),
                ),
                decoration = QqReplyDecoration(
                    textPrefix = "",
                    quoteMessageId = null,
                    mentionUserId = null,
                ),
            )

            require(payload is JSONArray)
            assertEquals(1, payload.length())
            assertEquals("image", payload.getJSONObject(0).getString("type"))
            assertEquals(
                "base64://${Base64.getEncoder().encodeToString(imageBytes)}",
                payload.getJSONObject(0).getJSONObject("data").getString("file"),
            )
        } finally {
            imageFile.delete()
        }
    }
}

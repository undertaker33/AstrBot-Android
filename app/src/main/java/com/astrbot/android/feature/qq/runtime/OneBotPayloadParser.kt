package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.MessageType
import org.json.JSONArray
import org.json.JSONObject

open class OneBotPayloadParser {

    open fun parse(payload: String): OneBotPayloadParseResult {
        val json = try {
            JSONObject(payload)
        } catch (e: Exception) {
            return OneBotPayloadParseResult.Invalid("Malformed JSON: ${e.message}")
        }

        val postType = json.optString("post_type")
        if (postType != "message") {
            return OneBotPayloadParseResult.Ignored("post_type=$postType is not a message event")
        }

        val messageType = when (json.optString("message_type")) {
            "private" -> MessageType.FriendMessage
            "group" -> MessageType.GroupMessage
            else -> return OneBotPayloadParseResult.Ignored("Unsupported message_type: ${json.optString("message_type")}")
        }

        val selfId = jsonValueAsString(json, "self_id")
        val userId = jsonValueAsString(json, "user_id")
        if (userId.isBlank()) {
            return OneBotPayloadParseResult.Invalid("Missing user_id in message event")
        }

        val groupId = jsonValueAsString(json, "group_id")
        val messageId = jsonValueAsString(json, "message_id")

        val parsed = parseMessage(
            rawMessage = json.opt("message"),
            fallbackText = json.optString("raw_message"),
            selfId = selfId,
        )

        val sender = json.optJSONObject("sender")
        val senderName = sender?.optString("card").orEmpty()
            .ifBlank { sender?.optString("nickname").orEmpty() }

        val conversationId = when (messageType) {
            MessageType.GroupMessage -> groupId
            else -> userId
        }

        val message = IncomingQqMessage(
            selfId = selfId,
            messageId = messageId,
            conversationId = conversationId,
            senderId = userId,
            senderName = senderName,
            text = parsed.text,
            messageType = messageType,
            attachments = parsed.attachments,
            mentionsSelf = parsed.mentionsSelf,
            mentionsAll = parsed.mentionsAll,
            rawPayload = payload,
        )

        return OneBotPayloadParseResult.Message(message, parsed.mentionsSelf, parsed.mentionsAll)
    }

    private fun parseMessage(
        rawMessage: Any?,
        fallbackText: String,
        selfId: String,
    ): ParsedPayload {
        if (rawMessage is JSONArray) {
            val builder = StringBuilder()
            var mentionsSelf = false
            var mentionsAll = false
            val attachments = mutableListOf<ConversationAttachment>()
            for (index in 0 until rawMessage.length()) {
                val segment = rawMessage.optJSONObject(index) ?: continue
                when (segment.optString("type")) {
                    "text" -> builder.append(segment.optJSONObject("data")?.optString("text").orEmpty())
                    "at" -> {
                        val qq = segment.optJSONObject("data")?.optString("qq").orEmpty()
                        if (qq.isNotBlank() && qq == selfId) mentionsSelf = true
                        if (qq.equals("all", ignoreCase = true)) mentionsAll = true
                    }
                    "image" -> {
                        val data = segment.optJSONObject("data")
                        attachments += ConversationAttachment(
                            id = "image-$index",
                            type = "image",
                            mimeType = "image/jpeg",
                            fileName = data?.optString("file").orEmpty(),
                            remoteUrl = data?.optString("url").orEmpty(),
                        )
                    }
                    "record" -> {
                        val data = segment.optJSONObject("data")
                        attachments += ConversationAttachment(
                            id = "audio-$index",
                            type = "audio",
                            mimeType = "audio/mpeg",
                            fileName = data?.optString("file").orEmpty(),
                            remoteUrl = data?.optString("url").orEmpty(),
                        )
                    }
                }
            }
            return ParsedPayload(
                text = builder.toString().trim(),
                mentionsSelf = mentionsSelf,
                mentionsAll = mentionsAll,
                attachments = attachments,
            )
        }

        if (rawMessage is String) {
            val mentionsSelf = CQ_AT_REGEX.containsMatchIn(rawMessage) &&
                CQ_AT_REGEX.findAll(rawMessage).any { it.groupValues.getOrNull(1).orEmpty() == selfId }
            val mentionsAll = CQ_AT_REGEX.findAll(rawMessage).any {
                it.groupValues.getOrNull(1).orEmpty().equals("all", ignoreCase = true)
            }
            return ParsedPayload(
                text = CQ_CODE_REGEX.replace(rawMessage, " ").trim(),
                mentionsSelf = mentionsSelf,
                mentionsAll = mentionsAll,
                attachments = emptyList(),
            )
        }

        return ParsedPayload(
            text = fallbackText.trim(),
            mentionsSelf = false,
            mentionsAll = false,
            attachments = emptyList(),
        )
    }

    private fun jsonValueAsString(json: JSONObject, key: String): String =
        json.opt(key)?.toString().orEmpty()

    private companion object {
        val CQ_AT_REGEX = Regex("""\[CQ:at,qq=([^,\]]+)(?:,[^\]]*)?]""", RegexOption.IGNORE_CASE)
        val CQ_CODE_REGEX = Regex("""\[CQ:[^\]]+]""", RegexOption.IGNORE_CASE)
    }
}

internal data class ParsedPayload(
    val text: String,
    val mentionsSelf: Boolean,
    val mentionsAll: Boolean,
    val attachments: List<ConversationAttachment>,
)

sealed interface OneBotPayloadParseResult {
    data class Message(
        val message: IncomingQqMessage,
        val mentionsSelf: Boolean,
        val mentionsAll: Boolean,
    ) : OneBotPayloadParseResult

    data class Ignored(val reason: String) : OneBotPayloadParseResult
    data class Invalid(val reason: String) : OneBotPayloadParseResult
}

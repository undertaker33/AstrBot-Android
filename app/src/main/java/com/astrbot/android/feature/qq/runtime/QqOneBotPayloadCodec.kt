package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.feature.qq.runtime.QqReplyDecoration
import java.io.File
import java.net.URI
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal object QqOneBotPayloadCodec {
    private val cqAtRegex = Regex("""\[CQ:at,qq=([^,\]]+)(?:,[^\]]*)?]""", RegexOption.IGNORE_CASE)
    private val cqCodeRegex = Regex("""\[CQ:[^\]]+]""", RegexOption.IGNORE_CASE)

    fun parseIncomingMessageEvent(json: JSONObject): IncomingMessageEvent? {
        val messageType = when (json.optString("message_type")) {
            "private" -> MessageType.FriendMessage
            "group" -> MessageType.GroupMessage
            else -> null
        } ?: return null

        val selfId = jsonValueAsString(json, "self_id")
        val userId = jsonValueAsString(json, "user_id")
        if (userId.isBlank()) return null

        val parsedMessage = parseMessage(
            rawMessage = json.opt("message"),
            fallbackText = json.optString("raw_message"),
            selfId = selfId,
        )
        val sender = json.optJSONObject("sender")
        val senderName = sender
            ?.optString("card")
            .orEmpty()
            .ifBlank { sender?.optString("nickname").orEmpty() }

        return IncomingMessageEvent(
            selfId = selfId,
            userId = userId,
            groupId = jsonValueAsString(json, "group_id"),
            messageId = jsonValueAsString(json, "message_id"),
            messageType = messageType,
            text = parsedMessage.text,
            promptContent = when (messageType) {
                MessageType.GroupMessage -> "${senderName.ifBlank { userId }}: ${parsedMessage.text}"
                else -> parsedMessage.text
            },
            mentionsSelf = parsedMessage.mentionsSelf,
            mentionsAll = parsedMessage.mentionsAll,
            attachments = parsedMessage.attachments,
            senderName = senderName,
        )
    }

    fun buildReplyPayload(
        text: String,
        attachments: List<ConversationAttachment>,
        decoration: QqReplyDecoration,
        mapAudioAttachment: (ConversationAttachment) -> String? = { null },
    ): Any {
        val finalText = decoration.textPrefix + text
        if (
            attachments.isEmpty() &&
            decoration.quoteMessageId == null &&
            decoration.mentionUserId == null
        ) {
            return finalText
        }
        val payload = JSONArray().apply {
            decoration.quoteMessageId?.let { messageId ->
                put(
                    JSONObject().put("type", "reply").put(
                        "data",
                        JSONObject().put("id", messageId),
                    ),
                )
            }
            decoration.mentionUserId?.let { userId ->
                put(
                    JSONObject().put("type", "at").put(
                        "data",
                        JSONObject().put("qq", userId),
                    ),
                )
            }
            if (finalText.isNotBlank()) {
                put(
                    JSONObject().put("type", "text").put(
                        "data",
                        JSONObject().put("text", finalText),
                    ),
                )
            }
            attachments.forEach { attachment ->
                when (attachment.type) {
                    "audio" -> {
                        val fileValue = mapAudioAttachment(attachment) ?: attachment.remoteUrl
                        if (fileValue.isNotBlank()) {
                            put(
                                JSONObject().put("type", "record").put(
                                    "data",
                                    JSONObject().put("file", fileValue),
                                ),
                            )
                        }
                    }

                    "image" -> {
                        val fileValue = imagePayloadValue(attachment)
                        if (fileValue.isNotBlank()) {
                            put(
                                JSONObject().put("type", "image").put(
                                    "data",
                                    JSONObject().put("file", fileValue),
                                ),
                            )
                        }
                    }
                }
            }
        }
        return if (payload.length() > 0) payload else finalText
    }

    private fun parseMessage(
        rawMessage: Any?,
        fallbackText: String,
        selfId: String,
    ): ParsedMessage {
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
                        if (qq.isNotBlank() && qq == selfId) {
                            mentionsSelf = true
                        }
                        if (qq.equals("all", ignoreCase = true)) {
                            mentionsAll = true
                        }
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
            return ParsedMessage(
                text = builder.toString().trim(),
                mentionsSelf = mentionsSelf,
                mentionsAll = mentionsAll,
                attachments = attachments,
            )
        }

        if (rawMessage is String) {
            val mentionsSelf = cqAtRegex.containsMatchIn(rawMessage) &&
                cqAtRegex.findAll(rawMessage).any { match ->
                    match.groupValues.getOrNull(1).orEmpty() == selfId
                }
            val mentionsAll = cqAtRegex.findAll(rawMessage).any { match ->
                match.groupValues.getOrNull(1).orEmpty().equals("all", ignoreCase = true)
            }
            return ParsedMessage(
                text = cqCodeRegex.replace(rawMessage, " ").trim(),
                mentionsSelf = mentionsSelf,
                mentionsAll = mentionsAll,
                attachments = emptyList(),
            )
        }

        return ParsedMessage(
            text = fallbackText.trim(),
            mentionsSelf = false,
            mentionsAll = false,
            attachments = emptyList(),
        )
    }

    private fun jsonValueAsString(json: JSONObject, key: String): String {
        return json.opt(key)?.toString().orEmpty()
    }

    private fun imagePayloadValue(attachment: ConversationAttachment): String {
        attachment.base64Data.takeIf { it.isNotBlank() }?.let { return "base64://$it" }
        val remoteUrl = attachment.remoteUrl
        val localFile = localFileForPayload(remoteUrl)
        if (localFile != null) {
            return runCatching {
                "base64://${Base64.getEncoder().encodeToString(localFile.readBytes())}"
            }.getOrDefault(remoteUrl)
        }
        return remoteUrl
    }

    private fun localFileForPayload(location: String): File? {
        if (location.isBlank()) return null
        return runCatching {
            when {
                location.startsWith("http://", ignoreCase = true) ||
                    location.startsWith("https://", ignoreCase = true) ||
                    location.startsWith("base64://", ignoreCase = true) -> null

                location.startsWith("file://", ignoreCase = true) -> File(URI(location))
                else -> File(location)
            }
        }.getOrNull()?.takeIf { it.exists() && it.isFile }
    }
}

internal data class IncomingMessageEvent(
    val selfId: String,
    val userId: String,
    val groupId: String,
    val messageId: String,
    val messageType: MessageType,
    val text: String,
    val promptContent: String,
    val mentionsSelf: Boolean,
    val mentionsAll: Boolean,
    val attachments: List<ConversationAttachment>,
    val senderName: String,
) {
    val targetId: String
        get() = if (messageType == MessageType.GroupMessage) groupId else userId
}

private data class ParsedMessage(
    val text: String,
    val mentionsSelf: Boolean,
    val mentionsAll: Boolean,
    val attachments: List<ConversationAttachment>,
)

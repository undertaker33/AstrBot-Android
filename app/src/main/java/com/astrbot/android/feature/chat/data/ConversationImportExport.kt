package com.astrbot.android.feature.chat.data

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.chat.defaultSessionRefFor
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal fun ConversationSession.toConversationJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("title", title)
        put("botId", botId)
        put("personaId", personaId)
        put("providerId", providerId)
        put("platformId", platformId)
        put("messageType", messageType.wireValue)
        put("originSessionId", originSessionId)
        put("maxContextMessages", maxContextMessages)
        put("sessionSttEnabled", sessionSttEnabled)
        put("sessionTtsEnabled", sessionTtsEnabled)
        put("pinned", pinned)
        put("titleCustomized", titleCustomized)
        put(
            "messages",
            JSONArray().apply {
                messages.forEach { message -> put(message.toConversationJson()) }
            },
        )
    }
}

internal fun JSONObject.toConversationSession(
    defaultTitle: String,
    defaultBotId: String = "qq-main",
): ConversationSession {
    val messagesArray = optJSONArray("messages") ?: JSONArray()
    val id = optString("id").ifBlank { UUID.randomUUID().toString() }
    val defaultRef = defaultSessionRefFor(id)
    return ConversationSession(
        id = id,
        title = optString("title").ifBlank { defaultTitle },
        botId = optString("botId").ifBlank { defaultBotId },
        personaId = optString("personaId").takeUnless { it.isBlank() || it == "default" }.orEmpty(),
        providerId = optString("providerId"),
        platformId = optString("platformId").ifBlank { defaultRef.platformId },
        messageType = MessageType.fromWireValue(optString("messageType")) ?: defaultRef.messageType,
        originSessionId = optString("originSessionId").ifBlank { defaultRef.originSessionId },
        maxContextMessages = optInt("maxContextMessages", 12),
        sessionSttEnabled = optBoolean("sessionSttEnabled", true),
        sessionTtsEnabled = optBoolean("sessionTtsEnabled", true),
        pinned = optBoolean("pinned", false),
        titleCustomized = optBoolean("titleCustomized", false),
        messages = buildList {
            for (index in 0 until messagesArray.length()) {
                add(messagesArray.optJSONObject(index)?.toConversationMessage() ?: continue)
            }
        },
    )
}

internal fun ConversationMessage.toConversationJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("role", role)
        put("content", content)
        put("timestamp", timestamp)
        put(
            "attachments",
            JSONArray().apply {
                attachments.forEach { attachment -> put(attachment.toConversationJson()) }
            },
        )
    }
}

internal fun JSONObject.toConversationMessage(): ConversationMessage {
    val attachmentsArray = optJSONArray("attachments") ?: JSONArray()
    return ConversationMessage(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        role = optString("role"),
        content = optString("content"),
        timestamp = optLong("timestamp", System.currentTimeMillis()),
        attachments = buildList {
            for (index in 0 until attachmentsArray.length()) {
                add(attachmentsArray.optJSONObject(index)?.toConversationAttachment() ?: continue)
            }
        },
    )
}

private fun ConversationAttachment.toConversationJson(): JSONObject {
    return JSONObject().apply {
        put("id", id)
        put("type", type)
        put("mimeType", mimeType)
        put("fileName", fileName)
        put("base64Data", base64Data)
        put("remoteUrl", remoteUrl)
    }
}

private fun JSONObject.toConversationAttachment(): ConversationAttachment {
    return ConversationAttachment(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        type = optString("type").ifBlank { "image" },
        mimeType = optString("mimeType").ifBlank { "image/jpeg" },
        fileName = optString("fileName"),
        base64Data = optString("base64Data"),
        remoteUrl = optString("remoteUrl"),
    )
}

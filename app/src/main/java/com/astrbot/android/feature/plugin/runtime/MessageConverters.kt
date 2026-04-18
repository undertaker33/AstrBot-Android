package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationToolCall
import com.astrbot.android.model.plugin.PluginExecutionProtocolJson
import java.util.Locale

object MessageConverters {

    fun List<ConversationMessage>.toPluginProviderMessages(): List<PluginProviderMessageDto> {
        return map { message ->
            val role = when (message.role.lowercase(Locale.US)) {
                "system" -> PluginProviderMessageRole.SYSTEM
                "assistant" -> PluginProviderMessageRole.ASSISTANT
                "tool" -> PluginProviderMessageRole.TOOL
                else -> PluginProviderMessageRole.USER
            }
            val parts = mutableListOf<PluginProviderMessagePartDto>()
            message.content.takeIf { it.isNotBlank() }?.let {
                parts += PluginProviderMessagePartDto.TextPart(it)
            }
            message.attachments.forEach { attachment ->
                val uri = attachment.remoteUrl.ifBlank {
                    attachment.base64Data.takeIf(String::isNotBlank)?.let { base64 ->
                        "data:${attachment.mimeType};base64,$base64"
                    } ?: "attachment://${attachment.id}"
                }
                parts += PluginProviderMessagePartDto.MediaRefPart(
                    uri = uri,
                    mimeType = attachment.mimeType.ifBlank { "application/octet-stream" },
                )
            }
            if (parts.isEmpty()) {
                parts += PluginProviderMessagePartDto.TextPart("[empty]")
            }
            val toolName = if (role == PluginProviderMessageRole.TOOL) {
                message.toolCallId.ifBlank { "tool" }
            } else {
                null
            }
            val toolMeta: Map<String, Any?>? = if (role == PluginProviderMessageRole.TOOL) {
                mapOf("__host" to mapOf("toolCallId" to message.toolCallId))
            } else {
                null
            }
            PluginProviderMessageDto(
                role = role,
                parts = parts,
                name = toolName,
                metadata = toolMeta,
                toolCalls = message.assistantToolCalls.map { toolCall ->
                    PluginProviderAssistantToolCall(
                        id = toolCall.id,
                        toolName = toolCall.name,
                        arguments = parseToolArguments(toolCall.arguments),
                    )
                },
            )
        }
    }

    fun List<PluginProviderMessageDto>.toConversationMessages(
        requestId: String,
    ): List<ConversationMessage> {
        return mapIndexed { index, message ->
            val text = message.parts
                .filterIsInstance<PluginProviderMessagePartDto.TextPart>()
                .joinToString(separator = "\n") { it.text }
            val attachments = message.parts
                .filterIsInstance<PluginProviderMessagePartDto.MediaRefPart>()
                .mapIndexed { attachmentIndex, part ->
                    ConversationAttachment(
                        id = "$requestId-$index-$attachmentIndex",
                        type = if (part.mimeType.startsWith("audio/")) "audio" else "image",
                        mimeType = part.mimeType,
                        remoteUrl = part.uri,
                    )
                }
            val toolCallId = if (message.role == PluginProviderMessageRole.TOOL) {
                extractHostToolCallId(message.metadata)
            } else {
                null
            }
            ConversationMessage(
                id = toolCallId ?: "$requestId-$index",
                role = message.role.wireValue,
                content = text,
                timestamp = System.currentTimeMillis(),
                attachments = attachments,
                toolCallId = toolCallId.orEmpty(),
                assistantToolCalls = message.toolCalls.map { toolCall ->
                    ConversationToolCall(
                        id = toolCall.normalizedId,
                        name = toolCall.normalizedToolName,
                        arguments = PluginExecutionProtocolJson.canonicalJson(toolCall.normalizedArguments),
                    )
                },
            )
        }
    }

    private fun extractHostToolCallId(metadata: Map<String, *>?): String? {
        val host = metadata?.get("__host") as? Map<*, *> ?: return null
        return (host["toolCallId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun parseToolArguments(raw: String): Map<String, Any?> {
        return runCatching {
            org.json.JSONObject(raw).keys().asSequence().associateWith { key ->
                org.json.JSONObject(raw).opt(key)
            }
        }.getOrDefault(emptyMap())
    }
}

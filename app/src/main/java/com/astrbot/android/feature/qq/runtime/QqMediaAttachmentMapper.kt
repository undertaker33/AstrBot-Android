package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.model.chat.ConversationAttachment
import java.io.File
import java.net.URI
import java.util.Base64

internal object QqMediaAttachmentMapper {

    fun materializeAudioForOneBot(attachment: ConversationAttachment): String? {
        if (attachment.type != "audio") return null
        attachment.base64Data.takeIf { it.isNotBlank() }?.let { return "base64://$it" }
        val location = attachment.remoteUrl
        val localFile = resolveLocalFile(location)
        if (localFile != null) {
            return runCatching {
                "base64://${Base64.getEncoder().encodeToString(localFile.readBytes())}"
            }.getOrDefault(location)
        }
        return location.takeIf { it.isNotBlank() }
    }

    fun imagePayloadValue(attachment: ConversationAttachment): String {
        attachment.base64Data.takeIf { it.isNotBlank() }?.let { return "base64://$it" }
        val remoteUrl = attachment.remoteUrl
        val localFile = resolveLocalFile(remoteUrl)
        if (localFile != null) {
            return runCatching {
                "base64://${Base64.getEncoder().encodeToString(localFile.readBytes())}"
            }.getOrDefault(remoteUrl)
        }
        return remoteUrl
    }

    private fun resolveLocalFile(location: String): File? {
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

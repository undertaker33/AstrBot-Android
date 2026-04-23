package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.model.chat.ConversationAttachment
import java.io.File
import java.util.Base64

internal class QqAudioAttachmentMaterializer(
    private val filesDirProvider: () -> File?,
    private val encodeSilkAudio: (File) -> File,
    private val log: (String) -> Unit,
) {
    fun materialize(attachment: ConversationAttachment): String? {
        if (attachment.base64Data.isBlank()) {
            return null
        }
        val filesDir = filesDirProvider() ?: return null
        return runCatching {
            val outputDir = File(filesDir, "runtime/tts-out").apply { mkdirs() }
            val extension = when {
                attachment.fileName.contains('.') -> attachment.fileName.substringAfterLast('.')
                attachment.mimeType.contains("wav") -> "wav"
                else -> "mp3"
            }
            val rawFile = File(outputDir, "tts-${System.currentTimeMillis()}-${attachment.id.take(8)}.$extension")
            rawFile.writeBytes(Base64.getDecoder().decode(attachment.base64Data))
            log("QQ TTS attachment materialized: ${rawFile.absolutePath}")
            val silkFile = encodeSilkAudio(rawFile)
            val napCatBase64 = "base64://${Base64.getEncoder().encodeToString(silkFile.readBytes())}"
            log("QQ TTS attachment converted to silk: ${silkFile.absolutePath}")
            log("QQ TTS attachment mapped for OneBot: base64://${silkFile.name} bytes=${silkFile.length()}")
            napCatBase64
        }.onFailure { error ->
            log("QQ TTS attachment materialize failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrNull()
    }
}

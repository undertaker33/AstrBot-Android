package com.astrbot.android.data.backup

import java.io.File
import java.util.Base64

data class TtsClipBackupPayload(
    val archivePath: String = "",
    val embeddedFileName: String = "",
    val embeddedDataBase64: String = "",
)

internal fun buildTtsClipBackupPayload(
    assetId: String,
    clipId: String,
    localPath: String,
    includeEmbeddedData: Boolean,
): TtsClipBackupPayload {
    val source = localPath.trim().takeIf { it.isNotBlank() }?.let(::File)
        ?.takeIf { it.exists() && it.isFile }
        ?: return TtsClipBackupPayload(
            embeddedFileName = "$clipId.bin",
        )
    return TtsClipBackupPayload(
        archivePath = ttsClipArchivePath(assetId, source.name),
        embeddedFileName = source.name,
        embeddedDataBase64 = if (includeEmbeddedData) {
            Base64.getEncoder().encodeToString(source.readBytes())
        } else {
            ""
        },
    )
}

internal fun ttsClipArchivePath(assetId: String, fileName: String): String {
    return "files/tts-reference-audio/$assetId/$fileName"
}

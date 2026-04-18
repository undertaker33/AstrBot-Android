package com.astrbot.android.core.db.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

class TtsBackupArchiveTest {
    @Test
    fun `zip backup metadata keeps archive path but omits embedded base64`() {
        val tempDir = Files.createTempDirectory("tts-backup-zip").toFile()
        val audioFile = File(tempDir, "voice.wav").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }

        val payload = buildTtsClipBackupPayload(
            assetId = "asset-1",
            clipId = "clip-1",
            localPath = audioFile.absolutePath,
            includeEmbeddedData = false,
        )

        assertEquals("files/tts-reference-audio/asset-1/voice.wav", payload.archivePath)
        assertEquals("voice.wav", payload.embeddedFileName)
        assertTrue(payload.embeddedDataBase64.isEmpty())
    }

    @Test
    fun `legacy json metadata can still embed base64 when requested`() {
        val tempDir = Files.createTempDirectory("tts-backup-json").toFile()
        val audioBytes = byteArrayOf(9, 8, 7)
        val audioFile = File(tempDir, "legacy.wav").apply { writeBytes(audioBytes) }

        val payload = buildTtsClipBackupPayload(
            assetId = "asset-legacy",
            clipId = "clip-legacy",
            localPath = audioFile.absolutePath,
            includeEmbeddedData = true,
        )

        assertEquals("files/tts-reference-audio/asset-legacy/legacy.wav", payload.archivePath)
        assertEquals(Base64.getEncoder().encodeToString(audioBytes), payload.embeddedDataBase64)
    }
}

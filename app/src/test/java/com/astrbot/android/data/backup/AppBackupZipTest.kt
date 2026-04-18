package com.astrbot.android.core.db.backup

import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AppBackupZipTest {
    @Test
    fun `write zip stores manifest and files`() {
        val tempDir = Files.createTempDirectory("app-backup-zip-write").toFile()
        val sourceFile = File(tempDir, "source.bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val zipFile = File(tempDir, "backup.zip")

        writeAppBackupZip(
            targetZip = zipFile,
            manifestJson = JSONObject().put("schema", "astrbot-android-full-backup-v1"),
            files = listOf(
                AppBackupZipEntrySource(
                    archivePath = "files/tts/reference/source.bin",
                    sourceFile = sourceFile,
                ),
            ),
        )

        val extracted = readAppBackupZip(zipFile, File(tempDir, "unzipped"))

        assertEquals("astrbot-android-full-backup-v1", extracted.manifestJson.getString("schema"))
        assertTrue(extracted.files.containsKey("files/tts/reference/source.bin"))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4), extracted.files.getValue("files/tts/reference/source.bin").readBytes())
    }

    @Test
    fun `read zip rejects archives without manifest`() {
        val tempDir = Files.createTempDirectory("app-backup-zip-read").toFile()
        val zipFile = File(tempDir, "broken.zip")

        java.util.zip.ZipOutputStream(zipFile.outputStream()).use { zip ->
            zip.putNextEntry(java.util.zip.ZipEntry("files/tts/reference/source.bin"))
            zip.write(byteArrayOf(9))
            zip.closeEntry()
        }

        val error = runCatching {
            readAppBackupZip(zipFile, File(tempDir, "unzipped"))
        }.exceptionOrNull()

        assertTrue(error?.message?.contains("manifest", ignoreCase = true) == true)
    }

    @Test
    fun `read backup payload file understands zip manifests`() {
        val tempDir = Files.createTempDirectory("app-backup-payload-read").toFile()
        val zipFile = File(tempDir, "full-backup.zip")

        writeAppBackupZip(
            targetZip = zipFile,
            manifestJson = JSONObject().put("schema", "astrbot-android-full-backup-v1").put("createdAt", 7L),
            files = emptyList(),
        )

        val payload = readAppBackupPayloadFile(zipFile, File(tempDir, "unzipped"))

        assertEquals("astrbot-android-full-backup-v1", payload.manifestJson.getString("schema"))
        assertEquals(7L, payload.manifestJson.getLong("createdAt"))
    }
}

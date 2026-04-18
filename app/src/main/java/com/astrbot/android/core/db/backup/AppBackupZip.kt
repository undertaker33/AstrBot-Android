package com.astrbot.android.core.db.backup

import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class AppBackupZipEntrySource(
    val archivePath: String,
    val sourceFile: File,
)

data class AppBackupZipReadResult(
    val manifestJson: JSONObject,
    val files: Map<String, File>,
)

internal fun writeAppBackupZip(
    targetZip: File,
    manifestJson: JSONObject,
    files: List<AppBackupZipEntrySource>,
) {
    targetZip.parentFile?.mkdirs()
    ZipOutputStream(targetZip.outputStream().buffered()).use { zip ->
        zip.putNextEntry(ZipEntry("manifest.json"))
        zip.write(manifestJson.toString(2).toByteArray(Charsets.UTF_8))
        zip.closeEntry()

        files.forEach { entry ->
            zip.putNextEntry(ZipEntry(entry.archivePath))
            entry.sourceFile.inputStream().use { input -> input.copyTo(zip) }
            zip.closeEntry()
        }
    }
}

internal fun readAppBackupZip(
    zipFile: File,
    outputDirectory: File,
): AppBackupZipReadResult {
    outputDirectory.mkdirs()
    var manifestJson: JSONObject? = null
    val extractedFiles = linkedMapOf<String, File>()

    ZipInputStream(zipFile.inputStream().buffered()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val normalizedPath = entry.name.replace('\\', '/')
            if (entry.isDirectory) {
                File(outputDirectory, normalizedPath).mkdirs()
                zip.closeEntry()
                continue
            }

            if (normalizedPath == "manifest.json") {
                manifestJson = JSONObject(zip.readBytes().toString(Charsets.UTF_8))
                zip.closeEntry()
                continue
            }

            val destination = File(outputDirectory, normalizedPath).apply {
                parentFile?.mkdirs()
            }
            destination.outputStream().use { output -> zip.copyTo(output) }
            extractedFiles[normalizedPath] = destination
            zip.closeEntry()
        }
    }

    return AppBackupZipReadResult(
        manifestJson = manifestJson ?: error("Backup archive is missing manifest.json"),
        files = extractedFiles,
    )
}

package com.astrbot.android.core.db.backup

import org.json.JSONObject
import java.io.File

data class AppBackupParsedPayload(
    val manifestJson: JSONObject,
    val extractedFiles: Map<String, File> = emptyMap(),
)

internal fun readAppBackupPayloadFile(
    file: File,
    extractionDirectory: File? = null,
): AppBackupParsedPayload {
    return if (file.extension.equals("zip", ignoreCase = true)) {
        val zip = readAppBackupZip(
            zipFile = file,
            outputDirectory = extractionDirectory ?: error("Zip backup reads require an extraction directory"),
        )
        AppBackupParsedPayload(
            manifestJson = zip.manifestJson,
            extractedFiles = zip.files,
        )
    } else {
        AppBackupParsedPayload(
            manifestJson = JSONObject(file.readText(Charsets.UTF_8)),
        )
    }
}

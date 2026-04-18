package com.astrbot.android.core.runtime.container

import com.astrbot.android.core.common.logging.RuntimeLogRepository

import android.system.Os
import org.apache.commons.compress.archivers.ar.ArArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files

@Suppress("DEPRECATION")
object DebPayloadExtractor {
    private const val offlineMarkerName = ".astrbot_offline_payload_v1"
    private const val qqMarkerName = ".astrbot_qq_payload_v1"

    fun ensurePrepared(rootfsDir: File, offlineDebTar: File?, qqDeb: File?) {
        if (offlineDebTar?.exists() == true && !isOfflinePayloadReady(rootfsDir)) {
            RuntimeLogRepository.append("Extracting bundled offline deb payloads into rootfs")
            extractOfflineBundle(offlineDebTar, rootfsDir)
            File(rootfsDir, offlineMarkerName).writeText("ok")
            RuntimeLogRepository.append("Bundled offline deb payloads ready")
        }

        if (qqDeb?.exists() == true && !isQqPayloadReady(rootfsDir)) {
            RuntimeLogRepository.append("Extracting bundled QQ payload into rootfs")
            extractDebFile(qqDeb, rootfsDir)
            File(rootfsDir, qqMarkerName).writeText("ok")
            RuntimeLogRepository.append("Bundled QQ payload ready")
        }
    }

    private fun isOfflinePayloadReady(rootfsDir: File): Boolean {
        val marker = File(rootfsDir, offlineMarkerName)
        val commandsReady =
            File(rootfsDir, "usr/bin/curl").exists() &&
            File(rootfsDir, "usr/bin/unzip").exists() &&
            File(rootfsDir, "usr/bin/Xvfb").exists() &&
            File(rootfsDir, "usr/bin/screen").exists() &&
            File(rootfsDir, "usr/bin/xauth").exists() &&
            File(rootfsDir, "usr/bin/g++").exists()
        return commandsReady || marker.exists()
    }

    private fun isQqPayloadReady(rootfsDir: File): Boolean {
        val marker = File(rootfsDir, qqMarkerName)
        return File(rootfsDir, "opt/QQ/qq").exists() || marker.exists()
    }

    private fun extractOfflineBundle(bundleTar: File, rootfsDir: File) {
        val tempDir = File(rootfsDir.parentFile, ".tmp_offline_debs")
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        TarArchiveInputStream(FileInputStream(bundleTar)).use { tarInput ->
            var entry = tarInput.nextTarEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.endsWith(".deb")) {
                    val tempDeb = File(tempDir, File(entry.name).name)
                    tempDeb.outputStream().use { output -> tarInput.copyTo(output) }
                    extractDebFile(tempDeb, rootfsDir)
                }
                entry = tarInput.nextTarEntry
            }
        }

        tempDir.deleteRecursively()
    }

    private fun extractDebFile(debFile: File, rootfsDir: File) {
        FileInputStream(debFile).use { input ->
            extractDebStream(input, rootfsDir, debFile.name)
        }
    }

    private fun extractDebStream(input: InputStream, rootfsDir: File, label: String) {
        ArArchiveInputStream(input).use { arInput ->
            while (true) {
                val entry = arInput.nextArEntry ?: break
                val name = entry.name.trim()
                if (!name.startsWith("data.tar")) {
                    continue
                }

                val payloadStream = when {
                    name.endsWith(".xz") -> XZInputStream(arInput)
                    name.endsWith(".gz") -> GzipCompressorInputStream(arInput)
                    name.endsWith(".zst") -> ZstdCompressorInputStream(arInput)
                    name.endsWith(".tar") -> arInput
                    else -> throw IllegalStateException("Unsupported deb payload format: $name")
                }
                extractTarPayload(payloadStream, rootfsDir, "$label:$name")
                return
            }
        }

        throw IllegalStateException("Missing data payload in deb: $label")
    }

    private fun extractTarPayload(input: InputStream, rootfsDir: File, label: String) {
        TarArchiveInputStream(input).use { tarInput ->
            var entry = tarInput.nextTarEntry
            while (entry != null) {
                val outputFile = resolveEntryFile(rootfsDir, entry)
                when {
                    entry.isDirectory -> {
                        outputFile.mkdirs()
                    }

                    entry.isSymbolicLink -> {
                        outputFile.parentFile?.mkdirs()
                        outputFile.delete()
                        Os.symlink(entry.linkName, outputFile.absolutePath)
                    }

                    entry.isLink -> {
                        outputFile.parentFile?.mkdirs()
                        val targetFile = resolveHardLinkTarget(rootfsDir, outputFile, entry.linkName)
                        outputFile.delete()
                        runCatching {
                            Files.createLink(outputFile.toPath(), targetFile.toPath())
                        }.onFailure {
                            targetFile.copyTo(outputFile, overwrite = true)
                        }
                    }

                    else -> {
                        outputFile.parentFile?.mkdirs()
                        outputFile.outputStream().use { output -> tarInput.copyTo(output) }
                        outputFile.setExecutable((entry.mode and 0b001_001_001) != 0, false)
                    }
                }
                entry = tarInput.nextTarEntry
            }
        }
        RuntimeLogRepository.append("Extracted deb payload: $label")
    }

    private fun resolveEntryFile(baseDir: File, entry: TarArchiveEntry): File {
        val sanitizedName = entry.name.removePrefix("./").trimStart('/')
        return resolvePath(baseDir, sanitizedName)
    }

    private fun resolveHardLinkTarget(baseDir: File, outputFile: File, linkName: String?): File {
        val name = linkName?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing hard link target for ${outputFile.absolutePath}")
        val rootRelativeTarget = runCatching { resolvePath(baseDir, name) }.getOrNull()
        if (rootRelativeTarget != null && rootRelativeTarget.exists()) {
            return rootRelativeTarget
        }
        val parentTarget = File(outputFile.parentFile, name)
        val basePath = baseDir.canonicalPath + File.separator
        val targetPath = parentTarget.canonicalPath
        if (!targetPath.startsWith(basePath)) {
            throw IllegalStateException("Blocked unsafe hard link target: $name")
        }
        return parentTarget
    }

    private fun resolvePath(baseDir: File, relativePath: String): File {
        val output = File(baseDir, relativePath.removePrefix("./").trimStart('/'))
        val basePath = baseDir.canonicalPath + File.separator
        val outputPath = output.canonicalPath
        if (!outputPath.startsWith(basePath)) {
            throw IllegalStateException("Blocked unsafe archive entry: $relativePath")
        }
        return output
    }
}

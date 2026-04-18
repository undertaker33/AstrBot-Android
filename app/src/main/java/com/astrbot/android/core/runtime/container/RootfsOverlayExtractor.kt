package com.astrbot.android.core.runtime.container

import com.astrbot.android.core.common.logging.RuntimeLogRepository

import android.system.Os
import java.io.File
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.FileInputStream
import java.nio.file.Files

@Suppress("DEPRECATION")
object RootfsOverlayExtractor {
    private const val markerName = ".astrbot_overlay_v1"

    fun ensureApplied(overlayFile: File, rootfsDir: File) {
        if (!overlayFile.exists()) {
            return
        }
        if (isOverlayApplied(rootfsDir)) {
            return
        }

        extractOverlay(overlayFile, rootfsDir)

        File(rootfsDir, markerName).writeText("ok")
        RuntimeLogRepository.append("Bundled rootfs overlay applied")
    }

    private fun extractOverlay(overlayFile: File, rootfsDir: File) {
        XZInputStream(FileInputStream(overlayFile)).use { xzInput ->
            TarArchiveInputStream(xzInput).use { tarInput ->
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
        }
    }

    private fun isOverlayApplied(rootfsDir: File): Boolean {
        val marker = File(rootfsDir, markerName)
        return marker.exists() &&
            File(rootfsDir, "usr/bin/curl").exists() &&
            File(rootfsDir, "usr/bin/unzip").exists() &&
            File(rootfsDir, "usr/bin/Xvfb").exists() &&
            File(rootfsDir, "usr/bin/screen").exists() &&
            File(rootfsDir, "usr/bin/xauth").exists() &&
            File(rootfsDir, "usr/bin/g++").exists() &&
            File(rootfsDir, "opt/QQ/qq").exists()
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
            throw IllegalStateException("Blocked unsafe overlay entry: $relativePath")
        }
        return output
    }
}

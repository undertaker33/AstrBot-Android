package com.astrbot.android.core.runtime.container

import com.astrbot.android.core.common.logging.RuntimeLogRepository

import android.system.Os
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

@Suppress("DEPRECATION")
object RootfsExtractor {
    private const val extractionMarkerName = ".astrbot_rootfs_v4_embedded"

    fun ensureExtracted(archiveFile: File, rootfsDir: File) {
        if (isValidRootfs(rootfsDir)) {
            return
        }

        if (!archiveFile.exists()) {
            throw IllegalStateException("Missing rootfs archive: ${archiveFile.absolutePath}")
        }

        if (rootfsDir.exists()) {
            RuntimeLogRepository.append("Rebuilding invalid Ubuntu rootfs")
            rootfsDir.deleteRecursively()
        }

        val tempDir = File(rootfsDir.parentFile, ".tmp_extract")
        tempDir.deleteRecursively()
        tempDir.mkdirs()

        RuntimeLogRepository.append("Extracting Ubuntu rootfs with embedded extractor")
        extractWithEmbeddedTar(archiveFile, tempDir)

        val sourceRoot = selectSourceRoot(tempDir)
        moveIntoRootfs(sourceRoot, rootfsDir)
        File(rootfsDir, extractionMarkerName).writeText("ok")
        if (tempDir.exists()) {
            tempDir.deleteRecursively()
        }
    }

    private fun extractWithEmbeddedTar(archiveFile: File, tempDir: File) {
        FileInputStream(archiveFile).use { fileInput ->
            XZInputStream(fileInput).use { xzInput ->
                TarArchiveInputStream(xzInput).use { tarInput ->
                    var entry = tarInput.nextTarEntry
                    while (entry != null) {
                        val outputFile = resolveEntryFile(tempDir, entry)
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
                                val targetFile = resolveHardLinkTarget(tempDir, outputFile, entry.linkName)
                                outputFile.delete()
                                runCatching {
                                    Files.createLink(outputFile.toPath(), targetFile.toPath())
                                }.onFailure {
                                    targetFile.copyTo(outputFile, overwrite = true)
                                }
                            }

                            else -> {
                                outputFile.parentFile?.mkdirs()
                                FileOutputStream(outputFile).use { output -> tarInput.copyTo(output) }
                                outputFile.setExecutable((entry.mode and 0b001_001_001) != 0, false)
                            }
                        }
                        entry = tarInput.nextTarEntry
                    }
                }
            }
        }
    }

    private fun isValidRootfs(rootfsDir: File): Boolean {
        if (!rootfsDir.exists()) {
            return false
        }
        val envFile = File(rootfsDir, "usr/bin/env")
        val bashFile = File(rootfsDir, "usr/bin/bash")
        val binPath = File(rootfsDir, "bin")
        val libPath = File(rootfsDir, "lib")
        val loaderPath = File(rootfsDir, "usr/lib/ld-linux-aarch64.so.1")
        val marker = File(rootfsDir, extractionMarkerName)
        val binIsSymlink = runCatching { Files.isSymbolicLink(binPath.toPath()) }.getOrDefault(false)
        val libIsSymlink = runCatching { Files.isSymbolicLink(libPath.toPath()) }.getOrDefault(false)
        return envFile.exists() && bashFile.exists() && loaderPath.exists() && marker.exists() && binIsSymlink && libIsSymlink
    }

    private fun selectSourceRoot(tempDir: File): File {
        val children = tempDir.listFiles().orEmpty()
        val directories = children.filter { it.isDirectory }
        return if (children.size == 1 && directories.size == 1) {
            directories.first()
        } else {
            tempDir
        }
    }

    private fun moveIntoRootfs(sourceRoot: File, rootfsDir: File) {
        rootfsDir.parentFile?.mkdirs()
        rootfsDir.deleteRecursively()

        if (sourceRoot != rootfsDir) {
            runCatching {
                Files.move(sourceRoot.toPath(), rootfsDir.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }.getOrElse {
                rootfsDir.mkdirs()
                sourceRoot.listFiles().orEmpty().forEach { child ->
                    Files.move(
                        child.toPath(),
                        File(rootfsDir, child.name).toPath(),
                        StandardCopyOption.REPLACE_EXISTING,
                    )
                }
            }
        }
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

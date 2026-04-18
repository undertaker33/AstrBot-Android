package com.astrbot.android.model.plugin

import com.astrbot.android.feature.plugin.data.PluginStoragePaths
import java.io.File

data class ExternalPluginWorkspacePaths(
    val privateRootDir: File,
    val importsDir: File,
    val runtimeDir: File,
    val exportsDir: File,
    val cacheDir: File,
)

object ExternalPluginWorkspacePolicy {
    fun ensureWorkspace(
        storagePaths: PluginStoragePaths,
        pluginId: String,
    ): ExternalPluginWorkspacePaths {
        val privateRootDir = storagePaths.privateDir(pluginId)
        val importsDir = File(privateRootDir, "imports")
        val runtimeDir = File(privateRootDir, "runtime")
        val exportsDir = File(privateRootDir, "exports")
        val cacheDir = File(privateRootDir, "cache")
        listOf(privateRootDir, importsDir, runtimeDir, exportsDir, cacheDir).forEach(File::mkdirs)
        return ExternalPluginWorkspacePaths(
            privateRootDir = privateRootDir,
            importsDir = importsDir,
            runtimeDir = runtimeDir,
            exportsDir = exportsDir,
            cacheDir = cacheDir,
        )
    }

    fun snapshot(
        storagePaths: PluginStoragePaths,
        pluginId: String,
    ): PluginHostWorkspaceSnapshot {
        val paths = ensureWorkspace(
            storagePaths = storagePaths,
            pluginId = pluginId,
        )
        val rootPath = paths.privateRootDir.absolutePath
        val files = if (paths.privateRootDir.exists()) {
            paths.privateRootDir.walkTopDown()
                .filter(File::isFile)
                .map { file ->
                    PluginWorkspaceFileEntry(
                        relativePath = file.relativeTo(paths.privateRootDir).invariantPath(),
                        sizeBytes = file.length(),
                        lastModifiedAtEpochMillis = file.lastModified(),
                    )
                }
                .sortedBy(PluginWorkspaceFileEntry::relativePath)
                .toList()
        } else {
            emptyList()
        }
        return PluginHostWorkspaceSnapshot(
            privateRootPath = rootPath,
            importsPath = paths.importsDir.absolutePath,
            runtimePath = paths.runtimeDir.absolutePath,
            exportsPath = paths.exportsDir.absolutePath,
            cachePath = paths.cacheDir.absolutePath,
            files = files,
        )
    }

    fun resolveWorkspaceFile(
        privateRootPath: String,
        relativePath: String,
    ): File {
        require(privateRootPath.isNotBlank()) { "Workspace root must not be blank." }
        require(relativePath.isNotBlank()) { "Workspace relative path must not be blank." }
        val rootDir = File(privateRootPath).canonicalFile
        val candidate = File(rootDir, relativePath).canonicalFile
        require(candidate.path.startsWith(rootDir.path + File.separator) || candidate == rootDir) {
            "Workspace path escapes plugin private root: $relativePath"
        }
        return candidate
    }

    fun buildImportTarget(
        importsDirPath: String,
        displayName: String,
    ): File {
        require(importsDirPath.isNotBlank()) { "Workspace imports path must not be blank." }
        val sanitizedName = displayName
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .trim()
            .ifBlank { "imported-file" }
        return resolveWorkspaceFile(
            privateRootPath = File(importsDirPath).parentFile?.absolutePath ?: importsDirPath,
            relativePath = "imports/$sanitizedName",
        )
    }
}

private fun File.invariantPath(): String {
    return path.replace(File.separatorChar, '/')
}

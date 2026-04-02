package com.astrbot.android.data.plugin

import java.io.File

data class PluginStoragePaths(
    val rootDir: File,
    val packagesDir: File,
    val extractedRootDir: File,
    val privateRootDir: File,
) {
    fun ensureBaseDirectories() {
        packagesDir.mkdirs()
        extractedRootDir.mkdirs()
        privateRootDir.mkdirs()
    }

    fun packageFile(fileName: String): File = File(packagesDir, fileName)

    fun extractedDir(pluginId: String): File = File(extractedRootDir, pluginId)

    fun privateDir(pluginId: String): File = File(privateRootDir, pluginId)

    companion object {
        fun fromFilesDir(filesDir: File): PluginStoragePaths {
            val rootDir = File(filesDir, "plugins")
            return PluginStoragePaths(
                rootDir = rootDir,
                packagesDir = File(rootDir, "packages"),
                extractedRootDir = File(rootDir, "extracted"),
                privateRootDir = File(rootDir, "private"),
            )
        }
    }
}

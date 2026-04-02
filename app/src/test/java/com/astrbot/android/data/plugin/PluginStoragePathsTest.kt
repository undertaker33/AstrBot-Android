package com.astrbot.android.data.plugin

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginStoragePathsTest {
    @Test
    fun plugin_storage_paths_maps_base_directories_under_files_dir() {
        val filesDir = Files.createTempDirectory("plugin-storage-paths").toFile()
        try {
            val paths = PluginStoragePaths.fromFilesDir(filesDir)

            assertEquals(filesDir.resolve("plugins"), paths.rootDir)
            assertEquals(filesDir.resolve("plugins/packages"), paths.packagesDir)
            assertEquals(filesDir.resolve("plugins/extracted"), paths.extractedRootDir)
            assertEquals(filesDir.resolve("plugins/private"), paths.privateRootDir)
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun plugin_storage_paths_resolves_plugin_specific_directories_and_creates_base_dirs() {
        val filesDir = Files.createTempDirectory("plugin-storage-layout").toFile()
        try {
            val paths = PluginStoragePaths.fromFilesDir(filesDir)

            paths.ensureBaseDirectories()

            assertTrue(paths.packagesDir.isDirectory)
            assertTrue(paths.extractedRootDir.isDirectory)
            assertTrue(paths.privateRootDir.isDirectory)
            assertEquals(
                filesDir.resolve("plugins/extracted/com.example.demo"),
                paths.extractedDir("com.example.demo"),
            )
            assertEquals(
                filesDir.resolve("plugins/private/com.example.demo"),
                paths.privateDir("com.example.demo"),
            )
            assertEquals(
                filesDir.resolve("plugins/packages/demo.zip"),
                paths.packageFile("demo.zip"),
            )
        } finally {
            filesDir.deleteRecursively()
        }
    }
}

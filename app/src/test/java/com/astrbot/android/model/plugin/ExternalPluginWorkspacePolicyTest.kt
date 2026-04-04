package com.astrbot.android.model.plugin

import com.astrbot.android.data.plugin.PluginStoragePaths
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginWorkspacePolicyTest {

    @Test
    fun workspace_policy_resolves_private_tree_and_lists_relative_entries() {
        val filesDir = Files.createTempDirectory("plugin-workspace-policy").toFile()
        try {
            val storagePaths = PluginStoragePaths.fromFilesDir(filesDir)
            val paths = ExternalPluginWorkspacePolicy.ensureWorkspace(
                storagePaths = storagePaths,
                pluginId = "com.astrbot.samples.meme_manager",
            )

            File(paths.privateRootDir, "imports/funny/cat.png").apply {
                parentFile?.mkdirs()
                writeText("png")
            }
            File(paths.privateRootDir, "runtime/index.json").apply {
                parentFile?.mkdirs()
                writeText("{}")
            }

            val snapshot = ExternalPluginWorkspacePolicy.snapshot(
                storagePaths = storagePaths,
                pluginId = "com.astrbot.samples.meme_manager",
            )

            assertEquals(
                File(filesDir, "plugins/private/com.astrbot.samples.meme_manager").absolutePath,
                snapshot.privateRootPath,
            )
            assertEquals(
                listOf("imports/funny/cat.png", "runtime/index.json"),
                snapshot.files.map(PluginWorkspaceFileEntry::relativePath),
            )
            assertTrue(File(snapshot.importsPath).exists())
            assertTrue(File(snapshot.runtimePath).exists())
            assertTrue(File(snapshot.exportsPath).exists())
            assertTrue(File(snapshot.cachePath).exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun workspace_policy_rejects_relative_path_escape() {
        val filesDir = Files.createTempDirectory("plugin-workspace-policy-escape").toFile()
        try {
            val storagePaths = PluginStoragePaths.fromFilesDir(filesDir)
            val snapshot = ExternalPluginWorkspacePolicy.snapshot(
                storagePaths = storagePaths,
                pluginId = "com.astrbot.samples.meme_manager",
            )
            ExternalPluginWorkspacePolicy.resolveWorkspaceFile(
                privateRootPath = snapshot.privateRootPath,
                relativePath = "../escape.txt",
            )
        } finally {
            filesDir.deleteRecursively()
        }
    }
}

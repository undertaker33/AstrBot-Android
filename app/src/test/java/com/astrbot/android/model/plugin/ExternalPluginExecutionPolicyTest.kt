package com.astrbot.android.model.plugin

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginExecutionPolicyTest {

    @Test
    fun trigger_contracts_split_online_host_behaviors_from_residual_legacy_triggers() {
        assertEquals(
            setOf(
                PluginTriggerSource.OnPluginEntryClick,
                PluginTriggerSource.OnCommand,
                PluginTriggerSource.BeforeSendMessage,
                PluginTriggerSource.AfterModelResponse,
            ),
            PluginTriggerContracts.onlineHostTriggers,
        )
        assertEquals(
            setOf(
                PluginTriggerSource.OnMessageReceived,
                PluginTriggerSource.OnSchedule,
                PluginTriggerSource.OnConversationEnter,
            ),
            PluginTriggerContracts.residualCompatOnlyTriggers,
        )
        assertTrue(PluginTriggerContracts.isOnlineHostTrigger(PluginTriggerSource.OnPluginEntryClick))
        assertTrue(PluginTriggerContracts.isOnlineHostTrigger(PluginTriggerSource.OnCommand))
        assertTrue(PluginTriggerContracts.isOnlineHostTrigger(PluginTriggerSource.BeforeSendMessage))
        assertTrue(PluginTriggerContracts.isOnlineHostTrigger(PluginTriggerSource.AfterModelResponse))
        assertFalse(PluginTriggerContracts.isOnlineHostTrigger(PluginTriggerSource.OnMessageReceived))
        assertFalse(PluginTriggerContracts.isOnlineHostTrigger(PluginTriggerSource.OnSchedule))
        assertFalse(PluginTriggerContracts.isOnlineHostTrigger(PluginTriggerSource.OnConversationEnter))
    }

    @Test
    fun media_source_resolver_supports_workspace_and_package_uris() {
        val extractedDir = Files.createTempDirectory("external-media-package").toFile()
        val workspaceDir = Files.createTempDirectory("external-media-workspace").toFile()
        try {
            val packageFile = File(extractedDir, "assets/banner.png").apply {
                parentFile?.mkdirs()
                writeText("banner")
            }
            val workspaceFile = File(workspaceDir, "imports/cat.png").apply {
                parentFile?.mkdirs()
                writeText("cat")
            }

            val packageResolved = ExternalPluginMediaSourceResolver.resolve(
                item = PluginMediaItem(
                    source = "plugin://package/assets/banner.png",
                    mimeType = "image/png",
                    altText = "banner",
                ),
                extractedDir = extractedDir.absolutePath,
                privateRootPath = workspaceDir.absolutePath,
            )
            val workspaceResolved = ExternalPluginMediaSourceResolver.resolve(
                item = PluginMediaItem(
                    source = "plugin://workspace/imports/cat.png",
                    mimeType = "image/png",
                    altText = "cat",
                ),
                extractedDir = extractedDir.absolutePath,
                privateRootPath = workspaceDir.absolutePath,
            )

            assertEquals(packageFile.absolutePath, packageResolved.resolvedSource)
            assertEquals(workspaceFile.absolutePath, workspaceResolved.resolvedSource)
        } finally {
            extractedDir.deleteRecursively()
            workspaceDir.deleteRecursively()
        }
    }

    @Test
    fun media_source_resolver_rejects_workspace_escape() {
        val extractedDir = Files.createTempDirectory("external-media-package-escape").toFile()
        val workspaceDir = Files.createTempDirectory("external-media-workspace-escape").toFile()
        try {
            try {
                ExternalPluginMediaSourceResolver.resolve(
                    item = PluginMediaItem(
                        source = "plugin://workspace/imports/../secret.txt",
                        mimeType = "text/plain",
                    ),
                    extractedDir = extractedDir.absolutePath,
                    privateRootPath = workspaceDir.absolutePath,
                )
            } catch (_: IllegalArgumentException) {
                return
            }
            throw AssertionError("Expected workspace media path escape to be rejected")
        } finally {
            extractedDir.deleteRecursively()
            workspaceDir.deleteRecursively()
        }
    }
}

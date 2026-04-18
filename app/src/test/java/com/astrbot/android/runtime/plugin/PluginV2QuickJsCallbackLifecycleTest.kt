package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2QuickJsCallbackLifecycleTest {

    @Test
    fun load_keeps_quickjs_command_callbacks_invocable_after_bootstrap_finishes() = runTest {
        PluginV2QuickJsTestGate.assumeAvailable()

        val pluginRoot = File("C:/Users/93445/Desktop/Astrbot/Plugin/Astrbot_Android_plugin_memes")
        require(pluginRoot.isDirectory) {
            "Missing external meme plugin fixture: ${pluginRoot.absolutePath}"
        }

        val workingRoot = Files.createTempDirectory("plugin-v2-loader-callback-lifecycle").toFile()
        try {
            pluginRoot.copyRecursively(workingRoot, overwrite = true)
            val record = samplePluginV2InstallRecord(
                pluginId = "io.github.astrbot.android.meme_manager",
            ).copyForFixture(workingRoot.absolutePath)

            val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
            val store = PluginV2ActiveRuntimeStore(logBus = logBus, clock = { 1L })
            val loader = PluginV2RuntimeLoader(
                sessionFactory = PluginV2RuntimeSessionFactory(
                    scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
                ),
                store = store,
                compiler = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L }),
                logBus = logBus,
                lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
                clock = { 1L },
            )

            val loadResult = loader.load(record)
            assertTrue(loadResult.status == PluginV2RuntimeLoadStatus.Loaded)

            val result = PluginV2DispatchEngine(
                store = store,
                logBus = logBus,
                clock = { 1L },
            ).dispatchMessage(
                event = PluginMessageEvent(
                    eventId = "evt-loader-callback-lifecycle",
                    platformAdapterType = "app_chat",
                    messageType = MessageType.FriendMessage,
                    conversationId = "session-1",
                    senderId = "app-user",
                    timestampEpochMillis = 1_710_000_000_000L,
                    rawText = "/\u8868\u60c5\u7ba1\u7406",
                    initialWorkingText = "/\u8868\u60c5\u7ba1\u7406",
                    rawMentions = emptyList(),
                    normalizedMentions = emptyList(),
                    extras = mapOf(
                        "source" to "app_chat",
                        "trigger" to "on_command",
                        "sessionId" to "session-1",
                    ),
                ),
            )

            assertNotNull(result.commandResponse)
            assertTrue(checkNotNull(result.commandResponse).text.isNotBlank())
        } finally {
            workingRoot.deleteRecursively()
        }
    }

    private fun PluginInstallRecord.copyForFixture(
        extractedDir: String,
    ): PluginInstallRecord {
        val contractSnapshot = requireNotNull(packageContractSnapshot)
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifestSnapshot,
            source = source,
            packageContractSnapshot = contractSnapshot.copy(
                runtime = contractSnapshot.runtime.copy(
                    bootstrap = "runtime/bootstrap.js",
                ),
            ),
            permissionSnapshot = permissionSnapshot,
            compatibilityState = compatibilityState,
            uninstallPolicy = uninstallPolicy,
            enabled = enabled,
            failureState = failureState,
            catalogSourceId = catalogSourceId,
            installedPackageUrl = installedPackageUrl,
            lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
            installedAt = installedAt,
            lastUpdatedAt = lastUpdatedAt,
            localPackagePath = localPackagePath,
            extractedDir = extractedDir,
        )
    }
}

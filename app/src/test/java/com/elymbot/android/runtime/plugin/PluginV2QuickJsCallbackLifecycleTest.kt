package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2QuickJsCallbackLifecycleTest {

    @Test
    fun quickjs_command_callback_waits_until_returned_promise_fulfills() = runTest {
        PluginV2QuickJsTestGate.assumeAvailable()

        withQuickJsCallbackFixture(
            pluginId = "com.elymbot.samples.quickjs.async.fulfilled",
            bootstrapSource = """
                export default async function bootstrap(hostApi) {
                  hostApi.registerCommandHandler({
                    registrationKey: "quickjs.async.fulfilled.command",
                    command: "async-promise",
                    handler: async (event) => {
                      await Promise.resolve("settled");
                      event.replyText("settled-after-await");
                    }
                  });
                }
            """.trimIndent(),
        ) { fixture ->
            val result = dispatchCommand(
                fixture = fixture,
                rawText = "/async-promise",
            )

            assertNotNull(result.commandResponse)
            assertEquals("settled-after-await", checkNotNull(result.commandResponse).text)
        }
    }

    @Test
    fun quickjs_command_callback_rejection_is_reported_after_promise_settles() = runTest {
        PluginV2QuickJsTestGate.assumeAvailable()

        withQuickJsCallbackFixture(
            pluginId = "com.elymbot.samples.quickjs.async.rejected",
            bootstrapSource = """
                export default async function bootstrap(hostApi) {
                  hostApi.registerCommandHandler({
                    registrationKey: "quickjs.async.rejected.command",
                    command: "async-reject",
                    handler: async () => {
                      await Promise.resolve();
                      throw new Error("rejected-after-await");
                    }
                  });
                }
            """.trimIndent(),
        ) { fixture ->
            val result = dispatchCommand(
                fixture = fixture,
                rawText = "/async-reject",
            )

            assertNull(result.commandResponse)
            assertTrue(
                fixture.logBus.snapshot(limit = 10, pluginId = fixture.session.pluginId)
                    .any { record ->
                        record.code == "plugin_error_hook_emitted" &&
                            record.metadata["errorMessage"].orEmpty().contains("rejected-after-await")
                    },
            )
        }
    }

    @Test
    fun load_keeps_quickjs_command_callbacks_invocable_after_bootstrap_finishes() = runTest {
        PluginV2QuickJsTestGate.assumeAvailable()

        val pluginRoot = File("C:/Users/93445/Desktop/ElymBot/Plugin/ElymBot_Android_plugin_memes")
        require(pluginRoot.isDirectory) {
            "Missing external meme plugin fixture: ${pluginRoot.absolutePath}"
        }

        val workingRoot = Files.createTempDirectory("plugin-v2-loader-callback-lifecycle").toFile()
        try {
            pluginRoot.copyRecursively(workingRoot, overwrite = true)
            val record = samplePluginV2InstallRecord(
                pluginId = "io.github.elymbot.android.meme_manager",
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

    private inline fun withQuickJsCallbackFixture(
        pluginId: String,
        bootstrapSource: String,
        block: (RuntimeFixture) -> Unit,
    ) {
        val workingRoot = Files.createTempDirectory("plugin-v2-quickjs-callback").toFile()
        try {
            val runtimeDir = File(workingRoot, "runtime").apply { mkdirs() }
            File(runtimeDir, "bootstrap.js").writeText(bootstrapSource, Charsets.UTF_8)
            val installRecord = samplePluginV2InstallRecord(pluginId = pluginId)
                .copyForFixture(workingRoot.absolutePath)
            val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
            val handle = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ).createSession(installRecord)
            try {
                handle.executeBootstrap()
                val compileResult = PluginV2RegistryCompiler(
                    logBus = logBus,
                    clock = { 1L },
                ).compile(requireNotNull(handle.session.rawRegistry))
                val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
                handle.session.attachCompiledRegistry(compiledRegistry)
                handle.session.transitionTo(PluginV2RuntimeSessionState.Active)
                val entry = buildActiveEntry(
                    handle = handle,
                    compiledRegistry = compiledRegistry,
                    diagnostics = compileResult.diagnostics,
                )
                block(
                    RuntimeFixture(
                        session = handle.session,
                        compiledRegistry = compiledRegistry,
                        entry = entry,
                        snapshot = PluginV2ActiveRuntimeSnapshot(
                            activeRuntimeEntriesByPluginId = mapOf(handle.session.pluginId to entry),
                            activeSessionsByPluginId = mapOf(handle.session.pluginId to handle.session),
                            compiledRegistriesByPluginId = mapOf(handle.session.pluginId to compiledRegistry),
                        ),
                        logBus = logBus,
                    ),
                )
            } finally {
                handle.dispose()
            }
        } finally {
            workingRoot.deleteRecursively()
        }
    }

    private suspend fun dispatchCommand(
        fixture: RuntimeFixture,
        rawText: String,
    ): PluginV2MessageDispatchResult {
        return PluginV2DispatchEngine(
            logBus = fixture.logBus,
            clock = { 1L },
        ).dispatchMessage(
            event = PluginMessageEvent(
                eventId = "evt-${rawText.hashCode()}",
                platformAdapterType = "app_chat",
                messageType = MessageType.FriendMessage,
                conversationId = "session-1",
                senderId = "app-user",
                timestampEpochMillis = 1_710_000_000_000L,
                rawText = rawText,
                initialWorkingText = rawText,
                rawMentions = emptyList(),
                normalizedMentions = emptyList(),
                extras = mapOf(
                    "source" to "app_chat",
                    "trigger" to "on_command",
                    "sessionId" to "session-1",
                ),
            ),
            snapshot = fixture.snapshot,
        )
    }

    private fun buildActiveEntry(
        handle: PluginV2RuntimeHandle,
        compiledRegistry: PluginV2CompiledRegistrySnapshot,
        diagnostics: List<PluginV2CompilerDiagnostic>,
    ): PluginV2ActiveRuntimeEntry {
        return PluginV2ActiveRuntimeEntry(
            session = handle.session,
            compiledRegistry = compiledRegistry,
            lastBootstrapSummary = PluginV2BootstrapSummary(
                pluginId = handle.session.pluginId,
                sessionInstanceId = handle.session.sessionInstanceId,
                compiledAtEpochMillis = 1L,
                handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                warningCount = diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                errorCount = diagnostics.count { it.severity == DiagnosticSeverity.Error },
            ),
            diagnostics = diagnostics,
            callbackTokens = handle.session.snapshotCallbackTokens(),
        )
    }

    private data class RuntimeFixture(
        val session: PluginV2RuntimeSession,
        val compiledRegistry: PluginV2CompiledRegistrySnapshot,
        val entry: PluginV2ActiveRuntimeEntry,
        val snapshot: PluginV2ActiveRuntimeSnapshot,
        val logBus: InMemoryPluginRuntimeLogBus,
    )

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

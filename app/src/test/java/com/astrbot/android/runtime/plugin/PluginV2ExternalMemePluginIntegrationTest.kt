package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2ExternalMemePluginIntegrationTest {

    @Test
    fun external_meme_plugin_replies_to_root_command_in_app_chat_ingress() = runBlocking {
        PluginV2QuickJsTestGate.assumeAvailable()

        val pluginRoot = File("C:/Users/93445/Desktop/Astrbot/Plugin/Astrbot_Android_plugin_memes")
        require(pluginRoot.isDirectory) {
            "Missing external meme plugin fixture: ${pluginRoot.absolutePath}"
        }

        withPluginFixture(pluginRoot) { fixture ->
            val engine = PluginV2DispatchEngine(
                logBus = InMemoryPluginRuntimeLogBus(clock = { 1L }),
                clock = { 1L },
            )

            val result = engine.dispatchMessage(
                event = PluginMessageEvent(
                    eventId = "evt-app-chat-root-command",
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
                snapshot = PluginV2ActiveRuntimeSnapshot(
                    activeRuntimeEntriesByPluginId = mapOf(fixture.entry.pluginId to fixture.entry),
                    activeSessionsByPluginId = mapOf(fixture.session.pluginId to fixture.session),
                    compiledRegistriesByPluginId = mapOf(fixture.session.pluginId to fixture.compiledRegistry),
                ),
            )

            val response = result.commandResponse
            assertNotNull(response)
            assertTrue(checkNotNull(response).text.isNotBlank())
            assertTrue(response.text.contains("\u8868\u60c5\u7ba1\u7406"))
            assertEquals("io.github.astrbot.android.meme_manager", response.pluginId)
        }
    }

    @Test
    fun external_meme_plugin_mutates_llm_request_and_result_for_matching_onebot_message() = runBlocking {
        PluginV2QuickJsTestGate.assumeAvailable()

        val pluginRoot = File("C:/Users/93445/Desktop/Astrbot/Plugin/Astrbot_Android_plugin_memes")
        require(pluginRoot.isDirectory) {
            "Missing external meme plugin fixture: ${pluginRoot.absolutePath}"
        }

        withPluginFixture(pluginRoot) { fixture ->
            val engine = PluginV2DispatchEngine(
                logBus = fixture.logBus,
                clock = { 1L },
            )
            val event = PluginMessageEvent(
                eventId = "evt-onebot-meme-llm",
                platformAdapterType = "onebot",
                messageType = MessageType.FriendMessage,
                conversationId = "friend:934457024",
                senderId = "934457024",
                timestampEpochMillis = 1_710_000_000_000L,
                rawText = "今天真开心 [happy]",
                initialWorkingText = "今天真开心 [happy]",
                rawMentions = emptyList(),
                normalizedMentions = emptyList(),
                extras = mapOf(
                    "source" to "onebot",
                    "trigger" to "before_send_message",
                ),
            )

            val ingressResult = engine.dispatchMessage(
                event = event,
                snapshot = fixture.snapshot,
            )
            assertFalse(ingressResult.propagationStopped)
            assertFalse(ingressResult.terminatedByCustomFilterFailure)

            val coordinator = PluginV2LlmPipelineCoordinator(
                dispatchEngine = engine,
                logBus = fixture.logBus,
                clock = { 2L },
                requestIdFactory = { "req-meme-1" },
            )

            val pipelineResult = coordinator.runPreSendStages(
                input = PluginV2LlmPipelineInput(
                    event = event,
                    messageIds = listOf("msg-1"),
                    streamingMode = PluginV2StreamingMode.NON_STREAM,
                    availableProviderIds = listOf("provider-1"),
                    availableModelIdsByProvider = mapOf("provider-1" to listOf("model-1")),
                    selectedProviderId = "provider-1",
                    selectedModelId = "model-1",
                    systemPrompt = "base prompt",
                    messages = listOf(
                        PluginProviderMessageDto(
                            role = PluginProviderMessageRole.USER,
                            parts = listOf(
                                PluginProviderMessagePartDto.TextPart("今天真开心 [happy]"),
                            ),
                        ),
                    ),
                    invokeProvider = { request, _ ->
                        PluginV2ProviderInvocationResult.NonStreaming(
                            PluginLlmResponse(
                                requestId = request.requestId,
                                providerId = request.selectedProviderId.ifBlank { "provider-1" },
                                modelId = request.selectedModelId.ifBlank { "model-1" },
                                text = "收到，这就给你一个开心回复。",
                            ),
                        )
                    },
                ),
                snapshot = fixture.snapshot,
            )

            assertTrue(
                pipelineResult.finalRequest.systemPrompt.orEmpty().contains("[happy]"),
            )
            assertEquals(1, pipelineResult.sendableResult.attachments.size)
            assertTrue(
                pipelineResult.sendableResult.attachments.single().uri.replace('\\', '/')
                    .contains("/memes/happy/"),
            )
        }
    }

    private inline fun withPluginFixture(
        pluginRoot: File,
        block: (RuntimeFixture) -> Unit,
    ) {
        val workingRoot = Files.createTempDirectory("external-meme-plugin").toFile()
        try {
            pluginRoot.copyRecursively(workingRoot, overwrite = true)
            val installRecord = samplePluginV2InstallRecord(
                pluginId = "io.github.astrbot.android.meme_manager",
            ).copyForFixture(workingRoot.absolutePath)
            val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
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
                val snapshot = PluginV2ActiveRuntimeSnapshot(
                    activeRuntimeEntriesByPluginId = mapOf(handle.session.pluginId to buildActiveEntry(handle, compiledRegistry, compileResult.diagnostics)),
                    activeSessionsByPluginId = mapOf(handle.session.pluginId to handle.session),
                    compiledRegistriesByPluginId = mapOf(handle.session.pluginId to compiledRegistry),
                )
                block(
                    RuntimeFixture(
                        session = handle.session,
                        compiledRegistry = compiledRegistry,
                        entry = snapshot.activeRuntimeEntriesByPluginId.getValue(handle.session.pluginId),
                        snapshot = snapshot,
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

    private data class RuntimeFixture(
        val session: PluginV2RuntimeSession,
        val compiledRegistry: PluginV2CompiledRegistrySnapshot,
        val entry: PluginV2ActiveRuntimeEntry,
        val snapshot: PluginV2ActiveRuntimeSnapshot,
        val logBus: InMemoryPluginRuntimeLogBus,
    )

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
}

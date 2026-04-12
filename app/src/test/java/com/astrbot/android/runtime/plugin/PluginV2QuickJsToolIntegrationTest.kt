package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2QuickJsToolIntegrationTest {

    @Test
    fun tool_fixtures_register_pre_post_hooks_and_reinject_tool_result_before_final_assistant() = runBlocking {
        PluginV2QuickJsTestGate.assumeAvailable()

        val logBus = InMemoryPluginRuntimeLogBus(capacity = 64, clock = { 6_000L })
        PluginRuntimeLogBusProvider.setBusOverrideForTests(logBus)
        try {
            withBootstrappedToolFixtures(logBus = logBus) { fixtures ->
                val requestRawRegistry = requireNotNull(fixtures.requestTool.session.rawRegistry)
                val respondRawRegistry = requireNotNull(fixtures.respondTool.session.rawRegistry)
                assertEquals(listOf("quick_reply"), requestRawRegistry.tools.map { it.descriptor.name })
                assertEquals(
                    listOf("on_using_llm_tool"),
                    requestRawRegistry.llmHooks.map { it.descriptor.hook },
                )
                assertEquals(
                    listOf("on_llm_tool_respond"),
                    respondRawRegistry.llmHooks.map { it.descriptor.hook },
                )

                val providerCalls = AtomicInteger(0)
                val seenRequests = CopyOnWriteArrayList<PluginProviderRequest>()
                val coordinator = PluginV2LlmPipelineCoordinator(
                    dispatchEngine = PluginV2DispatchEngine(logBus = logBus, clock = { 6_000L }),
                    toolExecutor = PluginV2ToolExecutor { args ->
                        assertEquals("com.astrbot.samples.request-tool-plugin:quick_reply", args.toolId)
                        PluginToolResult(
                            toolCallId = args.toolCallId,
                            requestId = args.requestId,
                            toolId = args.toolId,
                            status = PluginToolResultStatus.SUCCESS,
                            text = "quickjs fixture tool result: ${args.payload["topic"]}",
                        )
                    },
                    logBus = logBus,
                    clock = { 6_000L },
                    requestIdFactory = { "req-quickjs-tool-fixture" },
                )

                val result = coordinator.runPreSendStages(
                    input = quickJsToolPipelineInput { request, _ ->
                        seenRequests += request
                        when (providerCalls.incrementAndGet()) {
                            1 -> PluginV2ProviderInvocationResult.NonStreaming(
                                PluginLlmResponse(
                                    requestId = request.requestId,
                                    providerId = request.selectedProviderId,
                                    modelId = request.selectedModelId,
                                    toolCalls = listOf(
                                        PluginLlmToolCall(
                                            toolName = "quick_reply",
                                            arguments = linkedMapOf("topic" to "fixtures"),
                                        ),
                                    ),
                                ),
                            )

                            else -> {
                                val toolMessage = request.messages.last()
                                assertEquals(PluginProviderMessageRole.TOOL, toolMessage.role)
                                assertEquals("quick_reply", toolMessage.name)
                                val toolText = toolMessage.parts
                                    .filterIsInstance<PluginProviderMessagePartDto.TextPart>()
                                    .single()
                                    .text
                                assertEquals("quickjs fixture tool result: fixtures", toolText)
                                PluginV2ProviderInvocationResult.NonStreaming(
                                    PluginLlmResponse(
                                        requestId = request.requestId,
                                        providerId = request.selectedProviderId,
                                        modelId = request.selectedModelId,
                                        text = "assistant final from reinjected QuickJS tool result",
                                        toolCalls = emptyList(),
                                    ),
                                )
                            }
                        }
                    },
                    snapshot = fixtures.snapshot,
                )

                assertEquals(2, providerCalls.get())
                assertEquals("assistant final from reinjected QuickJS tool result", result.sendableResult.text)
                assertTrue(seenRequests.last().messages.any { it.role == PluginProviderMessageRole.TOOL })
                assertTrue(
                    result.hookInvocationTrace.any {
                        it.startsWith("${PluginV2InternalStage.UsingLlmTool.name}:")
                    },
                )
                assertTrue(
                    result.hookInvocationTrace.any {
                        it.startsWith("${PluginV2InternalStage.LlmToolRespond.name}:")
                    },
                )
            }
        } finally {
            PluginRuntimeLogBusProvider.setBusOverrideForTests(null)
        }
    }

    private inline fun withBootstrappedToolFixtures(
        logBus: PluginRuntimeLogBus,
        block: (ToolFixtures) -> Unit,
    ) {
        val requestFixture = bootstrappedFixture(
            fixtureName = "request-tool-plugin",
            pluginId = "com.astrbot.samples.request-tool-plugin",
            logBus = logBus,
        )
        val respondFixture = bootstrappedFixture(
            fixtureName = "respond-tool-plugin",
            pluginId = "com.astrbot.samples.respond-tool-plugin",
            logBus = logBus,
        )
        try {
            val sessionsByPluginId = listOf(requestFixture, respondFixture)
                .associateBy { fixture -> fixture.session.pluginId }
                .mapValues { (_, fixture) -> fixture.session }
            val toolState = compileCentralizedToolState(sessionsByPluginId)
            val snapshot = PluginV2ActiveRuntimeSnapshot(
                activeRuntimeEntriesByPluginId = listOf(requestFixture, respondFixture)
                    .associateBy { fixture -> fixture.session.pluginId }
                    .mapValues { (_, fixture) -> fixture.entry },
                activeSessionsByPluginId = sessionsByPluginId,
                compiledRegistriesByPluginId = listOf(requestFixture, respondFixture)
                    .associateBy { fixture -> fixture.session.pluginId }
                    .mapValues { (_, fixture) -> fixture.compiledRegistry },
                toolRegistrySnapshot = toolState.activeRegistry,
                toolRegistryDiagnostics = toolState.diagnostics,
                toolAvailabilityByName = toolState.availabilityByName,
            )
            block(
                ToolFixtures(
                    requestTool = requestFixture,
                    respondTool = respondFixture,
                    snapshot = snapshot,
                ),
            )
        } finally {
            requestFixture.close()
            respondFixture.close()
        }
    }

    private fun bootstrappedFixture(
        fixtureName: String,
        pluginId: String,
        logBus: PluginRuntimeLogBus,
    ): BootstrappedToolFixture {
        val sourceRoot = fixtureRoot(fixtureName)
        val workingRoot = Files.createTempDirectory("plugin-v2-tool-$fixtureName").toFile()
        sourceRoot.copyRecursively(workingRoot, overwrite = true)
        val handle = PluginV2RuntimeSessionFactory(
            scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            sessionInstanceIdFactory = { installRecord -> "session-${installRecord.pluginId}" },
        ).createSession(
            samplePluginV2InstallRecord(pluginId = pluginId)
                .copyForToolFixture(workingRoot.absolutePath),
        )
        return try {
            handle.installBootstrapGlobal(
                "__astrbotBootstrapHostApi",
                PluginV2BootstrapHostApi(
                    session = handle.session,
                    logBus = logBus,
                    clock = { 6_000L },
                ),
            )
            handle.executeBootstrap()
            val compileResult = PluginV2RegistryCompiler(logBus = logBus, clock = { 6_000L })
                .compile(requireNotNull(handle.session.rawRegistry))
            val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
            handle.session.attachCompiledRegistry(compiledRegistry)
            handle.session.transitionTo(PluginV2RuntimeSessionState.Active)
            BootstrappedToolFixture(
                handle = handle,
                workingRoot = workingRoot,
                compiledRegistry = compiledRegistry,
                entry = quickJsToolActiveEntry(
                    session = handle.session,
                    compiledRegistry = compiledRegistry,
                    diagnostics = compileResult.diagnostics,
                ),
            )
        } catch (error: Throwable) {
            handle.dispose()
            workingRoot.deleteRecursively()
            throw error
        }
    }

    private fun quickJsToolPipelineInput(
        invokeProvider: suspend (PluginProviderRequest, PluginV2StreamingMode) -> PluginV2ProviderInvocationResult,
    ): PluginV2LlmPipelineInput {
        val event = PluginMessageEvent(
            eventId = "evt-quickjs-tool-fixture",
            platformAdapterType = "onebot",
            messageType = MessageType.GroupMessage,
            conversationId = "conv-quickjs-tool",
            senderId = "user-quickjs-tool",
            timestampEpochMillis = 1_710_000_600_000L,
            rawText = "call quickjs tool",
            initialWorkingText = "call quickjs tool",
            rawMentions = emptyList(),
            normalizedMentions = emptyList(),
            extras = emptyMap(),
        )
        return PluginV2LlmPipelineInput(
            event = event,
            messageIds = listOf("msg-quickjs-tool-fixture"),
            streamingMode = PluginV2StreamingMode.NON_STREAM,
            availableProviderIds = listOf("provider-a"),
            availableModelIdsByProvider = mapOf("provider-a" to listOf("model-a")),
            selectedProviderId = "provider-a",
            selectedModelId = "model-a",
            messages = listOf(
                PluginProviderMessageDto(
                    role = PluginProviderMessageRole.USER,
                    parts = listOf(PluginProviderMessagePartDto.TextPart(event.rawText)),
                ),
            ),
            invokeProvider = invokeProvider,
        )
    }

    private fun fixtureRoot(name: String): File {
        val resource = requireNotNull(javaClass.classLoader?.getResource("plugin-v2-tool/$name")) {
            "Missing QuickJS tool integration fixture: $name"
        }
        return File(resource.toURI())
    }

    private fun PluginInstallRecord.copyForToolFixture(
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

    private fun quickJsToolActiveEntry(
        session: PluginV2RuntimeSession,
        compiledRegistry: PluginV2CompiledRegistrySnapshot,
        diagnostics: List<PluginV2CompilerDiagnostic>,
    ): PluginV2ActiveRuntimeEntry {
        return PluginV2ActiveRuntimeEntry(
            session = session,
            compiledRegistry = compiledRegistry,
            lastBootstrapSummary = PluginV2BootstrapSummary(
                pluginId = session.pluginId,
                sessionInstanceId = session.sessionInstanceId,
                compiledAtEpochMillis = 6_000L,
                handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                warningCount = diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                errorCount = diagnostics.count { it.severity == DiagnosticSeverity.Error },
            ),
            diagnostics = diagnostics,
            callbackTokens = session.snapshotCallbackTokens(),
        )
    }

    private data class ToolFixtures(
        val requestTool: BootstrappedToolFixture,
        val respondTool: BootstrappedToolFixture,
        val snapshot: PluginV2ActiveRuntimeSnapshot,
    )

    private data class BootstrappedToolFixture(
        val handle: PluginV2RuntimeHandle,
        val workingRoot: File,
        val compiledRegistry: PluginV2CompiledRegistrySnapshot,
        val entry: PluginV2ActiveRuntimeEntry,
    ) {
        val session: PluginV2RuntimeSession
            get() = handle.session

        fun close() {
            handle.dispose()
            workingRoot.deleteRecursively()
        }
    }
}

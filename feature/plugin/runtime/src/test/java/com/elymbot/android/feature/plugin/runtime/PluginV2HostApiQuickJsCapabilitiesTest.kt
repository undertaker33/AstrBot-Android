package com.elymbot.android.feature.plugin.runtime

import com.elymbot.android.model.chat.MessageType
import com.elymbot.android.model.plugin.PluginCompatibilityState
import com.elymbot.android.model.plugin.PluginInstallRecord
import com.elymbot.android.model.plugin.PluginManifest
import com.elymbot.android.model.plugin.PluginPackageContractSnapshot
import com.elymbot.android.model.plugin.PluginPermissionDeclaration
import com.elymbot.android.model.plugin.PluginRiskLevel
import com.elymbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.elymbot.android.model.plugin.PluginSource
import com.elymbot.android.model.plugin.PluginSourceType
import com.whl.quickjs.wrapper.QuickJSContext
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2HostApiQuickJsCapabilitiesTest {

    private val tempRoots = mutableListOf<File>()

    @After
    fun cleanup() {
        tempRoots.forEach { root -> root.deleteRecursively() }
        tempRoots.clear()
    }

    @Test
    fun quickjs_host_api_exposes_provider_message_send_and_conversation_history() = runTest {
        assumeTrue(runCatching { QuickJSContext.create().use { } }.isSuccess)
        val workingRoot = Files.createTempDirectory("plugin-v2-host-capabilities").toFile()
        tempRoots += workingRoot
        File(workingRoot, "runtime").mkdirs()
        File(workingRoot, "runtime/bootstrap.js").writeText(
            """
                export default async function bootstrap(hostApi) {
                  const providers = await hostApi.providers.list();
                  const models = await hostApi.providers.models({ providerId: "provider-main" });
                  hostApi.registerCommandHandler({
                    command: "cap",
                    handler: async (event) => {
                      const history = await hostApi.conversation.history({ limit: 5 });
                      const receipt = await hostApi.message.send({
                        text: providers[0].providerId + ":" + models[0].modelId + ":" + history.messages[0].messageId
                      });
                      event.replyText(receipt.receiptIds[0]);
                    }
                  });
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(logBus = logBus, clock = { 1L })
        val sendPort = RecordingMessageSendPort()
        val loader = PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ),
            compiler = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L }),
            clock = { 1L },
            logBus = logBus,
            store = store,
            lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
            providerReadApi = providerReadApi(logBus),
            messageSendApi = messageSendApi(logBus, sendPort),
            conversationHistoryApi = conversationHistoryApi(logBus),
        )

        val loadResult = loader.load(hostCapabilityPluginRecord(workingRoot))
        assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)

        val dispatchResult = PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 1L },
        ).dispatchMessage(
            event = PluginMessageEvent(
                eventId = "evt-capability",
                platformAdapterType = "app_chat",
                messageType = MessageType.FriendMessage,
                conversationId = "conversation-current",
                senderId = "user-1",
                timestampEpochMillis = 1L,
                rawText = "/cap",
                initialWorkingText = "/cap",
                rawMentions = emptyList(),
                normalizedMentions = emptyList(),
            ),
        )

        assertEquals("receipt-conversation-current", checkNotNull(dispatchResult.commandResponse).text)
        val sent = sendPort.requests.single()
        assertEquals("conversation-current", sent.conversationId)
        assertEquals("app_chat", sent.platformAdapterType)
        assertEquals("provider-main:model-main:message-latest", sent.text)
    }

    @Test
    fun quickjs_host_api_exposes_llm_generate_and_context_compress_with_distinct_audit_names() = runTest {
        assumeTrue(runCatching { QuickJSContext.create().use { } }.isSuccess)
        val workingRoot = Files.createTempDirectory("plugin-v2-host-llm-capabilities").toFile()
        tempRoots += workingRoot
        File(workingRoot, "runtime").mkdirs()
        File(workingRoot, "runtime/bootstrap.js").writeText(
            """
                export default async function bootstrap(hostApi) {
                  hostApi.registerCommandHandler({
                    command: "llmcap",
                    handler: async (event) => {
                      const direct = await hostApi.callLlm({
                        providerId: "provider-main",
                        modelId: "model-main",
                        messages: [{ role: "user", text: "direct" }],
                        maxTokens: 64
                      });
                      const generated = await hostApi.llm.generate({
                        providerId: "provider-main",
                        modelId: "model-main",
                        messages: [{ role: "user", text: "generate" }],
                        maxTokens: 64
                      });
                      const compressed = await hostApi.context.compress({
                        conversationId: "conversation-current",
                        providerId: "provider-main",
                        modelId: "model-main",
                        maxTokens: 128,
                        limit: 5
                      });
                      event.replyText(
                        direct.text + ":" + direct.usage.totalTokens + "|" +
                        generated.text + ":" + generated.usage.totalTokens + "|" +
                        compressed.summary + ":" + compressed.sourceMessageCount
                      );
                    }
                  });
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(logBus = logBus, clock = { 1L })
        val sendPort = RecordingMessageSendPort()
        val llmPort = RecordingHostLlmPort()
        val loader = PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ),
            compiler = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L }),
            clock = { 1L },
            logBus = logBus,
            store = store,
            lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
            providerReadApi = providerReadApi(logBus),
            messageSendApi = messageSendApi(logBus, sendPort),
            conversationHistoryApi = conversationHistoryApi(logBus),
            hostLlmApi = hostLlmApi(logBus, llmPort),
            contextCompressApi = contextCompressApi(logBus, llmPort),
        )

        val loadResult = loader.load(hostCapabilityPluginRecord(workingRoot))
        assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)

        val dispatchResult = PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 1L },
        ).dispatchMessage(
            event = PluginMessageEvent(
                eventId = "evt-llm-capability",
                platformAdapterType = "app_chat",
                messageType = MessageType.FriendMessage,
                conversationId = "conversation-current",
                senderId = "user-1",
                timestampEpochMillis = 1L,
                rawText = "/llmcap",
                initialWorkingText = "/llmcap",
                rawMentions = emptyList(),
                normalizedMentions = emptyList(),
            ),
        )

        assertEquals(
            "response-direct:6|response-generate:8|response-Please summarize the following conversation context for safe reuse by the host.:76:2",
            checkNotNull(dispatchResult.commandResponse).text,
        )
        assertEquals(3, llmPort.requests.size)
        assertTrue(llmPort.requests.all { request -> request.bypassPluginLlmHooks })
        val auditMetadata = logBus.snapshot(pluginId = "plugin.capability")
            .map { record -> record.metadata }
            .filter { metadata -> metadata["stage"] == "PluginV2HostApi" }
        val auditApis = auditMetadata.mapNotNull { metadata -> metadata["api"] }
        assertTrue(auditApis.contains(PluginV2HostLlmApi.HOST_API_CALL_LLM))
        assertTrue(auditApis.contains(PluginV2HostLlmApi.HOST_API_LLM_GENERATE))
        assertTrue(auditApis.contains(PluginV2ContextCompressApi.HOST_API_CONTEXT_COMPRESS))
        assertEquals(
            listOf(
                PluginV2HostApiPermissions.CALL_MODEL,
                PluginV2HostApiPermissions.CALL_MODEL,
            ),
            auditMetadata
                .filter { metadata ->
                    metadata["api"] == PluginV2HostLlmApi.HOST_API_CALL_LLM ||
                        metadata["api"] == PluginV2HostLlmApi.HOST_API_LLM_GENERATE
                }
                .map { metadata -> metadata["permissionId"] },
        )
    }

    @Test
    fun quickjs_agent_handler_is_invoked_through_host_api_agent_run() = runTest {
        assumeTrue(runCatching { QuickJSContext.create().use { } }.isSuccess)
        val workingRoot = Files.createTempDirectory("plugin-v2-agent-capabilities").toFile()
        tempRoots += workingRoot
        File(workingRoot, "runtime").mkdirs()
        File(workingRoot, "runtime/bootstrap.js").writeText(
            """
                export default async function bootstrap(hostApi) {
                  hostApi.registerAgent({
                    key: "research-agent",
                    systemPrompt: "Use host LLM.",
                    model: { providerId: "provider-main", modelId: "model-main" },
                    handler: async ({ input, agent }) => {
                      return await agent.run(input);
                    }
                  });
                  hostApi.registerCommandHandler({
                    command: "agentcap",
                    handler: async (event) => {
                      const result = await hostApi.agent.run({
                        key: "research-agent",
                        input: "agent question"
                      });
                      event.replyText(result.text + ":" + result.providerId + ":" + result.modelId);
                    }
                  });
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(logBus = logBus, clock = { 1L })
        val llmPort = RecordingHostLlmPort()
        val loader = PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ),
            compiler = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L }),
            clock = { 1L },
            logBus = logBus,
            store = store,
            lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
            providerReadApi = providerReadApi(logBus),
            hostLlmApi = hostLlmApi(logBus, llmPort),
            hostLlmPort = llmPort,
        )

        val loadResult = loader.load(hostCapabilityPluginRecord(workingRoot))
        assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)

        val dispatchResult = PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 1L },
        ).dispatchMessage(
            event = PluginMessageEvent(
                eventId = "evt-agent-capability",
                platformAdapterType = "app_chat",
                messageType = MessageType.FriendMessage,
                conversationId = "conversation-current",
                senderId = "user-1",
                timestampEpochMillis = 1L,
                rawText = "/agentcap",
                initialWorkingText = "/agentcap",
                rawMentions = emptyList(),
                normalizedMentions = emptyList(),
            ),
        )

        assertEquals(
            "response-agent question:provider-main:model-main",
            checkNotNull(dispatchResult.commandResponse).text,
        )
        assertEquals(1, llmPort.requests.size)
        assertTrue(llmPort.requests.single().bypassPluginLlmHooks)
    }

    @Test
    fun quickjs_agent_run_executes_registered_plugin_tool_handler_through_runtime_wiring() = runTest {
        assumeTrue(runCatching { QuickJSContext.create().use { } }.isSuccess)
        val workingRoot = Files.createTempDirectory("plugin-v2-agent-tool-capabilities").toFile()
        tempRoots += workingRoot
        File(workingRoot, "runtime").mkdirs()
        File(workingRoot, "runtime/bootstrap.js").writeText(
            """
                export default async function bootstrap(hostApi) {
                  hostApi.registerTool({
                    name: "lookup",
                    description: "Lookup a value",
                    inputSchema: {
                      type: "object",
                      properties: { query: { type: "string" } }
                    },
                    handler: async (args) => {
                      return { status: "success", text: "tool-hit-" + args.payload.query };
                    }
                  });
                  hostApi.registerAgent({
                    key: "tool-agent",
                    systemPrompt: "Use lookup before answering.",
                    tools: ["lookup"],
                    model: { providerId: "provider-main", modelId: "model-main" },
                    handler: async ({ input, agent }) => {
                      return await agent.run(input);
                    }
                  });
                  hostApi.registerCommandHandler({
                    command: "agenttool",
                    handler: async (event) => {
                      const result = await hostApi.agent.run({
                        key: "tool-agent",
                        input: "agent tool question"
                      });
                      event.replyText(result.text + ":" + result.toolCallCount + ":" + result.failureCode);
                    }
                  });
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(logBus = logBus, clock = { 1L })
        val llmPort = ToolCallingHostLlmPort()
        val loader = PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ),
            compiler = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L }),
            clock = { 1L },
            logBus = logBus,
            store = store,
            lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
            providerReadApi = providerReadApi(logBus),
            hostLlmApi = hostLlmApi(logBus, llmPort),
            hostLlmPort = llmPort,
        )

        val loadResult = loader.load(hostCapabilityPluginRecord(workingRoot))
        assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)

        val dispatchResult = PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 1L },
        ).dispatchMessage(
            event = PluginMessageEvent(
                eventId = "evt-agent-tool-capability",
                platformAdapterType = "app_chat",
                messageType = MessageType.FriendMessage,
                conversationId = "conversation-current",
                senderId = "user-1",
                timestampEpochMillis = 1L,
                rawText = "/agenttool",
                initialWorkingText = "/agenttool",
                rawMentions = emptyList(),
                normalizedMentions = emptyList(),
            ),
        )

        assertEquals(
            "final-tool-hit-agent tool question:1:",
            checkNotNull(dispatchResult.commandResponse).text,
        )
        assertEquals(2, llmPort.requests.size)
        assertTrue(llmPort.requests.all { request -> request.bypassPluginLlmHooks })
    }

    @Test
    fun quickjs_filters_anyof_registers_and_compiles_into_ast() = runTest {
        assumeTrue(runCatching { QuickJSContext.create().use { } }.isSuccess)
        val workingRoot = Files.createTempDirectory("plugin-v2-filter-anyof").toFile()
        tempRoots += workingRoot
        File(workingRoot, "runtime").mkdirs()
        File(workingRoot, "runtime/bootstrap.js").writeText(
            """
                export default async function bootstrap(hostApi) {
                  hostApi.registerMessageHandler({
                    registrationKey: "filters.anyof",
                    filters: {
                      anyOf: [
                        { eventMessageType: "group" },
                        { platformAdapterType: "onebot" }
                      ]
                    },
                    handler: async () => {}
                  });
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(logBus = logBus, clock = { 1L })
        val loader = PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ),
            compiler = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L }),
            clock = { 1L },
            logBus = logBus,
            store = store,
            lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
        )

        val loadResult = loader.load(hostCapabilityPluginRecord(workingRoot))

        assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)
        val expression = store.snapshot()
            .compiledRegistriesByPluginId
            .getValue("plugin.capability")
            .handlerRegistry
            .messageHandlers
            .single()
            .filterExpression
        val anyOf = expression as PluginV2CompiledFilterExpression.AnyOf
        assertEquals(2, anyOf.children.size)
        assertEquals(
            listOf("event_message_type", "platform_adapter_type"),
            anyOf.children.map { child -> (child as PluginV2CompiledFilterExpression.Builtin).reasonCode },
        )
    }

    @Test
    fun quickjs_rejects_declared_filters_and_filters_ast_on_same_handler() = runTest {
        assumeTrue(runCatching { QuickJSContext.create().use { } }.isSuccess)
        val workingRoot = Files.createTempDirectory("plugin-v2-filter-ambiguous").toFile()
        tempRoots += workingRoot
        File(workingRoot, "runtime").mkdirs()
        File(workingRoot, "runtime/bootstrap.js").writeText(
            """
                export default async function bootstrap(hostApi) {
                  hostApi.registerMessageHandler({
                    registrationKey: "filters.ambiguous",
                    declaredFilters: ["event_message_type:group"],
                    filters: { anyOf: [{ platformAdapterType: "onebot" }] },
                    handler: async () => {}
                  });
                }
            """.trimIndent(),
            Charsets.UTF_8,
        )
        val logBus = InMemoryPluginRuntimeLogBus(capacity = 128, clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(logBus = logBus, clock = { 1L })
        val loader = PluginV2RuntimeLoader(
            sessionFactory = PluginV2RuntimeSessionFactory(
                scriptExecutor = QuickJsExternalPluginScriptExecutor(initializeQuickJs = {}),
            ),
            compiler = PluginV2RegistryCompiler(logBus = logBus, clock = { 1L }),
            clock = { 1L },
            logBus = logBus,
            store = store,
            lifecycleManager = PluginV2LifecycleManager(store = store, logBus = logBus, clock = { 1L }),
        )

        val loadResult = loader.load(hostCapabilityPluginRecord(workingRoot))

        assertEquals(PluginV2RuntimeLoadStatus.Failed, loadResult.status)
        assertTrue(loadResult.diagnostics.any { it.code == "ambiguous_filter_sources" })
        assertTrue(store.snapshot().compiledRegistriesByPluginId["plugin.capability"] == null)
    }

    private fun providerReadApi(logBus: PluginRuntimeLogBus): PluginV2ProviderReadApi {
        return PluginV2ProviderReadApi(
            facade = hostApiFacade(logBus),
            providerReader = providerReadPort(),
        )
    }

    private fun providerReadPort(): PluginV2ProviderReadPort {
        return object : PluginV2ProviderReadPort {
            override suspend fun providers(): List<PluginV2ProviderReadProvider> {
                return listOf(
                    PluginV2ProviderReadProvider(
                        providerId = "provider-main",
                        displayName = "Provider Main",
                        enabled = true,
                        capabilities = setOf("chat"),
                        defaultModelId = "model-main",
                        models = listOf(
                            PluginV2ProviderReadModel(
                                modelId = "model-main",
                                displayName = "Model Main",
                                capabilities = setOf("chat"),
                                contextWindow = 8192,
                                supportsToolCalling = true,
                                supportsStreaming = true,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private fun messageSendApi(
        logBus: PluginRuntimeLogBus,
        sendPort: PluginV2MessageSendPort,
    ): PluginV2MessageSendApi {
        return PluginV2MessageSendApi(
            facade = hostApiFacade(logBus),
            sendPort = sendPort,
        )
    }

    private fun hostLlmApi(
        logBus: PluginRuntimeLogBus,
        llmPort: PluginV2HostLlmPort,
    ): PluginV2HostLlmApi {
        return PluginV2HostLlmApi(
            facade = hostApiFacade(logBus),
            providerReader = providerReadPort(),
            llmPort = llmPort,
        )
    }

    private fun contextCompressApi(
        logBus: PluginRuntimeLogBus,
        llmPort: PluginV2HostLlmPort,
    ): PluginV2ContextCompressApi {
        return PluginV2ContextCompressApi(
            facade = hostApiFacade(logBus),
            historyReader = PluginV2ConversationHistoryReadPort { request ->
                assertEquals("conversation-current", request.conversationId)
                listOf(
                    PluginV2ConversationHistoryMessage(
                        messageId = "message-old",
                        role = "user",
                        senderId = "user-1",
                        messageType = "friend",
                        text = "old",
                        timestampEpochMillis = 1L,
                    ),
                    PluginV2ConversationHistoryMessage(
                        messageId = "message-latest",
                        role = "assistant",
                        senderId = "assistant",
                        messageType = "friend",
                        text = "latest",
                        timestampEpochMillis = 2L,
                    ),
                )
            },
            llmPort = llmPort,
        )
    }

    private fun conversationHistoryApi(logBus: PluginRuntimeLogBus): PluginV2ConversationHistoryApi {
        return PluginV2ConversationHistoryApi(
            facade = hostApiFacade(logBus),
            historyReader = PluginV2ConversationHistoryReadPort { request ->
                assertEquals("conversation-current", request.conversationId)
                listOf(
                    PluginV2ConversationHistoryMessage(
                        messageId = "message-old",
                        role = "user",
                        senderId = "user-1",
                        messageType = "friend",
                        text = "old",
                        timestampEpochMillis = 1L,
                    ),
                    PluginV2ConversationHistoryMessage(
                        messageId = "message-latest",
                        role = "assistant",
                        senderId = "assistant",
                        messageType = "friend",
                        text = "latest",
                        timestampEpochMillis = 2L,
                    ),
                )
            },
        )
    }

    private fun hostApiFacade(logBus: PluginRuntimeLogBus): PluginV2HostApiFacade {
        return PluginV2HostApiFacade(
            permissionPolicy = PluginV2HostApiPermissionPolicy(),
            asyncBridge = PluginV2HostApiAsyncBridge(dispatcher = Dispatchers.Unconfined),
            auditLogger = PluginV2HostApiAuditLogger(logBus = logBus, clock = { 10L }),
            clock = { 10L },
        )
    }

    private fun hostCapabilityPluginRecord(workingRoot: File): PluginInstallRecord {
        val permissions = listOf(
            permission(PluginV2HostApiPermissions.PROVIDER_READ, "Provider read"),
            permission(PluginV2HostApiPermissions.SEND_MESSAGE, "Send message"),
            permission(PluginV2HostApiPermissions.CONVERSATION_READ, "Conversation read"),
            permission(PluginV2HostApiPermissions.CALL_MODEL, "Call model"),
            permission(PluginV2HostApiPermissions.CONTEXT_COMPRESS, "Compress context"),
            permission(PluginV2HostApiPermissions.AGENT_RUN, "Agent run"),
        )
        val manifest = PluginManifest(
            pluginId = "plugin.capability",
            version = "1.0.0",
            protocolVersion = 2,
            author = "ElymBot",
            title = "Capability Plugin",
            description = "Host API capability bridge test plugin.",
            permissions = permissions,
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "runtime/bootstrap.js",
        )
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = workingRoot.absolutePath,
                importedAt = 1L,
            ),
            packageContractSnapshot = PluginPackageContractSnapshot(
                protocolVersion = 2,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/bootstrap.js",
                    apiVersion = 1,
                ),
            ),
            permissionSnapshot = permissions,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            enabled = true,
            installedAt = 1L,
            lastUpdatedAt = 1L,
            localPackagePath = File(workingRoot, "plugin.zip").absolutePath,
            extractedDir = workingRoot.absolutePath,
        )
    }

    private fun permission(
        permissionId: String,
        title: String,
    ): PluginPermissionDeclaration {
        return PluginPermissionDeclaration(
            permissionId = permissionId,
            title = title,
            description = title,
            riskLevel = PluginRiskLevel.MEDIUM,
            required = true,
        )
    }

    private class RecordingMessageSendPort : PluginV2MessageSendPort {
        val requests = mutableListOf<PluginV2MessageSendPortRequest>()

        override suspend fun send(request: PluginV2MessageSendPortRequest): PluginV2MessageSendPortResult {
            requests += request
            return PluginV2MessageSendPortResult(
                success = true,
                receiptIds = listOf("receipt-${request.conversationId}"),
            )
        }
    }

    private class RecordingHostLlmPort : PluginV2HostLlmPort {
        val requests = mutableListOf<PluginV2HostLlmPortRequest>()

        override suspend fun generate(request: PluginV2HostLlmPortRequest): PluginV2HostLlmPortResult {
            requests += request
            val input = request.messages.firstOrNull()?.text.orEmpty()
            return PluginV2HostLlmPortResult(
                text = "response-$input",
                finishReason = "stop",
                providerId = request.providerId,
                modelId = request.modelId,
                usage = PluginLlmUsageSnapshot(totalTokens = input.length),
            )
        }
    }

    private class ToolCallingHostLlmPort : PluginV2HostLlmPort {
        val requests = mutableListOf<PluginV2HostLlmPortRequest>()

        override suspend fun generate(request: PluginV2HostLlmPortRequest): PluginV2HostLlmPortResult {
            requests += request
            return if (requests.size == 1) {
                PluginV2HostLlmPortResult(
                    text = "",
                    finishReason = "tool_calls",
                    providerId = request.providerId,
                    modelId = request.modelId,
                    toolCalls = listOf(
                        PluginLlmToolCall(
                            toolCallId = "call-lookup",
                            toolName = "lookup",
                            arguments = linkedMapOf("query" to request.messages.single().text),
                        ),
                    ),
                )
            } else {
                val toolMessage = request.messages.firstOrNull { message -> message.role == "tool" }?.text.orEmpty()
                PluginV2HostLlmPortResult(
                    text = "final-$toolMessage",
                    finishReason = "stop",
                    providerId = request.providerId,
                    modelId = request.modelId,
                )
            }
        }
    }
}

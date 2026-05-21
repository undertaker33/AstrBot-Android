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

    private fun providerReadApi(logBus: PluginRuntimeLogBus): PluginV2ProviderReadApi {
        return PluginV2ProviderReadApi(
            facade = hostApiFacade(logBus),
            providerReader = object : PluginV2ProviderReadPort {
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
            },
        )
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
}

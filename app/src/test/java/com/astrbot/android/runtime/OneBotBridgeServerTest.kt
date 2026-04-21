package com.astrbot.android.core.runtime.container

import com.astrbot.android.data.BotRepository
import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.core.runtime.context.RuntimeContextDataPort
import com.astrbot.android.core.runtime.context.RuntimeContextResolver
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.http.AstrBotHttpClient
import com.astrbot.android.data.http.HttpMethod
import com.astrbot.android.data.http.HttpRequestSpec
import com.astrbot.android.data.http.HttpResponsePayload
import com.astrbot.android.data.http.MultipartPartSpec
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.feature.bot.data.LegacyBotRepositoryAdapter
import com.astrbot.android.feature.config.data.LegacyConfigRepositoryAdapter
import com.astrbot.android.feature.persona.data.FeaturePersonaRepository as PersonaRepository
import com.astrbot.android.feature.persona.data.LegacyPersonaRepositoryAdapter
import com.astrbot.android.feature.plugin.runtime.ExternalPluginBridgeRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.DefaultAppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScopedFailureStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngineProvider
import com.astrbot.android.feature.plugin.runtime.createCompatPluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeRegistry
import com.astrbot.android.feature.plugin.runtime.PluginV2QuickJsTestGate
import com.astrbot.android.feature.plugin.runtime.PluginV2RegistryCompiler
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoadStatus
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSessionFactory
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.QuickJsExternalPluginScriptExecutor
import com.astrbot.android.feature.plugin.runtime.RecordingExternalPluginScriptExecutor
import com.astrbot.android.feature.plugin.runtime.createQuickJsExternalPluginInstallRecord
import com.astrbot.android.feature.plugin.runtime.executionContextFor
import com.astrbot.android.feature.plugin.runtime.runtimePlugin
import com.astrbot.android.feature.plugin.runtime.samplePluginV2InstallRecord
import com.astrbot.android.feature.provider.data.LegacyProviderRepositoryAdapter
import com.astrbot.android.feature.qq.data.LegacyQqConversationAdapter
import com.astrbot.android.feature.qq.data.LegacyQqPlatformConfigAdapter
import com.astrbot.android.feature.qq.runtime.DefaultQqProviderInvoker
import com.astrbot.android.feature.qq.runtime.QqOneBotRuntimeDependencies
import com.astrbot.android.feature.resource.data.FeatureResourceCenterRepository as ResourceCenterRepository
import com.astrbot.android.runtime.llm.LegacyChatCompletionServiceAdapter
import com.astrbot.android.runtime.llm.LegacyLlmProviderProbeAdapter
import com.astrbot.android.runtime.llm.LegacyRuntimeOrchestratorAdapter
import com.astrbot.android.feature.plugin.runtime.DefaultRuntimeLlmOrchestrator
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqOneBotBridgeServerTest {
    @Test
    fun `replyable qq message triggers plugins around model dispatch and reuses qq session semantics`() = runTest {
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(),
            providers = listOf(defaultChatProvider()),
        ) {
            val signalLog = CopyOnWriteArrayList<String>()
            val contexts = CopyOnWriteArrayList<PluginExecutionContext>()
            val httpCalls = AtomicInteger(0)
            PluginRuntimeRegistry.registerProvider {
                listOf(
                    runtimePlugin(
                        pluginId = "qq-before-plugin",
                        supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                    ) { context ->
                        signalLog += "plugin:before"
                        contexts += context
                        NoOp("before")
                    },
                    runtimePlugin(
                        pluginId = "qq-after-plugin",
                        supportedTriggers = setOf(PluginTriggerSource.AfterModelResponse),
                    ) { context ->
                        signalLog += "plugin:after"
                        contexts += context
                        NoOp("after")
                    },
                )
            }
            ChatCompletionService.setHttpClientOverrideForTests(
                FakeHttpClient(
                    onExecute = { request ->
                        signalLog += "http:chat:${request.method.value}"
                        httpCalls.incrementAndGet()
                        HttpResponsePayload(
                            code = 200,
                            body = """{"choices":[{"message":{"content":"model reply"}}]}""",
                            headers = emptyMap(),
                            url = request.url,
                        )
                    },
                ),
            )

            invokeHandlePayload(
                """
                {
                  "post_type": "message",
                  "message_type": "group",
                  "self_id": "10001",
                  "user_id": "20002",
                  "group_id": "30003",
                  "message_id": "message-replyable",
                  "sender": {
                    "card": "群名片",
                    "nickname": "昵称"
                  },
                  "message": [
                    { "type": "at", "data": { "qq": "10001" } },
                    { "type": "text", "data": { "text": "hello qq" } }
                  ]
                }
                """.trimIndent(),
            )

            assertEquals(listOf("plugin:before", "http:chat:POST", "plugin:after"), signalLog.toList())
            assertEquals(1, httpCalls.get())
            assertEquals(2, contexts.size)

            val beforeContext = contexts.first { it.trigger == PluginTriggerSource.BeforeSendMessage }
            assertEquals("qq", beforeContext.sessionRef.platformId)
            assertEquals(MessageType.GroupMessage, beforeContext.sessionRef.messageType)
            assertEquals("group:30003", beforeContext.sessionRef.originSessionId)
            assertEquals("群名片: hello qq", beforeContext.message.contentPreview)
            assertEquals(MessageType.GroupMessage.wireValue, beforeContext.message.messageType)
            assertEquals("qq", beforeContext.bot.platformId)

            val afterContext = contexts.first { it.trigger == PluginTriggerSource.AfterModelResponse }
            assertEquals("model reply", afterContext.message.contentPreview)
            assertEquals(0, afterContext.message.attachmentCount)
        }
    }

    @Test
    fun `replyable qq message consumes external quickjs plugins around model dispatch`() = runTest {
        val extractedDir = Files.createTempDirectory("onebot-external-quickjs").toFile()
        try {
            val record = createQuickJsExternalPluginInstallRecord(
                extractedDir = extractedDir,
                pluginId = "qq-external-plugin",
                supportedTriggers = listOf("before_send_message", "after_model_response"),
            )
            withOneBotState(
                bot = defaultBot(),
                config = defaultConfig(),
                providers = listOf(defaultChatProvider()),
            ) {
                val httpCalls = AtomicInteger(0)
                PluginRuntimeRegistry.registerExternalProvider {
                    ExternalPluginRuntimeCatalog.plugins(
                        records = listOf(record),
                        bridgeRuntime = ExternalPluginBridgeRuntime(
                            scriptExecutor = RecordingExternalPluginScriptExecutor(
                                outputs = listOf(
                                    JSONObject(mapOf("resultType" to "noop", "reason" to "before")).toString(),
                                    JSONObject(mapOf("resultType" to "noop", "reason" to "after")).toString(),
                                ),
                            ),
                        ),
                    )
                }
                ChatCompletionService.setHttpClientOverrideForTests(
                    FakeHttpClient(
                        onExecute = { request ->
                            httpCalls.incrementAndGet()
                            HttpResponsePayload(
                                code = 200,
                                body = """{"choices":[{"message":{"content":"model reply"}}]}""",
                                headers = emptyMap(),
                                url = request.url,
                            )
                        },
                    ),
                )

                invokeHandlePayload(
                    """
                    {
                      "post_type": "message",
                      "message_type": "private",
                      "self_id": "10001",
                      "user_id": "20002",
                      "message_id": "message-external-quickjs",
                      "raw_message": "hello external"
                    }
                    """.trimIndent(),
                )

                assertEquals(1, httpCalls.get())
                val sessionId = "qq-qq-main-private-20002"
                val messages = ConversationRepository.session(sessionId).messages
                assertEquals("hello external", messages.first { it.role == "user" }.content)
                assertEquals("model reply", messages.last { it.role == "assistant" }.content)
            }
        } finally {
            extractedDir.deleteRecursively()
        }
    }

    @Test
    fun `qq runtime ingress ignores registry external plugins when the catalog is empty`() = runTest {
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(),
            providers = listOf(defaultChatProvider()),
        ) {
            val externalRuns = AtomicInteger(0)
            PluginRuntimeRegistry.registerExternalProvider {
                listOf(
                    runtimePlugin(
                        pluginId = "qq-external-ingress-only",
                        supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                    ) {
                        externalRuns.incrementAndGet()
                        NoOp("external")
                    },
                )
            }
            ChatCompletionService.setHttpClientOverrideForTests(
                FakeHttpClient(
                    onExecute = { request ->
                        HttpResponsePayload(
                            code = 200,
                            body = """{"choices":[{"message":{"content":"qq reply"}}]}""",
                            headers = emptyMap(),
                            url = request.url,
                        )
                    },
                ),
            )

            invokeHandlePayload(
                """
                {
                  "post_type": "message",
                  "message_type": "private",
                  "self_id": "10001",
                  "user_id": "20002",
                  "message_id": "message-external-ingress-only",
                  "raw_message": "hello qq ingress"
                }
                """.trimIndent(),
            )

            assertEquals(0, externalRuns.get())
        }
    }

    @Test
    fun `qq auto reply failure persists assistant closure and sends short notice`() = runTest {
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(),
            providers = listOf(defaultChatProvider()),
        ) {
            val sentReplies = CopyOnWriteArrayList<String>()
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, text, _ ->
                sentReplies += text
                OneBotSendResult.success(listOf("failure-notice"))
            }
            ChatCompletionService.setHttpClientOverrideForTests(
                FakeHttpClient(
                    onExecute = { request ->
                        HttpResponsePayload(
                            code = 400,
                            body = """{"error":{"message":"Messages with role 'tool' must be a response to a preceding message with 'tool_calls'"}}""",
                            headers = emptyMap(),
                            url = request.url,
                        )
                    },
                ),
            )

            invokeHandlePayload(
                """
                {
                  "post_type": "message",
                  "message_type": "private",
                  "self_id": "10001",
                  "user_id": "20002",
                  "message_id": "message-auto-reply-failure",
                  "raw_message": "search web"
                }
                """.trimIndent(),
            )

            val messages = ConversationRepository.session("qq-qq-main-private-20002").messages
            assertEquals("search web", messages.first { it.role == "user" }.content)
            val assistantFailure = messages.last { it.role == "assistant" }
            assertEquals("工具调用失败：本轮自动回复未完成，请稍后再试。", assistantFailure.content)

            assertEquals(listOf("工具调用失败：本轮自动回复未完成，请稍后再试。"), sentReplies.toList())
            assertFalse(sentReplies.single().contains("tool_calls"))
            assertTrue(RuntimeLogRepository.logs.value.any { it.contains("tool_calls") })
        }
    }

    @Test
    fun `keyword blocked qq message does not trigger plugins or model dispatch`() = runTest {
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(
                keywordDetectionEnabled = true,
                keywordPatterns = listOf("forbidden"),
            ),
            providers = listOf(defaultChatProvider()),
        ) {
            val contexts = CopyOnWriteArrayList<PluginExecutionContext>()
            val httpCalls = AtomicInteger(0)
            PluginRuntimeRegistry.registerProvider {
                listOf(
                    runtimePlugin(
                        pluginId = "qq-before-plugin",
                        supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                    ) { context ->
                        contexts += context
                        NoOp("should-not-run")
                    },
                )
            }
            ChatCompletionService.setHttpClientOverrideForTests(
                FakeHttpClient(
                    onExecute = { request ->
                        httpCalls.incrementAndGet()
                        HttpResponsePayload(
                            code = 200,
                            body = """{"choices":[{"message":{"content":"unexpected"}}]}""",
                            headers = emptyMap(),
                            url = request.url,
                        )
                    },
                ),
            )

            invokeHandlePayload(
                """
                {
                  "post_type": "message",
                  "message_type": "group",
                  "self_id": "10001",
                  "user_id": "20002",
                  "group_id": "30003",
                  "message_id": "message-keyword-blocked",
                  "sender": {
                    "card": "群名片"
                  },
                  "message": [
                    { "type": "text", "data": { "text": "this is forbidden" } }
                  ]
                }
                """.trimIndent(),
            )

            assertTrue(contexts.isEmpty())
            assertEquals(0, httpCalls.get())
        }
    }

    @Test
    fun `qq runtime plugin failure does not break onebot main chain`() = runTest {
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(),
            providers = listOf(defaultChatProvider()),
        ) {
            val signalLog = CopyOnWriteArrayList<String>()
            val httpCalls = AtomicInteger(0)
            PluginRuntimeRegistry.registerProvider {
                listOf(
                    runtimePlugin(
                        pluginId = "qq-before-plugin-fail",
                        supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                    ) { context ->
                        signalLog += "plugin:before"
                        throw IllegalStateException("plugin exploded for ${context.message.messageId}")
                    },
                )
            }
            ChatCompletionService.setHttpClientOverrideForTests(
                FakeHttpClient(
                    onExecute = { request ->
                        signalLog += "http:chat"
                        httpCalls.incrementAndGet()
                        HttpResponsePayload(
                            code = 200,
                            body = """{"choices":[{"message":{"content":"still replied"}}]}""",
                            headers = emptyMap(),
                            url = request.url,
                        )
                    },
                ),
            )

            invokeHandlePayload(
                """
                {
                  "post_type": "message",
                  "message_type": "private",
                  "self_id": "10001",
                  "user_id": "20002",
                  "message_id": "message-plugin-failure",
                  "raw_message": "still reply"
                }
                """.trimIndent(),
            )

            assertEquals(listOf("plugin:before", "http:chat"), signalLog.toList())
            assertEquals(1, httpCalls.get())
            val sessionId = "qq-qq-main-private-20002"
            val assistantMessage = ConversationRepository.session(sessionId).messages.last { it.role == "assistant" }
            assertEquals("still replied", assistantMessage.content)
        }
    }

    @Test
    fun `plugin suspended by app chat shared store is skipped by qq runtime`() = runTest {
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(),
            providers = listOf(defaultChatProvider()),
        ) {
            val sharedStore = InMemoryPluginFailureStateStore()
            PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(sharedStore)
            val attempts = AtomicInteger(0)
            val httpCalls = AtomicInteger(0)
            PluginRuntimeRegistry.registerProvider {
                listOf(
                    runtimePlugin(
                        pluginId = "shared-plugin",
                        supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                    ) {
                        attempts.incrementAndGet()
                        error("shared runtime boom")
                    },
                )
            }
            ChatCompletionService.setHttpClientOverrideForTests(
                FakeHttpClient(
                    onExecute = { request ->
                        httpCalls.incrementAndGet()
                        HttpResponsePayload(
                            code = 200,
                            body = """{"choices":[{"message":{"content":"qq still replied"}}]}""",
                            headers = emptyMap(),
                            url = request.url,
                        )
                    },
                ),
            )

            repeat(3) {
                DefaultAppChatPluginRuntime.execute(
                    trigger = PluginTriggerSource.BeforeSendMessage,
                    contextFactory = ::executionContextFor,
                )
            }

            invokeHandlePayload(
                """
                {
                  "post_type": "message",
                  "message_type": "private",
                  "self_id": "10001",
                  "user_id": "20002",
                  "message_id": "message-shared-suspension",
                  "raw_message": "hello after suspension"
                }
                """.trimIndent(),
            )

            assertEquals(3, attempts.get())
            assertEquals(1, httpCalls.get())
            val snapshot = sharedStore.get("shared-plugin")
            assertTrue(snapshot?.isSuspended == true)
            assertEquals("shared runtime boom", snapshot?.lastErrorSummary)
        }
    }

    @Test
    fun `qq bot command keeps priority over plugins and model dispatch`() = runTest {
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(),
            providers = listOf(defaultChatProvider()),
        ) {
            val contexts = CopyOnWriteArrayList<PluginExecutionContext>()
            val httpCalls = AtomicInteger(0)
            PluginRuntimeRegistry.registerProvider {
                listOf(
                    runtimePlugin(
                        pluginId = "qq-before-plugin",
                        supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                    ) { context ->
                        contexts += context
                        NoOp("should-not-run")
                    },
                )
            }
            ChatCompletionService.setHttpClientOverrideForTests(
                FakeHttpClient(
                    onExecute = { request ->
                        httpCalls.incrementAndGet()
                        HttpResponsePayload(
                            code = 200,
                            body = """{"choices":[{"message":{"content":"unexpected"}}]}""",
                            headers = emptyMap(),
                            url = request.url,
                        )
                    },
                ),
            )

            invokeHandlePayload(
                """
                {
                  "post_type": "message",
                  "message_type": "private",
                  "self_id": "10001",
                  "user_id": "20002",
                  "message_id": "message-command-priority",
                  "raw_message": "/sid"
                }
                """.trimIndent(),
            )

            assertTrue(contexts.isEmpty())
            assertEquals(0, httpCalls.get())
        }
    }

    @Test
    fun `unsupported qq slash command falls through to external plugin on_command before unsupported fallback`() = runTest {
        val extractedDir = Files.createTempDirectory("onebot-qq-command-plugin").toFile()
        try {
            val record = createQuickJsExternalPluginInstallRecord(
                extractedDir = extractedDir,
                pluginId = "qq-external-plugin",
                supportedTriggers = listOf("on_command"),
            )
            withOneBotState(
                bot = defaultBot(),
                config = defaultConfig(),
                providers = listOf(defaultChatProvider()),
            ) {
                RuntimeLogRepository.clear()
                val httpCalls = AtomicInteger(0)
                val scriptExecutor = RecordingExternalPluginScriptExecutor(
                    outputs = listOf(
                        JSONObject(
                            mapOf(
                                "resultType" to "text",
                                "text" to "plugin command reply",
                            ),
                        ).toString(),
                    ),
                )
                PluginRuntimeCatalog.registerProvider {
                    ExternalPluginRuntimeCatalog.plugins(
                        records = listOf(record),
                        bridgeRuntime = ExternalPluginBridgeRuntime(
                            scriptExecutor = scriptExecutor,
                        ),
                    )
                }
                ChatCompletionService.setHttpClientOverrideForTests(
                    FakeHttpClient(
                        onExecute = { request ->
                            httpCalls.incrementAndGet()
                            HttpResponsePayload(
                                code = 200,
                                body = """{"choices":[{"message":{"content":"unexpected"}}]}""",
                                headers = emptyMap(),
                                url = request.url,
                            )
                        },
                    ),
                )

                invokeHandlePayload(
                    """
                    {
                      "post_type": "message",
                      "message_type": "private",
                      "self_id": "10001",
                      "user_id": "20002",
                      "message_id": "message-plugin-fallback",
                      "raw_message": "/表情管理 angry"
                    }
                    """.trimIndent(),
                )

                assertEquals(1, scriptExecutor.requests.size)
                assertTrue(scriptExecutor.requests.single().contextJson.contains("/表情管理 angry"))
                assertEquals(0, httpCalls.get())
                val logs = RuntimeLogRepository.logs.value
                assertTrue(logs.any { it.contains("QQ plugin command handled: plugin=qq-external-plugin result=TextResult") })
                assertTrue(logs.none { it.contains("Bot command handled via router") })
                assertTrue(logs.none { it.contains("Bot command unsupported after plugin fallback") })
            }
        } finally {
            extractedDir.deleteRecursively()
        }
    }

    @Test
    fun `unsupported qq slash command without plugin keeps unsupported fallback and skips model dispatch`() = runTest {
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(),
            providers = listOf(defaultChatProvider()),
        ) {
            RuntimeLogRepository.clear()
            val httpCalls = AtomicInteger(0)
            ChatCompletionService.setHttpClientOverrideForTests(
                FakeHttpClient(
                    onExecute = { request ->
                        httpCalls.incrementAndGet()
                        HttpResponsePayload(
                            code = 200,
                            body = """{"choices":[{"message":{"content":"unexpected"}}]}""",
                            headers = emptyMap(),
                            url = request.url,
                        )
                    },
                ),
            )

            invokeHandlePayload(
                """
                {
                  "post_type": "message",
                  "message_type": "private",
                  "self_id": "10001",
                  "user_id": "20002",
                  "message_id": "message-unsupported-fallback",
                  "raw_message": "/does-not-exist"
                }
                """.trimIndent(),
            )

            assertEquals(0, httpCalls.get())
            val logs = RuntimeLogRepository.logs.value
            assertTrue(logs.any { it.contains("Bot command unsupported after plugin fallback: does-not-exist") })
            assertTrue(logs.none { it.contains("Bot command handled via router") })
        }
    }

    @Test
    fun `real external meme plugin decorates onebot qq reply with attachment`() = runTest {
        PluginV2QuickJsTestGate.assumeAvailable()

        val pluginRoot = File("C:/Users/93445/Desktop/Astrbot/Plugin/Astrbot_Android_plugin_memes")
        require(pluginRoot.isDirectory) {
            "Missing external meme plugin fixture: ${pluginRoot.absolutePath}"
        }

        val workingRoot = Files.createTempDirectory("onebot-external-meme-v2").toFile()
        try {
            pluginRoot.copyRecursively(workingRoot, overwrite = true)
            val record = samplePluginV2InstallRecord(
                pluginId = "io.github.astrbot.android.meme_manager",
            ).copyForFixture(workingRoot.absolutePath)
            val logBus = InMemoryPluginRuntimeLogBus(capacity = 256, clock = { 1L })
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

            withOneBotState(
                bot = defaultBot(),
                config = defaultConfig(),
                providers = listOf(defaultChatProvider()),
            ) {
                RuntimeLogRepository.clear()
                PluginV2ActiveRuntimeStoreProvider.setStoreOverrideForTests(store)
                val sentAttachments = CopyOnWriteArrayList<List<ConversationAttachment>>()
                QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, attachments ->
                    sentAttachments += attachments
                    OneBotSendResult.success(listOf("meme-receipt"))
                }
                ChatCompletionService.setHttpClientOverrideForTests(
                    FakeHttpClient(
                        onExecute = { request ->
                            HttpResponsePayload(
                                code = 200,
                                body = """{"choices":[{"message":{"content":"收到，这就给你安排一个开心回复。"}}]}""",
                                headers = emptyMap(),
                                url = request.url,
                            )
                        },
                    ),
                )

                val loadResult = loader.load(record)
                assertEquals(PluginV2RuntimeLoadStatus.Loaded, loadResult.status)

                invokeHandlePayload(
                    """
                    {
                      "post_type": "message",
                      "message_type": "private",
                      "self_id": "10001",
                      "user_id": "934457024",
                      "message_id": "message-external-meme-onebot",
                      "raw_message": "今天真开心 [happy]"
                    }
                    """.trimIndent(),
                )

                assertTrue(sentAttachments.isNotEmpty())
                assertEquals(1, sentAttachments.last().size)
                assertTrue(
                    sentAttachments.last().single().remoteUrl.replace('\\', '/').contains("/memes/happy/"),
                )
                val logs = RuntimeLogRepository.logs.value
                assertTrue(logs.any { it.contains("QQ v2 reply delivered: plugin=io.github.astrbot.android.meme_manager") })
            }
        } finally {
            PluginV2ActiveRuntimeStoreProvider.setStoreOverrideForTests(null)
            workingRoot.deleteRecursively()
        }
    }

    @Test
    fun `unsupported qq slash command still falls back when plugin returns noop`() = runTest {
        val extractedDir = Files.createTempDirectory("onebot-qq-command-noop").toFile()
        try {
            val record = createQuickJsExternalPluginInstallRecord(
                extractedDir = extractedDir,
                pluginId = "qq-external-plugin",
                supportedTriggers = listOf("on_command"),
            )
            withOneBotState(
                bot = defaultBot(),
                config = defaultConfig(),
                providers = listOf(defaultChatProvider()),
            ) {
                RuntimeLogRepository.clear()
                val httpCalls = AtomicInteger(0)
                val scriptExecutor = RecordingExternalPluginScriptExecutor(
                    outputs = listOf(
                        JSONObject(
                            mapOf(
                                "resultType" to "noop",
                                "reason" to "command not matched",
                            ),
                        ).toString(),
                    ),
                )
                PluginRuntimeCatalog.registerProvider {
                    ExternalPluginRuntimeCatalog.plugins(
                        records = listOf(record),
                        bridgeRuntime = ExternalPluginBridgeRuntime(
                            scriptExecutor = scriptExecutor,
                        ),
                    )
                }
                ChatCompletionService.setHttpClientOverrideForTests(
                    FakeHttpClient(
                        onExecute = { request ->
                            httpCalls.incrementAndGet()
                            HttpResponsePayload(
                                code = 200,
                                body = """{"choices":[{"message":{"content":"unexpected"}}]}""",
                                headers = emptyMap(),
                                url = request.url,
                            )
                        },
                    ),
                )

                invokeHandlePayload(
                    """
                    {
                      "post_type": "message",
                      "message_type": "private",
                      "self_id": "10001",
                      "user_id": "20002",
                      "message_id": "message-plugin-noop-fallback",
                      "raw_message": "/表情管理 missing"
                    }
                    """.trimIndent(),
                )

                assertEquals(1, scriptExecutor.requests.size)
                assertEquals(0, httpCalls.get())
                val logs = RuntimeLogRepository.logs.value
                assertTrue(logs.any { it.contains("QQ plugin command produced no consumable results: command=/表情管理") })
                assertTrue(logs.any { it.contains("Bot command unsupported after plugin fallback: 表情管理") })
            }
        } finally {
            extractedDir.deleteRecursively()
        }
    }

    private suspend fun withOneBotState(
        bot: BotProfile,
        config: ConfigProfile,
        providers: List<ProviderProfile>,
        block: suspend () -> Unit,
    ) {
        val botSnapshot = BotRepository.snapshotProfiles()
        val selectedBotIdSnapshot = BotRepository.selectedBotId.value
        val configSnapshot = ConfigRepository.snapshotProfiles()
        val selectedConfigIdSnapshot = ConfigRepository.selectedProfileId.value
        val providerSnapshot = ProviderRepository.snapshotProfiles()
        val sessionSnapshot = ConversationRepository.snapshotSessions()
        val runtimeContextDataPort = object : RuntimeContextDataPort {
            override fun resolveConfig(configProfileId: String) = ConfigRepository.resolve(configProfileId)

            override fun listProviders() = ProviderRepository.providers.value

            override fun findEnabledPersona(personaId: String) =
                PersonaRepository.personas.value.firstOrNull { it.id == personaId && it.enabled }

            override fun session(sessionId: String) = ConversationRepository.session(sessionId)

            override fun compatibilitySnapshotForConfig(config: ConfigProfile) =
                ResourceCenterRepository.compatibilitySnapshotForConfig(config)
        }
        val runtimeContextResolverPort = object : RuntimeContextResolverPort {
            override fun resolve(
                event: com.astrbot.android.core.runtime.context.RuntimeIngressEvent,
                bot: BotProfile,
                overrideProviderId: String?,
                overridePersonaId: String?,
            ) = RuntimeContextResolver.resolve(
                event = event,
                bot = bot,
                dataPort = runtimeContextDataPort,
                overrideProviderId = overrideProviderId,
                overridePersonaId = overridePersonaId,
            )
        }
        try {
            PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(
                InMemoryPluginScopedFailureStateStore(),
            )
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                OneBotSendResult.success(listOf("test-receipt"))
            }
            QqOneBotBridgeServer.installRuntimeDependencies(
                QqOneBotRuntimeDependencies(
                    botPort = LegacyBotRepositoryAdapter(),
                    configPort = LegacyConfigRepositoryAdapter(),
                    personaPort = LegacyPersonaRepositoryAdapter(),
                    providerPort = LegacyProviderRepositoryAdapter(),
                    conversationPort = LegacyQqConversationAdapter(),
                    platformConfigPort = LegacyQqPlatformConfigAdapter(),
                    orchestrator = LegacyRuntimeOrchestratorAdapter(DefaultRuntimeLlmOrchestrator()),
                    runtimeContextResolverPort = runtimeContextResolverPort,
                    appChatPluginRuntime = DefaultAppChatPluginRuntime,
                    pluginV2DispatchEngine = PluginV2DispatchEngineProvider.engine(),
                    failureStateStore = PluginRuntimeFailureStateStoreProvider.store(),
                    scopedFailureStateStore = PluginRuntimeScopedFailureStateStoreProvider.store(),
                    providerInvoker = DefaultQqProviderInvoker(LegacyChatCompletionServiceAdapter()),
                    gatewayFactory = createCompatPluginHostCapabilityGatewayFactory(),
                    llmProviderProbePort = LegacyLlmProviderProbeAdapter(),
                    logBus = InMemoryPluginRuntimeLogBus(),
                ),
            )
            BotRepository.restoreProfiles(listOf(bot), bot.id)
            ConfigRepository.restoreProfiles(listOf(config), config.id)
            ProviderRepository.restoreProfiles(providers)
            ConversationRepository.restoreSessions(emptyList())
            block()
        } finally {
            ChatCompletionService.setHttpClientOverrideForTests(null)
            QqOneBotBridgeServer.setReplySenderOverrideForTests(null)
            PluginRuntimeCatalog.reset()
            PluginRuntimeRegistry.reset()
            PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(null)
            PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(null)
            BotRepository.restoreProfiles(botSnapshot, selectedBotIdSnapshot)
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigIdSnapshot)
            ProviderRepository.restoreProfiles(providerSnapshot)
            ConversationRepository.restoreSessions(sessionSnapshot)
        }
    }

    private fun com.astrbot.android.model.plugin.PluginInstallRecord.copyForFixture(
        extractedDir: String,
    ): com.astrbot.android.model.plugin.PluginInstallRecord {
        val contractSnapshot = requireNotNull(packageContractSnapshot)
        return com.astrbot.android.model.plugin.PluginInstallRecord.restoreFromPersistedState(
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

    private suspend fun invokeHandlePayload(payload: String) {
        QqOneBotBridgeServer.handlePayload(payload)
        RuntimeLogRepository.flush()
    }

    private fun defaultBot(): BotProfile {
        return BotProfile(
            id = "qq-main",
            displayName = "Primary Bot",
            boundQqUins = listOf("10001"),
            defaultProviderId = "provider-1",
            configProfileId = "config-qq",
            autoReplyEnabled = true,
        )
    }

    private fun defaultConfig(
        keywordDetectionEnabled: Boolean = false,
        keywordPatterns: List<String> = emptyList(),
    ): ConfigProfile {
        return ConfigProfile(
            id = "config-qq",
            defaultChatProviderId = "provider-1",
            replyOnAtOnlyEnabled = false,
            keywordDetectionEnabled = keywordDetectionEnabled,
            keywordPatterns = keywordPatterns,
        )
    }

    private fun defaultChatProvider(): ProviderProfile {
        return ProviderProfile(
            id = "provider-1",
            name = "Test Chat",
            baseUrl = "https://example.com",
            model = "gpt-test",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "test-key",
            capabilities = setOf(ProviderCapability.CHAT),
            enabled = true,
        )
    }

    private class FakeHttpClient(
        private val onExecute: (HttpRequestSpec) -> HttpResponsePayload,
    ) : AstrBotHttpClient {
        override fun execute(requestSpec: HttpRequestSpec): HttpResponsePayload = onExecute(requestSpec)

        override fun executeBytes(requestSpec: HttpRequestSpec): ByteArray {
            throw UnsupportedOperationException("Binary requests are not expected in this test")
        }

        override suspend fun executeStream(
            requestSpec: HttpRequestSpec,
            onLine: suspend (String) -> Unit,
        ) {
            throw UnsupportedOperationException("Streaming requests are not expected in this test")
        }

        override fun executeMultipart(
            requestSpec: HttpRequestSpec,
            parts: List<MultipartPartSpec>,
        ): HttpResponsePayload {
            throw UnsupportedOperationException("Multipart requests are not expected in this test")
        }
    }
}

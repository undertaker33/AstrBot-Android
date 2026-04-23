package com.astrbot.android.core.runtime.container

import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.core.runtime.context.RuntimeContextDataPort
import com.astrbot.android.core.runtime.context.RuntimeContextResolver
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.data.http.AstrBotHttpClient
import com.astrbot.android.data.http.HttpMethod
import com.astrbot.android.data.http.HttpRequestSpec
import com.astrbot.android.data.http.HttpResponsePayload
import com.astrbot.android.data.http.MultipartPartSpec
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.core.runtime.llm.ChatCompletionServiceLlmClient
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.core.runtime.llm.SttProbeResult
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.DefaultPluginExecutionHostOperations
import com.astrbot.android.feature.plugin.runtime.DefaultPluginExecutionHostResolver
import com.astrbot.android.feature.plugin.runtime.DefaultPluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.ExternalPluginBridgeRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.DefaultAppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.PluginExecutionEngine
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeDispatcher
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduler
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.PluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
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
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.feature.qq.runtime.DefaultQqProviderInvoker
import com.astrbot.android.feature.qq.runtime.QqOneBotBridgeServer
import com.astrbot.android.feature.qq.runtime.QqOneBotBridgeServerTestAccess
import com.astrbot.android.feature.qq.runtime.QqOneBotRuntimeDependencies
import com.astrbot.android.feature.qq.runtime.QqPluginExecutionService
import com.astrbot.android.feature.resource.data.ResourceCenterCompatibility
import com.astrbot.android.feature.plugin.runtime.DefaultRuntimeLlmOrchestrator
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqOneBotBridgeServerTest {
    private var currentRepositories: InMemoryOneBotRepositories? = null

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
            PluginRuntimeCatalog.registerProvider {
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
                val messages = currentConversation(sessionId).messages
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

            val messages = currentConversation("qq-qq-main-private-20002").messages
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
            PluginRuntimeCatalog.registerProvider {
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
            PluginRuntimeCatalog.registerProvider {
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
            val assistantMessage = currentConversation(sessionId).messages.last { it.role == "assistant" }
            assertEquals("still replied", assistantMessage.content)
        }
    }

    @Test
    fun `plugin suspended by app chat shared store is skipped by qq runtime`() = runTest {
        val sharedStore = InMemoryPluginFailureStateStore()
        val sharedScopedStore = InMemoryPluginScopedFailureStateStore()
        val sharedLogBus = InMemoryPluginRuntimeLogBus()
        val failureGuard = PluginFailureGuard(
            store = sharedStore,
            scopedStore = sharedScopedStore,
            logBus = sharedLogBus,
        )
        val sharedEngine = PluginExecutionEngine(
            dispatcher = PluginRuntimeDispatcher(
                failureGuard = failureGuard,
                scheduler = PluginRuntimeScheduler(
                    store = InMemoryPluginScheduleStateStore(),
                ),
                logBus = sharedLogBus,
            ),
            failureGuard = failureGuard,
            logBus = sharedLogBus,
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(),
            providers = listOf(defaultChatProvider()),
            failureStateStore = sharedStore,
            scopedFailureStateStore = sharedScopedStore,
            logBus = sharedLogBus,
        ) {
            val attempts = AtomicInteger(0)
            val httpCalls = AtomicInteger(0)
            PluginRuntimeCatalog.registerProvider {
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
                sharedEngine.executeBatch(
                    trigger = PluginTriggerSource.BeforeSendMessage,
                    plugins = PluginRuntimeCatalog.plugins(),
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
            PluginRuntimeCatalog.registerProvider {
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
        executeLegacyPluginsDuringLlmDispatch: Boolean = true,
        appChatPluginRuntime: AppChatLlmPipelineRuntime = DefaultAppChatPluginRuntime,
        pluginV2DispatchEngine: PluginV2DispatchEngine = emptyPluginV2DispatchEngine(),
        failureStateStore: PluginFailureStateStore = InMemoryPluginFailureStateStore(),
        scopedFailureStateStore: PluginScopedFailureStateStore = InMemoryPluginScopedFailureStateStore(),
        logBus: PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus(),
        gatewayFactory: PluginHostCapabilityGatewayFactory = testPluginHostCapabilityGatewayFactory(),
        hostCapabilityGateway: PluginHostCapabilityGateway = testPluginHostCapabilityGateway(),
        block: suspend () -> Unit,
    ) {
        val repositories = InMemoryOneBotRepositories(
            bot = bot,
            config = config,
            providers = providers,
        )
        val runtimeContextDataPort = object : RuntimeContextDataPort {
            override fun resolveConfig(configProfileId: String) = repositories.configPort.resolve(configProfileId)

            override fun listProviders() = repositories.providerPort.providers.value

            override fun findEnabledPersona(personaId: String) =
                repositories.personaPort.personas.value.firstOrNull { it.id == personaId && it.enabled }

            override fun session(sessionId: String) = repositories.conversationPort.session(sessionId)

            override fun compatibilitySnapshotForConfig(config: ConfigProfile) =
                repositories.compatibilitySnapshotForConfig(config)
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
            currentRepositories = repositories
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                OneBotSendResult.success(listOf("test-receipt"))
            }
            val runtimeDependencies = QqOneBotRuntimeDependencies(
                botPort = repositories.botPort,
                configPort = repositories.configPort,
                personaPort = repositories.personaPort,
                providerPort = repositories.providerPort,
                conversationPort = repositories.conversationPort,
                platformConfigPort = repositories.platformConfigPort,
                orchestrator = DefaultRuntimeLlmOrchestrator(),
                runtimeContextResolverPort = runtimeContextResolverPort,
                appChatPluginRuntime = appChatPluginRuntime,
                pluginCatalog = PluginRuntimeCatalog::plugins,
                pluginV2DispatchEngine = pluginV2DispatchEngine,
                failureStateStore = failureStateStore,
                scopedFailureStateStore = scopedFailureStateStore,
                providerInvoker = DefaultQqProviderInvoker(ChatCompletionServiceLlmClient()),
                gatewayFactory = gatewayFactory,
                hostCapabilityGateway = hostCapabilityGateway,
                hostActionExecutor = ExternalPluginHostActionExecutor(),
                pluginExecutionService = QqPluginExecutionService(
                    pluginCatalog = PluginRuntimeCatalog::plugins,
                    failureStateStore = failureStateStore,
                    scopedFailureStateStore = scopedFailureStateStore,
                    logBus = logBus,
                ),
                llmProviderProbePort = chatCompletionServiceProbePort(),
                logBus = logBus,
                silkAudioEncoder = { input -> input },
                executeLegacyPluginsDuringLlmDispatch = executeLegacyPluginsDuringLlmDispatch,
            )
            QqOneBotBridgeServerTestAccess.primeRuntimeDependencies(runtimeDependencies)
            block()
        } finally {
            currentRepositories = null
            ChatCompletionService.setHttpClientOverrideForTests(null)
            QqOneBotBridgeServer.setReplySenderOverrideForTests(null)
            QqOneBotBridgeServerTestAccess.clearRuntimeDependencies()
            PluginRuntimeCatalog.reset()
            PluginRuntimeRegistry.reset()
        }
    }

    private fun currentConversation(sessionId: String): ConversationSession {
        return requireNotNull(currentRepositories) {
            "OneBot test repositories are not initialized."
        }.conversationPort.session(sessionId)
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

    private fun emptyPluginV2DispatchEngine(): PluginV2DispatchEngine {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        return PluginV2DispatchEngine(
            store = PluginV2ActiveRuntimeStore(
                logBus = logBus,
                clock = { 1L },
            ),
            logBus = logBus,
            clock = { 1L },
        )
    }

    private fun testPluginHostCapabilityGatewayFactory(): PluginHostCapabilityGatewayFactory {
        return PluginHostCapabilityGatewayFactory(
            resolver = DefaultPluginExecutionHostResolver(
                DefaultPluginExecutionHostOperations(),
            ),
            hostActionExecutor = ExternalPluginHostActionExecutor(),
        )
    }

    private fun testPluginHostCapabilityGateway(): PluginHostCapabilityGateway {
        return DefaultPluginHostCapabilityGateway(
            resolver = DefaultPluginExecutionHostResolver(
                DefaultPluginExecutionHostOperations(),
            ),
            hostActionExecutor = ExternalPluginHostActionExecutor(),
        )
    }

    private fun chatCompletionServiceProbePort(): LlmProviderProbePort {
        return object : LlmProviderProbePort {
            override fun fetchModels(baseUrl: String, apiKey: String, providerType: ProviderType): List<String> {
                return ChatCompletionService.fetchModels(baseUrl, apiKey, providerType)
            }

            override fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
                return ChatCompletionService.detectMultimodalRule(provider)
            }

            override fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
                return ChatCompletionService.probeMultimodalSupport(provider)
            }

            override fun detectNativeStreamingRule(provider: ProviderProfile): FeatureSupportState {
                return ChatCompletionService.detectNativeStreamingRule(provider)
            }

            override fun probeNativeStreamingSupport(provider: ProviderProfile): FeatureSupportState {
                return ChatCompletionService.probeNativeStreamingSupport(provider)
            }

            override fun probeSttSupport(provider: ProviderProfile): SttProbeResult {
                val result = ChatCompletionService.probeSttSupport(provider)
                return SttProbeResult(state = result.state, transcript = result.transcript)
            }

            override fun probeTtsSupport(provider: ProviderProfile): FeatureSupportState {
                return ChatCompletionService.probeTtsSupport(provider)
            }

            override fun transcribeAudio(
                provider: ProviderProfile,
                attachment: ConversationAttachment,
            ): String {
                return ChatCompletionService.transcribeAudio(provider, attachment)
            }

            override fun synthesizeSpeech(
                provider: ProviderProfile,
                text: String,
                voiceId: String,
                readBracketedContent: Boolean,
            ): ConversationAttachment {
                return ChatCompletionService.synthesizeSpeech(provider, text, voiceId, readBracketedContent)
            }
        }
    }

    private class InMemoryOneBotRepositories(
        bot: BotProfile,
        config: ConfigProfile,
        providers: List<ProviderProfile>,
        personas: List<PersonaProfile> = listOf(
            PersonaProfile(
                id = "default",
                name = "Default Assistant",
                systemPrompt = "You are a concise, reliable QQ assistant.",
                enabledTools = setOf("web_search"),
            ),
        ),
    ) {
        private val botsFlow = MutableStateFlow(listOf(bot))
        private val selectedBotIdFlow = MutableStateFlow(bot.id)
        private val configsFlow = MutableStateFlow(listOf(config))
        private val selectedConfigIdFlow = MutableStateFlow(config.id)
        private val providersFlow = MutableStateFlow(providers)
        private val personasFlow = MutableStateFlow(personas)
        private val sessionsFlow = MutableStateFlow<List<ConversationSession>>(emptyList())

        val botPort: BotRepositoryPort = object : BotRepositoryPort {
            override val bots: StateFlow<List<BotProfile>> = botsFlow
            override val selectedBotId: StateFlow<String> = selectedBotIdFlow

            override fun currentBot(): BotProfile {
                return botsFlow.value.firstOrNull { it.id == selectedBotIdFlow.value }
                    ?: botsFlow.value.first()
            }

            override fun snapshotProfiles(): List<BotProfile> = botsFlow.value.map { profile ->
                profile.copy(
                    boundQqUins = profile.boundQqUins.toList(),
                    triggerWords = profile.triggerWords.toList(),
                )
            }

            override fun create(name: String): BotProfile {
                val created = BotProfile(
                    id = "bot-${UUID.randomUUID()}",
                    displayName = name,
                    configProfileId = configPort.resolveExistingId(selectedConfigIdFlow.value),
                )
                runBlocking { save(created) }
                return created
            }

            override suspend fun save(profile: BotProfile) {
                val updated = botsFlow.value.filterNot { it.id == profile.id } + profile
                botsFlow.value = updated
                if (selectedBotIdFlow.value.isBlank() || updated.none { it.id == selectedBotIdFlow.value }) {
                    selectedBotIdFlow.value = profile.id
                }
            }

            override suspend fun create(profile: BotProfile) {
                save(profile)
            }

            override suspend fun delete(id: String) {
                val updated = botsFlow.value.filterNot { it.id == id }
                botsFlow.value = updated
                if (selectedBotIdFlow.value == id) {
                    selectedBotIdFlow.value = updated.firstOrNull()?.id.orEmpty()
                }
            }

            override suspend fun select(id: String) {
                if (botsFlow.value.any { it.id == id }) {
                    selectedBotIdFlow.value = id
                }
            }
        }

        val configPort: ConfigRepositoryPort = object : ConfigRepositoryPort {
            override val profiles: StateFlow<List<ConfigProfile>> = configsFlow
            override val selectedProfileId: StateFlow<String> = selectedConfigIdFlow

            override fun snapshotProfiles(): List<ConfigProfile> = configsFlow.value.map { profile ->
                profile.copy(
                    adminUids = profile.adminUids.toList(),
                    wakeWords = profile.wakeWords.toList(),
                    whitelistEntries = profile.whitelistEntries.toList(),
                    keywordPatterns = profile.keywordPatterns.toList(),
                    mcpServers = profile.mcpServers.toList(),
                    skills = profile.skills.toList(),
                )
            }

            override fun create(name: String): ConfigProfile {
                val created = ConfigProfile(id = "config-${UUID.randomUUID()}", name = name)
                runBlocking { save(created) }
                return created
            }

            override fun resolve(id: String): ConfigProfile {
                return configsFlow.value.firstOrNull { it.id == id }
                    ?: configsFlow.value.first()
            }

            override fun resolveExistingId(id: String?): String {
                return configsFlow.value.firstOrNull { it.id == id }?.id ?: configsFlow.value.first().id
            }

            override suspend fun save(profile: ConfigProfile) {
                val updated = configsFlow.value.filterNot { it.id == profile.id } + profile
                configsFlow.value = updated
                if (selectedConfigIdFlow.value.isBlank() || updated.none { it.id == selectedConfigIdFlow.value }) {
                    selectedConfigIdFlow.value = profile.id
                }
            }

            override suspend fun delete(id: String) {
                val updated = configsFlow.value.filterNot { it.id == id }
                configsFlow.value = if (updated.isEmpty()) listOf(ConfigProfile(id = "default", name = "Default Config")) else updated
                if (selectedConfigIdFlow.value == id) {
                    selectedConfigIdFlow.value = configsFlow.value.first().id
                }
            }

            override suspend fun select(id: String) {
                if (configsFlow.value.any { it.id == id }) {
                    selectedConfigIdFlow.value = id
                }
            }
        }

        val personaPort: PersonaRepositoryPort = object : PersonaRepositoryPort {
            override val personas: StateFlow<List<PersonaProfile>> = personasFlow

            override fun snapshotProfiles(): List<PersonaProfile> = personasFlow.value.map { persona ->
                persona.copy(enabledTools = persona.enabledTools.toSet())
            }

            override fun snapshotToolEnablement(): List<PersonaToolEnablementSnapshot> =
                personasFlow.value.map { persona ->
                    PersonaToolEnablementSnapshot(
                        personaId = persona.id,
                        enabled = persona.enabled,
                        enabledTools = persona.enabledTools.toSet(),
                    )
                }

            override fun snapshotToolEnablement(personaId: String): PersonaToolEnablementSnapshot? =
                snapshotToolEnablement().firstOrNull { it.personaId == personaId }

            override suspend fun add(profile: PersonaProfile) {
                personasFlow.value = personasFlow.value.filterNot { it.id == profile.id } + profile
            }

            override suspend fun update(profile: PersonaProfile) {
                personasFlow.value = personasFlow.value.map { persona ->
                    if (persona.id == profile.id) profile else persona
                }
            }

            override suspend fun toggleEnabled(id: String, enabled: Boolean) {
                personasFlow.value = personasFlow.value.map { persona ->
                    if (persona.id == id) persona.copy(enabled = enabled) else persona
                }
            }

            override suspend fun toggleEnabled(id: String) {
                personasFlow.value = personasFlow.value.map { persona ->
                    if (persona.id == id) persona.copy(enabled = !persona.enabled) else persona
                }
            }

            override suspend fun delete(id: String) {
                personasFlow.value = personasFlow.value.filterNot { it.id == id }
            }
        }

        val providerPort: ProviderRepositoryPort = object : ProviderRepositoryPort {
            override val providers: StateFlow<List<ProviderProfile>> = providersFlow

            override fun snapshotProfiles(): List<ProviderProfile> = providersFlow.value.map { provider ->
                provider.copy(
                    capabilities = provider.capabilities.toSet(),
                    ttsVoiceOptions = provider.ttsVoiceOptions.toList(),
                )
            }

            override fun providersWithCapability(capability: ProviderCapability): List<ProviderProfile> =
                providersFlow.value.filter { capability in it.capabilities }

            override fun toggleEnabled(id: String) {
                providersFlow.value = providersFlow.value.map { provider ->
                    if (provider.id == id) provider.copy(enabled = !provider.enabled) else provider
                }
            }

            override fun updateMultimodalProbeSupport(id: String, support: FeatureSupportState) {
                updateProvider(id) { provider -> provider.copy(multimodalProbeSupport = support) }
            }

            override fun updateNativeStreamingProbeSupport(id: String, support: FeatureSupportState) {
                updateProvider(id) { provider -> provider.copy(nativeStreamingProbeSupport = support) }
            }

            override fun updateSttProbeSupport(id: String, support: FeatureSupportState) {
                updateProvider(id) { provider -> provider.copy(sttProbeSupport = support) }
            }

            override fun updateTtsProbeSupport(id: String, support: FeatureSupportState) {
                updateProvider(id) { provider -> provider.copy(ttsProbeSupport = support) }
            }

            override suspend fun save(profile: ProviderProfile) {
                providersFlow.value = providersFlow.value.filterNot { it.id == profile.id } + profile
            }

            override suspend fun delete(id: String) {
                providersFlow.value = providersFlow.value.filterNot { it.id == id }
            }

            private fun updateProvider(
                id: String,
                transform: (ProviderProfile) -> ProviderProfile,
            ) {
                providersFlow.value = providersFlow.value.map { provider ->
                    if (provider.id == id) transform(provider) else provider
                }
            }
        }

        val conversationPort: QqConversationPort = object : QqConversationPort {
            override fun sessions(): List<ConversationSession> = sessionsFlow.value.map { session ->
                session.copy(
                    messages = session.messages.map { message ->
                        message.copy(attachments = message.attachments.map { it.copy() })
                    },
                )
            }

            override fun resolveOrCreateSession(
                sessionId: String,
                title: String,
                messageType: MessageType,
            ): ConversationSession {
                val existing = sessionsFlow.value.firstOrNull { it.id == sessionId }
                if (existing == null) {
                    return createSession(sessionId = sessionId, title = title, messageType = messageType)
                }
                if (existing.title != title && !existing.titleCustomized) {
                    val updated = existing.copy(title = title)
                    sessionsFlow.value = sessionsFlow.value.map { session ->
                        if (session.id == sessionId) updated else session
                    }
                    return updated
                }
                return existing
            }

            override fun session(sessionId: String): ConversationSession {
                return sessionsFlow.value.firstOrNull { it.id == sessionId }
                    ?: createSession(sessionId, title = "新对话", messageType = MessageType.FriendMessage)
            }

            override fun appendMessage(
                sessionId: String,
                role: String,
                content: String,
                attachments: List<ConversationAttachment>,
            ): String {
                val session = session(sessionId)
                val message = ConversationMessage(
                    id = UUID.randomUUID().toString(),
                    role = role,
                    content = content,
                    timestamp = System.currentTimeMillis(),
                    attachments = attachments,
                )
                sessionsFlow.value = sessionsFlow.value.map { existing ->
                    if (existing.id == session.id) existing.copy(messages = existing.messages + message) else existing
                }
                return message.id
            }

            override fun updateSessionBindings(
                sessionId: String,
                botId: String,
                providerId: String,
                personaId: String,
            ) {
                updateSession(sessionId) { session ->
                    session.copy(
                        botId = botId,
                        providerId = providerId,
                        personaId = personaId,
                    )
                }
            }

            override fun updateSessionServiceFlags(
                sessionId: String,
                sessionSttEnabled: Boolean?,
                sessionTtsEnabled: Boolean?,
            ) {
                updateSession(sessionId) { session ->
                    session.copy(
                        sessionSttEnabled = sessionSttEnabled ?: session.sessionSttEnabled,
                        sessionTtsEnabled = sessionTtsEnabled ?: session.sessionTtsEnabled,
                    )
                }
            }

            override fun replaceMessages(
                sessionId: String,
                messages: List<ConversationMessage>,
            ) {
                updateSession(sessionId) { session ->
                    session.copy(messages = messages)
                }
            }

            override fun renameSession(sessionId: String, title: String) {
                updateSession(sessionId) { session ->
                    session.copy(title = title, titleCustomized = true)
                }
            }

            override fun deleteSession(sessionId: String) {
                sessionsFlow.value = sessionsFlow.value.filterNot { it.id == sessionId }
            }

            private fun createSession(
                sessionId: String,
                title: String,
                messageType: MessageType,
            ): ConversationSession {
                val created = ConversationSession(
                    id = sessionId,
                    title = title,
                    botId = botPort.currentBot().id,
                    personaId = "",
                    providerId = "",
                    messageType = messageType,
                    maxContextMessages = 12,
                    messages = emptyList(),
                )
                sessionsFlow.value = sessionsFlow.value + created
                return created
            }

            private fun updateSession(
                sessionId: String,
                transform: (ConversationSession) -> ConversationSession,
            ) {
                val target = session(sessionId)
                sessionsFlow.value = sessionsFlow.value.map { session ->
                    if (session.id == target.id) transform(target) else session
                }
            }
        }

        val platformConfigPort: QqPlatformConfigPort = object : QqPlatformConfigPort {
            override fun resolveQqBot(selfId: String): BotProfile? {
                val cleanedSelfId = selfId.trim()
                val enabledBots = botsFlow.value.filter { candidate ->
                    candidate.platformName.equals("QQ", ignoreCase = true) && candidate.autoReplyEnabled
                }
                if (cleanedSelfId.isBlank()) {
                    return enabledBots.firstOrNull()
                }
                return enabledBots.firstOrNull { candidate ->
                    candidate.id == selectedBotIdFlow.value && candidate.boundQqUins.contains(cleanedSelfId)
                } ?: enabledBots.firstOrNull { candidate ->
                    candidate.boundQqUins.contains(cleanedSelfId)
                } ?: enabledBots.firstOrNull()
            }

            override fun qqReplyQuoteEnabled(botId: String): Boolean =
                resolveConfigForBot(botId)?.quoteSenderMessageEnabled == true

            override fun qqReplyMentionEnabled(botId: String): Boolean =
                resolveConfigForBot(botId)?.mentionSenderEnabled == true

            override fun qqAutoReplyEnabled(botId: String): Boolean =
                botsFlow.value.firstOrNull { it.id == botId }?.autoReplyEnabled == true

            override fun qqWakeWords(botId: String): List<String> {
                val botProfile = botsFlow.value.firstOrNull { it.id == botId } ?: return emptyList()
                val configProfile = resolveConfigForBot(botId)
                return (botProfile.triggerWords + (configProfile?.wakeWords ?: emptyList())).distinct()
            }

            override fun qqWhitelist(botId: String, messageType: MessageType): List<String> =
                resolveConfigForBot(botId)?.whitelistEntries ?: emptyList()

            override fun qqWhitelistEnabled(botId: String): Boolean =
                resolveConfigForBot(botId)?.whitelistEnabled == true

            override fun qqRateLimitWindow(botId: String): Int =
                resolveConfigForBot(botId)?.rateLimitWindowSeconds ?: 0

            override fun qqRateLimitMaxCount(botId: String): Int =
                resolveConfigForBot(botId)?.rateLimitMaxCount ?: 0

            override fun qqRateLimitStrategy(botId: String): String =
                resolveConfigForBot(botId)?.rateLimitStrategy.orEmpty()

            override fun qqIsolateGroupUser(botId: String): Boolean =
                resolveConfigForBot(botId)?.sessionIsolationEnabled == true

            private fun resolveConfigForBot(botId: String): ConfigProfile? {
                val botProfile = botsFlow.value.firstOrNull { it.id == botId } ?: return null
                return configsFlow.value.firstOrNull { it.id == botProfile.configProfileId }
            }
        }

        fun compatibilitySnapshotForConfig(profile: ConfigProfile) =
            ResourceCenterCompatibility.projectionsFromConfigProfile(profile)
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

package com.astrbot.android.runtime

import com.astrbot.android.data.BotRepository
import com.astrbot.android.data.ChatCompletionService
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
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.runtime.plugin.DefaultAppChatPluginRuntime
import com.astrbot.android.runtime.plugin.InMemoryPluginFailureStateStore
import com.astrbot.android.runtime.plugin.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import com.astrbot.android.runtime.plugin.executionContextFor
import com.astrbot.android.runtime.plugin.runtimePlugin
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OneBotBridgeServerTest {
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
        try {
            BotRepository.restoreProfiles(listOf(bot), bot.id)
            ConfigRepository.restoreProfiles(listOf(config), config.id)
            ProviderRepository.restoreProfiles(providers)
            ConversationRepository.restoreSessions(emptyList())
            block()
        } finally {
            ChatCompletionService.setHttpClientOverrideForTests(null)
            PluginRuntimeRegistry.reset()
            PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(null)
            BotRepository.restoreProfiles(botSnapshot, selectedBotIdSnapshot)
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigIdSnapshot)
            ProviderRepository.restoreProfiles(providerSnapshot)
            ConversationRepository.restoreSessions(sessionSnapshot)
        }
    }

    private suspend fun invokeHandlePayload(payload: String) {
        val method = OneBotBridgeServer::class.java.getDeclaredMethod(
            "handlePayload",
            String::class.java,
            Continuation::class.java,
        )
        method.isAccessible = true
        suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
            val result = method.invoke(OneBotBridgeServer, payload, continuation)
            if (result === COROUTINE_SUSPENDED) {
                COROUTINE_SUSPENDED
            } else {
                Unit
            }
        }
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

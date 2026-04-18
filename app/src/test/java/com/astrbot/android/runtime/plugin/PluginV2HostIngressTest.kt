package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.data.BotRepository
import com.astrbot.android.core.runtime.llm.ChatCompletionService
import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.data.ProviderRepository
import com.astrbot.android.data.http.AstrBotHttpClient
import com.astrbot.android.data.http.HttpRequestSpec
import com.astrbot.android.data.http.HttpResponsePayload
import com.astrbot.android.data.http.MultipartPartSpec
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.AppChatLlm
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginBotSummary
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginRuntimeLogLevel
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.runtime.IncomingMessageEvent
import com.astrbot.android.feature.qq.runtime.QqOneBotBridgeServer
import com.astrbot.android.feature.qq.runtime.OneBotSendResult
import com.astrbot.android.core.common.logging.RuntimeLogRepository
import com.astrbot.android.feature.qq.runtime.QqSessionKeyFactory
import java.nio.file.Files
import java.util.AbstractMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2HostIngressTest {

    @Test
    fun default_app_chat_before_send_message_keeps_legacy_dispatcher_path_without_v2_ingress() {
        val v2MessageEvents = CopyOnWriteArrayList<PluginMessageEvent>()
        val legacyRuns = AtomicInteger(0)
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            v2EngineWithHandlers(
                onMessage = { event ->
                    v2MessageEvents += event
                },
            ),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "legacy-supported",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    legacyRuns.incrementAndGet()
                    NoOp("legacy")
                },
                runtimePlugin(
                    pluginId = "legacy-unsupported",
                    supportedTriggers = setOf(PluginTriggerSource.OnCommand),
                ) {
                    NoOp("unsupported")
                },
            )
        }

        try {
            val batch = DefaultAppChatPluginRuntime.execute(
                trigger = PluginTriggerSource.BeforeSendMessage,
                contextFactory = { plugin ->
                    val base = executionContextFor(
                        plugin = plugin,
                        trigger = PluginTriggerSource.BeforeSendMessage,
                    )
                    base.copy(
                        message = base.message.copy(
                            contentPreview = "hello-from-app",
                        ),
                        triggerMetadata = base.triggerMetadata.copy(
                            eventId = "evt-app-before-send",
                        ),
                    )
                },
            )

            assertTrue(v2MessageEvents.isEmpty())
            assertEquals(1, legacyRuns.get())
            assertEquals(
                PluginDispatchSkipReason.UnsupportedTrigger,
                batch.skipped
                    .single { skipped -> skipped.plugin.pluginId == "legacy-unsupported" }
                    .reason,
            )
        } finally {
            PluginRuntimeRegistry.reset()
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
        }
    }

    @Test
    fun default_app_chat_before_send_message_does_not_consult_v2_stop_propagation() {
        val legacyRuns = AtomicInteger(0)
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            v2EngineWithHandlers(
                onMessage = { event ->
                    event.stopPropagation()
                },
            ),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "legacy-should-not-run",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    legacyRuns.incrementAndGet()
                    NoOp("legacy")
                },
            )
        }

        try {
            val batch = DefaultAppChatPluginRuntime.execute(
                trigger = PluginTriggerSource.BeforeSendMessage,
                contextFactory = { plugin ->
                    executionContextFor(
                        plugin = plugin,
                        trigger = PluginTriggerSource.BeforeSendMessage,
                    )
                },
            )

            assertEquals(1, legacyRuns.get())
            assertEquals(1, batch.outcomes.size)
        } finally {
            PluginRuntimeRegistry.reset()
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
        }
    }

    @Test
    fun onebot_before_send_v2_stop_propagation_returns_terminal_result_without_legacy_fallback() {
        val legacyRuns = AtomicInteger(0)
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            v2EngineWithHandlers(
                onMessage = { event ->
                    event.stopPropagation()
                },
            ),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "legacy-onebot-stop",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    legacyRuns.incrementAndGet()
                    NoOp("legacy")
                },
            )
        }

        try {
            val result = invokeExecuteQqPlugins(
                trigger = PluginTriggerSource.BeforeSendMessage,
                event = oneBotEvent(
                    messageId = "msg-stop",
                    text = "hello-onebot",
                ),
                contextFactory = { plugin ->
                    oneBotContext(
                        plugin = plugin,
                        trigger = PluginTriggerSource.BeforeSendMessage,
                        event = oneBotEvent(
                            messageId = "msg-stop",
                            text = "hello-onebot",
                        ),
                    )
                },
            )

            assertTrue(result?.propagationStopped == true)
            assertEquals(0, legacyRuns.get())
        } finally {
            PluginRuntimeRegistry.reset()
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
        }
    }

    @Test
    fun onebot_before_send_v2_custom_filter_error_stop_returns_terminal_result_without_legacy_fallback() {
        val legacyRuns = AtomicInteger(0)
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            v2EngineWithCustomFilterFailure(),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "legacy-onebot-filter",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    legacyRuns.incrementAndGet()
                    NoOp("legacy")
                },
            )
        }

        try {
            val result = invokeExecuteQqPlugins(
                trigger = PluginTriggerSource.BeforeSendMessage,
                event = oneBotEvent(
                    messageId = "msg-filter",
                    text = "hello-onebot",
                ),
                contextFactory = { plugin ->
                    oneBotContext(
                        plugin = plugin,
                        trigger = PluginTriggerSource.BeforeSendMessage,
                        event = oneBotEvent(
                            messageId = "msg-filter",
                            text = "hello-onebot",
                        ),
                    )
                },
            )

            assertTrue(result?.terminatedByCustomFilterFailure == true)
            assertTrue(result?.userVisibleFailureMessage?.isNotBlank() == true)
            assertEquals(0, legacyRuns.get())
        } finally {
            PluginRuntimeRegistry.reset()
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
        }
    }

    @Test
    fun onebot_plain_message_and_slash_command_both_materialize_v2_message_event() {
        val v2MessageEvents = CopyOnWriteArrayList<PluginMessageEvent>()
        val v2CommandEvents = CopyOnWriteArrayList<PluginCommandEvent>()
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            v2EngineWithHandlers(
                onMessage = { event ->
                    v2MessageEvents += event
                },
                onCommand = { event ->
                    v2CommandEvents += event
                },
            ),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "legacy-onebot-plugin",
                    supportedTriggers = setOf(
                        PluginTriggerSource.BeforeSendMessage,
                        PluginTriggerSource.OnCommand,
                    ),
                ) {
                    NoOp("legacy")
                },
            )
        }

        try {
            val plainEvent = oneBotEvent(
                messageId = "msg-plain",
                text = "hello-onebot",
            )
            invokeExecuteQqPlugins(
                trigger = PluginTriggerSource.BeforeSendMessage,
                event = plainEvent,
                contextFactory = { plugin ->
                    oneBotContext(
                        plugin = plugin,
                        trigger = PluginTriggerSource.BeforeSendMessage,
                        event = plainEvent,
                    )
                },
            )

            val slashEvent = oneBotEvent(
                messageId = "msg-command",
                text = "/echo host-ingress",
            )
            val bot = BotProfile(
                id = "qq-main",
                configProfileId = "config-main",
                displayName = "Host Bot",
            )
            val session = ConversationSession(
                id = "qq-session",
                title = "QQ Session",
                botId = bot.id,
                personaId = "",
                providerId = "",
                platformId = "qq",
                messageType = MessageType.GroupMessage,
                originSessionId = "group:30003",
                maxContextMessages = 12,
                messages = emptyList(),
            )
            invokeHandlePluginCommand(
                event = slashEvent,
                bot = bot,
                config = ConfigProfile(id = bot.configProfileId),
                sessionId = session.id,
                session = session,
            )

            assertEquals(
                listOf("hello-onebot", "/echo host-ingress"),
                v2MessageEvents.map { event -> event.rawText },
            )
            assertTrue(v2MessageEvents.all { event -> event.platformAdapterType == "onebot" })
            assertEquals(1, v2CommandEvents.size)
            assertEquals("echo", v2CommandEvents.single().commandPath.last())
        } finally {
            PluginRuntimeRegistry.reset()
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
        }
    }

    @Test
    fun onebot_slash_command_consumes_v2_command_reply_without_legacy_unsupported_fallback() = runBlocking {
        val replies = CopyOnWriteArrayList<String>()
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, text, _ ->
                replies += text
                OneBotSendResult.success(receiptIds = listOf("receipt-v2-command"))
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(
                v2EngineWithHandlers(
                    onCommand = { event ->
                        val replyText = PluginCommandEvent::class.java.methods.firstOrNull { method ->
                            method.name == "replyText" &&
                                method.parameterTypes.contentEquals(arrayOf(String::class.java))
                        } ?: error("replyText method missing")
                        replyText.invoke(event, "v2 command reply")
                    },
                ),
            )
            RuntimeLogRepository.clear()

            val handled = invokeHandlePluginCommand(
                event = oneBotEvent(
                    messageId = "msg-command-v2-reply",
                    text = "/echo hello",
                ),
                bot = defaultBot(),
                config = defaultConfig(textStreamingEnabled = false),
                sessionId = "qq-session",
                session = ConversationSession(
                    id = "qq-session",
                    title = "QQ Session",
                    botId = defaultBot().id,
                    personaId = "",
                    providerId = "",
                    platformId = "qq",
                    messageType = MessageType.GroupMessage,
                    originSessionId = "group:30003",
                    maxContextMessages = 12,
                    messages = emptyList(),
                ),
            )

            assertTrue(handled)
            assertEquals(listOf("v2 command reply"), replies.toList())
            val logs = RuntimeLogRepository.logs.value
            assertTrue(logs.none { it.contains("Bot command unsupported after plugin fallback") })
        }
    }

    @Test
    fun onebot_slash_command_consumes_v2_command_attachment_reply_from_plugin_assets() = runBlocking {
        val replyAttachments = CopyOnWriteArrayList<List<ConversationAttachment>>()
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, attachments ->
                replyAttachments += attachments
                OneBotSendResult.success(receiptIds = listOf("receipt-v2-command-attachment"))
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(
                v2EngineWithHandlers(
                    onCommand = { event ->
                        val replyResult = PluginCommandEvent::class.java.methods.firstOrNull { method ->
                            method.name == "replyResult" &&
                                method.parameterTypes.size == 1
                        } ?: error("replyResult method missing")
                        replyResult.invoke(
                            event,
                            linkedMapOf(
                                "text" to "preview",
                                "attachments" to listOf(
                                    linkedMapOf(
                                        "assetPath" to "resources/memes/happy/demo.jpg",
                                        "mimeType" to "image/jpeg",
                                        "label" to "happy",
                                    ),
                                ),
                            ),
                        )
                    },
                ),
            )

            val handled = invokeHandlePluginCommand(
                event = oneBotEvent(
                    messageId = "msg-command-v2-attachment",
                    text = "/echo happy",
                ),
                bot = defaultBot(),
                config = defaultConfig(textStreamingEnabled = false),
                sessionId = "qq-session",
                session = ConversationSession(
                    id = "qq-session",
                    title = "QQ Session",
                    botId = defaultBot().id,
                    personaId = "",
                    providerId = "",
                    platformId = "qq",
                    messageType = MessageType.GroupMessage,
                    originSessionId = "group:30003",
                    maxContextMessages = 12,
                    messages = emptyList(),
                ),
            )

            assertTrue(handled)
            assertEquals(1, replyAttachments.size)
            assertEquals(1, replyAttachments.single().size)
            assertTrue(replyAttachments.single().single().remoteUrl.replace('\\', '/').endsWith("/resources/memes/happy/demo.jpg"))
        }
    }

    @Test
    fun onebot_v2_dispatch_failure_is_contained_and_legacy_fallback_continues() {
        val legacyRuns = AtomicInteger(0)
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            v2EngineWithDispatchFailure(),
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "legacy-onebot-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    legacyRuns.incrementAndGet()
                    NoOp("legacy")
                },
            )
        }

        try {
            invokeExecuteQqPlugins(
                trigger = PluginTriggerSource.BeforeSendMessage,
                event = oneBotEvent(
                    messageId = "msg-regex-failure",
                    text = "hello-onebot",
                ),
                contextFactory = { plugin ->
                    oneBotContext(
                        plugin = plugin,
                        trigger = PluginTriggerSource.BeforeSendMessage,
                        event = oneBotEvent(
                            messageId = "msg-regex-failure",
                            text = "hello-onebot",
                        ),
                    )
                },
            )

            assertEquals(1, legacyRuns.get())
        } finally {
            PluginRuntimeRegistry.reset()
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
        }
    }

    @Test
    fun external_runtime_catalog_keeps_skipping_v2_records_for_legacy_message_path() {
        val extractedDir = Files.createTempDirectory("host-ingress-external-catalog").toFile()
        try {
            val v2Record = samplePluginV2InstallRecord(
                pluginId = "com.astrbot.samples.host_ingress_v2",
            )
            val legacyRecord = createQuickJsExternalPluginInstallRecord(
                extractedDir = extractedDir,
                pluginId = "com.astrbot.samples.host_ingress_legacy",
                supportedTriggers = listOf("before_send_message"),
            )

            val plugins = ExternalPluginRuntimeCatalog.plugins(
                records = listOf(v2Record, legacyRecord),
            )

            assertEquals(
                listOf("com.astrbot.samples.host_ingress_legacy"),
                plugins.map { plugin -> plugin.pluginId },
            )
        } finally {
            extractedDir.deleteRecursively()
        }
    }

    @Test
    fun onebot_enters_llm_coordinator_only_when_ingress_allows_app_chat_path() = runBlocking {
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                buildPipelineResult(
                    input = input,
                    shouldSend = false,
                    text = "suppressed",
                )
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                OneBotSendResult.success()
            }

            PluginV2DispatchEngineProvider.setEngineOverrideForTests(
                v2EngineWithHandlers(
                    onMessage = { event ->
                        event.stopPropagation()
                    },
                ),
            )
            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-ingress-stop", text = "astrbot hello ingress stop"))
            assertEquals(0, runtime.preSendCalls.get())

            PluginV2DispatchEngineProvider.setEngineOverrideForTests(
                v2EngineWithHandlers(),
            )
            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-ingress-pass", text = "astrbot hello ingress pass"))
            assertEquals(1, runtime.preSendCalls.get())
        }
    }

    @Test
    fun onebot_llm_ingress_uses_host_session_identity_for_conversation_id() = runBlocking {
        val capturedConversationId = AtomicReference<String>()
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                capturedConversationId.set(input.event.conversationId)
                buildPipelineResult(
                    input = input,
                    shouldSend = false,
                    text = "identity-check",
                )
            },
        )
        val bot = defaultBot()
        val config = defaultConfig(
            textStreamingEnabled = false,
            sessionIsolationEnabled = true,
        )
        withOneBotState(
            bot = bot,
            config = config,
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                OneBotSendResult.success()
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-session-identity", text = "astrbot hello identity"))

            val sessionId = QqSessionKeyFactory.build(
                botId = bot.id,
                messageType = MessageType.GroupMessage,
                groupId = "30003",
                userId = "20002",
                isolated = true,
            )
            val expectedConversationId = ConversationRepository
                .session(sessionId)
                .originSessionId
                .ifBlank { sessionId }

            assertEquals(expectedConversationId, capturedConversationId.get())
            assertEquals("group:30003:user:20002", capturedConversationId.get())
            assertFalse(capturedConversationId.get() == "30003")
        }
    }

    @Test
    fun onebot_llm_event_extras_include_runtime_target_context_for_host_tools() = runBlocking {
        val capturedExtras = AtomicReference<Map<String, *>?>()
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                capturedExtras.set(input.event.extras)
                buildPipelineResult(
                    input = input,
                    shouldSend = false,
                    text = "context-check",
                )
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                OneBotSendResult.success()
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(
                oneBotMessagePayload(
                    messageId = "msg-host-tool-context",
                    text = "astrbot 五分钟后提醒我喝水",
                ),
            )

            val extras = requireNotNull(capturedExtras.get())
            assertEquals("qq_runtime", extras["source"])
            assertEquals(PluginTriggerSource.BeforeSendMessage.wireValue, extras["trigger"])
            assertEquals("qq-main", extras["botId"])
            assertEquals("config-qq", extras["configProfileId"])
            assertEquals("provider-1", extras["providerId"])
        }
    }

    @Test
    fun onebot_after_sent_view_uses_same_public_conversation_id_as_event() = runBlocking {
        val capturedEventConversationId = AtomicReference<String>()
        val capturedViewConversationId = AtomicReference<String>()
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = "conversation-domain-check",
                )
            },
            afterSent = { event, view ->
                capturedEventConversationId.set(event.conversationId)
                capturedViewConversationId.set(view.conversationId)
            },
        )
        val bot = defaultBot()
        val config = defaultConfig(
            textStreamingEnabled = false,
            sessionIsolationEnabled = true,
        )
        withOneBotState(
            bot = bot,
            config = config,
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                OneBotSendResult.success(receiptIds = listOf("receipt-domain"))
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-conversation-domain", text = "astrbot hello domain"))

            assertEquals("group:30003:user:20002", capturedEventConversationId.get())
            assertEquals(capturedEventConversationId.get(), capturedViewConversationId.get())
        }
    }

    @Test
    fun onebot_llm_main_path_skips_legacy_before_send_even_when_ingress_allows_app_chat() = runBlocking {
        val legacyRuns = AtomicInteger(0)
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                buildPipelineResult(
                    input = input,
                    shouldSend = false,
                    text = "suppressed",
                )
            },
        )
        PluginRuntimeRegistry.registerProvider {
            listOf(
                runtimePlugin(
                    pluginId = "legacy-before-send-should-not-run",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    legacyRuns.incrementAndGet()
                    NoOp("legacy")
                },
            )
        }
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                OneBotSendResult.success()
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-no-legacy-before-send", text = "astrbot hello v2 only"))

            assertEquals(0, legacyRuns.get())
            assertEquals(1, runtime.deliveredPipelineCalls.get())
        }
    }

    @Test
    fun should_send_false_skips_send_persist_success_snapshot_and_after_sent() = runBlocking {
        val sendAttempts = AtomicInteger(0)
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                buildPipelineResult(
                    input = input,
                    shouldSend = false,
                    text = "suppressed-by-plugin",
                )
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                sendAttempts.incrementAndGet()
                OneBotSendResult.success()
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-should-send-false", text = "astrbot hello suppress"))

            assertEquals(0, sendAttempts.get())
            assertEquals(0, runtime.afterSentCalls.get())
            val messages = ConversationRepository.session("qq-qq-main-group-30003").messages
            assertEquals(1, messages.size)
            assertEquals("user", messages.single().role)
        }
    }

    @Test
    fun send_success_persists_assistant_and_receipt_then_triggers_after_sent_once() = runBlocking {
        val sendAttempts = AtomicInteger(0)
        val assistantPersistedBeforeAfterSent = AtomicBoolean(false)
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = "coordinator-success",
                )
            },
            afterSent = { _, view ->
                assistantPersistedBeforeAfterSent.set(
                    ConversationRepository.session("qq-qq-main-group-30003").messages.any { message -> message.role == "assistant" },
                )
                assertEquals("group:30003", view.conversationId)
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                sendAttempts.incrementAndGet()
                OneBotSendResult.success(receiptIds = listOf("receipt-123"))
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-send-success", text = "astrbot hello success"))

            assertEquals(1, sendAttempts.get())
            assertEquals(1, runtime.afterSentCalls.get())
            assertTrue(assistantPersistedBeforeAfterSent.get())
            val sessionMessages = ConversationRepository.session("qq-qq-main-group-30003").messages
            assertTrue(sessionMessages.any { message -> message.role == "assistant" && message.content == "coordinator-success" })
            assertEquals(listOf("receipt-123"), runtime.afterSentViews.single().receiptIds)
        }
    }

    @Test
    fun send_failure_emits_llm_pipeline_failed_and_skips_after_sent() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 200L })
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = "send-failure-text",
                )
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            PluginRuntimeLogBusProvider.setBusOverrideForTests(logBus)
            try {
                QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
                QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                    OneBotSendResult.failure("send-boom")
                }
                PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

                invokeHandlePayload(oneBotMessagePayload(messageId = "msg-send-failure", text = "astrbot hello failure"))

                assertEquals(0, runtime.afterSentCalls.get())
                val sessionMessages = ConversationRepository.session("qq-qq-main-group-30003").messages
                assertFalse(sessionMessages.any { message -> message.role == "assistant" })
                assertTrue(logBus.snapshot(limit = 50).any { record -> record.code == "llm_pipeline_failed" })
            } finally {
                PluginRuntimeLogBusProvider.setBusOverrideForTests(null)
            }
        }
    }

    @Test
    fun non_streaming_and_streaming_use_same_send_persist_after_sent_order() = runBlocking {
        val nonStreamTrace = executeSingleRoundAndCaptureOrder(
            config = defaultConfig(textStreamingEnabled = false),
            provider = defaultChatProvider(),
            messageId = "msg-order-non-stream",
            text = "astrbot hello non stream",
        )
        val nativeStreamTrace = executeSingleRoundAndCaptureOrder(
            config = defaultConfig(textStreamingEnabled = true),
            provider = defaultChatProvider(),
            messageId = "msg-order-native-stream",
            text = "astrbot hello native stream",
        )

        assertEquals(listOf("send", "after_sent"), nonStreamTrace.map { entry -> entry.first })
        assertEquals(listOf("send", "after_sent"), nativeStreamTrace.map { entry -> entry.first })
        assertEquals(nonStreamTrace.map { entry -> entry.first }, nativeStreamTrace.map { entry -> entry.first })
        assertEquals(
            listOf(PluginV2StreamingMode.NON_STREAM, PluginV2StreamingMode.NON_STREAM),
            nonStreamTrace.map { entry -> entry.second },
        )
        assertEquals(
            listOf(PluginV2StreamingMode.NATIVE_STREAM, PluginV2StreamingMode.NATIVE_STREAM),
            nativeStreamTrace.map { entry -> entry.second },
        )
    }

    @Test
    fun onebot_pseudo_streaming_sends_aggregated_response_as_host_pseudo_segments() = runBlocking {
        val sentTexts = CopyOnWriteArrayList<String>()
        val capturedMode = AtomicReference<PluginV2StreamingMode>()
        val streamedText = "第一句来了，这里稍微长一点。第二句继续补充，方便观察分段。第三句收尾。"
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                capturedMode.set(input.streamingMode)
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = streamedText,
                )
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = true),
            providers = listOf(
                defaultChatProvider(
                    providerType = ProviderType.CUSTOM,
                    nativeStreamingRuleSupport = FeatureSupportState.UNSUPPORTED,
                ),
            ),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, text, _ ->
                sentTexts += text
                OneBotSendResult.success(receiptIds = listOf("receipt-${sentTexts.size}"))
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-pseudo-stream", text = "astrbot hello pseudo stream"))

            assertEquals(PluginV2StreamingMode.PSEUDO_STREAM, capturedMode.get())
            assertTrue(sentTexts.size > 1)
            assertFalse(sentTexts.any { text -> text == streamedText })
            assertEquals(1, runtime.afterSentCalls.get())
        }
    }

    @Test
    fun onebot_native_streaming_still_sends_host_segments_when_streaming_is_enabled() = runBlocking {
        val sentTexts = CopyOnWriteArrayList<String>()
        val capturedMode = AtomicReference<PluginV2StreamingMode>()
        val streamedText = "第一句来了，这里稍微长一点。第二句继续补充，方便观察分段。第三句收尾。"
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                capturedMode.set(input.streamingMode)
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = streamedText,
                )
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = true),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, text, _ ->
                sentTexts += text
                OneBotSendResult.success(receiptIds = listOf("receipt-${sentTexts.size}"))
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(oneBotMessagePayload(messageId = "msg-native-stream", text = "astrbot hello native stream"))

            assertEquals(PluginV2StreamingMode.NATIVE_STREAM, capturedMode.get())
            assertTrue(sentTexts.size > 1)
            assertFalse(sentTexts.any { text -> text == streamedText })
            assertEquals(1, runtime.afterSentCalls.get())
        }
    }

    @Test
    fun onebot_voice_streaming_sends_audio_segments_separately() = runBlocking {
        val sentAttachmentCounts = CopyOnWriteArrayList<Int>()
        val sentTexts = CopyOnWriteArrayList<String>()
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = "First voice segment! Second voice segment?",
                )
            },
        )
        val fakeHttpClient = object : AstrBotHttpClient {
            override fun execute(requestSpec: HttpRequestSpec): HttpResponsePayload {
                throw UnsupportedOperationException("HTTP text requests are not expected in this test")
            }

            override fun executeBytes(requestSpec: HttpRequestSpec): ByteArray {
                return byteArrayOf(1, 2, 3, 4)
            }

            override suspend fun executeStream(
                requestSpec: HttpRequestSpec,
                onLine: suspend (String) -> Unit,
            ) {
                throw UnsupportedOperationException("HTTP streaming requests are not expected in this test")
            }

            override fun executeMultipart(
                requestSpec: HttpRequestSpec,
                parts: List<MultipartPartSpec>,
            ): HttpResponsePayload {
                throw UnsupportedOperationException("Multipart requests are not expected in this test")
            }
        }
        ChatCompletionService.setHttpClientOverrideForTests(fakeHttpClient)
        try {
            withOneBotState(
                bot = defaultBot(),
                config = defaultConfig(
                    textStreamingEnabled = false,
                    ttsEnabled = true,
                    alwaysTtsEnabled = true,
                    defaultTtsProviderId = "provider-tts",
                    voiceStreamingEnabled = true,
                    ttsVoiceId = "voice-1",
                ),
                providers = listOf(
                    defaultChatProvider(),
                    defaultTtsProvider(),
                ),
            ) {
                QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
                QqOneBotBridgeServer.setReplySenderOverrideForTests { _, text, attachments ->
                    sentTexts += text
                    sentAttachmentCounts += attachments.size
                    OneBotSendResult.success(receiptIds = listOf("receipt-${sentAttachmentCounts.size}"))
                }
                PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

                invokeHandlePayload(oneBotMessagePayload(messageId = "msg-voice-stream", text = "astrbot hello voice stream"))

                assertTrue(sentAttachmentCounts.size > 1)
                assertTrue(sentAttachmentCounts.all { count -> count == 1 })
                assertTrue(sentTexts.all(String::isEmpty))
                assertEquals(1, runtime.afterSentCalls.get())
            }
        } finally {
            ChatCompletionService.setHttpClientOverrideForTests(null)
        }
    }

    @Test
    fun onebot_attachment_only_message_uses_non_blank_llm_input_snapshot() = runBlocking {
        val capturedWorkingText = AtomicReference<String>()
        val sentTexts = CopyOnWriteArrayList<String>()
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                capturedWorkingText.set(input.event.workingText)
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = "image handled",
                )
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, text, _ ->
                sentTexts += text
                OneBotSendResult.success(receiptIds = listOf("receipt-image"))
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

            invokeHandlePayload(oneBotImagePayload(messageId = "msg-image-only"))

            assertTrue(capturedWorkingText.get().isNotBlank())
            assertEquals(listOf("image handled"), sentTexts.toList())
            assertEquals(1, runtime.afterSentCalls.get())
        }
    }

    @Test
    fun after_sent_handler_error_only_emits_on_plugin_error_without_rolling_back_sent_message() = runBlocking {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 300L })
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = "after-sent-error-text",
                )
            },
            afterSent = { _, _ ->
                error("after-sent-handler-boom")
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = defaultConfig(textStreamingEnabled = false),
            providers = listOf(defaultChatProvider()),
        ) {
            PluginV2LifecycleManagerProvider.manager()
            PluginRuntimeLogBusProvider.setBusOverrideForTests(logBus)
            try {
                PluginV2LifecycleManagerProvider.setManagerOverrideForTests(
                    PluginV2LifecycleManager(logBus = logBus),
                )
                QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
                QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                    OneBotSendResult.success(receiptIds = listOf("receipt-after-sent"))
                }
                PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())

                invokeHandlePayload(oneBotMessagePayload(messageId = "msg-after-sent-error", text = "astrbot hello after sent"))

                val sessionMessages = ConversationRepository.session("qq-qq-main-group-30003").messages
                assertTrue(sessionMessages.any { message -> message.role == "assistant" && message.content == "after-sent-error-text" })
                val records = logBus.snapshot(limit = 80)
                assertTrue(records.any { record -> record.code == "plugin_error_hook_emitted" })
                assertFalse(records.any { record -> record.code == "llm_pipeline_failed" })
            } finally {
                PluginRuntimeLogBusProvider.setBusOverrideForTests(null)
            }
        }
    }

    private fun invokeExecuteQqPlugins(
        trigger: PluginTriggerSource,
        event: IncomingMessageEvent,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginV2MessageDispatchResult? {
        val method = QqOneBotBridgeServer::class.java.getDeclaredMethod(
            "executeQqPlugins",
            PluginTriggerSource::class.java,
            IncomingMessageEvent::class.java,
            Function1::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(QqOneBotBridgeServer, trigger, event, contextFactory) as PluginV2MessageDispatchResult?
    }

    private fun invokeHandlePluginCommand(
        event: IncomingMessageEvent,
        bot: BotProfile,
        config: ConfigProfile,
        sessionId: String,
        session: ConversationSession,
    ): Boolean {
        val method = QqOneBotBridgeServer::class.java.getDeclaredMethod(
            "handlePluginCommand",
            IncomingMessageEvent::class.java,
            BotProfile::class.java,
            ConfigProfile::class.java,
            String::class.java,
            ConversationSession::class.java,
            com.astrbot.android.model.PersonaProfile::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(
            QqOneBotBridgeServer,
            event,
            bot,
            config,
            sessionId,
            session,
            null,
        ) as Boolean
    }

    private fun oneBotContext(
        plugin: PluginRuntimePlugin,
        trigger: PluginTriggerSource,
        event: IncomingMessageEvent,
    ): PluginExecutionContext {
        return PluginExecutionContext(
            trigger = trigger,
            pluginId = plugin.pluginId,
            pluginVersion = plugin.pluginVersion,
            sessionRef = MessageSessionRef(
                platformId = "qq",
                messageType = event.messageType,
                originSessionId = if (event.groupId.isNotBlank()) "group:${event.groupId}" else "private:${event.userId}",
            ),
            message = PluginMessageSummary(
                messageId = event.messageId,
                contentPreview = event.text,
                senderId = event.userId,
                messageType = event.messageType.wireValue,
                attachmentCount = event.attachments.size,
                timestamp = 1_710_000_000_000L,
            ),
            bot = PluginBotSummary(
                botId = "qq-main",
                displayName = "QQ Host",
                platformId = "qq",
            ),
            config = PluginConfigSummary(
                providerId = "",
                modelId = "",
                personaId = "",
            ),
            triggerMetadata = PluginTriggerMetadata(
                eventId = "evt-${event.messageId}",
                extras = mapOf(
                    "source" to "qq_runtime",
                    "messageId" to event.messageId,
                ),
            ),
        )
    }

    private fun oneBotEvent(
        messageId: String,
        text: String,
    ): IncomingMessageEvent {
        return IncomingMessageEvent(
            selfId = "10001",
            userId = "20002",
            groupId = "30003",
            messageId = messageId,
            messageType = MessageType.GroupMessage,
            text = text,
            promptContent = text,
            mentionsSelf = false,
            mentionsAll = false,
            attachments = emptyList(),
            senderName = "tester",
        )
    }

    private suspend fun invokeHandlePayload(payload: String) {
        val method = QqOneBotBridgeServer::class.java.getDeclaredMethod(
            "handlePayload",
            String::class.java,
            Continuation::class.java,
        )
        method.isAccessible = true
        suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
            val result = method.invoke(QqOneBotBridgeServer, payload, continuation)
            if (result === COROUTINE_SUSPENDED) {
                COROUTINE_SUSPENDED
            } else {
                Unit
            }
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
        val runtimeLogsSnapshot = RuntimeLogRepository.logs.value
        try {
            PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(
                InMemoryPluginScopedFailureStateStore(),
            )
            BotRepository.restoreProfiles(listOf(bot), bot.id)
            ConfigRepository.restoreProfiles(listOf(config), config.id)
            ProviderRepository.restoreProfiles(providers)
            ConversationRepository.restoreSessions(emptyList())
            RuntimeLogRepository.clear()
            block()
        } finally {
            QqOneBotBridgeServer.setReplySenderOverrideForTests(null)
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(null)
            AppChatPluginRuntimeCoordinatorProvider.setCoordinatorOverrideForTests(null)
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
            PluginRuntimeRegistry.reset()
            PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(null)
            PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(null)
            PluginV2LifecycleManagerProvider.setManagerOverrideForTests(null)
            BotRepository.restoreProfiles(botSnapshot, selectedBotIdSnapshot)
            ConfigRepository.restoreProfiles(configSnapshot, selectedConfigIdSnapshot)
            ProviderRepository.restoreProfiles(providerSnapshot)
            ConversationRepository.restoreSessions(sessionSnapshot)
            RuntimeLogRepository.clear()
            runtimeLogsSnapshot.forEach(RuntimeLogRepository::append)
        }
    }

    private fun oneBotMessagePayload(
        messageId: String,
        text: String,
    ): String {
        return """
            {
              "post_type": "message",
              "message_type": "group",
              "self_id": "10001",
              "user_id": "20002",
              "group_id": "30003",
              "message_id": "$messageId",
              "raw_message": "$text"
            }
        """.trimIndent()
    }

    private fun oneBotImagePayload(messageId: String): String {
        return """
            {
              "post_type": "message",
              "message_type": "private",
              "self_id": "10001",
              "user_id": "20002",
              "message_id": "$messageId",
              "message": [
                { "type": "image", "data": { "file": "image.jpg", "url": "https://example.com/image.jpg" } }
              ]
            }
        """.trimIndent()
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
        textStreamingEnabled: Boolean,
        sessionIsolationEnabled: Boolean = false,
        ttsEnabled: Boolean = false,
        alwaysTtsEnabled: Boolean = false,
        defaultTtsProviderId: String = "",
        voiceStreamingEnabled: Boolean = false,
        ttsVoiceId: String = "",
    ): ConfigProfile {
        return ConfigProfile(
            id = "config-qq",
            defaultChatProviderId = "provider-1",
            replyOnAtOnlyEnabled = false,
            keywordDetectionEnabled = false,
            textStreamingEnabled = textStreamingEnabled,
            sessionIsolationEnabled = sessionIsolationEnabled,
            ttsEnabled = ttsEnabled,
            alwaysTtsEnabled = alwaysTtsEnabled,
            defaultTtsProviderId = defaultTtsProviderId,
            voiceStreamingEnabled = voiceStreamingEnabled,
            ttsVoiceId = ttsVoiceId,
        )
    }

    private fun defaultChatProvider(
        providerType: ProviderType = ProviderType.OPENAI_COMPATIBLE,
        nativeStreamingRuleSupport: FeatureSupportState = FeatureSupportState.SUPPORTED,
    ): ProviderProfile {
        return ProviderProfile(
            id = "provider-1",
            name = "Test Chat",
            baseUrl = "https://example.com",
            model = "gpt-test",
            providerType = providerType,
            apiKey = "test-key",
            capabilities = setOf(ProviderCapability.CHAT),
            enabled = true,
            nativeStreamingRuleSupport = nativeStreamingRuleSupport,
        )
    }

    private fun defaultTtsProvider(): ProviderProfile {
        return ProviderProfile(
            id = "provider-tts",
            name = "Test TTS",
            baseUrl = "https://example.com",
            model = "tts-1",
            providerType = ProviderType.OPENAI_TTS,
            apiKey = "test-key",
            capabilities = setOf(ProviderCapability.TTS),
            enabled = true,
        )
    }

    private suspend fun executeSingleRoundAndCaptureOrder(
        config: ConfigProfile,
        provider: ProviderProfile,
        messageId: String,
        text: String,
    ): List<Pair<String, PluginV2StreamingMode>> {
        val order = mutableListOf<Pair<String, PluginV2StreamingMode>>()
        val modeRef = AtomicReference(PluginV2StreamingMode.NON_STREAM)
        val runtime = RecordingAppChatPluginRuntime(
            preSend = { input ->
                modeRef.set(input.streamingMode)
                buildPipelineResult(
                    input = input,
                    shouldSend = true,
                    text = "order-check",
                )
            },
            afterSent = { _, _ ->
                val hasAssistant = ConversationRepository.session("qq-qq-main-group-30003").messages.any { it.role == "assistant" }
                assertTrue(hasAssistant)
                order += "after_sent" to modeRef.get()
            },
        )
        withOneBotState(
            bot = defaultBot(),
            config = config,
            providers = listOf(provider),
        ) {
            QqOneBotBridgeServer.setAppChatPluginRuntimeOverrideForTests(runtime)
            QqOneBotBridgeServer.setReplySenderOverrideForTests { _, _, _ ->
                val hasAssistantBeforeSend = ConversationRepository
                    .session("qq-qq-main-group-30003")
                    .messages
                    .any { it.role == "assistant" }
                assertFalse(hasAssistantBeforeSend)
                order += "send" to modeRef.get()
                OneBotSendResult.success(receiptIds = listOf("receipt-order"))
            }
            PluginV2DispatchEngineProvider.setEngineOverrideForTests(v2EngineWithHandlers())
            invokeHandlePayload(oneBotMessagePayload(messageId = messageId, text = text))
            assertEquals(1, runtime.afterSentCalls.get())
        }
        return order.toList()
    }

    private fun buildPipelineResult(
        input: PluginV2LlmPipelineInput,
        shouldSend: Boolean,
        text: String,
    ): PluginV2LlmPipelineResult {
        val admission = LlmPipelineAdmission(
            requestId = "req-${input.event.eventId}",
            conversationId = input.event.conversationId,
            messageIds = input.messageIds,
            llmInputSnapshot = input.event.workingText,
            routingTarget = AppChatLlm.AppChat,
            streamingMode = input.streamingMode,
        )
        val finalRequest = PluginProviderRequest(
            requestId = admission.requestId,
            availableProviderIds = input.availableProviderIds,
            availableModelIdsByProvider = input.availableModelIdsByProvider,
            conversationId = admission.conversationId,
            messageIds = input.messageIds,
            llmInputSnapshot = admission.llmInputSnapshot,
            selectedProviderId = input.selectedProviderId,
            selectedModelId = input.selectedModelId,
            systemPrompt = input.systemPrompt,
            messages = input.messages,
            temperature = input.temperature,
            topP = input.topP,
            maxTokens = input.maxTokens,
            streamingEnabled = input.streamingEnabled,
            metadata = input.metadata,
        )
        val response = PluginLlmResponse(
            requestId = admission.requestId,
            providerId = finalRequest.selectedProviderId.ifBlank { input.availableProviderIds.firstOrNull().orEmpty() },
            modelId = finalRequest.selectedModelId.ifBlank {
                input.availableModelIdsByProvider[finalRequest.selectedProviderId].orEmpty().firstOrNull().orEmpty()
            },
            text = text,
        )
        val sendableResult = PluginMessageEventResult(
            requestId = admission.requestId,
            conversationId = admission.conversationId,
            text = text,
            markdown = false,
            attachments = emptyList(),
            shouldSend = shouldSend,
        )
        return PluginV2LlmPipelineResult(
            admission = admission,
            finalRequest = finalRequest,
            finalResponse = response,
            sendableResult = sendableResult,
            hookInvocationTrace = emptyList(),
            decoratingRunResult = PluginV2EventResultCoordinator.DecoratingRunResult(
                finalResult = sendableResult,
                appliedHandlerIds = emptyList(),
            ),
        )
    }

    private class RecordingAppChatPluginRuntime(
        private val preSend: suspend (PluginV2LlmPipelineInput) -> PluginV2LlmPipelineResult,
        private val afterSent: suspend (PluginMessageEvent, PluginV2AfterSentView) -> Unit = { _, _ -> },
    ) : AppChatPluginRuntime, AppChatLlmPipelineRuntime {
        val preSendCalls = AtomicInteger(0)
        val afterSentCalls = AtomicInteger(0)
        val deliveredPipelineCalls = AtomicInteger(0)
        val afterSentViews = CopyOnWriteArrayList<PluginV2AfterSentView>()

        override fun execute(
            trigger: PluginTriggerSource,
            contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
        ): PluginExecutionBatchResult {
            return PluginExecutionBatchResult(
                trigger = trigger,
                outcomes = emptyList(),
                skipped = emptyList(),
            )
        }

        override suspend fun runLlmPipeline(input: PluginV2LlmPipelineInput): PluginV2LlmPipelineResult {
            preSendCalls.incrementAndGet()
            return preSend(input)
        }

        override suspend fun dispatchAfterMessageSent(
            event: PluginMessageEvent,
            afterSentView: PluginV2AfterSentView,
        ): PluginV2LlmStageDispatchResult {
            afterSentCalls.incrementAndGet()
            afterSentViews += afterSentView
            afterSent(event, afterSentView)
            return PluginV2LlmStageDispatchResult(
                stage = PluginV2InternalStage.AfterMessageSent,
                invokedHandlerIds = emptyList(),
                observations = emptyList(),
            )
        }

        override suspend fun deliverLlmPipeline(
            request: PluginV2HostLlmDeliveryRequest,
        ): PluginV2HostLlmDeliveryResult {
            deliveredPipelineCalls.incrementAndGet()
            val pipelineResult = runLlmPipeline(request.pipelineInput)
            if (!pipelineResult.sendableResult.shouldSend) {
                return PluginV2HostLlmDeliveryResult.Suppressed(pipelineResult)
            }
            val preparedReply = request.prepareReply(pipelineResult)
            val sendResult = request.sendReply(preparedReply)
            if (!sendResult.success) {
                PluginRuntimeLogBusProvider.bus().publishLifecycleRecord(
                    pluginId = "__host__",
                    pluginVersion = "",
                    occurredAtEpochMillis = 1L,
                    level = PluginRuntimeLogLevel.Error,
                    code = "llm_pipeline_failed",
                    message = "Plugin v2 llm pipeline observation.",
                    metadata = linkedMapOf(
                        "requestId" to pipelineResult.admission.requestId,
                        "stage" to "Send",
                        "runtimeSessionId" to "test",
                        "streamingMode" to request.pipelineInput.streamingMode.name,
                        "outcome" to "FAILED",
                        "reason" to sendResult.errorSummary.ifBlank { "send_failed" },
                    ),
                )
                return PluginV2HostLlmDeliveryResult.SendFailed(
                    pipelineResult = pipelineResult,
                    sendResult = sendResult,
                )
            }
            request.persistDeliveredReply(preparedReply, sendResult, pipelineResult)
            val afterSentView = PluginV2EventResultCoordinator().buildAfterSentView(
                requestId = pipelineResult.admission.requestId,
                conversationId = pipelineResult.admission.conversationId,
                sendAttemptId = "recording-send",
                platformAdapterType = request.platformAdapterType,
                platformInstanceKey = request.platformInstanceKey,
                sentAtEpochMs = 1L,
                deliveryStatus = PluginV2AfterSentView.DeliveryStatus.SUCCESS,
                receiptIds = sendResult.receiptIds,
                deliveredEntries = preparedReply.deliveredEntries,
                usage = pipelineResult.finalResponse.usage,
            )
            val afterSentError = runCatching {
                dispatchAfterMessageSent(
                    event = request.pipelineInput.event,
                    afterSentView = afterSentView,
                )
            }.exceptionOrNull()
            if (afterSentError != null) {
                PluginV2LifecycleManagerProvider.manager().emitPluginError(
                    event = PluginV2LlmAfterSentPayload(
                        event = request.pipelineInput.event,
                        view = afterSentView,
                    ),
                    pluginName = "__host__",
                    handlerName = "after_message_sent",
                    error = afterSentError,
                    tracebackText = afterSentError.stackTraceToString(),
                )
            }
            return PluginV2HostLlmDeliveryResult.Sent(
                pipelineResult = pipelineResult,
                preparedReply = preparedReply,
                sendResult = sendResult,
                afterSentView = afterSentView,
            )
        }
    }

    private fun v2EngineWithHandlers(
        onMessage: suspend (PluginMessageEvent) -> Unit = {},
        onCommand: suspend (PluginCommandEvent) -> Unit = {},
    ): PluginV2DispatchEngine {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(
            logBus = logBus,
            clock = { 1L },
        )
        val fixture = runtimeFixture(
            pluginId = "com.astrbot.samples.host_ingress_v2",
            logBus = logBus,
            register = { hostApi, _ ->
                hostApi.registerMessageHandler(
                    MessageHandlerRegistrationInput(
                        base = BaseHandlerRegistrationInput(
                            registrationKey = "message.ingress",
                            priority = 100,
                        ),
                        handler = EventAwareHandle { payload ->
                            onMessage(payload as PluginMessageEvent)
                        },
                    ),
                )
                hostApi.registerCommandHandler(
                    CommandHandlerRegistrationInput(
                        base = BaseHandlerRegistrationInput(
                            registrationKey = "command.ingress",
                            priority = 80,
                        ),
                        command = "echo",
                        handler = EventAwareHandle { payload ->
                            onCommand(payload as PluginCommandEvent)
                        },
                    ),
                )
            },
        )
        runBlocking {
            store.commitLoadedRuntime(fixture.entry)
        }
        return PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 1L },
        )
    }

    private fun v2EngineWithDispatchFailure(): PluginV2DispatchEngine {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(
            logBus = logBus,
            clock = { 1L },
        )
        val snapshotRefField = PluginV2ActiveRuntimeStore::class.java.getDeclaredField("snapshotRef").apply {
            isAccessible = true
        }
        @Suppress("UNCHECKED_CAST")
        val snapshotRef = snapshotRefField.get(store) as AtomicReference<PluginV2ActiveRuntimeSnapshot>
        snapshotRef.set(
            PluginV2ActiveRuntimeSnapshot(
                activeRuntimeEntriesByPluginId = emptyMap(),
                activeSessionsByPluginId = object : AbstractMap<String, PluginV2RuntimeSession>() {
                    override val entries: MutableSet<MutableMap.MutableEntry<String, PluginV2RuntimeSession>>
                        get() = error("boom:host-ingress-dispatch")
                },
                compiledRegistriesByPluginId = emptyMap(),
            ),
        )
        return PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 1L },
        )
    }

    private fun v2EngineWithCustomFilterFailure(): PluginV2DispatchEngine {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(
            logBus = logBus,
            clock = { 1L },
        )
        val fixture = runtimeFixture(
            pluginId = "com.astrbot.samples.host_ingress_v2",
            logBus = logBus,
            register = { hostApi, _ ->
                hostApi.registerMessageHandler(
                    MessageHandlerRegistrationInput(
                        base = BaseHandlerRegistrationInput(
                            registrationKey = "message.filter.failure",
                            declaredFilters = listOf(BootstrapFilterDescriptor.message("custom_filter:explode")),
                        ),
                        handler = object : PluginV2EventAwareCallbackHandle, PluginV2CustomFilterAwareCallbackHandle {
                            override fun invoke() = Unit

                            override suspend fun handleEvent(event: PluginErrorEventPayload) {
                                event as PluginMessageEvent
                            }

                            override suspend fun evaluateCustomFilter(request: PluginV2CustomFilterRequest): Boolean {
                                error("boom:filter")
                            }
                        },
                    ),
                )
            },
        )
        runBlocking {
            store.commitLoadedRuntime(fixture.entry)
        }
        return PluginV2DispatchEngine(
            store = store,
            logBus = logBus,
            clock = { 1L },
        )
    }

    private fun runtimeFixture(
        pluginId: String,
        logBus: PluginRuntimeLogBus,
        register: (PluginV2BootstrapHostApi, PluginV2RuntimeSession) -> Unit,
    ): RuntimeFixture {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        )
        session.transitionTo(PluginV2RuntimeSessionState.Loading)
        session.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)

        val hostApi = PluginV2BootstrapHostApi(
            session = session,
            logBus = logBus,
            clock = { 1L },
        )
        register(hostApi, session)

        val compileResult = PluginV2RegistryCompiler(
            logBus = logBus,
            clock = { 1L },
        ).compile(requireNotNull(session.rawRegistry))
        val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
        session.attachCompiledRegistry(compiledRegistry)
        session.transitionTo(PluginV2RuntimeSessionState.Active)

        return RuntimeFixture(
            session = session,
            compiledRegistry = compiledRegistry,
            entry = PluginV2ActiveRuntimeEntry(
                session = session,
                compiledRegistry = compiledRegistry,
                lastBootstrapSummary = PluginV2BootstrapSummary(
                    pluginId = pluginId,
                    sessionInstanceId = session.sessionInstanceId,
                    compiledAtEpochMillis = 0L,
                    handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                    warningCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                    errorCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Error },
                ),
                diagnostics = compileResult.diagnostics,
                callbackTokens = session.snapshotCallbackTokens(),
            ),
        )
    }

    private data class RuntimeFixture(
        val session: PluginV2RuntimeSession,
        val compiledRegistry: PluginV2CompiledRegistrySnapshot,
        val entry: PluginV2ActiveRuntimeEntry,
    )

    private class EventAwareHandle(
        private val onEvent: suspend (PluginErrorEventPayload) -> Unit,
    ) : PluginV2EventAwareCallbackHandle {
        override fun invoke() = Unit

        override suspend fun handleEvent(event: PluginErrorEventPayload) {
            onEvent(event)
        }
    }
}

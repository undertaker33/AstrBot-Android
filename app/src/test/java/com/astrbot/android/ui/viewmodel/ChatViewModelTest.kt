package com.astrbot.android.ui.viewmodel

import com.astrbot.android.MainDispatcherRule
import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.plugin.ErrorResult
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.MediaResult
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginExecutionResult
import com.astrbot.android.model.plugin.PluginHostAction
import com.astrbot.android.model.plugin.PluginInstallState
import com.astrbot.android.model.plugin.PluginInstallStatus
import com.astrbot.android.model.plugin.PluginMediaItem
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.model.plugin.TextResult
import com.astrbot.android.runtime.plugin.AppChatPluginRuntime
import com.astrbot.android.runtime.plugin.DefaultAppChatPluginRuntime
import com.astrbot.android.runtime.plugin.EngineBackedAppChatPluginRuntime
import com.astrbot.android.runtime.plugin.ExternalPluginBridgeRuntime
import com.astrbot.android.runtime.plugin.ExternalPluginRuntimeCatalog
import com.astrbot.android.runtime.plugin.PluginRuntimeRegistry
import com.astrbot.android.runtime.plugin.RecordingExternalPluginScriptExecutor
import com.astrbot.android.runtime.plugin.createQuickJsExternalPluginInstallRecord
import com.astrbot.android.runtime.plugin.InMemoryPluginFailureStateStore
import com.astrbot.android.runtime.plugin.InMemoryPluginRuntimeLogBus
import com.astrbot.android.runtime.plugin.BaseHandlerRegistrationInput
import com.astrbot.android.runtime.plugin.BootstrapFilterDescriptor
import com.astrbot.android.runtime.plugin.CommandHandlerRegistrationInput
import com.astrbot.android.runtime.plugin.DiagnosticSeverity
import com.astrbot.android.runtime.plugin.PluginExecutionBatchResult
import com.astrbot.android.runtime.plugin.PluginExecutionEngine
import com.astrbot.android.runtime.plugin.PluginDispatchSkip
import com.astrbot.android.runtime.plugin.PluginDispatchSkipReason
import com.astrbot.android.runtime.plugin.PluginFailureGuard
import com.astrbot.android.runtime.plugin.PluginMessageEvent
import com.astrbot.android.runtime.plugin.PluginCommandEvent
import com.astrbot.android.runtime.plugin.PluginRuntimeDispatcher
import com.astrbot.android.runtime.plugin.PluginRuntimeHandler
import com.astrbot.android.runtime.plugin.PluginRuntimePlugin
import com.astrbot.android.runtime.plugin.PluginV2ActiveRuntimeEntry
import com.astrbot.android.runtime.plugin.PluginV2ActiveRuntimeStore
import com.astrbot.android.runtime.plugin.PluginV2BootstrapHostApi
import com.astrbot.android.runtime.plugin.PluginV2BootstrapSummary
import com.astrbot.android.runtime.plugin.PluginV2CallbackHandle
import com.astrbot.android.runtime.plugin.PluginV2CompiledRegistrySnapshot
import com.astrbot.android.runtime.plugin.PluginV2CustomFilterAwareCallbackHandle
import com.astrbot.android.runtime.plugin.PluginV2CustomFilterRequest
import com.astrbot.android.runtime.plugin.PluginV2DispatchEngine
import com.astrbot.android.runtime.plugin.PluginV2DispatchEngineProvider
import com.astrbot.android.runtime.plugin.PluginV2EventAwareCallbackHandle
import com.astrbot.android.runtime.plugin.PluginV2RegistryCompiler
import com.astrbot.android.runtime.plugin.PluginV2RuntimeSession
import com.astrbot.android.runtime.plugin.PluginV2RuntimeSessionState
import com.astrbot.android.runtime.plugin.PluginErrorEventPayload
import com.astrbot.android.runtime.plugin.MessageHandlerRegistrationInput
import com.astrbot.android.runtime.plugin.samplePluginV2InstallRecord
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(dispatcher)

    @org.junit.After
    fun tearDown() {
        PluginRuntimeRegistry.reset()
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(null)
    }

    @Test
    fun init_prefers_non_default_restored_session() = runTest(dispatcher) {
        val deps = FakeChatDependencies(
            sessions = listOf(
                defaultSession(),
                ConversationSession(
                    id = "session-restored",
                    title = "Restored",
                    botId = "bot-2",
                    providerId = "provider-2",
                    personaId = "",
                    maxContextMessages = 12,
                    messages = emptyList(),
                ),
            ),
            bots = listOf(defaultBot(), defaultBot(id = "bot-2", defaultProviderId = "provider-2")),
            providers = listOf(defaultChatProvider("provider-2")),
        )

        val viewModel = ChatViewModel(deps)
        advanceUntilIdle()

        assertEquals("session-restored", viewModel.uiState.value.selectedSessionId)
        assertTrue(deps.bindingUpdates.any { it.sessionId == "session-restored" && it.providerId == "provider-2" })
    }

    @Test
    fun send_message_without_available_provider_does_not_append_messages() = runTest(dispatcher) {
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot()),
            providers = emptyList(),
        )
        val viewModel = ChatViewModel(deps)
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("hello")
        advanceUntilIdle()

        assertEquals(0, deps.appendedMessages.size)
        assertTrue(deps.loggedMessages.any { it.contains("Chat send blocked") })
        assertEquals(false, viewModel.uiState.value.isSending)
    }

    @Test
    fun send_message_triggers_before_send_plugin_after_user_message_persist_and_before_model_dispatch() = runTest(dispatcher) {
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val depsSignals = deps.signalLog
        val runtime = RecordingAppChatPluginRuntime(
            plugins = listOf(
                runtimePlugin(
                    pluginId = "before-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    depsSignals += "plugin:before"
                    TextResult("seen")
                },
                runtimePlugin(
                    pluginId = "after-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.AfterModelResponse),
                ) {
                    depsSignals += "plugin:after"
                    NoOp("done")
                },
            ),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("hello plugin")
        advanceUntilIdle()

        val beforeBatch = runtime.batches.first { it.trigger == PluginTriggerSource.BeforeSendMessage }
        assertEquals("hello plugin", beforeBatch.outcomes.single().context.message.contentPreview)
        assertEquals(0, beforeBatch.outcomes.single().context.message.attachmentCount)

        assertOrder(
            signals = deps.signalLog,
            before = "append:user:hello plugin",
            after = "plugin:before",
        )
        assertOrder(
            signals = deps.signalLog,
            before = "plugin:before",
            after = "model:sync",
        )
        assertOrder(
            signals = deps.signalLog,
            before = "model:sync",
            after = "plugin:after",
        )
        assertEquals(1, deps.sentChatRequests)
    }

    @Test
    fun send_message_v2_before_send_stop_uses_real_message_event_without_legacy_context_factory() = runTest(dispatcher) {
        val v2Events = CopyOnWriteArrayList<PluginMessageEvent>()
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            appChatV2Engine(
                onMessage = { event ->
                    v2Events += event
                    event.stopPropagation()
                },
            ),
        )
        val legacyExecuteCalls = AtomicInteger(0)
        val runtime = object : AppChatPluginRuntime {
            override fun execute(
                trigger: PluginTriggerSource,
                contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
            ): PluginExecutionBatchResult {
                legacyExecuteCalls.incrementAndGet()
                error("legacy runtime must not run after v2 stop")
            }
        }
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("hello v2 stop")
        advanceUntilIdle()

        assertEquals(1, v2Events.size)
        assertEquals("hello v2 stop", v2Events.single().rawText)
        assertEquals("hello v2 stop", v2Events.single().workingText)
        assertEquals("app_chat", v2Events.single().platformAdapterType)
        assertEquals("chat-main", v2Events.single().conversationId)
        assertEquals("app-user", v2Events.single().senderId)
        assertEquals("app_chat", v2Events.single().extras["source"])
        assertEquals(0, legacyExecuteCalls.get())
        assertEquals(0, deps.sentChatRequests)
    }

    @Test
    fun send_message_v2_before_send_allows_legacy_fallback_when_not_terminated() = runTest(dispatcher) {
        val v2Events = CopyOnWriteArrayList<PluginMessageEvent>()
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            appChatV2Engine(
                onMessage = { event ->
                    v2Events += event
                },
            ),
        )
        val legacyContextFactories = AtomicInteger(0)
        val legacyTriggers = CopyOnWriteArrayList<PluginTriggerSource>()
        val runtime = object : AppChatPluginRuntime {
            override fun execute(
                trigger: PluginTriggerSource,
                contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
            ): PluginExecutionBatchResult {
                legacyTriggers += trigger
                val plugin = runtimePlugin(
                    pluginId = "legacy-before",
                    supportedTriggers = setOf(trigger),
                ) {
                    NoOp("legacy")
                }
                val context = contextFactory(plugin)
                legacyContextFactories.incrementAndGet()
                if (trigger == PluginTriggerSource.BeforeSendMessage) {
                    assertEquals("hello fallback", context.message.contentPreview)
                }
                return PluginExecutionBatchResult(
                    trigger = trigger,
                    outcomes = emptyList(),
                    skipped = emptyList(),
                )
            }
        }
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("hello fallback")
        advanceUntilIdle()

        assertEquals(listOf("hello fallback"), v2Events.map { it.rawText })
        assertTrue(legacyTriggers.contains(PluginTriggerSource.BeforeSendMessage))
        assertTrue(legacyContextFactories.get() >= 1)
        assertEquals(1, deps.sentChatRequests)
        assertEquals("model reply", deps.latestAssistantMessage()?.content)
    }

    @Test
    fun send_message_v2_before_send_custom_filter_failure_appends_user_visible_message_and_skips_model() = runTest(dispatcher) {
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            appChatV2Engine(
                customFilterFailure = true,
            ),
        )
        val legacyExecuteCalls = AtomicInteger(0)
        val runtime = object : AppChatPluginRuntime {
            override fun execute(
                trigger: PluginTriggerSource,
                contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
            ): PluginExecutionBatchResult {
                legacyExecuteCalls.incrementAndGet()
                error("legacy runtime must not run after v2 custom filter failure")
            }
        }
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("hello filter failure")
        advanceUntilIdle()

        assertEquals(0, legacyExecuteCalls.get())
        assertEquals(0, deps.sentChatRequests)
        assertEquals(
            "Plugin filter failed. Please try again later.",
            deps.latestAssistantMessage()?.content,
        )
    }

    @Test
    fun send_message_triggers_after_model_response_plugin_after_streaming_and_tts_updates() = runTest(dispatcher) {
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(
                defaultBot(
                    defaultProviderId = "provider-stream",
                    configProfileId = "config-stream",
                ),
            ),
            providers = listOf(
                streamingChatProvider("provider-stream"),
                defaultTtsProvider("tts-1"),
            ),
            config = ConfigProfile(
                id = "config-stream",
                defaultChatProviderId = "provider-stream",
                defaultTtsProviderId = "tts-1",
                textStreamingEnabled = true,
                ttsEnabled = true,
                alwaysTtsEnabled = true,
                ttsVoiceId = "voice-1",
            ),
        ).also {
            it.streamingResponse = "streamed reply"
            it.streamingDeltas = listOf("streamed ", "reply")
        }
        val depsSignals = deps.signalLog
        val runtime = RecordingAppChatPluginRuntime(
            plugins = listOf(
                runtimePlugin(
                    pluginId = "after-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.AfterModelResponse),
                ) {
                    depsSignals += "plugin:after"
                    TextResult("after")
                },
            ),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("hello stream")
        advanceUntilIdle()
        val afterBatch = runtime.batches.single { it.trigger == PluginTriggerSource.AfterModelResponse }
        val afterContext = afterBatch.outcomes.single().context
        assertEquals("streamed reply", afterContext.message.contentPreview)
        assertEquals(1, afterContext.message.attachmentCount)
        assertOrder(
            signals = deps.signalLog,
            before = "tts",
            after = "plugin:after",
        )
        assertEquals(1, deps.sentStreamingRequests)
        assertEquals(1, deps.synthesizedRequests)
    }

    @Test
    fun send_message_keeps_command_priority_over_plugins_and_model_dispatch() = runTest(dispatcher) {
        val runtime = RecordingAppChatPluginRuntime(
            plugins = listOf(
                runtimePlugin(
                    pluginId = "before-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    TextResult("should-not-run")
                },
            ),
        )
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("/sid")
        advanceUntilIdle()

        assertTrue(runtime.batches.isEmpty())
        assertEquals(0, deps.sentChatRequests)
        assertEquals(1, deps.appendedMessages.count { it.role == "assistant" })
    }

    @Test
    fun send_message_unsupported_slash_command_invokes_v2_command_handler_once_and_skips_before_send_reentry() = runTest(dispatcher) {
        val v2CommandEvents = CopyOnWriteArrayList<PluginCommandEvent>()
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            appChatV2Engine(
                onCommand = { event ->
                    v2CommandEvents += event
                },
            ),
        )
        val runtime = RecordingAppChatPluginRuntime(
            plugins = listOf(
                runtimePlugin(
                    pluginId = "command-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.OnCommand, PluginTriggerSource.BeforeSendMessage),
                ) {
                    NoOp("legacy")
                },
            ),
        )
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("/unsupported app chat")
        advanceUntilIdle()

        assertEquals(listOf(PluginTriggerSource.OnCommand), runtime.batches.map { it.trigger })
        assertEquals(1, v2CommandEvents.size)
        assertEquals("/unsupported app chat", v2CommandEvents.single().rawText)
        assertEquals(0, deps.sentChatRequests)
    }

    @Test
    fun send_message_unsupported_slash_command_falls_back_to_model_when_no_plugin_handles_it() = runTest(dispatcher) {
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            appChatV2Engine(),
        )
        val runtime = RecordingAppChatPluginRuntime(plugins = emptyList())
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("/unsupported app chat")
        advanceUntilIdle()

        assertEquals(1, deps.sentChatRequests)
        assertEquals("model reply", deps.latestAssistantMessage()?.content)
    }

    @Test
    fun send_message_dispatches_plugin_command_and_appends_text_reply_without_model_dispatch() = runTest(dispatcher) {
        val runtime = RecordingAppChatPluginRuntime(
            plugins = listOf(
                runtimePlugin(
                    pluginId = "command-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.OnCommand),
                ) { context ->
                    assertEquals("/plugin hello", context.triggerMetadata.command)
                    TextResult("plugin command reply")
                },
            ),
        )
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("/plugin hello")
        advanceUntilIdle()

        val batch = runtime.batches.single()
        assertEquals(PluginTriggerSource.OnCommand, batch.trigger)
        assertEquals("/plugin hello", batch.outcomes.single().context.message.contentPreview)
        assertEquals(0, deps.sentChatRequests)
        assertEquals("plugin command reply", deps.latestAssistantMessage()?.content)
    }

    @Test
    fun send_message_dispatches_external_catalog_quickjs_command_without_model_dispatch() = runTest(dispatcher) {
        val tempDir = Files.createTempDirectory("chat-external-quickjs-command").toFile()
        try {
            val record = createQuickJsExternalPluginInstallRecord(
                extractedDir = tempDir,
                pluginId = "external-command-plugin",
                supportedTriggers = listOf("on_command"),
            )
            PluginRuntimeRegistry.registerExternalProvider {
                ExternalPluginRuntimeCatalog.plugins(
                    records = listOf(record),
                    bridgeRuntime = ExternalPluginBridgeRuntime(
                        scriptExecutor = RecordingExternalPluginScriptExecutor(
                            outputs = listOf(
                                JSONObject(
                                    mapOf(
                                        "resultType" to "text",
                                        "text" to "external quickjs command reply",
                                    ),
                                ).toString(),
                            ),
                        ),
                    ),
                )
            }

            val deps = FakeChatDependencies(
                sessions = listOf(defaultSession()),
                bots = listOf(defaultBot(defaultProviderId = "provider-1")),
                providers = listOf(defaultChatProvider("provider-1")),
            )
            val viewModel = ChatViewModel(
                dependencies = deps,
                appChatPluginRuntime = DefaultAppChatPluginRuntime,
                ioDispatcher = dispatcher,
            )
            advanceUntilIdle()
            deps.clearRecordedSignals()

            viewModel.sendMessage("/external hello")
            advanceUntilIdle()

            assertEquals(0, deps.sentChatRequests)
            assertEquals("external quickjs command reply", deps.latestAssistantMessage()?.content)
        } finally {
            PluginRuntimeRegistry.reset()
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun send_message_reports_plugin_command_runtime_failure_without_model_dispatch() = runTest(dispatcher) {
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val runtime = object : AppChatPluginRuntime {
            override fun execute(
                trigger: PluginTriggerSource,
                contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
            ): PluginExecutionBatchResult {
                throw IllegalStateException("runtime exploded")
            }
        }
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("/plugin fail")
        advanceUntilIdle()

        assertEquals(0, deps.sentChatRequests)
        assertEquals("插件命令执行失败：runtime exploded", deps.latestAssistantMessage()?.content)
    }

    @Test
    fun send_message_rethrows_cancellation_from_model_dispatch_without_turning_it_into_failure() = runTest(dispatcher) {
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        deps.sendConfiguredChatFailure = CancellationException("cancelled")
        val viewModel = ChatViewModel(
            dependencies = deps,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        runCatching {
            viewModel.sendMessage("please cancel")
            advanceUntilIdle()
        }

        assertEquals(1, deps.sentChatRequests)
        assertEquals("", viewModel.uiState.value.error)
        assertEquals("", deps.latestAssistantMessage()?.content)
    }

    @Test
    fun send_message_rethrows_cancellation_from_plugin_command_runtime_without_turning_it_into_failure() = runTest(dispatcher) {
        val runtime = object : AppChatPluginRuntime {
            override fun execute(
                trigger: PluginTriggerSource,
                contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
            ): PluginExecutionBatchResult {
                throw CancellationException("cancelled")
            }
        }
        val v2LogBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(
            PluginV2DispatchEngine(
                store = PluginV2ActiveRuntimeStore(
                    logBus = v2LogBus,
                    clock = { 1L },
                ),
                logBus = v2LogBus,
                clock = { 1L },
            ),
        )
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        runCatching {
            viewModel.sendMessage("/unsupported app chat")
            advanceUntilIdle()
        }

        assertEquals(0, deps.sentChatRequests)
        assertTrue(deps.appendedMessages.isEmpty())
        assertEquals("", viewModel.uiState.value.error)
    }

    @Test
    fun send_message_reports_suspended_plugin_command_without_model_dispatch() = runTest(dispatcher) {
        val plugin = runtimePlugin(
            pluginId = "command-plugin",
            supportedTriggers = setOf(PluginTriggerSource.OnCommand),
        ) {
            TextResult("should not run")
        }
        val runtime = object : AppChatPluginRuntime {
            override fun execute(
                trigger: PluginTriggerSource,
                contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
            ): PluginExecutionBatchResult {
                return PluginExecutionBatchResult(
                    trigger = trigger,
                    outcomes = emptyList(),
                    skipped = listOf(
                        PluginDispatchSkip(
                            plugin = plugin,
                            reason = PluginDispatchSkipReason.FailureSuspended,
                        ),
                    ),
                ).also {
                    contextFactory(plugin)
                }
            }
        }
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("/plugin suspended")
        advanceUntilIdle()

        assertEquals(0, deps.sentChatRequests)
        assertEquals(
            "插件 command-plugin 因连续失败已被暂时熔断，请稍后再试。",
            deps.latestAssistantMessage()?.content,
        )
    }

    @Test
    fun send_message_dispatches_plugin_command_and_appends_media_reply_without_model_dispatch() = runTest(dispatcher) {
        val tempDir = Files.createTempDirectory("chat-plugin-command-media").toFile()
        try {
            val extractedDir = tempDir.resolve("plugin").apply { mkdirs() }
            extractedDir.resolve("assets").mkdirs()
            extractedDir.resolve("assets/banner.png").writeText("banner", Charsets.UTF_8)
            val runtime = RecordingAppChatPluginRuntime(
                plugins = listOf(
                    runtimePlugin(
                        pluginId = "command-plugin",
                        supportedTriggers = setOf(PluginTriggerSource.OnCommand),
                        extractedDir = extractedDir.absolutePath,
                    ) {
                        MediaResult(
                            items = listOf(
                                PluginMediaItem(
                                    source = "plugin://package/assets/banner.png",
                                    mimeType = "image/png",
                                    altText = "Banner",
                                ),
                            ),
                        )
                    },
                ),
            )
            val deps = FakeChatDependencies(
                sessions = listOf(defaultSession()),
                bots = listOf(defaultBot(defaultProviderId = "provider-1")),
                providers = listOf(defaultChatProvider("provider-1")),
            )
            val viewModel = ChatViewModel(
                dependencies = deps,
                appChatPluginRuntime = runtime,
                ioDispatcher = dispatcher,
            )
            advanceUntilIdle()
            deps.clearRecordedSignals()

            viewModel.sendMessage("/plugin media")
            advanceUntilIdle()

            assertEquals(0, deps.sentChatRequests)
            val assistantMessage = deps.latestAssistantMessage()
            assertEquals(1, assistantMessage?.attachments?.size)
            assertEquals(
                extractedDir.resolve("assets/banner.png").absolutePath,
                assistantMessage?.attachments?.single()?.remoteUrl,
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun send_message_dispatches_plugin_command_host_action_send_message_without_model_dispatch() = runTest(dispatcher) {
        val runtime = RecordingAppChatPluginRuntime(
            plugins = listOf(
                runtimePlugin(
                    pluginId = "command-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.OnCommand),
                ) {
                    HostActionRequest(
                        action = PluginHostAction.SendMessage,
                        payload = mapOf("text" to "host action reply"),
                    )
                },
            ),
        )
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("/plugin host-action")
        advanceUntilIdle()

        assertEquals(0, deps.sentChatRequests)
        assertEquals("host action reply", deps.latestAssistantMessage()?.content)
    }

    @Test
    fun send_message_isolates_plugin_failures_and_completes_model_flow() = runTest(dispatcher) {
        val runtime = RecordingAppChatPluginRuntime(
            plugins = listOf(
                runtimePlugin(
                    pluginId = "before-plugin",
                    supportedTriggers = setOf(PluginTriggerSource.BeforeSendMessage),
                ) {
                    throw IllegalStateException("plugin exploded")
                },
            ),
        )
        val deps = FakeChatDependencies(
            sessions = listOf(defaultSession()),
            bots = listOf(defaultBot(defaultProviderId = "provider-1")),
            providers = listOf(defaultChatProvider("provider-1")),
        )
        val viewModel = ChatViewModel(
            dependencies = deps,
            appChatPluginRuntime = runtime,
            ioDispatcher = dispatcher,
        )
        advanceUntilIdle()
        deps.clearRecordedSignals()

        viewModel.sendMessage("still send")
        advanceUntilIdle()
        val failedOutcome = runtime.batches
            .single { it.trigger == PluginTriggerSource.BeforeSendMessage }
            .outcomes
            .single()
        assertFalse(failedOutcome.succeeded)
        assertTrue(failedOutcome.result is ErrorResult)
        assertEquals(1, deps.sentChatRequests)
        assertEquals("model reply", deps.latestAssistantMessage()?.content)
        assertEquals("", viewModel.uiState.value.error)
    }

    private class FakeChatDependencies(
        sessions: List<ConversationSession>,
        bots: List<BotProfile>,
        providers: List<ProviderProfile>,
        private val config: ConfigProfile = ConfigProfile(
            id = "config-default",
            defaultChatProviderId = providers.firstOrNull { ProviderCapability.CHAT in it.capabilities }?.id.orEmpty(),
        ),
    ) : ChatViewModelDependencies {
        override val defaultSessionId: String = "chat-main"
        override val defaultSessionTitle: String = "Default session"
        override val bots: StateFlow<List<BotProfile>> = MutableStateFlow(bots)
        override val selectedBotId: StateFlow<String> = MutableStateFlow(bots.firstOrNull()?.id ?: "qq-main")
        override val providers: StateFlow<List<ProviderProfile>> = MutableStateFlow(providers)
        override val configProfiles: StateFlow<List<ConfigProfile>> = MutableStateFlow(listOf(config))
        override val sessions: MutableStateFlow<List<ConversationSession>> = MutableStateFlow(sessions)
        override val personas: StateFlow<List<PersonaProfile>> = MutableStateFlow(emptyList())

        data class BindingUpdate(
            val sessionId: String,
            val providerId: String,
            val personaId: String,
            val botId: String,
        )

        val bindingUpdates = mutableListOf<BindingUpdate>()
        val appendedMessages = mutableListOf<ConversationMessage>()
        val loggedMessages = mutableListOf<String>()
        val signalLog = mutableListOf<String>()

        var sentChatRequests = 0
        var sentStreamingRequests = 0
        var synthesizedRequests = 0
        var chatResponse: String = "model reply"
        var streamingResponse: String = "streamed reply"
        var streamingDeltas: List<String> = listOf("streamed ", "reply")
        var sendConfiguredChatFailure: Throwable? = null

        override fun session(sessionId: String): ConversationSession {
            return sessions.value.first { it.id == sessionId }
        }

        override fun createSession(botId: String): ConversationSession {
            val created = ConversationSession(
                id = "chat-created-${sessions.value.size}",
                title = defaultSessionTitle,
                botId = botId,
                providerId = "",
                personaId = "",
                maxContextMessages = 12,
                messages = emptyList(),
            )
            sessions.value = sessions.value + created
            signalLog += "session:create:${created.id}"
            return created
        }

        override fun deleteSession(sessionId: String) {
            sessions.value = sessions.value.filterNot { it.id == sessionId }
            signalLog += "session:delete:$sessionId"
        }

        override fun renameSession(sessionId: String, title: String) {
            mutateSession(sessionId) { it.copy(title = title) }
            signalLog += "session:rename:$sessionId:$title"
        }

        override fun toggleSessionPinned(sessionId: String) {
            mutateSession(sessionId) { it.copy(pinned = !it.pinned) }
            signalLog += "session:pin:$sessionId"
        }

        override fun updateSessionServiceFlags(sessionId: String, sessionSttEnabled: Boolean?, sessionTtsEnabled: Boolean?) {
            mutateSession(sessionId) {
                it.copy(
                    sessionSttEnabled = sessionSttEnabled ?: it.sessionSttEnabled,
                    sessionTtsEnabled = sessionTtsEnabled ?: it.sessionTtsEnabled,
                )
            }
            signalLog += "session:service-flags:$sessionId"
        }

        override fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) {
            mutateSession(sessionId) {
                it.copy(
                    providerId = providerId,
                    personaId = personaId,
                    botId = botId,
                )
            }
            bindingUpdates += BindingUpdate(sessionId, providerId, personaId, botId)
            signalLog += "bind:$sessionId"
        }

        override fun appendMessage(
            sessionId: String,
            role: String,
            content: String,
            attachments: List<ConversationAttachment>,
        ): String {
            val message = ConversationMessage(
                id = "msg-${appendedMessages.size}",
                role = role,
                content = content,
                timestamp = appendedMessages.size.toLong() + 1L,
                attachments = attachments,
            )
            appendedMessages += message
            mutateSession(sessionId) { it.copy(messages = it.messages + message) }
            signalLog += "append:$role:${content.ifBlank { "<empty>" }}"
            return message.id
        }

        override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
            mutateSession(sessionId) { it.copy(messages = messages) }
            signalLog += "replace:$sessionId"
        }

        override fun updateMessage(
            sessionId: String,
            messageId: String,
            content: String?,
            attachments: List<ConversationAttachment>?,
        ) {
            mutateSession(sessionId) { session ->
                session.copy(
                    messages = session.messages.map { message ->
                        if (message.id != messageId) {
                            message
                        } else {
                            message.copy(
                                content = content ?: message.content,
                                attachments = attachments ?: message.attachments,
                            )
                        }
                    },
                )
            }
            val updated = session(sessionId).messages.first { it.id == messageId }
            signalLog += "update:$messageId:${updated.content.ifBlank { "<empty>" }}:${updated.attachments.size}"
        }

        override fun syncSystemSessionTitle(sessionId: String, title: String) {
            mutateSession(sessionId) { it.copy(title = title) }
            signalLog += "title:$sessionId:$title"
        }

        override fun resolveConfig(profileId: String): ConfigProfile = config

        override fun saveConfig(profile: ConfigProfile) = Unit

        override fun saveBot(profile: BotProfile) = Unit

        override fun saveProvider(profile: ProviderProfile) = Unit

        override suspend fun transcribeAudio(provider: ProviderProfile, attachment: ConversationAttachment): String {
            signalLog += "stt"
            return "transcribed"
        }

        override suspend fun sendConfiguredChat(
            provider: ProviderProfile,
            messages: List<ConversationMessage>,
            systemPrompt: String?,
            config: ConfigProfile?,
            availableProviders: List<ProviderProfile>,
        ): String {
            sentChatRequests += 1
            signalLog += "model:sync"
            sendConfiguredChatFailure?.let { throw it }
            return chatResponse
        }

        override suspend fun sendConfiguredChatStream(
            provider: ProviderProfile,
            messages: List<ConversationMessage>,
            systemPrompt: String?,
            config: ConfigProfile,
            availableProviders: List<ProviderProfile>,
            onDelta: suspend (String) -> Unit,
        ): String {
            sentStreamingRequests += 1
            signalLog += "model:stream"
            streamingDeltas.forEach { delta -> onDelta(delta) }
            return streamingResponse
        }

        override suspend fun synthesizeSpeech(
            provider: ProviderProfile,
            text: String,
            voiceId: String,
            readBracketedContent: Boolean,
        ): ConversationAttachment {
            synthesizedRequests += 1
            signalLog += "tts"
            return ConversationAttachment(
                id = "tts-${synthesizedRequests}",
                type = "audio",
                mimeType = "audio/mpeg",
                fileName = "reply.mp3",
                base64Data = "audio",
            )
        }

        override suspend fun <T> withSessionLock(sessionId: String, block: suspend () -> T): T {
            signalLog += "lock:$sessionId"
            return block()
        }

        override fun log(message: String) {
            loggedMessages += message
        }

        fun clearRecordedSignals() {
            signalLog.clear()
            loggedMessages.clear()
        }

        fun latestAssistantMessage(): ConversationMessage? {
            return sessions.value
                .flatMap { it.messages }
                .lastOrNull { it.role == "assistant" }
        }

        private fun mutateSession(sessionId: String, transform: (ConversationSession) -> ConversationSession) {
            sessions.value = sessions.value.map { session ->
                if (session.id == sessionId) transform(session) else session
            }
        }
    }

    private class RecordingAppChatPluginRuntime(
        plugins: List<PluginRuntimePlugin>,
    ) : AppChatPluginRuntime {
        private val failureGuard = PluginFailureGuard(InMemoryPluginFailureStateStore())
        private val delegate = EngineBackedAppChatPluginRuntime(
            pluginProvider = { plugins },
            engine = PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(failureGuard),
                failureGuard = failureGuard,
            ),
        )

        val batches = mutableListOf<PluginExecutionBatchResult>()

        override fun execute(
            trigger: PluginTriggerSource,
            contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
        ): PluginExecutionBatchResult {
            return delegate.execute(trigger, contextFactory).also { batches += it }
        }
    }

    private fun appChatV2Engine(
        onMessage: suspend (PluginMessageEvent) -> Unit = {},
        onCommand: suspend (PluginCommandEvent) -> Unit = {},
        customFilterFailure: Boolean = false,
    ): PluginV2DispatchEngine {
        val logBus = InMemoryPluginRuntimeLogBus(clock = { 1L })
        val store = PluginV2ActiveRuntimeStore(
            logBus = logBus,
            clock = { 1L },
        )
        val fixture = appChatV2RuntimeFixture(
            pluginId = "com.example.chat.v2.ingress",
            logBus = logBus,
            customFilterFailure = customFilterFailure,
            onMessage = onMessage,
            onCommand = onCommand,
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

    private fun appChatV2RuntimeFixture(
        pluginId: String,
        logBus: InMemoryPluginRuntimeLogBus,
        customFilterFailure: Boolean,
        onMessage: suspend (PluginMessageEvent) -> Unit,
        onCommand: suspend (PluginCommandEvent) -> Unit,
    ): AppChatV2RuntimeFixture {
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
        hostApi.registerMessageHandler(
            MessageHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "message.ingress",
                    declaredFilters = if (customFilterFailure) {
                        listOf(BootstrapFilterDescriptor.message("custom_filter:fail"))
                    } else {
                        emptyList()
                    },
                ),
                handler = if (customFilterFailure) {
                    object : PluginV2EventAwareCallbackHandle, PluginV2CustomFilterAwareCallbackHandle {
                        override fun invoke() = Unit

                        override suspend fun handleEvent(event: PluginErrorEventPayload) = Unit

                        override suspend fun evaluateCustomFilter(request: PluginV2CustomFilterRequest): Boolean {
                            error("boom:filter")
                        }
                    }
                } else {
                    object : PluginV2EventAwareCallbackHandle {
                        override fun invoke() = Unit

                        override suspend fun handleEvent(event: PluginErrorEventPayload) {
                            onMessage(event as PluginMessageEvent)
                        }
                    }
                },
            ),
        )
        hostApi.registerCommandHandler(
            CommandHandlerRegistrationInput(
                base = BaseHandlerRegistrationInput(
                    registrationKey = "command.ingress",
                ),
                command = "unsupported",
                handler = object : PluginV2EventAwareCallbackHandle {
                    override fun invoke() = Unit

                    override suspend fun handleEvent(event: PluginErrorEventPayload) {
                        onCommand(event as PluginCommandEvent)
                    }
                },
            ),
        )
        val compileResult = PluginV2RegistryCompiler(
            logBus = logBus,
            clock = { 1L },
        ).compile(requireNotNull(session.rawRegistry))
        val compiledRegistry = requireNotNull(compileResult.compiledRegistry)
        session.attachCompiledRegistry(compiledRegistry)
        session.transitionTo(PluginV2RuntimeSessionState.Active)
        return AppChatV2RuntimeFixture(
            entry = PluginV2ActiveRuntimeEntry(
                session = session,
                compiledRegistry = compiledRegistry,
                lastBootstrapSummary = PluginV2BootstrapSummary(
                    pluginId = pluginId,
                    sessionInstanceId = session.sessionInstanceId,
                    compiledAtEpochMillis = 1L,
                    handlerCount = compiledRegistry.handlerRegistry.totalHandlerCount,
                    warningCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Warning },
                    errorCount = compileResult.diagnostics.count { it.severity == DiagnosticSeverity.Error },
                ),
                diagnostics = compileResult.diagnostics,
                callbackTokens = session.snapshotCallbackTokens(),
            ),
        )
    }

    private data class AppChatV2RuntimeFixture(
        val entry: PluginV2ActiveRuntimeEntry,
    )

    private fun runtimePlugin(
        pluginId: String,
        supportedTriggers: Set<PluginTriggerSource>,
        extractedDir: String = "/plugins/$pluginId",
        handler: (PluginExecutionContext) -> PluginExecutionResult,
    ): PluginRuntimePlugin {
        val manifest = PluginManifest(
            pluginId = pluginId,
            version = "1.0.0",
            protocolVersion = 1,
            author = "AstrBot",
            title = pluginId,
            description = "test plugin",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = "send_message",
                    title = "Send message",
                    description = "Allows sending message",
                    riskLevel = PluginRiskLevel.LOW,
                ),
            ),
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "entry",
            riskLevel = PluginRiskLevel.LOW,
        )
        return PluginRuntimePlugin(
            pluginId = pluginId,
            pluginVersion = manifest.version,
            installState = PluginInstallState(
                status = PluginInstallStatus.INSTALLED,
                installedVersion = manifest.version,
                source = PluginSource(
                    sourceType = PluginSourceType.LOCAL_FILE,
                    location = "/plugins/$pluginId.zip",
                    importedAt = 1L,
                ),
                manifestSnapshot = manifest,
                permissionSnapshot = manifest.permissions,
                compatibilityState = PluginCompatibilityState.evaluated(
                    protocolSupported = true,
                    minHostVersionSatisfied = true,
                    maxHostVersionSatisfied = true,
                ),
                enabled = true,
                lastInstalledAt = 1L,
                lastUpdatedAt = 1L,
                localPackagePath = "/plugins/$pluginId.zip",
                extractedDir = extractedDir,
            ),
            supportedTriggers = supportedTriggers,
            handler = PluginRuntimeHandler { context -> handler(context) },
        )
    }

    private fun assertOrder(signals: List<String>, before: String, after: String) {
        val beforeIndex = signals.indexOfFirst { it == before }
        val afterIndex = signals.indexOfFirst { it == after }
        assertTrue("Missing signal: $before in $signals", beforeIndex >= 0)
        assertTrue("Missing signal: $after in $signals", afterIndex >= 0)
        assertTrue("Expected $before before $after in $signals", beforeIndex < afterIndex)
    }

    private fun defaultSession(
        id: String = "chat-main",
        botId: String = "qq-main",
    ): ConversationSession {
        return ConversationSession(
            id = id,
            title = "Default session",
            botId = botId,
            providerId = "",
            personaId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )
    }

    private fun defaultBot(
        id: String = "qq-main",
        defaultProviderId: String = "",
        configProfileId: String = "config-default",
    ): BotProfile {
        return BotProfile(
            id = id,
            displayName = id,
            configProfileId = configProfileId,
            defaultProviderId = defaultProviderId,
        )
    }

    private fun defaultChatProvider(id: String): ProviderProfile {
        return ProviderProfile(
            id = id,
            name = id,
            baseUrl = "https://example.com",
            model = "gpt",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "key",
            capabilities = setOf(ProviderCapability.CHAT),
            enabled = true,
            multimodalRuleSupport = FeatureSupportState.UNKNOWN,
            multimodalProbeSupport = FeatureSupportState.UNKNOWN,
        )
    }

    private fun streamingChatProvider(id: String): ProviderProfile {
        return ProviderProfile(
            id = id,
            name = id,
            baseUrl = "https://example.com",
            model = "gpt-4o-mini",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "key",
            capabilities = setOf(ProviderCapability.CHAT),
            enabled = true,
            nativeStreamingRuleSupport = FeatureSupportState.SUPPORTED,
            nativeStreamingProbeSupport = FeatureSupportState.SUPPORTED,
        )
    }

    private fun defaultTtsProvider(id: String): ProviderProfile {
        return ProviderProfile(
            id = id,
            name = id,
            baseUrl = "https://example.com",
            model = "tts-1",
            providerType = ProviderType.OPENAI_TTS,
            apiKey = "key",
            capabilities = setOf(ProviderCapability.TTS),
            enabled = true,
            ttsProbeSupport = FeatureSupportState.SUPPORTED,
        )
    }
}

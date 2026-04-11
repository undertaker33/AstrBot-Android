package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.NoOp
import com.astrbot.android.model.plugin.PluginBotSummary
import com.astrbot.android.model.plugin.PluginConfigSummary
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginMessageSummary
import com.astrbot.android.model.plugin.PluginTriggerMetadata
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.runtime.IncomingMessageEvent
import com.astrbot.android.runtime.OneBotBridgeServer
import java.nio.file.Files
import java.util.AbstractMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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

    private fun invokeExecuteQqPlugins(
        trigger: PluginTriggerSource,
        event: IncomingMessageEvent,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginV2MessageDispatchResult? {
        val method = OneBotBridgeServer::class.java.getDeclaredMethod(
            "executeQqPlugins",
            PluginTriggerSource::class.java,
            IncomingMessageEvent::class.java,
            Function1::class.java,
        )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(OneBotBridgeServer, trigger, event, contextFactory) as PluginV2MessageDispatchResult?
    }

    private fun invokeHandlePluginCommand(
        event: IncomingMessageEvent,
        bot: BotProfile,
        config: ConfigProfile,
        sessionId: String,
        session: ConversationSession,
    ): Boolean {
        val method = OneBotBridgeServer::class.java.getDeclaredMethod(
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
            OneBotBridgeServer,
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

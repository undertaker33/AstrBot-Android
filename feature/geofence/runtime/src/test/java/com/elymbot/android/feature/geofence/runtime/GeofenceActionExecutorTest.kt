package com.elymbot.android.feature.geofence.runtime

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.core.runtime.context.ContextPolicy
import com.elymbot.android.core.runtime.context.DeliveryPolicy
import com.elymbot.android.core.runtime.context.IngressTrigger
import com.elymbot.android.core.runtime.context.ProviderCapabilitySnapshot
import com.elymbot.android.core.runtime.context.ResolvedRuntimeContext
import com.elymbot.android.core.runtime.context.RuntimeBotSnapshot
import com.elymbot.android.core.runtime.context.RuntimeConfigSnapshot
import com.elymbot.android.core.runtime.context.RuntimeContextResolverPort
import com.elymbot.android.core.runtime.context.RuntimeIngressEvent
import com.elymbot.android.core.runtime.context.RuntimePlatform
import com.elymbot.android.core.runtime.context.RuntimeProviderSnapshot
import com.elymbot.android.core.runtime.context.ToolSourceContext
import com.elymbot.android.core.runtime.llm.LlmClientPort
import com.elymbot.android.core.runtime.llm.LlmInvocationRequest
import com.elymbot.android.core.runtime.llm.LlmInvocationResult
import com.elymbot.android.core.runtime.llm.LlmStreamEvent
import com.elymbot.android.feature.bot.domain.BotRepositoryPort
import com.elymbot.android.feature.bot.domain.model.BotProfile
import com.elymbot.android.feature.config.domain.ConfigRepositoryPort
import com.elymbot.android.feature.config.domain.model.ConfigProfile
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutionContext
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceActionExecutionResult
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryPort
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryRequest
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceMessageDeliveryResult
import com.elymbot.android.feature.plugin.domain.runtime.AppChatLlmPipelineRuntime
import com.elymbot.android.feature.plugin.domain.runtime.AppChatPluginRuntime
import com.elymbot.android.feature.plugin.domain.runtime.LlmPipelineAdmission
import com.elymbot.android.feature.plugin.domain.runtime.PlatformLlmCallbacks
import com.elymbot.android.feature.plugin.domain.runtime.PluginDispatchSkip
import com.elymbot.android.feature.plugin.domain.runtime.PluginExecutionBatchResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginExecutionMergeSnapshot
import com.elymbot.android.feature.plugin.domain.runtime.PluginHostCapabilityGateway
import com.elymbot.android.feature.plugin.domain.runtime.PluginLlmResponse
import com.elymbot.android.feature.plugin.domain.runtime.PluginMessageEvent
import com.elymbot.android.feature.plugin.domain.runtime.PluginMessageEventResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginRuntimePlugin
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2AfterSentView
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2DecoratingRunSnapshot
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2DispatchObservation
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostLlmDeliveryRequest
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostLlmDeliveryResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostPreparedReply
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2HostSendResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2LlmPipelineInput
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2LlmPipelineResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2LlmStageDispatchResult
import com.elymbot.android.feature.plugin.domain.runtime.PluginV2ProviderInvocationResult
import com.elymbot.android.feature.plugin.domain.runtime.RuntimeLlmOrchestratorPort
import com.elymbot.android.model.chat.ConversationMessage
import com.elymbot.android.model.plugin.AppChatLlm
import com.elymbot.android.model.plugin.HostActionRequest
import com.elymbot.android.model.plugin.PluginExecutionContext
import com.elymbot.android.model.plugin.PluginHostAction
import com.elymbot.android.model.plugin.PluginTriggerSource
import com.elymbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceActionExecutorTest {
    @Test
    fun send_message_action_delivers_through_host_port() = runBlocking {
        val deliveryPort = RecordingGeofenceMessageDeliveryPort()
        val executor = executor(deliveryPort = deliveryPort)

        val result = executor.execute(actionContext(rule = actionRule(GeofenceActionType.SEND_MESSAGE)))

        assertTrue(result.success)
        assertEquals("app_chat", deliveryPort.requests.single().platform)
        assertEquals("conversation-1", deliveryPort.requests.single().conversationId)
        assertEquals("Geofence prompt", deliveryPort.requests.single().text)
    }

    @Test
    fun agent_prompt_constructs_geofence_event_ingress() = runBlocking {
        val resolver = RecordingRuntimeContextResolver()
        val executor = executor(runtimeContextResolver = resolver)

        val result = executor.execute(actionContext(rule = actionRule(GeofenceActionType.AGENT_PROMPT)))

        assertTrue(result.success)
        val ingress = resolver.capturedEvent
        assertEquals(IngressTrigger.GEOFENCE_EVENT, ingress?.trigger)
        @Suppress("UNCHECKED_CAST")
        val payload = ingress?.rawPlatformPayload as Map<String, Any?>
        assertEquals("rule-1", payload["ruleId"])
        assertEquals("Office reminder", payload["ruleName"])
        assertEquals("region-1", payload["regionId"])
        assertEquals("region-1", payload["regionLabel"])
        assertEquals("enter", payload["transition"])
        assertEquals("redacted", payload["latitude"])
        assertEquals("redacted", payload["longitude"])
        assertEquals(100f, payload["radiusMeters"])
        assertEquals(123L, payload["occurredAt"])
        assertEquals("config-1", payload["configId"])
    }

    @Test
    fun geofence_llm_turn_redacts_exact_coordinates_from_hook_visible_inputs() = runBlocking {
        val orchestrator = RecordingRuntimeOrchestrator()
        val executor = executor(
            config = ConfigProfile(id = "config-1", defaultChatProviderId = "provider-1", webSearchEnabled = true),
            orchestrator = orchestrator,
        )

        val result = executor.execute(actionContext(rule = actionRule(GeofenceActionType.WEATHER_FORECAST)))

        assertTrue(result.success)
        val hookVisibleText = listOf(
            orchestrator.userMessage?.content.orEmpty(),
            orchestrator.capturedContext?.ingressEvent?.rawPlatformPayload.toString(),
            orchestrator.capturedContext?.ingressEvent?.text.orEmpty(),
        ).joinToString("\n")
        assertFalse(hookVisibleText.contains("31.2304"))
        assertFalse(hookVisibleText.contains("121.4737"))
    }

    @Test
    fun weather_forecast_fails_when_target_config_web_search_disabled() = runBlocking {
        val executor = executor(config = ConfigProfile(id = "config-1", webSearchEnabled = false))

        val result = executor.execute(actionContext(rule = actionRule(GeofenceActionType.WEATHER_FORECAST)))

        assertEquals(false, result.success)
        assertEquals("web_search_disabled", result.errorCode)
    }

    @Test
    fun weather_forecast_fails_when_exact_target_config_is_missing_even_if_resolve_falls_back() = runBlocking {
        val executor = executor(
            config = ConfigProfile(id = "fallback-config", defaultChatProviderId = "provider-1", webSearchEnabled = true),
        )

        val result = executor.execute(actionContext(rule = actionRule(GeofenceActionType.WEATHER_FORECAST)))

        assertEquals(false, result.success)
        assertEquals("missing_target_context", result.errorCode)
    }

    @Test
    fun host_capability_action_returns_unsupported_action() = runBlocking {
        val executor = executor()

        val result = executor.execute(actionContext(rule = actionRule(GeofenceActionType.HOST_CAPABILITY)))

        assertEquals(false, result.success)
        assertEquals("unsupported_action", result.errorCode)
    }

    @Test
    fun action_with_unknown_target_platform_fails_without_app_chat_fallback() = runBlocking {
        val executor = executor()

        val result = executor.execute(
            actionContext(
                rule = actionRule(GeofenceActionType.SEND_MESSAGE).copy(targetPlatform = "sms"),
            ),
        )

        assertEquals(false, result.success)
        assertEquals("invalid_target_platform", result.errorCode)
    }

    @Test
    fun action_with_explicit_bot_from_other_config_fails() = runBlocking {
        val executor = executor(
            bot = BotProfile(id = "bot-1", configProfileId = "config-2", defaultProviderId = "provider-1"),
        )

        val result = executor.execute(actionContext(rule = actionRule(GeofenceActionType.SEND_MESSAGE)))

        assertEquals(false, result.success)
        assertEquals("missing_target_context", result.errorCode)
    }

    @Test
    fun news_digest_prompt_contains_transition_and_region_context() = runBlocking {
        val orchestrator = RecordingRuntimeOrchestrator()
        val executor = executor(
            config = ConfigProfile(id = "config-1", defaultChatProviderId = "provider-1", webSearchEnabled = true),
            orchestrator = orchestrator,
        )

        val result = executor.execute(
            actionContext(
                rule = actionRule(GeofenceActionType.NEWS_DIGEST),
                transition = GeofenceTransition.EXIT,
            ),
        )

        assertTrue(result.success)
        val prompt = orchestrator.userMessage?.content.orEmpty()
        assertTrue(prompt.contains("transition: exit"))
        assertTrue(prompt.contains("region: region-1"))
    }

    @Test
    fun transition_processor_records_action_success_summary() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("rule-1", "region-1")
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
        )
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = localSequenceClock(100L, 150L, 180L),
            executionIdFactory = { "execution-1" },
            actionExecutor = { GeofenceActionExecutionResult.success("""{"receipt_ids":["r1"]}""") },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.completedCount)
        assertEquals("complete", repository.records.last().status)
        assertTrue(repository.records.last().deliverySummary.contains("receipt_ids"))
    }

    @Test
    fun transition_processor_records_action_failure_summary() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("rule-1", "region-1")
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
        )
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = localSequenceClock(100L, 150L, 180L),
            executionIdFactory = { "execution-1" },
            actionExecutor = {
                GeofenceActionExecutionResult.failure(
                    errorCode = "delivery_failed",
                    errorMessage = "Delivery failed",
                    deliverySummary = "delivery_failed",
                )
            },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.failedCount)
        assertEquals("failed", repository.records.last().status)
        assertEquals("delivery_failed", repository.records.last().errorCode)
    }

    @Test
    fun transition_processor_does_not_resurrect_rule_paused_during_action_execution() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("rule-1", "region-1")
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
        )
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = localSequenceClock(100L, 150L, 180L),
            executionIdFactory = { "execution-1" },
            actionExecutor = {
                repository.updateRule(
                    rule(regions = listOf(region())).copy(
                        name = "Paused while running",
                        enabled = false,
                        status = com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus.PAUSED,
                    ),
                )
                GeofenceActionExecutionResult.success("""{"receipt_ids":["r1"]}""")
            },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.completedCount)
        assertEquals(
            com.elymbot.android.feature.geofence.domain.model.GeofenceRuleStatus.PAUSED,
            repository.ruleSnapshot("rule-1")?.status,
        )
        assertEquals("Paused while running", repository.ruleSnapshot("rule-1")?.name)
    }

    private fun executor(
        deliveryPort: GeofenceMessageDeliveryPort = RecordingGeofenceMessageDeliveryPort(),
        config: ConfigProfile = ConfigProfile(id = "config-1", defaultChatProviderId = "provider-1", webSearchEnabled = true),
        bot: BotProfile = BotProfile(id = "bot-1", configProfileId = "config-1", defaultProviderId = "provider-1"),
        runtimeContextResolver: RuntimeContextResolverPort = RecordingRuntimeContextResolver(),
        orchestrator: RuntimeLlmOrchestratorPort = RecordingRuntimeOrchestrator(),
    ): DefaultGeofenceActionExecutor {
        return DefaultGeofenceActionExecutor(
            deliveryPort = deliveryPort,
            botRepositoryPort = FakeBotRepositoryPort(bot),
            configRepositoryPort = FakeConfigRepositoryPort(config),
            runtimeContextResolverPort = runtimeContextResolver,
            llmClientPort = NoOpLlmClient,
            orchestrator = orchestrator,
            appChatPluginRuntime = FakeAppChatPluginRuntime,
            hostCapabilityGateway = NoOpPluginHostCapabilityGateway,
            runtimeLogger = RuntimeLogger.noop(),
        )
    }

    private fun actionContext(
        rule: com.elymbot.android.feature.geofence.domain.model.GeofenceRule,
        transition: GeofenceTransition = GeofenceTransition.ENTER,
    ): GeofenceActionExecutionContext {
        return GeofenceActionExecutionContext(
            rule = rule,
            region = region(),
            binding = binding(),
            transition = transition,
            occurredAtMillis = 123L,
        )
    }

    private fun actionRule(actionType: GeofenceActionType): com.elymbot.android.feature.geofence.domain.model.GeofenceRule =
        rule(regions = listOf(region())).copy(
            actionType = actionType,
            actionPrompt = "Geofence prompt",
            targetPlatform = RuntimePlatform.APP_CHAT.wireValue,
            targetConversationId = "conversation-1",
            targetBotId = "bot-1",
            targetConfigProfileId = "config-1",
            targetProviderId = "provider-1",
        )
}

private class RecordingGeofenceMessageDeliveryPort : GeofenceMessageDeliveryPort {
    val requests = mutableListOf<GeofenceMessageDeliveryRequest>()

    override suspend fun deliver(request: GeofenceMessageDeliveryRequest): GeofenceMessageDeliveryResult {
        requests += request
        return GeofenceMessageDeliveryResult(
            success = true,
            deliveredMessageCount = 1,
            receiptIds = listOf("receipt-1"),
        )
    }
}

private class RecordingRuntimeContextResolver : RuntimeContextResolverPort {
    var capturedEvent: RuntimeIngressEvent? = null

    override fun resolve(
        event: RuntimeIngressEvent,
        bot: RuntimeBotSnapshot,
        overrideProviderId: String?,
        overridePersonaId: String?,
    ): ResolvedRuntimeContext {
        capturedEvent = event
        return resolvedRuntimeContext(event, bot)
    }
}

private class RecordingRuntimeOrchestrator : RuntimeLlmOrchestratorPort {
    var userMessage: ConversationMessage? = null
    var capturedContext: ResolvedRuntimeContext? = null

    override suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent?,
    ): PluginV2HostLlmDeliveryResult {
        this.userMessage = userMessage
        this.capturedContext = ctx
        val pipelineResult = pluginPipelineResult(ctx.conversationId, userMessage.id)
        val prepared = callbacks.prepareReply(pipelineResult)
        val sendResult = callbacks.sendReply(prepared)
        return PluginV2HostLlmDeliveryResult.Sent(
            pipelineResult = pipelineResult,
            preparedReply = prepared,
            sendResult = sendResult,
            afterSentView = afterSentView(ctx.conversationId, prepared, sendResult),
        )
    }
}

private class FakeBotRepositoryPort(private val bot: BotProfile) : BotRepositoryPort {
    override val bots = MutableStateFlow(listOf(bot))
    override val selectedBotId = MutableStateFlow(bot.id)
    override fun currentBot(): BotProfile = bot
    override fun snapshotProfiles(): List<BotProfile> = listOf(bot)
    override fun create(name: String): BotProfile = bot.copy(displayName = name)
    override suspend fun save(profile: BotProfile) = Unit
    override suspend fun create(profile: BotProfile) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun select(id: String) = Unit
}

private class FakeConfigRepositoryPort(private val config: ConfigProfile) : ConfigRepositoryPort {
    override val profiles = MutableStateFlow(listOf(config))
    override val selectedProfileId = MutableStateFlow(config.id)
    override fun snapshotProfiles(): List<ConfigProfile> = listOf(config)
    override fun create(name: String): ConfigProfile = config.copy(name = name)
    override fun resolve(id: String): ConfigProfile = config
    override fun resolveExistingId(id: String?): String = config.id
    override suspend fun save(profile: ConfigProfile) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun select(id: String) = Unit
}

private object NoOpLlmClient : LlmClientPort {
    override suspend fun sendWithTools(request: LlmInvocationRequest): LlmInvocationResult =
        LlmInvocationResult(text = "ok")

    override fun streamWithTools(request: LlmInvocationRequest): Flow<LlmStreamEvent> = emptyFlow()
}

private object FakeAppChatPluginRuntime : AppChatPluginRuntime, AppChatLlmPipelineRuntime {
    override fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult = PluginExecutionBatchResult(
        trigger = trigger,
        outcomes = emptyList(),
        skipped = emptyList<PluginDispatchSkip>(),
        merged = PluginExecutionMergeSnapshot(),
    )

    override suspend fun runLlmPipeline(input: PluginV2LlmPipelineInput): PluginV2LlmPipelineResult =
        error("Fake orchestrator should not call runLlmPipeline")

    override suspend fun deliverLlmPipeline(request: PluginV2HostLlmDeliveryRequest): PluginV2HostLlmDeliveryResult =
        error("Fake orchestrator should not call deliverLlmPipeline")

    override suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult = PluginV2LlmStageDispatchResult(
        stage = com.elymbot.android.feature.plugin.domain.runtime.PluginV2InternalStage.AfterMessageSent,
        invokedHandlerIds = emptyList(),
        observations = emptyList<PluginV2DispatchObservation>(),
    )
}

private object NoOpPluginHostCapabilityGateway : PluginHostCapabilityGateway {
    override fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ) = com.elymbot.android.feature.plugin.domain.runtime.ExternalPluginHostActionExecutionResult(
        action = PluginHostAction.SendMessage,
        succeeded = false,
        code = "not_used",
        message = "not used",
        failureSnapshot = com.elymbot.android.feature.plugin.domain.runtime.PluginFailureSnapshot(
            pluginId = pluginId,
        ),
    )

    override fun injectContext(context: PluginExecutionContext): PluginExecutionContext = context
}

private fun resolvedRuntimeContext(
    event: RuntimeIngressEvent,
    bot: RuntimeBotSnapshot,
): ResolvedRuntimeContext {
    val config = RuntimeConfigSnapshot(id = "config-1", defaultChatProviderId = "provider-1")
    val provider = RuntimeProviderSnapshot(
        id = "provider-1",
        name = "Provider",
        baseUrl = "https://example.invalid/v1",
        model = "model-1",
        providerType = "CUSTOM",
        apiKey = "",
        capabilities = setOf("CHAT"),
        enabled = true,
    )
    return ResolvedRuntimeContext(
        requestId = "ctx-1",
        ingressEvent = event,
        bot = bot,
        config = config,
        persona = null,
        provider = provider,
        availableProviders = listOf(provider),
        conversationId = event.conversationId,
        messageWindow = emptyList(),
        scheduledTaskContextWindow = emptyList(),
        contextPolicy = ContextPolicy(
            strategy = config.contextLimitStrategy,
            maxTurns = config.maxContextTurns,
            dequeueTurns = config.dequeueContextTurns,
            compressInstruction = config.llmCompressInstruction,
            compressKeepRecent = config.llmCompressKeepRecent,
            compressProviderId = config.llmCompressProviderId,
        ),
        personaToolSnapshot = null,
        providerCapabilities = ProviderCapabilitySnapshot(
            supportsToolCalling = false,
            supportsStreaming = false,
            supportsMultimodal = false,
        ),
        webSearchEnabled = config.webSearchEnabled,
        proactiveEnabled = config.proactiveEnabled,
        mcpServers = emptyList(),
        skills = emptyList(),
        promptSkills = emptyList(),
        toolSkills = emptyList(),
        toolSourceContext = ToolSourceContext.fromConfigSnapshot(
            config = config,
            requestId = "ctx-1",
            platform = event.platform,
            conversationId = event.conversationId,
            ingressTrigger = event.trigger,
        ),
        deliveryPolicy = DeliveryPolicy(
            platform = event.platform,
            streamingEnabled = false,
            quoteSenderMessage = false,
            mentionSender = false,
            replyTextPrefix = "",
            ttsEnabled = false,
            alwaysTts = false,
        ),
        realWorldTimeAwarenessEnabled = false,
    )
}

private fun pluginPipelineResult(conversationId: String, messageId: String): PluginV2LlmPipelineResult {
    val sendable = PluginMessageEventResult(
        requestId = "req-1",
        conversationId = conversationId,
        text = "Geofence response",
    )
    return PluginV2LlmPipelineResult(
        admission = LlmPipelineAdmission(
            requestId = "req-1",
            conversationId = conversationId,
            messageIds = listOf(messageId),
            llmInputSnapshot = "Geofence prompt",
            routingTarget = AppChatLlm.AppChat,
            streamingMode = PluginV2StreamingMode.NON_STREAM,
        ),
        finalRequest = com.elymbot.android.feature.plugin.domain.runtime.PluginProviderRequest(
            requestId = "req-1",
            availableProviderIds = listOf("provider-1"),
            availableModelIdsByProvider = mapOf("provider-1" to listOf("model-1")),
            conversationId = conversationId,
            messageIds = listOf(messageId),
            llmInputSnapshot = "Geofence prompt",
            selectedProviderId = "provider-1",
            selectedModelId = "model-1",
        ),
        finalResponse = PluginLlmResponse(
            requestId = "req-1",
            providerId = "provider-1",
            modelId = "model-1",
            text = "Geofence response",
        ),
        sendableResult = sendable,
        hookInvocationTrace = emptyList(),
        decoratingRunResult = object : PluginV2DecoratingRunSnapshot {
            override val finalResult: PluginMessageEventResult = sendable
            override val appliedHandlerIds: List<String> = emptyList()
            override val stoppedByHandlerId: String? = null
            override val mutationTrace: List<*> = emptyList<Any>()
        },
    )
}

private fun afterSentView(
    conversationId: String,
    prepared: PluginV2HostPreparedReply,
    sendResult: PluginV2HostSendResult,
): PluginV2AfterSentView =
    PluginV2AfterSentView(
        requestId = "req-1",
        conversationId = conversationId,
        sendAttemptId = "send-1",
        platformAdapterType = RuntimePlatform.APP_CHAT.wireValue,
        platformInstanceKey = "geofence:rule-1",
        sentAtEpochMs = 1L,
        deliveryStatus = PluginV2AfterSentView.DeliveryStatus.SUCCESS,
        receiptIds = sendResult.receiptIds,
        deliveredEntries = prepared.deliveredEntries,
    )

private fun localSequenceClock(vararg values: Long): () -> Long {
    var index = 0
    return {
        val value = values.getOrElse(index) { values.last() }
        index += 1
        value
    }
}

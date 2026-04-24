package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.core.runtime.context.ResolvedRuntimeContext
import com.astrbot.android.core.runtime.context.RuntimeContextDataPort
import com.astrbot.android.core.runtime.context.RuntimeContextResolver
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.context.RuntimeIngressEvent
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmInvocationRequest
import com.astrbot.android.core.runtime.llm.LlmInvocationResult
import com.astrbot.android.core.runtime.llm.LlmStreamEvent
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.LlmPipelineAdmission
import com.astrbot.android.feature.plugin.runtime.PlatformLlmCallbacks
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginLlmResponse
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.runtime.PluginMessageEventResult
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2AfterSentView
import com.astrbot.android.feature.plugin.runtime.PluginV2EventResultCoordinator
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryResult
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineInput
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineResult
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmStageDispatchResult
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.plugin.runtime.createCompatPluginHostCapabilityGateway
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.plugin.AppChatLlm
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduledTaskRuntimeExecutorTest {
    @Test
    fun execute_uses_scheduled_task_message_without_persisting_note_as_user_message() = runBlocking {
        val bot = BotProfile(
            id = "bot-1",
            displayName = "Bot",
            defaultProviderId = "provider-1",
            configProfileId = "config-1",
        )
        val conversationPort = RecordingConversationPort(
            session = ConversationSession(
                id = "chat-1",
                title = "Chat",
                botId = bot.id,
                personaId = "",
                providerId = "provider-1",
                maxContextMessages = 10,
                messages = listOf(
                    ConversationMessage(
                        id = "old-user",
                        role = "user",
                        content = "半小时后提醒我喝水",
                        timestamp = 1L,
                    ),
                ),
            ),
        )
        val orchestrator = RecordingOrchestrator()

        val summary = ScheduledTaskRuntimeExecutor.execute(
            context = CronJobExecutionContext(
                jobId = "job-1",
                name = "喝水提醒",
                description = "提醒用户喝水",
                jobType = "active_agent",
                note = "提醒用户该喝水了",
                sessionId = "chat-1",
                platform = "app",
                conversationId = "chat-1",
                botId = bot.id,
                configProfileId = "config-1",
                personaId = "",
                providerId = "provider-1",
                origin = "active_capability",
                runOnce = true,
                runAt = "2026-04-24T11:30:00+08:00",
            ),
            runtimeDependencies = ScheduledTaskRuntimeDependencies(
                llmClient = FakeLlmClient(),
                botPort = FakeBotPort(bot),
                orchestrator = orchestrator,
                runtimeContextResolverPort = FakeRuntimeContextResolverPort(conversationPort),
                deliveryPort = FakeScheduledMessageDeliveryPort(),
                appChatPluginRuntime = ThrowingAppChatLlmPipelineRuntime,
                hostCapabilityGateway = createCompatPluginHostCapabilityGateway(),
            ),
        )

        assertEquals("app", summary.platform)
        assertEquals("chat-1", summary.conversationId)
        assertEquals("scheduled_task", orchestrator.userMessageRole)
        assertEquals("cron:job-1", orchestrator.userMessageId)
        assertTrue(orchestrator.messageWindowContents.isEmpty())
        assertFalse(conversationPort.appendedRoles.contains("user"))
    }

}

private class RecordingOrchestrator : RuntimeLlmOrchestratorPort {
    var userMessageRole: String = ""
    var userMessageId: String = ""
    var messageWindowContents: List<String> = emptyList()

    override suspend fun dispatchLlm(
        ctx: ResolvedRuntimeContext,
        llmRuntime: AppChatLlmPipelineRuntime,
        callbacks: PlatformLlmCallbacks,
        userMessage: ConversationMessage,
        preBuiltPluginEvent: PluginMessageEvent?,
    ): PluginV2HostLlmDeliveryResult {
        userMessageRole = userMessage.role
        userMessageId = userMessage.id
        messageWindowContents = ctx.messageWindow.map { it.content }
        return sentResult(
            conversationId = ctx.conversationId,
            messageId = userMessage.id,
            text = "该喝水了",
        )
    }

    private fun sentResult(
        conversationId: String,
        messageId: String,
        text: String,
    ): PluginV2HostLlmDeliveryResult.Sent {
        val admission = LlmPipelineAdmission(
            requestId = "req-1",
            conversationId = conversationId,
            messageIds = listOf(messageId),
            llmInputSnapshot = text,
            routingTarget = AppChatLlm.AppChat,
            streamingMode = PluginV2StreamingMode.NON_STREAM,
        )
        val sendable = PluginMessageEventResult(
            requestId = "req-1",
            conversationId = conversationId,
            text = text,
        )
        val request = PluginProviderRequest(
            requestId = "req-1",
            availableProviderIds = listOf("provider-1"),
            availableModelIdsByProvider = mapOf("provider-1" to listOf("model-1")),
            conversationId = conversationId,
            messageIds = listOf(messageId),
            llmInputSnapshot = text,
            selectedProviderId = "provider-1",
            selectedModelId = "model-1",
            systemPrompt = "scheduled",
        )
        val result = PluginV2LlmPipelineResult(
            admission = admission,
            finalRequest = request,
            finalResponse = PluginLlmResponse(
                requestId = "req-1",
                providerId = "provider-1",
                modelId = "model-1",
                text = text,
            ),
            sendableResult = sendable,
            hookInvocationTrace = emptyList(),
            decoratingRunResult = PluginV2EventResultCoordinator.DecoratingRunResult(
                finalResult = sendable,
                appliedHandlerIds = emptyList(),
            ),
        )
        val deliveredEntry = PluginV2AfterSentView.DeliveredEntry(
            entryId = messageId,
            entryType = "assistant",
            textPreview = text,
            attachmentCount = 0,
        )
        return PluginV2HostLlmDeliveryResult.Sent(
            pipelineResult = result,
            preparedReply = PluginV2HostPreparedReply(
                text = text,
                deliveredEntries = listOf(deliveredEntry),
            ),
            sendResult = PluginV2HostSendResult(success = true, receiptIds = listOf("receipt-1")),
            afterSentView = PluginV2AfterSentView(
                requestId = "req-1",
                conversationId = conversationId,
                sendAttemptId = "send-1",
                platformAdapterType = "app_chat",
                platformInstanceKey = "cron:job-1",
                sentAtEpochMs = 1L,
                deliveryStatus = PluginV2AfterSentView.DeliveryStatus.SUCCESS,
                receiptIds = listOf("receipt-1"),
                deliveredEntries = listOf(deliveredEntry),
            ),
        )
    }
}

private class RecordingConversationPort(
    session: ConversationSession,
) : ConversationRepositoryPort {
    private var currentSession = session
    override val defaultSessionId: String = session.id
    override val sessions = MutableStateFlow(listOf(session))
    val appendedRoles = mutableListOf<String>()

    override fun contextPreview(sessionId: String): String = currentSession.messages.joinToString("\n") { it.content }

    override fun session(sessionId: String): ConversationSession = currentSession

    override fun syncSystemSessionTitle(sessionId: String, title: String) = Unit

    override fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment>,
    ): String {
        appendedRoles += role
        val id = "$role-${appendedRoles.size}"
        currentSession = currentSession.copy(
            messages = currentSession.messages + ConversationMessage(
                id = id,
                role = role,
                content = content,
                timestamp = appendedRoles.size.toLong(),
                attachments = attachments,
            ),
        )
        sessions.value = listOf(currentSession)
        return id
    }

    override fun updateSessionBindings(sessionId: String, providerId: String, personaId: String, botId: String) = Unit

    override fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean?,
        sessionTtsEnabled: Boolean?,
    ) = Unit

    override fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String?,
        attachments: List<ConversationAttachment>?,
    ) = Unit

    override fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        currentSession = currentSession.copy(messages = messages)
        sessions.value = listOf(currentSession)
    }

    override fun renameSession(sessionId: String, title: String) = Unit

    override fun deleteSession(sessionId: String) = Unit
}

private class FakeRuntimeContextResolverPort(
    private val conversationPort: ConversationRepositoryPort,
) : RuntimeContextResolverPort {
    private val provider = ProviderProfile(
        id = "provider-1",
        name = "Provider",
        baseUrl = "https://example.invalid/v1",
        model = "model-1",
        providerType = ProviderType.OPENAI_COMPATIBLE,
        apiKey = "",
        capabilities = setOf(ProviderCapability.CHAT),
    )
    private val config = ConfigProfile(
        id = "config-1",
        name = "Config",
        defaultChatProviderId = provider.id,
        proactiveEnabled = true,
    )

    override fun resolve(
        event: RuntimeIngressEvent,
        bot: BotProfile,
        overrideProviderId: String?,
        overridePersonaId: String?,
    ): ResolvedRuntimeContext {
        val dataPort = object : RuntimeContextDataPort {
            override fun resolveConfig(configProfileId: String): ConfigProfile = config

            override fun listProviders(): List<ProviderProfile> = listOf(provider)

            override fun findEnabledPersona(personaId: String) = null

            override fun session(sessionId: String): ConversationSession = conversationPort.session(sessionId)

            override fun compatibilitySnapshotForConfig(config: ConfigProfile): ResourceCenterCompatibilitySnapshot =
                ResourceCenterCompatibilitySnapshot(resources = emptyList(), projections = emptyList())
        }
        return RuntimeContextResolver.resolve(
            event = event,
            bot = bot,
            dataPort = dataPort,
            overrideProviderId = overrideProviderId,
            overridePersonaId = overridePersonaId,
        )
    }
}

private class FakeBotPort(private val bot: BotProfile) : BotRepositoryPort {
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

private class FakeLlmClient : LlmClientPort {
    override suspend fun sendWithTools(request: LlmInvocationRequest): LlmInvocationResult =
        LlmInvocationResult(text = "该喝水了")

    override fun streamWithTools(request: LlmInvocationRequest): Flow<LlmStreamEvent> = emptyFlow()
}

private class FakeScheduledMessageDeliveryPort : ScheduledMessageDeliveryPort {
    override suspend fun deliver(request: ScheduledMessageDeliveryRequest): ScheduledMessageDeliveryResult {
        return ScheduledMessageDeliveryResult(
            success = true,
            deliveredMessageCount = 1,
            receiptIds = listOf("receipt-1"),
        )
    }
}

private object ThrowingAppChatLlmPipelineRuntime : AppChatLlmPipelineRuntime {
    override suspend fun runLlmPipeline(input: PluginV2LlmPipelineInput): PluginV2LlmPipelineResult {
        error("The fake orchestrator should not call runLlmPipeline.")
    }

    override suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
    ): PluginV2HostLlmDeliveryResult {
        error("The fake orchestrator should not call deliverLlmPipeline.")
    }

    override suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult {
        error("The fake orchestrator should not call dispatchAfterMessageSent.")
    }
}

package com.astrbot.android.feature.chat.runtime

import kotlinx.coroutines.flow.flow

import com.astrbot.android.core.runtime.llm.LlmResponseSegmenter
import com.astrbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import com.astrbot.android.feature.chat.domain.AppChatRequest
import com.astrbot.android.feature.chat.domain.AppChatRuntimeEvent
import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.core.runtime.context.RuntimeIngressEvent
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.core.runtime.context.SenderInfo
import com.astrbot.android.core.runtime.context.StreamingModeResolver
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PlatformLlmCallbacks
import com.astrbot.android.feature.plugin.runtime.PluginProviderRequest
import com.astrbot.android.feature.plugin.runtime.PluginV2AfterSentView
import com.astrbot.android.feature.plugin.runtime.PluginV2FollowupSender
import com.astrbot.android.feature.plugin.runtime.PluginV2HostLlmDeliveryResult
import com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult
import com.astrbot.android.feature.plugin.runtime.PluginV2LlmPipelineResult
import com.astrbot.android.feature.plugin.runtime.PluginV2ProviderInvocationResult
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.plugin.runtime.createCompatPluginHostCapabilityGatewayFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlin.coroutines.CoroutineContext

/**
 * Wraps a shared runtime-context resolver seam and [RuntimeLlmOrchestratorPort]
 * behind [AppChatRuntimePort].
 *
 * This service owns the LLM pipeline invocation for App Chat. It resolves runtime
 * context, calls the shared orchestrator, and emits [AppChatRuntimeEvent]s for the
 * domain layer to consume.
 */
internal class AppChatRuntimeService(
    private val chatDependencies: ChatViewModelRuntimeBindings,
    private val appChatPluginRuntime: AppChatPluginRuntime,
    private val llmOrchestrator: RuntimeLlmOrchestratorPort,
    private val providerInvocationService: AppChatProviderInvocationService,
    private val preparedReplyService: AppChatPreparedReplyService,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
) : AppChatRuntimePort {

    @Deprecated(
        "Compat constructor bypasses Hilt. Production path must use the primary constructor.",
        level = DeprecationLevel.WARNING,
    )
    constructor(
        chatDependencies: ChatViewModelRuntimeBindings,
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext = Dispatchers.IO,
    ) : this(
        chatDependencies,
        appChatPluginRuntime,
        com.astrbot.android.feature.plugin.runtime.DefaultRuntimeLlmOrchestrator(),
        AppChatProviderInvocationService(
            chatDependencies = chatDependencies,
            ioDispatcher = ioDispatcher,
        ),
        AppChatPreparedReplyService(
            chatDependencies = chatDependencies,
            ioDispatcher = ioDispatcher,
        ),
        createCompatPluginHostCapabilityGatewayFactory(),
    )

    override fun send(request: AppChatRequest): Flow<AppChatRuntimeEvent> = flow {
        try {
            val session = chatDependencies.session(request.sessionId)
            val bot = chatDependencies.bots.value
                .firstOrNull { it.id == session.botId }
                ?: error("Bot not found: ${session.botId}")
            val provider = chatDependencies.providers.value
                .firstOrNull {
                    it.id == session.providerId &&
                        it.enabled &&
                        ProviderCapability.CHAT in it.capabilities
                }
                ?: chatDependencies.providers.value
                    .firstOrNull { it.enabled && ProviderCapability.CHAT in it.capabilities }
                ?: error("No enabled chat provider available")
            val config = runCatching { chatDependencies.resolveConfig(bot.configProfileId) }.getOrNull()

            val userMessage = session.messages.firstOrNull { it.id == request.userMessageId }
                ?: error("User message not found: ${request.userMessageId}")

            val llmRuntime = appChatPluginRuntime as? AppChatLlmPipelineRuntime
                ?: error("AppChatPluginRuntime must implement AppChatLlmPipelineRuntime")

            val runtimeContext = chatDependencies.runtimeContextResolverPort.resolve(
                event = RuntimeIngressEvent(
                    platform = RuntimePlatform.APP_CHAT,
                    conversationId = session.originSessionId.ifBlank { session.id },
                    messageId = userMessage.id,
                    sender = SenderInfo(userId = "app-user"),
                    messageType = session.messageType,
                    text = userMessage.content,
                ),
                bot = bot,
                overrideProviderId = provider.id,
                overridePersonaId = session.personaId,
            )

            val streamingMode = StreamingModeResolver.resolve(runtimeContext)

            val wantsTts = config?.ttsEnabled == true &&
                session.sessionTtsEnabled &&
                (config.alwaysTtsEnabled || request.text.endsWith("~"))
            val ttsProvider = config
                ?.defaultTtsProviderId
                ?.takeIf { config.ttsEnabled && session.sessionTtsEnabled }
                ?.let { ttsProviderId ->
                    chatDependencies.providers.value.firstOrNull { it.id == ttsProviderId && it.enabled }
                }

            val sink = StreamingMessageSink()

            val callbacks = object : PlatformLlmCallbacks {
                override val platformInstanceKey = bot.id
                override val hostCapabilityGateway = gatewayFactory.create()
                override val followupSender: PluginV2FollowupSender? = null

                override suspend fun prepareReply(
                    result: PluginV2LlmPipelineResult,
                ): PluginV2HostPreparedReply {
                    return preparedReplyService.prepareReply(
                        result = result,
                        wantsTts = wantsTts,
                        ttsProvider = ttsProvider,
                        ttsConfig = config,
                    )
                }

                override suspend fun sendReply(
                    prepared: PluginV2HostPreparedReply,
                ): PluginV2HostSendResult = PluginV2HostSendResult(success = true)

                override suspend fun persistDeliveredReply(
                    prepared: PluginV2HostPreparedReply,
                    sendResult: PluginV2HostSendResult,
                    pipelineResult: PluginV2LlmPipelineResult,
                ) {
                    sink.updateText(prepared.text)
                    if (prepared.attachments.isNotEmpty()) {
                        sink.updateAttachments(prepared.attachments)
                    }
                    sink.seal()
                }

                override suspend fun invokeProvider(
                    request: PluginProviderRequest,
                    mode: PluginV2StreamingMode,
                    ctx: com.astrbot.android.core.runtime.context.ResolvedRuntimeContext,
                ): PluginV2ProviderInvocationResult {
                    return providerInvocationService.invokeProvider(
                        request = request,
                        mode = mode,
                        config = config,
                        ctx = ctx,
                    )
                }
            }

            val deliveryResult = llmOrchestrator.dispatchLlm(
                ctx = runtimeContext,
                llmRuntime = llmRuntime,
                callbacks = callbacks,
                userMessage = userMessage,
            )

            when (deliveryResult) {
                is PluginV2HostLlmDeliveryResult.Sent -> {
                    val text = sink.text
                    val attachments = sink.attachments

                    if (streamingMode != PluginV2StreamingMode.NON_STREAM &&
                        attachments.isEmpty() &&
                        text.isNotBlank()
                    ) {
                        val segments = LlmResponseSegmenter.split(
                            text = text,
                            stripTrailingBoundaryPunctuation = true,
                        )
                        if (segments.isNotEmpty()) {
                            val buffer = StringBuilder()
                            for (segment in segments) {
                                buffer.append(segment)
                                emit(AppChatRuntimeEvent.AssistantDelta(buffer.toString()))
                            }
                        }
                    }

                    emit(AppChatRuntimeEvent.AssistantFinal(text))

                    if (attachments.isNotEmpty()) {
                        emit(AppChatRuntimeEvent.AttachmentUpdate(attachments))
                    }
                }

                is PluginV2HostLlmDeliveryResult.Suppressed -> {
                    emit(
                        AppChatRuntimeEvent.Failure(
                            "App chat LLM result suppressed: ${deliveryResult.pipelineResult.admission.requestId}",
                        ),
                    )
                }

                is PluginV2HostLlmDeliveryResult.SendFailed -> {
                    emit(
                        AppChatRuntimeEvent.Failure(
                            deliveryResult.sendResult.errorSummary.ifBlank { "send_failed" },
                        ),
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(AppChatRuntimeEvent.Failure(e.message ?: e.javaClass.simpleName, e))
        }
    }
}

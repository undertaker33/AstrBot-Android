package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource

interface AppChatPluginRuntime {
    fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult
}

internal interface AppChatLlmPipelineRuntime {
    suspend fun runLlmPipeline(
        input: PluginV2LlmPipelineInput,
    ): PluginV2LlmPipelineResult

    suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
    ): PluginV2HostLlmDeliveryResult

    suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult
}

internal data class PluginV2HostPreparedReply(
    val text: String,
    val attachments: List<ConversationAttachment> = emptyList(),
    val deliveredEntries: List<PluginV2AfterSentView.DeliveredEntry> = emptyList(),
)

internal data class PluginV2HostSendResult(
    val success: Boolean,
    val receiptIds: List<String> = emptyList(),
    val errorSummary: String = "",
)

internal data class PluginV2HostLlmDeliveryRequest(
    val pipelineInput: PluginV2LlmPipelineInput,
    val conversationId: String,
    val platformAdapterType: String,
    val platformInstanceKey: String,
    val hostCapabilityGateway: PluginHostCapabilityGateway = DefaultPluginHostCapabilityGateway(),
    val prepareReply: suspend (PluginV2LlmPipelineResult) -> PluginV2HostPreparedReply,
    val sendReply: suspend (PluginV2HostPreparedReply) -> PluginV2HostSendResult,
    val persistDeliveredReply: suspend (
        PluginV2HostPreparedReply,
        PluginV2HostSendResult,
        PluginV2LlmPipelineResult,
    ) -> Unit,
)

internal sealed interface PluginV2HostLlmDeliveryResult {
    val pipelineResult: PluginV2LlmPipelineResult

    data class Suppressed(
        override val pipelineResult: PluginV2LlmPipelineResult,
    ) : PluginV2HostLlmDeliveryResult

    data class SendFailed(
        override val pipelineResult: PluginV2LlmPipelineResult,
        val sendResult: PluginV2HostSendResult,
    ) : PluginV2HostLlmDeliveryResult

    data class Sent(
        override val pipelineResult: PluginV2LlmPipelineResult,
        val preparedReply: PluginV2HostPreparedReply,
        val sendResult: PluginV2HostSendResult,
        val afterSentView: PluginV2AfterSentView,
    ) : PluginV2HostLlmDeliveryResult
}

internal class EngineBackedAppChatPluginRuntime(
    private val pluginProvider: () -> List<PluginRuntimePlugin>,
    private val engine: PluginExecutionEngine,
    private val defaultHostCapabilityGatewayProvider: () -> PluginHostCapabilityGateway = {
        DefaultPluginHostCapabilityGateway()
    },
) : AppChatPluginRuntime, AppChatLlmPipelineRuntime {
    override fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        return engine.executeBatch(
            trigger = trigger,
            plugins = pluginProvider(),
            contextFactory = contextFactory,
        )
    }

    override suspend fun runLlmPipeline(
        input: PluginV2LlmPipelineInput,
    ): PluginV2LlmPipelineResult {
        val hostCapabilityGateway = defaultHostCapabilityGatewayProvider()
        return AppChatPluginRuntimeCoordinatorProvider.coordinator(hostCapabilityGateway).runPreSendStages(
            input = input,
            snapshot = hostCapabilityGateway.registerHostBuiltinTools(
                PluginV2ActiveRuntimeStoreProvider.store().snapshot(),
                personaSnapshot = input.personaToolEnablementSnapshot,
            ),
        )
    }

    override suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
    ): PluginV2HostLlmDeliveryResult {
        return AppChatPluginRuntimeCoordinatorProvider.coordinator(request.hostCapabilityGateway).deliverLlmPipeline(
            request = request,
            snapshot = request.hostCapabilityGateway.registerHostBuiltinTools(
                PluginV2ActiveRuntimeStoreProvider.store().snapshot(),
                personaSnapshot = request.pipelineInput.personaToolEnablementSnapshot,
            ),
        )
    }

    override suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult {
        return AppChatPluginRuntimeCoordinatorProvider
            .coordinator(defaultHostCapabilityGatewayProvider())
            .dispatchAfterMessageSent(
            event = event,
            afterSentView = afterSentView,
        )
    }
}

internal object AppChatPluginRuntimeCoordinatorProvider {
    @Volatile
    private var coordinatorOverrideForTests: PluginV2LlmPipelineCoordinator? = null

    fun coordinator(hostCapabilityGateway: PluginHostCapabilityGateway): PluginV2LlmPipelineCoordinator {
        return coordinatorOverrideForTests ?: PluginV2LlmPipelineCoordinator(
            toolExecutor = PluginV2ToolExecutor { args ->
                hostCapabilityGateway.executeHostBuiltinTool(args) ?: PluginToolResult(
                    toolCallId = args.toolCallId,
                    requestId = args.requestId,
                    toolId = args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "tool_executor_unavailable",
                    text = "Tool executor is not wired yet.",
                )
            },
        )
    }

    internal fun setCoordinatorOverrideForTests(
        coordinator: PluginV2LlmPipelineCoordinator?,
    ) {
        coordinatorOverrideForTests = coordinator
    }
}

object PluginRuntimeRegistry {
    @Volatile
    private var pluginProvider: () -> List<PluginRuntimePlugin> = { emptyList() }

    @Volatile
    private var externalProviders: List<() -> List<PluginRuntimePlugin>> = emptyList()

    fun plugins(): List<PluginRuntimePlugin> {
        return buildList {
            addAll(pluginProvider())
            externalProviders.forEach { provider ->
                addAll(provider())
            }
        }
    }

    fun registerProvider(provider: () -> List<PluginRuntimePlugin>) {
        pluginProvider = provider
    }

    fun registerExternalProvider(provider: () -> List<PluginRuntimePlugin>) {
        externalProviders = externalProviders + provider
    }

    fun reset() {
        pluginProvider = { emptyList() }
        externalProviders = emptyList()
    }
}

internal object DefaultAppChatPluginRuntime : AppChatPluginRuntime, AppChatLlmPipelineRuntime {
    private fun delegate(): EngineBackedAppChatPluginRuntime {
        val plugins = PluginRuntimeRegistry.plugins()
        val failureGuard = PluginFailureGuard(
            store = PluginRuntimeFailureStateStoreProvider.store(),
        )
        return EngineBackedAppChatPluginRuntime(
            pluginProvider = { plugins },
            engine = PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(failureGuard),
                failureGuard = failureGuard,
            ),
        )
    }

    override fun execute(
        trigger: PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> PluginExecutionContext,
    ): PluginExecutionBatchResult {
        return delegate().execute(trigger, contextFactory)
    }

    override suspend fun runLlmPipeline(
        input: PluginV2LlmPipelineInput,
    ): PluginV2LlmPipelineResult {
        return delegate().runLlmPipeline(input)
    }

    override suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
    ): PluginV2HostLlmDeliveryResult {
        return delegate().deliverLlmPipeline(request)
    }

    override suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult {
        return delegate().dispatchAfterMessageSent(
            event = event,
            afterSentView = afterSentView,
        )
    }
}

package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.feature.plugin.runtime.toolsource.FutureToolSourceRegistry
import com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceIdentity
import com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceInvokeRequest

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

internal fun interface PluginV2FollowupSender {
    fun send(text: String, attachments: List<ConversationAttachment>): PluginV2HostSendResult
}

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
    val hostCapabilityGateway: PluginHostCapabilityGateway,
    val followupSender: PluginV2FollowupSender? = null,
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
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
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
        val configProfileId = input.configProfileId.orEmpty()
        val futureRegistry = FutureToolSourceRegistry()
        val toolSourceContext = input.toolSourceContext
            ?: FutureToolSourceRegistry.contextForConfig(configProfileId)
        val futureDescriptors = futureRegistry.collectToolDescriptors(toolSourceContext)
        val activeFutureKinds = futureDescriptors.map { it.sourceKind }.toSet()
        val snapshot = hostCapabilityGateway.registerHostBuiltinTools(
            PluginRuntimeDependencyBridge.activeRuntimeStore().snapshot(),
            personaSnapshot = input.personaToolEnablementSnapshot,
            futureSourceDescriptors = futureDescriptors,
            activeFutureSourceKinds = activeFutureKinds,
        )
        return AppChatPluginRuntimeCoordinatorProvider.coordinator(
            hostCapabilityGateway = hostCapabilityGateway,
            futureRegistry = futureRegistry,
            toolSourceContext = toolSourceContext,
            toolRegistrySnapshot = snapshot.toolRegistrySnapshot,
        ).runPreSendStages(
            input = input,
            snapshot = snapshot,
        )
    }

    override suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
    ): PluginV2HostLlmDeliveryResult {
        val configProfileId = request.pipelineInput.configProfileId.orEmpty()
        val futureRegistry = FutureToolSourceRegistry()
        val toolSourceContext = request.pipelineInput.toolSourceContext
            ?: FutureToolSourceRegistry.contextForConfig(configProfileId)
        val futureDescriptors = futureRegistry.collectToolDescriptors(toolSourceContext)
        val activeFutureKinds = futureDescriptors.map { it.sourceKind }.toSet()
        val snapshot = request.hostCapabilityGateway.registerHostBuiltinTools(
            PluginRuntimeDependencyBridge.activeRuntimeStore().snapshot(),
            personaSnapshot = request.pipelineInput.personaToolEnablementSnapshot,
            futureSourceDescriptors = futureDescriptors,
            activeFutureSourceKinds = activeFutureKinds,
        )
        return AppChatPluginRuntimeCoordinatorProvider.coordinator(
            hostCapabilityGateway = request.hostCapabilityGateway,
            futureRegistry = futureRegistry,
            toolSourceContext = toolSourceContext,
            toolRegistrySnapshot = snapshot.toolRegistrySnapshot,
        ).deliverLlmPipeline(
            request = request,
            snapshot = snapshot,
        )
    }

    override suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult {
        return AppChatPluginRuntimeCoordinatorProvider
            .coordinator(hostCapabilityGateway)
            .dispatchAfterMessageSent(
            event = event,
            afterSentView = afterSentView,
        )
    }
}

internal object AppChatPluginRuntimeCoordinatorProvider {
    @Volatile
    private var coordinatorOverrideForTests: PluginV2LlmPipelineCoordinator? = null

    fun coordinator(
        hostCapabilityGateway: PluginHostCapabilityGateway,
        futureRegistry: FutureToolSourceRegistry? = null,
        toolSourceContext: com.astrbot.android.feature.plugin.runtime.toolsource.ToolSourceContext? = null,
        toolRegistrySnapshot: PluginV2ToolRegistrySnapshot? = null,
    ): PluginV2LlmPipelineCoordinator {
        return coordinatorOverrideForTests ?: PluginV2LlmPipelineCoordinator(
            dispatchEngine = PluginRuntimeDependencyBridge.dispatchEngine(),
            logBus = PluginRuntimeDependencyBridge.logBus(),
            lifecycleManager = PluginRuntimeDependencyBridge.lifecycleManager(),
            toolExecutor = PluginV2ToolExecutor { args ->
                // 1. Try host builtin tools first
                val hostResult = hostCapabilityGateway.executeHostBuiltinTool(args)
                if (hostResult != null) return@PluginV2ToolExecutor hostResult

                // 2. Try future source tools (ACTIVE_CAPABILITY, MCP, etc.)
                if (futureRegistry != null) {
                    val entry = toolRegistrySnapshot?.activeEntriesByToolId?.get(args.toolId)
                    if (entry != null) {
                        val invokeResult = futureRegistry.invoke(
                            ToolSourceInvokeRequest(
                                identity = ToolSourceIdentity(
                                    sourceKind = entry.sourceKind,
                                    ownerId = entry.pluginId,
                                    sourceRef = entry.name,
                                    displayName = entry.name,
                                ),
                                args = args,
                                timeoutMs = 60_000L,
                                configProfileId = extractConfigProfileId(args),
                                toolSourceContext = toolSourceContext,
                            ),
                        )
                        if (invokeResult != null) return@PluginV2ToolExecutor invokeResult.result
                    }
                }

                // 3. Fallback error
                PluginToolResult(
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

    private fun extractConfigProfileId(args: PluginToolArgs): String? {
        val host = args.metadata?.get("__host") as? Map<*, *> ?: return null
        val eventExtras = host["eventExtras"] as? Map<*, *> ?: return null
        return (eventExtras["configProfileId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
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
        PluginRuntimeCatalog.registerProvider(provider)
    }

    fun registerExternalProvider(provider: () -> List<PluginRuntimePlugin>) {
        externalProviders = externalProviders + provider
    }

    fun reset() {
        pluginProvider = { emptyList() }
        externalProviders = emptyList()
        PluginRuntimeCatalog.reset()
    }
}

@Deprecated(
    "Compat-only. Production code should use a Hilt-owned AppChatPluginRuntime.",
    level = DeprecationLevel.WARNING,
)
internal object DefaultAppChatPluginRuntime : AppChatPluginRuntime, AppChatLlmPipelineRuntime {
    private fun delegate(): EngineBackedAppChatPluginRuntime {
        val plugins = PluginRuntimeCatalog.plugins()
        val failureGuard = PluginFailureGuard(
            store = PluginRuntimeFailureStateStoreProvider.store(),
        )
        return EngineBackedAppChatPluginRuntime(
            pluginProvider = { plugins },
            engine = PluginExecutionEngine(
                dispatcher = PluginRuntimeDispatcher(failureGuard),
                failureGuard = failureGuard,
            ),
            hostCapabilityGateway = createCompatPluginHostCapabilityGateway(),
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

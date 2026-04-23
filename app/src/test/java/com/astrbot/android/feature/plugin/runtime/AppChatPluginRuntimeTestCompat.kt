package com.astrbot.android.feature.plugin.runtime

internal object DefaultAppChatPluginRuntime : AppChatPluginRuntime, AppChatLlmPipelineRuntime {
    override fun execute(
        trigger: com.astrbot.android.model.plugin.PluginTriggerSource,
        contextFactory: (PluginRuntimePlugin) -> com.astrbot.android.model.plugin.PluginExecutionContext,
    ): PluginExecutionBatchResult {
        return runtime().execute(trigger, contextFactory)
    }

    override suspend fun runLlmPipeline(
        input: PluginV2LlmPipelineInput,
    ): PluginV2LlmPipelineResult {
        return runtime().runLlmPipeline(input)
    }

    override suspend fun deliverLlmPipeline(
        request: PluginV2HostLlmDeliveryRequest,
    ): PluginV2HostLlmDeliveryResult {
        return runtime().deliverLlmPipeline(request)
    }

    override suspend fun dispatchAfterMessageSent(
        event: PluginMessageEvent,
        afterSentView: PluginV2AfterSentView,
    ): PluginV2LlmStageDispatchResult {
        return runtime().dispatchAfterMessageSent(event, afterSentView)
    }

    private fun runtime(): EngineBackedAppChatPluginRuntime {
        val failureGuard = testPluginFailureGuard(
            store = InMemoryPluginFailureStateStore(),
            scopedStore = InMemoryPluginScopedFailureStateStore(),
        )
        return EngineBackedAppChatPluginRuntime(
            pluginProvider = PluginRuntimeCatalog::plugins,
            engine = PluginExecutionEngine(
                dispatcher = testPluginRuntimeDispatcher(
                    failureGuard = failureGuard,
                ),
                failureGuard = failureGuard,
            ),
            hostCapabilityGateway = createCompatPluginHostCapabilityGateway(),
        )
    }
}

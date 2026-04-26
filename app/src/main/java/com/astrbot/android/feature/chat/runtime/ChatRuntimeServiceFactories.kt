package com.astrbot.android.feature.chat.runtime

import com.astrbot.android.feature.chat.domain.AppChatRuntimePort
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.cron.runtime.ScheduledTaskIntentFallbackResponder
import com.astrbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

internal class AppChatProviderInvocationServiceFactory @Inject constructor() {
    fun create(
        chatDependencies: ChatViewModelRuntimeBindings,
        ioDispatcher: CoroutineContext,
    ): AppChatProviderInvocationService {
        return AppChatProviderInvocationService(
            chatDependencies = chatDependencies,
            ioDispatcher = ioDispatcher,
        )
    }
}

internal class AppChatPreparedReplyServiceFactory @Inject constructor() {
    fun create(
        chatDependencies: ChatViewModelRuntimeBindings,
        ioDispatcher: CoroutineContext,
    ): AppChatPreparedReplyService {
        return AppChatPreparedReplyService(
            chatDependencies = chatDependencies,
            ioDispatcher = ioDispatcher,
        )
    }
}

internal class AppChatRuntimeServiceFactory @Inject constructor(
    private val llmOrchestrator: RuntimeLlmOrchestratorPort,
    private val providerInvocationServiceFactory: AppChatProviderInvocationServiceFactory,
    private val preparedReplyServiceFactory: AppChatPreparedReplyServiceFactory,
    private val gatewayFactory: PluginHostCapabilityGatewayFactory,
    private val scheduledTaskFallbackResponder: ScheduledTaskIntentFallbackResponder,
) {
    fun create(
        chatDependencies: ChatViewModelRuntimeBindings,
        appChatPluginRuntime: AppChatPluginRuntime,
        ioDispatcher: CoroutineContext,
    ): AppChatRuntimePort {
        return AppChatRuntimeService(
            chatDependencies = chatDependencies,
            appChatPluginRuntime = appChatPluginRuntime,
            llmOrchestrator = llmOrchestrator,
            providerInvocationService = providerInvocationServiceFactory.create(
                chatDependencies = chatDependencies,
                ioDispatcher = ioDispatcher,
            ),
            preparedReplyService = preparedReplyServiceFactory.create(
                chatDependencies = chatDependencies,
                ioDispatcher = ioDispatcher,
            ),
            gatewayFactory = gatewayFactory,
            scheduledTaskFallbackResponder = scheduledTaskFallbackResponder,
        )
    }
}

internal class AppChatPluginCommandServiceFactory @Inject constructor(
    private val hostCapabilityGateway: PluginHostCapabilityGateway,
    private val hostActionExecutor: ExternalPluginHostActionExecutor,
    private val dispatchEngine: PluginV2DispatchEngine,
) {
    fun create(
        dependencies: ChatViewModelRuntimeBindings,
        appChatPluginRuntime: AppChatPluginRuntime,
    ): AppChatPluginCommandService {
        return AppChatPluginCommandService(
            dependencies = dependencies,
            appChatPluginRuntime = appChatPluginRuntime,
            hostCapabilityGateway = hostCapabilityGateway,
            hostActionExecutor = hostActionExecutor,
            dispatchEngine = dispatchEngine,
        )
    }
}

package com.astrbot.android.di.hilt

import com.astrbot.android.core.runtime.audio.TencentSilkEncoder
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.plugin.runtime.AppChatLlmPipelineRuntime
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.astrbot.android.feature.plugin.runtime.ExternalPluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.feature.qq.runtime.DefaultQqProviderInvoker
import com.astrbot.android.feature.qq.runtime.HiltQqOneBotBridgeRuntime
import com.astrbot.android.feature.qq.runtime.HiltQqRuntimeGraphFactory
import com.astrbot.android.feature.qq.runtime.QqBridgeRuntime
import com.astrbot.android.feature.qq.runtime.QqOneBotRuntimeDependencies
import com.astrbot.android.feature.qq.runtime.QqPluginExecutionService
import com.astrbot.android.feature.qq.runtime.QqRuntimeGraphFactory
import com.astrbot.android.feature.qq.runtime.QqScheduledMessageSender
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeServicesModule {

    @Provides
    @Singleton
    fun provideQqProviderInvoker(
        llmClientPort: LlmClientPort,
    ): DefaultQqProviderInvoker = DefaultQqProviderInvoker(llmClientPort)

    @Provides
    @Singleton
    fun provideQqOneBotRuntimeDependencies(
        botPort: BotRepositoryPort,
        configPort: ConfigRepositoryPort,
        personaPort: PersonaRepositoryPort,
        providerPort: ProviderRepositoryPort,
        conversationPort: QqConversationPort,
        platformConfigPort: QqPlatformConfigPort,
        orchestrator: RuntimeLlmOrchestratorPort,
        runtimeContextResolverPort: RuntimeContextResolverPort,
        appChatPluginRuntime: AppChatPluginRuntime,
        pluginCatalog: ExternalPluginRuntimeCatalog,
        pluginV2DispatchEngine: PluginV2DispatchEngine,
        failureStateStore: PluginFailureStateStore,
        scopedFailureStateStore: PluginScopedFailureStateStore,
        providerInvoker: DefaultQqProviderInvoker,
        gatewayFactory: PluginHostCapabilityGatewayFactory,
        hostCapabilityGateway: PluginHostCapabilityGateway,
        hostActionExecutor: ExternalPluginHostActionExecutor,
        pluginExecutionService: QqPluginExecutionService,
        llmProviderProbePort: LlmProviderProbePort,
        logBus: PluginRuntimeLogBus,
        tencentSilkEncoder: TencentSilkEncoder,
    ): QqOneBotRuntimeDependencies {
        return QqOneBotRuntimeDependencies(
            botPort = botPort,
            configPort = configPort,
            personaPort = personaPort,
            providerPort = providerPort,
            conversationPort = conversationPort,
            platformConfigPort = platformConfigPort,
            orchestrator = orchestrator,
            runtimeContextResolverPort = runtimeContextResolverPort,
            appChatPluginRuntime = appChatPluginRuntime as AppChatLlmPipelineRuntime,
            pluginCatalog = pluginCatalog::plugins,
            pluginV2DispatchEngine = pluginV2DispatchEngine,
            failureStateStore = failureStateStore,
            scopedFailureStateStore = scopedFailureStateStore,
            providerInvoker = providerInvoker,
            gatewayFactory = gatewayFactory,
            hostCapabilityGateway = hostCapabilityGateway,
            hostActionExecutor = hostActionExecutor,
            pluginExecutionService = pluginExecutionService,
            llmProviderProbePort = llmProviderProbePort,
            logBus = logBus,
            silkAudioEncoder = tencentSilkEncoder::encode,
        )
    }

    @Provides
    @Singleton
    fun provideQqRuntimeGraphFactory(
        factory: HiltQqRuntimeGraphFactory,
    ): QqRuntimeGraphFactory = factory

    @Provides
    @Singleton
    fun provideQqBridgeRuntime(
        runtime: HiltQqOneBotBridgeRuntime,
    ): QqBridgeRuntime = runtime

    @Provides
    @Singleton
    fun provideQqScheduledMessageSender(
        runtime: HiltQqOneBotBridgeRuntime,
    ): QqScheduledMessageSender = runtime
}

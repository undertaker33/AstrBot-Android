package com.astrbot.android.di.hilt.runtime

import com.astrbot.android.feature.plugin.data.PluginRepositoryStatePort
import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.EngineBackedAppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginRuntimeCatalog
import com.astrbot.android.feature.plugin.runtime.PluginExecutionEngine
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeDispatcher
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduler
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.toolsource.FutureToolSourceContextResolver
import com.astrbot.android.feature.plugin.runtime.toolsource.FutureToolSourceRegistry
import com.astrbot.android.feature.plugin.runtime.toolsource.PortBackedFutureToolSourceContextResolver
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PluginRuntimeServicesModule {

    @Provides
    @Singleton
    fun providePluginHostCapabilityGateway(
        gatewayFactory: PluginHostCapabilityGatewayFactory,
    ): PluginHostCapabilityGateway = gatewayFactory.create()

    @Provides
    @Singleton
    fun providePluginExecutionEngine(
        failureGuard: PluginFailureGuard,
        scheduler: PluginRuntimeScheduler,
        logBus: PluginRuntimeLogBus,
    ): PluginExecutionEngine = PluginExecutionEngine(
        dispatcher = PluginRuntimeDispatcher(
            failureGuard = failureGuard,
            scheduler = scheduler,
            logBus = logBus,
        ),
        failureGuard = failureGuard,
        scheduler = scheduler,
        logBus = logBus,
    )

    @Provides
    @Singleton
    fun provideExternalPluginRuntimeCatalog(
        repositoryStatePort: PluginRepositoryStatePort,
    ): ExternalPluginRuntimeCatalog = ExternalPluginRuntimeCatalog(
        repositoryStatePort = repositoryStatePort,
    )

    @Provides
    @Singleton
    fun provideAppChatPluginRuntime(
        pluginCatalog: ExternalPluginRuntimeCatalog,
        engine: PluginExecutionEngine,
        hostCapabilityGateway: PluginHostCapabilityGateway,
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        pluginV2DispatchEngine: PluginV2DispatchEngine,
        pluginV2LifecycleManager: PluginV2LifecycleManager,
        logBus: PluginRuntimeLogBus,
        futureToolSourceRegistry: FutureToolSourceRegistry,
    ): AppChatPluginRuntime = EngineBackedAppChatPluginRuntime(
        pluginCatalog,
        engine,
        hostCapabilityGateway,
        activeRuntimeStore,
        pluginV2DispatchEngine,
        logBus,
        pluginV2LifecycleManager,
        futureToolSourceRegistry,
    )

    @Provides
    @Singleton
    fun provideFutureToolSourceContextResolver(
        resolver: PortBackedFutureToolSourceContextResolver,
    ): FutureToolSourceContextResolver = resolver
}

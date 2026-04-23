package com.astrbot.android.di.hilt

import com.astrbot.android.feature.plugin.runtime.DefaultPluginExecutionHostResolver
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.DefaultPluginExecutionHostOperations
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostResolver
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.data.config.PluginHostConfigResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginHostCapabilityModule {

    @Binds
    @Singleton
    abstract fun bindPluginExecutionHostResolver(
        impl: DefaultPluginExecutionHostResolver,
    ): PluginExecutionHostResolver

    companion object {
        @Provides
        @Singleton
        @JvmStatic
        fun provideDefaultPluginExecutionHostOperations(
            hostConfigResolver: PluginHostConfigResolver,
        ): DefaultPluginExecutionHostOperations = DefaultPluginExecutionHostOperations(
            hostConfigResolver = hostConfigResolver,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun provideExternalPluginHostActionExecutor(
            failureGuard: PluginFailureGuard,
            logBus: PluginRuntimeLogBus,
        ): ExternalPluginHostActionExecutor = ExternalPluginHostActionExecutor(
            failureGuard = failureGuard,
            logBus = logBus,
        )

        @Provides
        @Singleton
        @JvmStatic
        fun providePluginHostCapabilityGatewayFactory(
            resolver: PluginExecutionHostResolver,
            hostActionExecutor: ExternalPluginHostActionExecutor,
        ): PluginHostCapabilityGatewayFactory = PluginHostCapabilityGatewayFactory(
            resolver = resolver,
            hostActionExecutor = hostActionExecutor,
        )
    }
}

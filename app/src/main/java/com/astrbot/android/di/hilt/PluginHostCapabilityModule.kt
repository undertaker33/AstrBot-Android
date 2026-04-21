package com.astrbot.android.di.hilt

import com.astrbot.android.feature.plugin.runtime.DefaultPluginExecutionHostResolver
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGatewayFactory
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostResolver
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
        fun providePluginHostCapabilityGatewayFactory(
            resolver: PluginExecutionHostResolver,
        ): PluginHostCapabilityGatewayFactory = PluginHostCapabilityGatewayFactory(resolver)
    }
}

package com.astrbot.android.di.hilt.runtime

import com.astrbot.android.core.runtime.container.ContainerBridgeStatePort
import com.astrbot.android.core.runtime.context.DefaultRuntimeContextResolverPort
import com.astrbot.android.core.runtime.context.RuntimeContextDataPort
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.di.ProductionContainerBridgeStatePort
import com.astrbot.android.di.ProductionRuntimeContextDataPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeContextServicesModule {

    @Provides
    @Singleton
    fun provideRuntimeContextDataPort(): RuntimeContextDataPort = ProductionRuntimeContextDataPort

    @Provides
    @Singleton
    fun provideContainerBridgeStatePort(
        bridgeStatePort: ProductionContainerBridgeStatePort,
    ): ContainerBridgeStatePort = bridgeStatePort

    @Provides
    @Singleton
    fun provideRuntimeContextResolverPort(
        resolver: DefaultRuntimeContextResolverPort,
    ): RuntimeContextResolverPort = resolver
}

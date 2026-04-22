package com.astrbot.android.di.hilt

import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.SharedRuntimeNetworkTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

internal object RuntimeNetworkTransportRegistry {
    @Volatile
    private var installedTransport: RuntimeNetworkTransport? = null

    fun installFromHilt(transport: RuntimeNetworkTransport) {
        installedTransport = transport
    }

    fun transport(): RuntimeNetworkTransport = installedTransport ?: SharedRuntimeNetworkTransport.get()
}

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeNetworkModule {

    @Provides
    @Singleton
    fun provideRuntimeNetworkTransport(): RuntimeNetworkTransport =
        SharedRuntimeNetworkTransport.get().also(RuntimeNetworkTransportRegistry::installFromHilt)
}

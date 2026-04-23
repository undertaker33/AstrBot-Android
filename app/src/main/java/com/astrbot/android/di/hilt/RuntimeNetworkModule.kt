package com.astrbot.android.di.hilt

import com.astrbot.android.core.runtime.network.OkHttpRuntimeNetworkTransport
import com.astrbot.android.core.runtime.network.RuntimeNetworkTransport
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeNetworkModule {

    @Provides
    @Singleton
    fun provideRuntimeNetworkTransport(): RuntimeNetworkTransport = OkHttpRuntimeNetworkTransport()
}

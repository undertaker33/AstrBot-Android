package com.astrbot.android.di.hilt.presentation

import com.astrbot.android.di.hilt.BotLoginState
import com.astrbot.android.di.hilt.BridgeConfig
import com.astrbot.android.di.hilt.BridgeConfigSaver
import com.astrbot.android.di.hilt.BridgeRuntimeState
import com.astrbot.android.di.hilt.QqLoginState
import com.astrbot.android.feature.qq.data.NapCatBridgeStateOwner
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.ui.viewmodel.DefaultQQLoginViewModelBindings
import com.astrbot.android.ui.viewmodel.QQLoginViewModelBindings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Module
@InstallIn(SingletonComponent::class)
internal abstract class QqViewModelBindingsModule {

    @Binds
    @Singleton
    abstract fun bindQqLoginViewModelBindings(
        bindings: DefaultQQLoginViewModelBindings,
    ): QQLoginViewModelBindings

    companion object {

        @Provides
        @BridgeConfig
        fun provideBridgeConfig(
            bridgeStateOwner: NapCatBridgeStateOwner,
        ): StateFlow<NapCatBridgeConfig> = bridgeStateOwner.config

        @Provides
        @BridgeRuntimeState
        fun provideBridgeRuntimeState(
            bridgeStateOwner: NapCatBridgeStateOwner,
        ): StateFlow<NapCatRuntimeState> = bridgeStateOwner.runtimeState

        @Provides
        fun provideBridgeConfigSaver(
            bridgeStateOwner: NapCatBridgeStateOwner,
        ): BridgeConfigSaver = BridgeConfigSaver(bridgeStateOwner::updateConfig)

        @Provides
        @BotLoginState
        fun provideBotLoginState(): StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

        @Provides
        @QqLoginState
        fun provideQqLoginState(): StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState
    }
}

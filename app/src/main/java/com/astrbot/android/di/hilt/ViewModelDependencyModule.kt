package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.di.BotViewModelDependencies
import com.astrbot.android.di.BridgeViewModelDependencies
import com.astrbot.android.di.ChatViewModelDependencies
import com.astrbot.android.di.ChatViewModelRuntimeBindings
import com.astrbot.android.di.ConfigViewModelDependencies
import com.astrbot.android.di.ConversationViewModelDependencies
import com.astrbot.android.di.HiltBotViewModelDependencies
import com.astrbot.android.di.HiltBridgeViewModelDependencies
import com.astrbot.android.di.HiltChatViewModelDependencies
import com.astrbot.android.di.HiltChatViewModelRuntimeBindings
import com.astrbot.android.di.HiltConfigViewModelDependencies
import com.astrbot.android.di.HiltConversationViewModelDependencies
import com.astrbot.android.di.HiltPersonaViewModelDependencies
import com.astrbot.android.di.HiltPluginViewModelDependencies
import com.astrbot.android.di.HiltProviderViewModelDependencies
import com.astrbot.android.di.HiltQQLoginViewModelDependencies
import com.astrbot.android.di.HiltRuntimeAssetViewModelDependencies
import com.astrbot.android.di.PersonaViewModelDependencies
import com.astrbot.android.di.PluginViewModelDependencies
import com.astrbot.android.di.ProviderViewModelDependencies
import com.astrbot.android.di.QQLoginViewModelDependencies
import com.astrbot.android.di.RuntimeAssetViewModelDependencies
import com.astrbot.android.di.createPluginInstallIntentHandler
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.plugin.runtime.catalog.PluginInstallIntentHandler
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ViewModelDependencyModule {

    @Provides
    @Singleton
    fun provideBridgeViewModelDependencies(): BridgeViewModelDependencies = HiltBridgeViewModelDependencies

    @Provides
    @Singleton
    fun provideBotViewModelDependencies(): BotViewModelDependencies = HiltBotViewModelDependencies

    @Provides
    @Singleton
    fun provideProviderViewModelDependencies(): ProviderViewModelDependencies = HiltProviderViewModelDependencies

    @Provides
    @Singleton
    fun provideConfigViewModelDependencies(): ConfigViewModelDependencies = HiltConfigViewModelDependencies

    @Provides
    @Singleton
    fun provideConversationViewModelDependencies(): ConversationViewModelDependencies = HiltConversationViewModelDependencies

    @Provides
    @Singleton
    fun providePersonaViewModelDependencies(): PersonaViewModelDependencies = HiltPersonaViewModelDependencies

    @Provides
    @Singleton
    fun providePluginViewModelDependencies(): PluginViewModelDependencies = HiltPluginViewModelDependencies

    @Provides
    @Singleton
    fun provideQqLoginViewModelDependencies(): QQLoginViewModelDependencies = HiltQQLoginViewModelDependencies

    @Provides
    fun providePluginInstallIntentHandler(): PluginInstallIntentHandler = createPluginInstallIntentHandler()

    @Provides
    @Singleton
    fun provideRuntimeAssetViewModelDependencies(
        @ApplicationContext appContext: Context,
    ): RuntimeAssetViewModelDependencies {
        return HiltRuntimeAssetViewModelDependencies(appContext)
    }

    @Provides
    @Singleton
    fun provideChatViewModelRuntimeBindings(
        conversationRepositoryPort: ConversationRepositoryPort,
        runtimeLlmOrchestrator: RuntimeLlmOrchestratorPort,
    ): ChatViewModelRuntimeBindings {
        return HiltChatViewModelRuntimeBindings(
            dependencies = HiltChatViewModelDependencies,
            runtimeLlmOrchestrator = runtimeLlmOrchestrator,
            conversationRepositoryPort = conversationRepositoryPort,
        )
    }

    @Provides
    @Singleton
    fun provideChatViewModelDependencies(
        runtimeBindings: ChatViewModelRuntimeBindings,
    ): ChatViewModelDependencies {
        HiltChatViewModelDependencies.configureRuntimeBindingsProvider { runtimeBindings }
        return HiltChatViewModelDependencies
    }
}

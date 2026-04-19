package com.astrbot.android.di.hilt

import com.astrbot.android.feature.bot.data.LegacyBotRepositoryAdapter
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.chat.data.LegacyConversationRepositoryAdapter
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.config.data.LegacyConfigRepositoryAdapter
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.persona.data.LegacyPersonaRepositoryAdapter
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.data.LegacyProviderRepositoryAdapter
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.data.LegacyQqConversationAdapter
import com.astrbot.android.feature.qq.data.LegacyQqPlatformConfigAdapter
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object RepositoryPortModule {

    @Provides
    @Singleton
    fun provideBotRepositoryPort(): BotRepositoryPort = LegacyBotRepositoryAdapter()

    @Provides
    @Singleton
    fun provideConfigRepositoryPort(): ConfigRepositoryPort = LegacyConfigRepositoryAdapter()

    @Provides
    @Singleton
    fun providePersonaRepositoryPort(): PersonaRepositoryPort = LegacyPersonaRepositoryAdapter()

    @Provides
    @Singleton
    fun provideProviderRepositoryPort(): ProviderRepositoryPort = LegacyProviderRepositoryAdapter()

    @Provides
    @Singleton
    fun provideConversationRepositoryPort(): ConversationRepositoryPort = LegacyConversationRepositoryAdapter()

    @Provides
    @Singleton
    fun provideQqConversationPort(): QqConversationPort = LegacyQqConversationAdapter()

    @Provides
    @Singleton
    fun provideQqPlatformConfigPort(): QqPlatformConfigPort = LegacyQqPlatformConfigAdapter()
}

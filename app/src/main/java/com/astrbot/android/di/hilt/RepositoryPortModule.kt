package com.astrbot.android.di.hilt

import com.astrbot.android.feature.bot.data.FeatureBotRepositoryPortAdapter
import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.chat.data.FeatureConversationRepositoryPortAdapter
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.config.data.FeatureConfigRepositoryPortAdapter
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.cron.data.FeatureCronJobRepositoryPortAdapter
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.persona.data.FeaturePersonaRepositoryPortAdapter
import com.astrbot.android.feature.persona.domain.PersonaRepositoryPort
import com.astrbot.android.feature.provider.data.FeatureProviderRepositoryPortAdapter
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.data.FeatureQqConversationPortAdapter
import com.astrbot.android.feature.qq.data.FeatureQqPlatformConfigPortAdapter
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqPlatformConfigPort
import com.astrbot.android.feature.resource.data.FeatureResourceCenterPortAdapter
import com.astrbot.android.feature.resource.domain.ResourceCenterPort
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
    fun provideBotRepositoryPort(
        adapter: FeatureBotRepositoryPortAdapter,
    ): BotRepositoryPort = adapter

    @Provides
    @Singleton
    fun provideConfigRepositoryPort(
        adapter: FeatureConfigRepositoryPortAdapter,
    ): ConfigRepositoryPort = adapter

    @Provides
    @Singleton
    fun providePersonaRepositoryPort(
        adapter: FeaturePersonaRepositoryPortAdapter,
    ): PersonaRepositoryPort = adapter

    @Provides
    @Singleton
    fun provideProviderRepositoryPort(
        adapter: FeatureProviderRepositoryPortAdapter,
    ): ProviderRepositoryPort = adapter

    @Provides
    @Singleton
    fun provideConversationRepositoryPort(
        adapter: FeatureConversationRepositoryPortAdapter,
    ): ConversationRepositoryPort = adapter

    @Provides
    @Singleton
    fun provideCronJobRepositoryPort(
        adapter: FeatureCronJobRepositoryPortAdapter,
    ): CronJobRepositoryPort = adapter

    @Provides
    @Singleton
    fun provideQqConversationPort(
        adapter: FeatureQqConversationPortAdapter,
    ): QqConversationPort = adapter

    @Provides
    @Singleton
    fun provideQqPlatformConfigPort(
        adapter: FeatureQqPlatformConfigPortAdapter,
    ): QqPlatformConfigPort = adapter

    @Provides
    @Singleton
    fun provideResourceCenterPort(
        adapter: FeatureResourceCenterPortAdapter,
    ): ResourceCenterPort = adapter
}

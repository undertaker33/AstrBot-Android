package com.astrbot.android.di.hilt

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.astrbot.android.data.db.BotAggregateDao
import com.astrbot.android.data.db.ConfigAggregateDao
import com.astrbot.android.data.db.ConversationAggregateDao
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.PersonaAggregateDao
import com.astrbot.android.data.db.PluginCatalogDao
import com.astrbot.android.data.db.PluginConfigSnapshotDao
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.PluginStateEntryDao
import com.astrbot.android.data.db.ProviderAggregateDao
import com.astrbot.android.data.db.TtsVoiceAssetAggregateDao
import com.astrbot.android.data.db.cron.CronJobDao
import com.astrbot.android.data.db.cron.CronJobExecutionRecordDao
import com.astrbot.android.data.db.resource.ResourceCenterDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import javax.inject.Named

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideAstrBotDatabase(
        @ApplicationContext appContext: Context,
    ): AstrBotDatabase = Room.databaseBuilder(
        appContext.applicationContext,
        AstrBotDatabase::class.java,
        "astrbot-native.db",
    )
        .addMigrations(*AstrBotDatabase.allMigrations)
        .build()

    @Provides
    fun provideAppPreferenceDao(
        database: AstrBotDatabase,
    ): AppPreferenceDao = database.appPreferenceDao()

    @Provides
    fun provideBotAggregateDao(
        database: AstrBotDatabase,
    ): BotAggregateDao = database.botAggregateDao()

    @Provides
    fun provideConfigAggregateDao(
        database: AstrBotDatabase,
    ): ConfigAggregateDao = database.configAggregateDao()

    @Provides
    fun provideConversationAggregateDao(
        database: AstrBotDatabase,
    ): ConversationAggregateDao = database.conversationAggregateDao()

    @Provides
    fun providePersonaAggregateDao(
        database: AstrBotDatabase,
    ): PersonaAggregateDao = database.personaAggregateDao()

    @Provides
    fun providePluginInstallAggregateDao(
        database: AstrBotDatabase,
    ): PluginInstallAggregateDao = database.pluginInstallAggregateDao()

    @Provides
    fun providePluginCatalogDao(
        database: AstrBotDatabase,
    ): PluginCatalogDao = database.pluginCatalogDao()

    @Provides
    fun providePluginConfigSnapshotDao(
        database: AstrBotDatabase,
    ): PluginConfigSnapshotDao = database.pluginConfigSnapshotDao()

    @Provides
    fun providePluginStateEntryDao(
        database: AstrBotDatabase,
    ): PluginStateEntryDao = database.pluginStateEntryDao()

    @Provides
    fun provideProviderAggregateDao(
        database: AstrBotDatabase,
    ): ProviderAggregateDao = database.providerAggregateDao()

    @Provides
    fun provideResourceCenterDao(
        database: AstrBotDatabase,
    ): ResourceCenterDao = database.resourceCenterDao()

    @Provides
    fun provideCronJobDao(
        database: AstrBotDatabase,
    ): CronJobDao = database.cronJobDao()

    @Provides
    fun provideCronJobExecutionRecordDao(
        database: AstrBotDatabase,
    ): CronJobExecutionRecordDao = database.cronJobExecutionRecordDao()

    @Provides
    fun provideTtsVoiceAssetAggregateDao(
        database: AstrBotDatabase,
    ): TtsVoiceAssetAggregateDao = database.ttsVoiceAssetAggregateDao()

    @Provides
    @Named("botBindingsPreferences")
    fun provideBotBindingsPreferences(
        @ApplicationContext appContext: Context,
    ): SharedPreferences = appContext.getSharedPreferences("bot_bindings", Context.MODE_PRIVATE)

    @Provides
    @Named("configProfilesPreferences")
    fun provideConfigProfilesPreferences(
        @ApplicationContext appContext: Context,
    ): SharedPreferences = appContext.getSharedPreferences("config_profiles", Context.MODE_PRIVATE)

    @Provides
    @Named("personaProfilesPreferences")
    fun providePersonaProfilesPreferences(
        @ApplicationContext appContext: Context,
    ): SharedPreferences = appContext.getSharedPreferences("persona_profiles", Context.MODE_PRIVATE)

    @Provides
    @Named("providerProfilesPreferences")
    fun provideProviderProfilesPreferences(
        @ApplicationContext appContext: Context,
    ): SharedPreferences = appContext.getSharedPreferences("provider_profiles", Context.MODE_PRIVATE)

    @Provides
    @Named("legacyConversationStorageFile")
    fun provideLegacyConversationStorageFile(
        @ApplicationContext appContext: Context,
    ): File = File(appContext.filesDir, "persistent_conversations.json")
}

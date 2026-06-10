package com.elymbot.android.app.integration.db

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Room
import com.elymbot.android.data.db.BotAggregateDao
import com.elymbot.android.data.db.ConfigAggregateDao
import com.elymbot.android.data.db.ConversationAggregateDao
import com.elymbot.android.data.db.ElymBotDatabase
import com.elymbot.android.data.db.AppPreferenceDao
import com.elymbot.android.data.db.PersonaAggregateDao
import com.elymbot.android.data.db.PluginCatalogDao
import com.elymbot.android.data.db.PluginConfigSnapshotDao
import com.elymbot.android.data.db.PluginInstallAggregateDao
import com.elymbot.android.data.db.PluginStateEntryDao
import com.elymbot.android.data.db.ProviderAggregateDao
import com.elymbot.android.data.db.TtsVoiceAssetAggregateDao
import com.elymbot.android.data.db.astrBotDatabaseMigrations
import com.elymbot.android.data.db.cron.CronJobDao
import com.elymbot.android.data.db.cron.CronJobExecutionRecordDao
import com.elymbot.android.data.db.geofence.GeofenceRuleDao
import com.elymbot.android.data.db.resource.ResourceCenterDao
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
    fun provideElymBotDatabase(
        @ApplicationContext appContext: Context,
    ): ElymBotDatabase = Room.databaseBuilder(
        appContext.applicationContext,
        ElymBotDatabase::class.java,
        "elymbot-native.db",
    )
        .addMigrations(*astrBotDatabaseMigrations)
        .build()

    @Provides
    fun provideAppPreferenceDao(
        database: ElymBotDatabase,
    ): AppPreferenceDao = database.appPreferenceDao()

    @Provides
    fun provideBotAggregateDao(
        database: ElymBotDatabase,
    ): BotAggregateDao = database.botAggregateDao()

    @Provides
    fun provideConfigAggregateDao(
        database: ElymBotDatabase,
    ): ConfigAggregateDao = database.configAggregateDao()

    @Provides
    fun provideConversationAggregateDao(
        database: ElymBotDatabase,
    ): ConversationAggregateDao = database.conversationAggregateDao()

    @Provides
    fun providePersonaAggregateDao(
        database: ElymBotDatabase,
    ): PersonaAggregateDao = database.personaAggregateDao()

    @Provides
    fun providePluginInstallAggregateDao(
        database: ElymBotDatabase,
    ): PluginInstallAggregateDao = database.pluginInstallAggregateDao()

    @Provides
    fun providePluginCatalogDao(
        database: ElymBotDatabase,
    ): PluginCatalogDao = database.pluginCatalogDao()

    @Provides
    fun providePluginConfigSnapshotDao(
        database: ElymBotDatabase,
    ): PluginConfigSnapshotDao = database.pluginConfigSnapshotDao()

    @Provides
    fun providePluginStateEntryDao(
        database: ElymBotDatabase,
    ): PluginStateEntryDao = database.pluginStateEntryDao()

    @Provides
    fun provideProviderAggregateDao(
        database: ElymBotDatabase,
    ): ProviderAggregateDao = database.providerAggregateDao()

    @Provides
    fun provideResourceCenterDao(
        database: ElymBotDatabase,
    ): ResourceCenterDao = database.resourceCenterDao()

    @Provides
    fun provideCronJobDao(
        database: ElymBotDatabase,
    ): CronJobDao = database.cronJobDao()

    @Provides
    fun provideCronJobExecutionRecordDao(
        database: ElymBotDatabase,
    ): CronJobExecutionRecordDao = database.cronJobExecutionRecordDao()

    @Provides
    fun provideGeofenceRuleDao(
        database: ElymBotDatabase,
    ): GeofenceRuleDao = database.geofenceRuleDao()

    @Provides
    fun provideTtsVoiceAssetAggregateDao(
        database: ElymBotDatabase,
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

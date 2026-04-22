package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.AppPreferenceDao
import com.astrbot.android.data.db.PluginCatalogDao
import com.astrbot.android.data.db.PluginConfigSnapshotDao
import com.astrbot.android.data.db.PluginInstallAggregateDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object DatabaseModule {

    @Provides
    @Singleton
    fun provideAstrBotDatabase(
        @ApplicationContext appContext: Context,
    ): AstrBotDatabase = AstrBotDatabase.get(appContext)

    @Provides
    fun provideAppPreferenceDao(
        database: AstrBotDatabase,
    ): AppPreferenceDao = database.appPreferenceDao()

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
}

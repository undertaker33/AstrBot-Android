package com.astrbot.android.di.hilt.presentation

import com.astrbot.android.di.hilt.PluginCatalogEntries
import com.astrbot.android.di.hilt.PluginGovernanceReadModels
import com.astrbot.android.di.hilt.PluginRecords
import com.astrbot.android.di.hilt.PluginRepositorySources
import com.astrbot.android.feature.plugin.data.FeaturePluginRepositoryStateOwner
import com.astrbot.android.feature.plugin.data.PluginRepositoryStatePort
import com.astrbot.android.feature.plugin.presentation.bindings.PluginManagementBindings
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceReadModel
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceRepository
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.ui.viewmodel.DefaultPluginViewModelBindings
import com.astrbot.android.ui.viewmodel.PluginViewModelBindings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PluginViewModelBindingsModule {

    @Binds
    @Singleton
    abstract fun bindPluginViewModelBindings(
        bindings: DefaultPluginViewModelBindings,
    ): PluginViewModelBindings

    @Binds
    @Singleton
    abstract fun bindPluginManagementBindings(
        bindings: DefaultPluginViewModelBindings,
    ): PluginManagementBindings

    companion object {

        @Provides
        @PluginRecords
        fun providePluginRecords(
            pluginRepositoryStateOwner: FeaturePluginRepositoryStateOwner,
        ): StateFlow<@JvmSuppressWildcards List<PluginInstallRecord>> = pluginRepositoryStateOwner.records

        @Provides
        @PluginRepositorySources
        fun providePluginRepositorySources(
            pluginRepositoryStateOwner: FeaturePluginRepositoryStateOwner,
        ): StateFlow<@JvmSuppressWildcards List<PluginRepositorySource>> = pluginRepositoryStateOwner.repositorySources

        @Provides
        @PluginCatalogEntries
        fun providePluginCatalogEntries(
            pluginRepositoryStateOwner: FeaturePluginRepositoryStateOwner,
        ): StateFlow<@JvmSuppressWildcards List<PluginCatalogEntryRecord>> = pluginRepositoryStateOwner.catalogEntries

        @Provides
        @Singleton
        fun providePluginGovernanceRepository(
            repositoryStatePort: PluginRepositoryStatePort,
            activeRuntimeStore: PluginV2ActiveRuntimeStore,
            failureStateStore: PluginFailureStateStore,
            logBus: PluginRuntimeLogBus,
        ): PluginGovernanceRepository = PluginGovernanceRepository(
            repositoryStatePort = repositoryStatePort,
            runtimeSnapshotProvider = activeRuntimeStore::snapshot,
            failureStateStore = failureStateStore,
            logBus = logBus,
        )

        @Provides
        @PluginGovernanceReadModels
        fun providePluginGovernanceReadModels(
            repository: PluginGovernanceRepository,
        ): Flow<@JvmSuppressWildcards Map<String, PluginGovernanceReadModel>> = repository.observeReadModels()
    }
}

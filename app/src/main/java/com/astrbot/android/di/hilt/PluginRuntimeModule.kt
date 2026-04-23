package com.astrbot.android.di.hilt

import com.astrbot.android.feature.plugin.data.PluginRepositoryStatePort
import com.astrbot.android.feature.plugin.data.EmptyPluginRepositoryStatePort
import com.astrbot.android.feature.plugin.data.FeaturePluginRepositoryStateOwner
import com.astrbot.android.feature.plugin.data.config.DefaultPluginHostConfigResolver
import com.astrbot.android.feature.plugin.data.config.PluginConfigStorage
import com.astrbot.android.feature.plugin.data.config.PluginHostConfigResolver
import com.astrbot.android.feature.plugin.data.config.RoomPluginConfigStorage
import com.astrbot.android.feature.plugin.data.state.PluginStateStore
import com.astrbot.android.feature.plugin.data.state.RoomPluginStateStore
import com.astrbot.android.data.db.PluginStateEntryDao
import com.astrbot.android.feature.plugin.domain.cleanup.DefaultPluginDataCleanupService
import com.astrbot.android.feature.plugin.domain.cleanup.DefaultPluginRuntimeArtifactCleaner
import com.astrbot.android.feature.plugin.domain.cleanup.PluginDataCleanupService
import com.astrbot.android.feature.plugin.domain.cleanup.PluginRuntimeArtifactCleaner
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduler
import com.astrbot.android.feature.plugin.runtime.PluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.PluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.PluginV2FilterEvaluator
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.PluginV2RegistryCompiler
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeSessionFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object PluginRuntimeModule {

    @Provides
    @Singleton
    fun providePluginRepositoryStatePort(
        owner: FeaturePluginRepositoryStateOwner,
    ): PluginRepositoryStatePort = owner

    @Provides
    @Singleton
    fun providePluginConfigStorage(
        storage: RoomPluginConfigStorage,
    ): PluginConfigStorage = storage

    @Provides
    @Singleton
    fun providePluginHostConfigResolver(
        resolver: DefaultPluginHostConfigResolver,
    ): PluginHostConfigResolver = resolver

    @Provides
    @Singleton
    fun providePluginStateStore(
        dao: PluginStateEntryDao,
    ): PluginStateStore = RoomPluginStateStore(
        dao = dao,
    )

    @Provides
    @Singleton
    fun providePluginRuntimeArtifactCleaner(
        cleaner: DefaultPluginRuntimeArtifactCleaner,
    ): PluginRuntimeArtifactCleaner = cleaner

    @Provides
    @Singleton
    fun providePluginDataCleanupService(
        service: DefaultPluginDataCleanupService,
    ): PluginDataCleanupService = service

    @Provides
    @Singleton
    fun providePluginRuntimeLogBus(): PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus()

    @Provides
    @Singleton
    fun providePluginFailureStateStore(): PluginFailureStateStore = InMemoryPluginFailureStateStore()

    @Provides
    @Singleton
    fun providePluginScopedFailureStateStore(): PluginScopedFailureStateStore = InMemoryPluginScopedFailureStateStore()

    @Provides
    @Singleton
    fun providePluginScheduleStateStore(): PluginScheduleStateStore = InMemoryPluginScheduleStateStore()

    @Provides
    @Singleton
    fun providePluginV2ActiveRuntimeStore(
        logBus: PluginRuntimeLogBus,
    ): PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStore(logBus = logBus)

    @Provides
    @Singleton
    fun providePluginFailureGuard(
        failureStateStore: PluginFailureStateStore,
        scopedFailureStateStore: PluginScopedFailureStateStore,
        logBus: PluginRuntimeLogBus,
    ): PluginFailureGuard = PluginFailureGuard(
        store = failureStateStore,
        scopedStore = scopedFailureStateStore,
        logBus = logBus,
    )

    @Provides
    @Singleton
    fun providePluginRuntimeScheduler(
        scheduleStateStore: PluginScheduleStateStore,
    ): PluginRuntimeScheduler = PluginRuntimeScheduler(
        store = scheduleStateStore,
    )

    @Provides
    @Singleton
    fun providePluginV2LifecycleManager(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
    ): PluginV2LifecycleManager = PluginV2LifecycleManager(
        clock = System::currentTimeMillis,
        logBus = logBus,
        store = activeRuntimeStore,
    )

    @Provides
    @Singleton
    fun providePluginV2DispatchEngine(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
    ): PluginV2DispatchEngine = PluginV2DispatchEngine(
        clock = System::currentTimeMillis,
        logBus = logBus,
        store = activeRuntimeStore,
        lifecycleManager = lifecycleManager,
        filterEvaluator = PluginV2FilterEvaluator(
            logBus = logBus,
        ),
    )

    @Provides
    @Singleton
    fun providePluginV2RuntimeLoader(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        repositoryStatePort: PluginRepositoryStatePort,
        stateStore: PluginStateStore,
    ): PluginV2RuntimeLoader = createPluginV2RuntimeLoader(
        activeRuntimeStore = activeRuntimeStore,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
        repositoryStatePort = repositoryStatePort,
        stateStore = stateStore,
    )

    internal fun providePluginV2RuntimeLoader(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        stateStore: PluginStateStore,
    ): PluginV2RuntimeLoader = createPluginV2RuntimeLoader(
        activeRuntimeStore = activeRuntimeStore,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
        repositoryStatePort = EmptyPluginRepositoryStatePort,
        stateStore = stateStore,
    )

    private fun createPluginV2RuntimeLoader(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
        repositoryStatePort: PluginRepositoryStatePort,
        stateStore: PluginStateStore,
    ): PluginV2RuntimeLoader = PluginV2RuntimeLoader(
        sessionFactory = PluginV2RuntimeSessionFactory(),
        compiler = PluginV2RegistryCompiler(logBus = logBus),
        clock = System::currentTimeMillis,
        logBus = logBus,
        store = activeRuntimeStore,
        lifecycleManager = lifecycleManager,
        repositoryStatePort = repositoryStatePort,
        stateStore = stateStore,
    )
}

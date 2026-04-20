package com.astrbot.android.di.hilt

import com.astrbot.android.feature.plugin.runtime.InMemoryPluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.InMemoryPluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginFailureGuard
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBusProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduler
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeFailureStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScheduleStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeScopedFailureStateStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginScheduleStateStore
import com.astrbot.android.feature.plugin.runtime.PluginScopedFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStoreProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngineProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2FilterEvaluator
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManager
import com.astrbot.android.feature.plugin.runtime.PluginV2LifecycleManagerProvider
import com.astrbot.android.feature.plugin.runtime.PluginV2RegistryCompiler
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoader
import com.astrbot.android.feature.plugin.runtime.PluginV2RuntimeLoaderProvider
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
    fun providePluginRuntimeLogBus(): PluginRuntimeLogBus = InMemoryPluginRuntimeLogBus().also {
        PluginRuntimeLogBusProvider.setBusOverrideForTests(it)
    }

    @Provides
    @Singleton
    fun providePluginFailureStateStore(): PluginFailureStateStore = InMemoryPluginFailureStateStore().also {
        PluginRuntimeFailureStateStoreProvider.setStoreOverrideForTests(it)
    }

    @Provides
    @Singleton
    fun providePluginScopedFailureStateStore(): PluginScopedFailureStateStore = InMemoryPluginScopedFailureStateStore().also {
        PluginRuntimeScopedFailureStateStoreProvider.setStoreOverrideForTests(it)
    }

    @Provides
    @Singleton
    fun providePluginScheduleStateStore(): PluginScheduleStateStore = InMemoryPluginScheduleStateStore().also {
        PluginRuntimeScheduleStateStoreProvider.setStoreOverrideForTests(it)
    }

    @Provides
    @Singleton
    fun providePluginV2ActiveRuntimeStore(
        logBus: PluginRuntimeLogBus,
    ): PluginV2ActiveRuntimeStore = PluginV2ActiveRuntimeStore(logBus = logBus).also {
        PluginV2ActiveRuntimeStoreProvider.setStoreOverrideForTests(it)
    }

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
        store = activeRuntimeStore,
        logBus = logBus,
    ).also {
        PluginV2LifecycleManagerProvider.setManagerOverrideForTests(it)
    }

    @Provides
    @Singleton
    fun providePluginV2DispatchEngine(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
    ): PluginV2DispatchEngine = PluginV2DispatchEngine(
        store = activeRuntimeStore,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
        filterEvaluator = PluginV2FilterEvaluator(
            logBus = logBus,
        ),
    ).also {
        PluginV2DispatchEngineProvider.setEngineOverrideForTests(it)
    }

    @Provides
    @Singleton
    fun providePluginV2RuntimeLoader(
        activeRuntimeStore: PluginV2ActiveRuntimeStore,
        logBus: PluginRuntimeLogBus,
        lifecycleManager: PluginV2LifecycleManager,
    ): PluginV2RuntimeLoader = PluginV2RuntimeLoader(
        sessionFactory = PluginV2RuntimeSessionFactory(),
        compiler = PluginV2RegistryCompiler(),
        store = activeRuntimeStore,
        logBus = logBus,
        lifecycleManager = lifecycleManager,
    ).also {
        PluginV2RuntimeLoaderProvider.setLoaderOverrideForTests(it)
    }
}

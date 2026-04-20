@file:Suppress("DEPRECATION")

package com.astrbot.android.di.hilt

import android.content.Context
import com.astrbot.android.core.runtime.context.RuntimeContextResolverPort
import com.astrbot.android.core.runtime.llm.LlmClientPort
import com.astrbot.android.core.runtime.llm.LlmProviderProbePort
import com.astrbot.android.data.RuntimeAssetRepository
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.feature.config.data.RoomPhase3DataTransactionService
import com.astrbot.android.feature.config.domain.Phase3DataTransactionService
import com.astrbot.android.feature.provider.runtime.DefaultProviderRuntimePort
import com.astrbot.android.feature.provider.runtime.ProviderRuntimePort
import com.astrbot.android.feature.chat.domain.ConversationRepositoryPort
import com.astrbot.android.feature.plugin.data.FeaturePluginRepository
import com.astrbot.android.ui.viewmodel.DefaultPluginViewModelBindings
import com.astrbot.android.ui.viewmodel.DefaultQQLoginViewModelBindings
import com.astrbot.android.ui.viewmodel.PluginViewModelBindings
import com.astrbot.android.ui.viewmodel.QQLoginViewModelBindings
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceReadModel
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceRepository
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.feature.qq.data.NapCatBridgeRepository
import com.astrbot.android.feature.plugin.runtime.RuntimeLlmOrchestratorPort
import com.astrbot.android.core.runtime.audio.TtsVoiceAssetRepository
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.RuntimeAssetState
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import javax.inject.Qualifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class BotLoginState

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class QqLoginState

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class BridgeConfig

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class BridgeRuntimeState

/**
 * Dependency-driven bridge config save operation, injected into BridgeViewModel
 * so the shell never imports feature/data packages directly.
 */
fun interface BridgeConfigSaver {
    fun save(config: NapCatBridgeConfig)
}

/**
 * Dependency-driven operations for RuntimeAssetViewModel so the shell never
 * imports root data packages directly.
 */
interface RuntimeAssetViewModelOps {
    fun refresh(context: Context)
    suspend fun downloadAsset(context: Context, assetId: String)
    suspend fun clearAsset(context: Context, assetId: String)
    suspend fun downloadOnDeviceTtsModel(context: Context, modelId: String)
    suspend fun clearOnDeviceTtsModel(context: Context, modelId: String)
}

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class RuntimeAssetStateFlow

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginRecords

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginRepositorySources

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginCatalogEntries

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class PluginGovernanceReadModels

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class TtsVoiceAssets

@Module
@InstallIn(SingletonComponent::class)
internal object ViewModelDependencyModule {

    @Provides
    @BridgeConfig
    fun provideBridgeConfig(): StateFlow<NapCatBridgeConfig> = NapCatBridgeRepository.config

    @Provides
    @BridgeRuntimeState
    fun provideBridgeRuntimeState(): StateFlow<NapCatRuntimeState> = NapCatBridgeRepository.runtimeState

    @Provides
    fun provideBridgeConfigSaver(): BridgeConfigSaver = BridgeConfigSaver { config ->
        NapCatBridgeRepository.updateConfig(config)
    }

    @Provides
    fun provideRuntimeAssetViewModelOps(): RuntimeAssetViewModelOps = object : RuntimeAssetViewModelOps {
        override fun refresh(context: Context) = RuntimeAssetRepository.refresh(context)
        override suspend fun downloadAsset(context: Context, assetId: String) = RuntimeAssetRepository.downloadAsset(context, assetId)
        override suspend fun clearAsset(context: Context, assetId: String) = RuntimeAssetRepository.clearAsset(context, assetId)
        override suspend fun downloadOnDeviceTtsModel(context: Context, modelId: String) = RuntimeAssetRepository.downloadOnDeviceTtsModel(context, modelId)
        override suspend fun clearOnDeviceTtsModel(context: Context, modelId: String) = RuntimeAssetRepository.clearOnDeviceTtsModel(context, modelId)
    }

    @Provides
    @PluginRecords
    fun providePluginRecords(): StateFlow<@JvmSuppressWildcards List<PluginInstallRecord>> = FeaturePluginRepository.records

    @Provides
    @PluginRepositorySources
    fun providePluginRepositorySources(): StateFlow<@JvmSuppressWildcards List<PluginRepositorySource>> = FeaturePluginRepository.repositorySources

    @Provides
    @PluginCatalogEntries
    fun providePluginCatalogEntries(): StateFlow<@JvmSuppressWildcards List<PluginCatalogEntryRecord>> = FeaturePluginRepository.catalogEntries

    @Provides
    @Singleton
    fun providePluginGovernanceRepository(): PluginGovernanceRepository = PluginGovernanceRepository()

    @Provides
    @PluginGovernanceReadModels
    fun providePluginGovernanceReadModels(
        repository: PluginGovernanceRepository,
    ): Flow<@JvmSuppressWildcards Map<String, PluginGovernanceReadModel>> = repository.observeReadModels()

    @Provides
    @Singleton
    fun providePluginViewModelBindings(
        bindings: DefaultPluginViewModelBindings,
    ): PluginViewModelBindings = bindings

    @Provides
    @Singleton
    fun provideQqLoginViewModelBindings(
        bindings: DefaultQQLoginViewModelBindings,
    ): QQLoginViewModelBindings = bindings

    @Provides
    @RuntimeAssetStateFlow
    fun provideRuntimeAssetState(): StateFlow<RuntimeAssetState> = RuntimeAssetRepository.state

    @Provides
    @BotLoginState
    fun provideBotLoginState(): StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    @Provides
    @QqLoginState
    fun provideQqLoginState(): StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    @Provides
    @TtsVoiceAssets
    fun provideTtsVoiceAssets(): StateFlow<@JvmSuppressWildcards List<TtsVoiceReferenceAsset>> = TtsVoiceAssetRepository.assets

    @Provides
    @Singleton
    fun providePhase3DataTransactionService(
        database: AstrBotDatabase,
    ): Phase3DataTransactionService = RoomPhase3DataTransactionService(database)

    @Provides
    @Singleton
    fun provideProviderRuntimePort(probePort: LlmProviderProbePort): ProviderRuntimePort = DefaultProviderRuntimePort(probePort)

    @Provides
    @Singleton
    fun provideChatViewModelRuntimeBindings(
        bindings: DefaultChatViewModelRuntimeBindings,
    ): ChatViewModelRuntimeBindings = bindings
}

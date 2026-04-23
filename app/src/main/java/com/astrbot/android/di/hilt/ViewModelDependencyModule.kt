package com.astrbot.android.di.hilt

import android.content.Context
import android.net.Uri
import com.astrbot.android.data.RuntimeAssetStateOwner
import com.astrbot.android.feature.config.data.RoomPhase3DataTransactionService
import com.astrbot.android.feature.config.domain.Phase3DataTransactionService
import com.astrbot.android.feature.provider.runtime.DefaultProviderRuntimePort
import com.astrbot.android.feature.provider.runtime.ProviderRuntimePort
import com.astrbot.android.feature.plugin.data.FeaturePluginRepositoryStateOwner
import com.astrbot.android.feature.plugin.data.PluginRepositoryStatePort
import com.astrbot.android.feature.plugin.presentation.bindings.PluginManagementBindings
import com.astrbot.android.ui.viewmodel.DefaultPluginViewModelBindings
import com.astrbot.android.ui.viewmodel.DefaultQQLoginViewModelBindings
import com.astrbot.android.ui.viewmodel.PluginViewModelBindings
import com.astrbot.android.ui.viewmodel.QQLoginViewModelBindings
import com.astrbot.android.feature.plugin.runtime.PluginFailureStateStore
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceReadModel
import com.astrbot.android.feature.plugin.runtime.PluginGovernanceRepository
import com.astrbot.android.feature.plugin.runtime.PluginRuntimeLogBus
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeStore
import com.astrbot.android.feature.qq.data.NapCatLoginRepository
import com.astrbot.android.feature.qq.data.NapCatBridgeStateOwner
import com.astrbot.android.core.runtime.audio.TtsVoiceAssetRepository
import com.astrbot.android.core.runtime.audio.TtsVoiceAssetRepository.ImportReferenceAudioResult
import com.astrbot.android.model.NapCatBridgeConfig
import com.astrbot.android.model.NapCatLoginState
import com.astrbot.android.model.NapCatRuntimeState
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.RuntimeAssetState
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
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

@Singleton
internal class TtsVoiceAssetStateOwner @Inject constructor(
    private val repository: TtsVoiceAssetRepository,
) {
    val assets: StateFlow<List<TtsVoiceReferenceAsset>> = repository.assets

    fun listVoiceChoicesFor(provider: ProviderProfile?): List<Pair<String, String>> {
        return repository.listVoiceChoicesFor(provider)
    }

    fun importReferenceAudio(
        context: Context,
        sourceUri: Uri,
        name: String = "",
        assetId: String? = null,
    ): ImportReferenceAudioResult {
        return repository.importReferenceAudio(
            context = context,
            sourceUri = sourceUri,
            name = name,
            assetId = assetId,
        )
    }

    fun saveProviderBinding(
        assetId: String,
        providerId: String,
        providerType: ProviderType,
        model: String,
        voiceId: String,
        displayName: String,
    ) {
        repository.saveProviderBinding(
            assetId = assetId,
            providerId = providerId,
            providerType = providerType,
            model = model,
            voiceId = voiceId,
            displayName = displayName,
        )
    }

    fun renameBinding(assetId: String, bindingId: String, displayName: String) {
        repository.renameBinding(
            assetId = assetId,
            bindingId = bindingId,
            displayName = displayName,
        )
    }

    fun clearReferenceAudio(assetId: String) {
        repository.clearReferenceAudio(assetId)
    }

    fun deleteReferenceClip(assetId: String, clipId: String) {
        repository.deleteReferenceClip(assetId, clipId)
    }

    fun deleteBinding(assetId: String, bindingId: String) {
        repository.deleteBinding(assetId, bindingId)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class ViewModelDependencyModule {

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

    @Binds
    @Singleton
    abstract fun bindQqLoginViewModelBindings(
        bindings: DefaultQQLoginViewModelBindings,
    ): QQLoginViewModelBindings

    @Binds
    @Singleton
    abstract fun bindPhase3DataTransactionService(
        service: RoomPhase3DataTransactionService,
    ): Phase3DataTransactionService

    @Binds
    @Singleton
    abstract fun bindProviderRuntimePort(
        runtimePort: DefaultProviderRuntimePort,
    ): ProviderRuntimePort

    @Binds
    @Singleton
    abstract fun bindChatViewModelRuntimeBindings(
        bindings: DefaultChatViewModelRuntimeBindings,
    ): ChatViewModelRuntimeBindings

    companion object {

    @Provides
    @BridgeConfig
    fun provideBridgeConfig(
        bridgeStateOwner: NapCatBridgeStateOwner,
    ): StateFlow<NapCatBridgeConfig> = bridgeStateOwner.config

    @Provides
    @BridgeRuntimeState
    fun provideBridgeRuntimeState(
        bridgeStateOwner: NapCatBridgeStateOwner,
    ): StateFlow<NapCatRuntimeState> = bridgeStateOwner.runtimeState

    @Provides
    fun provideBridgeConfigSaver(
        bridgeStateOwner: NapCatBridgeStateOwner,
    ): BridgeConfigSaver = BridgeConfigSaver(bridgeStateOwner::updateConfig)

    @Provides
    fun provideRuntimeAssetViewModelOps(
        runtimeAssetStateOwner: RuntimeAssetStateOwner,
    ): RuntimeAssetViewModelOps = object : RuntimeAssetViewModelOps {
        override fun refresh(context: Context) = runtimeAssetStateOwner.refresh(context)
        override suspend fun downloadAsset(context: Context, assetId: String) = runtimeAssetStateOwner.downloadAsset(context, assetId)
        override suspend fun clearAsset(context: Context, assetId: String) = runtimeAssetStateOwner.clearAsset(context, assetId)
        override suspend fun downloadOnDeviceTtsModel(context: Context, modelId: String) = runtimeAssetStateOwner.downloadOnDeviceTtsModel(context, modelId)
        override suspend fun clearOnDeviceTtsModel(context: Context, modelId: String) = runtimeAssetStateOwner.clearOnDeviceTtsModel(context, modelId)
    }

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

    @Provides
    @RuntimeAssetStateFlow
    fun provideRuntimeAssetState(
        runtimeAssetStateOwner: RuntimeAssetStateOwner,
    ): StateFlow<RuntimeAssetState> = runtimeAssetStateOwner.state

    @Provides
    @BotLoginState
    fun provideBotLoginState(): StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    @Provides
    @QqLoginState
    fun provideQqLoginState(): StateFlow<NapCatLoginState> = NapCatLoginRepository.loginState

    @Provides
    @TtsVoiceAssets
    fun provideTtsVoiceAssets(
        ttsVoiceAssetStateOwner: TtsVoiceAssetStateOwner,
    ): StateFlow<@JvmSuppressWildcards List<TtsVoiceReferenceAsset>> = ttsVoiceAssetStateOwner.assets
    }
}

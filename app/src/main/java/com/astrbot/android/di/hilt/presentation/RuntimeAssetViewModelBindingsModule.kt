package com.astrbot.android.di.hilt.presentation

import android.content.Context
import com.astrbot.android.data.RuntimeAssetStateOwner
import com.astrbot.android.di.hilt.RuntimeAssetStateFlow
import com.astrbot.android.di.hilt.RuntimeAssetViewModelOps
import com.astrbot.android.di.hilt.TtsVoiceAssetStateOwner
import com.astrbot.android.di.hilt.TtsVoiceAssets
import com.astrbot.android.model.RuntimeAssetState
import com.astrbot.android.model.TtsVoiceReferenceAsset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.StateFlow

@Module
@InstallIn(SingletonComponent::class)
internal object RuntimeAssetViewModelBindingsModule {

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
    @RuntimeAssetStateFlow
    fun provideRuntimeAssetState(
        runtimeAssetStateOwner: RuntimeAssetStateOwner,
    ): StateFlow<RuntimeAssetState> = runtimeAssetStateOwner.state

    @Provides
    @TtsVoiceAssets
    fun provideTtsVoiceAssets(
        ttsVoiceAssetStateOwner: TtsVoiceAssetStateOwner,
    ): StateFlow<@JvmSuppressWildcards List<TtsVoiceReferenceAsset>> = ttsVoiceAssetStateOwner.assets
}

package com.astrbot.android.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class TtsVoiceAssetAggregate(
    @Embedded val asset: TtsVoiceAssetEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "assetId",
    )
    val clips: List<TtsVoiceClipEntity>,
    @Relation(
        parentColumn = "id",
        entityColumn = "assetId",
    )
    val providerBindings: List<TtsVoiceProviderBindingEntity>,
)

data class TtsVoiceAssetWriteModel(
    val asset: TtsVoiceAssetEntity,
    val clips: List<TtsVoiceClipEntity>,
    val providerBindings: List<TtsVoiceProviderBindingEntity>,
)

package com.astrbot.android.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class ProviderAggregate(
    @Embedded val provider: ProviderEntity,
    @Relation(parentColumn = "id", entityColumn = "providerId")
    val capabilities: List<ProviderCapabilityEntity>,
    @Relation(parentColumn = "id", entityColumn = "providerId")
    val ttsVoiceOptions: List<ProviderTtsVoiceOptionEntity>,
)

data class ProviderWriteModel(
    val provider: ProviderEntity,
    val capabilities: List<ProviderCapabilityEntity>,
    val ttsVoiceOptions: List<ProviderTtsVoiceOptionEntity>,
)

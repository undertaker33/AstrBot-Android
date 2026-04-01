package com.astrbot.android.data.db

import com.astrbot.android.model.ClonedVoiceBinding
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.TtsVoiceReferenceAsset
import com.astrbot.android.model.TtsVoiceReferenceClip

fun TtsVoiceAssetAggregate.toModel(): TtsVoiceReferenceAsset {
    return TtsVoiceReferenceAsset(
        id = asset.id,
        name = asset.name,
        source = asset.source,
        localPath = asset.localPath,
        remoteUrl = asset.remoteUrl,
        durationMs = asset.durationMs,
        sampleRateHz = asset.sampleRateHz,
        clips = clips.sortedBy { it.sortIndex }.map { clip ->
            TtsVoiceReferenceClip(
                id = clip.id,
                localPath = clip.localPath,
                durationMs = clip.durationMs,
                sampleRateHz = clip.sampleRateHz,
                createdAt = clip.createdAt,
            )
        },
        providerBindings = providerBindings.sortedBy { it.sortIndex }.map { binding ->
            ClonedVoiceBinding(
                id = binding.id,
                providerId = binding.providerId,
                providerType = runCatching { ProviderType.valueOf(binding.providerType) }
                    .getOrDefault(ProviderType.OPENAI_TTS),
                model = binding.model,
                voiceId = binding.voiceId,
                displayName = binding.displayName,
                createdAt = binding.createdAt,
                lastVerifiedAt = binding.lastVerifiedAt,
                status = binding.status,
            )
        },
        createdAt = asset.createdAt,
    )
}

fun TtsVoiceReferenceAsset.toWriteModel(): TtsVoiceAssetWriteModel {
    return TtsVoiceAssetWriteModel(
        asset = TtsVoiceAssetEntity(
            id = id,
            name = name,
            source = source,
            localPath = localPath,
            remoteUrl = remoteUrl,
            durationMs = durationMs,
            sampleRateHz = sampleRateHz,
            createdAt = createdAt,
            updatedAt = System.currentTimeMillis(),
        ),
        clips = clips.mapIndexed { index, clip ->
            TtsVoiceClipEntity(
                id = clip.id,
                assetId = id,
                localPath = clip.localPath,
                durationMs = clip.durationMs,
                sampleRateHz = clip.sampleRateHz,
                createdAt = clip.createdAt,
                sortIndex = index,
            )
        },
        providerBindings = providerBindings.mapIndexed { index, binding ->
            TtsVoiceProviderBindingEntity(
                id = binding.id,
                assetId = id,
                providerId = binding.providerId,
                providerType = binding.providerType.name,
                model = binding.model,
                voiceId = binding.voiceId,
                displayName = binding.displayName,
                createdAt = binding.createdAt,
                lastVerifiedAt = binding.lastVerifiedAt,
                status = binding.status,
                sortIndex = index,
            )
        },
    )
}

package com.elymbot.android.data.db

import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.persona.domain.model.PersonaCoverMetadata
import com.elymbot.android.feature.persona.domain.model.PersonaCropSpec
import com.elymbot.android.feature.persona.domain.model.normalizePersonaTags

fun PersonaAggregate.toProfile(): PersonaProfile {
    return PersonaProfile(
        id = persona.id,
        name = persona.name,
        tags = normalizePersonaTags(tags.sortedBy { it.sortIndex }.map { it.tag }),
        systemPrompt = prompts.firstOrNull()?.systemPrompt.orEmpty(),
        enabledTools = enabledTools.sortedBy { it.sortIndex }.map { it.toolName }.toSet(),
        defaultProviderId = persona.defaultProviderId,
        maxContextMessages = persona.maxContextMessages,
        enabled = persona.enabled,
        cover = covers.firstOrNull()?.toMetadata(),
    )
}

fun PersonaProfile.toWriteModel(sortIndex: Int): PersonaWriteModel {
    return PersonaWriteModel(
        persona = PersonaEntity(
            id = id,
            name = name,
            defaultProviderId = defaultProviderId,
            maxContextMessages = maxContextMessages,
            enabled = enabled,
            sortIndex = sortIndex,
            updatedAt = System.currentTimeMillis(),
        ),
        prompt = PersonaPromptEntity(id, systemPrompt),
        enabledTools = enabledTools.toList().mapIndexed { index, tool -> PersonaEnabledToolEntity(id, tool, index) },
        tags = normalizePersonaTags(tags).mapIndexed { index, tag -> PersonaTagEntity(id, tag, index) },
        cover = cover?.let { metadata ->
            PersonaCoverAssetEntity(id, metadata.assetRef, metadata.contentSha256, metadata.pixelWidth, metadata.pixelHeight,
                metadata.portraitCrop.centerX, metadata.portraitCrop.centerY, metadata.portraitCrop.zoom,
                metadata.squareCrop.centerX, metadata.squareCrop.centerY, metadata.squareCrop.zoom, metadata.updatedAt)
        },
    )
}

private fun PersonaCoverAssetEntity.toMetadata() = PersonaCoverMetadata(
    assetRef, contentSha256, pixelWidth, pixelHeight,
    PersonaCropSpec(portraitCenterX, portraitCenterY, portraitZoom),
    PersonaCropSpec(squareCenterX, squareCenterY, squareZoom), updatedAt,
)

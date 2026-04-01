package com.astrbot.android.data.db

import com.astrbot.android.model.PersonaProfile

fun PersonaAggregate.toProfile(): PersonaProfile {
    return PersonaProfile(
        id = persona.id,
        name = persona.name,
        tag = persona.tag,
        systemPrompt = prompts.firstOrNull()?.systemPrompt.orEmpty(),
        enabledTools = enabledTools.sortedBy { it.sortIndex }.map { it.toolName }.toSet(),
        defaultProviderId = persona.defaultProviderId,
        maxContextMessages = persona.maxContextMessages,
        enabled = persona.enabled,
    )
}

fun PersonaProfile.toWriteModel(sortIndex: Int): PersonaWriteModel {
    return PersonaWriteModel(
        persona = PersonaEntity(
            id = id,
            name = name,
            tag = tag,
            defaultProviderId = defaultProviderId,
            maxContextMessages = maxContextMessages,
            enabled = enabled,
            sortIndex = sortIndex,
            updatedAt = System.currentTimeMillis(),
        ),
        prompt = PersonaPromptEntity(id, systemPrompt),
        enabledTools = enabledTools.toList().mapIndexed { index, tool -> PersonaEnabledToolEntity(id, tool, index) },
    )
}

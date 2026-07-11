package com.elymbot.android.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class PersonaAggregate(
    @Embedded val persona: PersonaEntity,
    @Relation(parentColumn = "id", entityColumn = "personaId")
    val prompts: List<PersonaPromptEntity>,
    @Relation(parentColumn = "id", entityColumn = "personaId")
    val enabledTools: List<PersonaEnabledToolEntity>,
    @Relation(parentColumn = "id", entityColumn = "personaId") val tags: List<PersonaTagEntity> = emptyList(),
    @Relation(parentColumn = "id", entityColumn = "personaId") val covers: List<PersonaCoverAssetEntity> = emptyList(),
)

data class PersonaWriteModel(
    val persona: PersonaEntity,
    val prompt: PersonaPromptEntity,
    val enabledTools: List<PersonaEnabledToolEntity>,
    val tags: List<PersonaTagEntity>,
    val cover: PersonaCoverAssetEntity?,
)

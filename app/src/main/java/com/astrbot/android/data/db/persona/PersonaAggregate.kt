package com.astrbot.android.data.db

import androidx.room.Embedded
import androidx.room.Relation

data class PersonaAggregate(
    @Embedded val persona: PersonaEntity,
    @Relation(parentColumn = "id", entityColumn = "personaId")
    val prompts: List<PersonaPromptEntity>,
    @Relation(parentColumn = "id", entityColumn = "personaId")
    val enabledTools: List<PersonaEnabledToolEntity>,
)

data class PersonaWriteModel(
    val persona: PersonaEntity,
    val prompt: PersonaPromptEntity,
    val enabledTools: List<PersonaEnabledToolEntity>,
)

package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "persona_prompts",
    primaryKeys = ["personaId"],
    foreignKeys = [
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class PersonaPromptEntity(
    val personaId: String,
    val systemPrompt: String,
)

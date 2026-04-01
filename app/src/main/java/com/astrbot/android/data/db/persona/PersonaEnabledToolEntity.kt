package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "persona_enabled_tools",
    primaryKeys = ["personaId", "toolName"],
    foreignKeys = [
        ForeignKey(
            entity = PersonaEntity::class,
            parentColumns = ["id"],
            childColumns = ["personaId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["personaId", "sortIndex"])],
)
data class PersonaEnabledToolEntity(
    val personaId: String,
    val toolName: String,
    val sortIndex: Int,
)

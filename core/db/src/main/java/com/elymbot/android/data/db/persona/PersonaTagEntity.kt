package com.elymbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "persona_tags",
    primaryKeys = ["personaId", "tag"],
    foreignKeys = [ForeignKey(entity = PersonaEntity::class, parentColumns = ["id"], childColumns = ["personaId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["personaId", "sortIndex"])],
)
data class PersonaTagEntity(val personaId: String, val tag: String, val sortIndex: Int)

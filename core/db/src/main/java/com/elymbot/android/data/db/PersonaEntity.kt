package com.elymbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "persona_profiles")
data class PersonaEntity(
    @PrimaryKey val id: String,
    val name: String,
    val defaultProviderId: String,
    val maxContextMessages: Int,
    val enabled: Boolean,
    val sortIndex: Int,
    val updatedAt: Long,
)

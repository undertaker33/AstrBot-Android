package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "config_skills",
    primaryKeys = ["configId", "skillId"],
    foreignKeys = [
        ForeignKey(
            entity = ConfigProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["configId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["configId", "sortIndex"])],
)
data class ConfigSkillEntity(
    val configId: String,
    val skillId: String,
    val name: String,
    val description: String,
    val active: Boolean,
    val sortIndex: Int,
)

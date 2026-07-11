package com.elymbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "persona_cover_assets",
    foreignKeys = [ForeignKey(entity = PersonaEntity::class, parentColumns = ["id"], childColumns = ["personaId"], onDelete = ForeignKey.CASCADE)],
)
data class PersonaCoverAssetEntity(
    @PrimaryKey val personaId: String,
    val assetRef: String,
    val contentSha256: String,
    val pixelWidth: Int,
    val pixelHeight: Int,
    val portraitCenterX: Float,
    val portraitCenterY: Float,
    val portraitZoom: Float,
    val squareCenterX: Float,
    val squareCenterY: Float,
    val squareZoom: Float,
    val updatedAt: Long,
)

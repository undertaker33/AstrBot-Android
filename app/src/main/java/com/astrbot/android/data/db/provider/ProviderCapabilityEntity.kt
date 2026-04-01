package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.ForeignKey

@Entity(
    tableName = "provider_capabilities",
    primaryKeys = ["providerId", "capability"],
    foreignKeys = [
        ForeignKey(
            entity = ProviderEntity::class,
            parentColumns = ["id"],
            childColumns = ["providerId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ProviderCapabilityEntity(
    val providerId: String,
    val capability: String,
)

package com.astrbot.android.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
@Entity(tableName = "provider_profiles")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val model: String,
    val providerType: String,
    val apiKey: String,
    val enabled: Boolean,
    val multimodalRuleSupport: String,
    val multimodalProbeSupport: String,
    val nativeStreamingRuleSupport: String,
    val nativeStreamingProbeSupport: String,
    val sttProbeSupport: String,
    val ttsProbeSupport: String,
    val sortIndex: Int,
    val updatedAt: Long,
)

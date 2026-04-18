package com.astrbot.android.feature.config.domain

import com.astrbot.android.model.ConfigProfile
import kotlinx.coroutines.flow.StateFlow

interface ConfigRepositoryPort {
    val profiles: StateFlow<List<ConfigProfile>>
    val selectedProfileId: StateFlow<String>
    fun snapshotProfiles(): List<ConfigProfile>
    fun resolve(id: String): ConfigProfile
    fun resolveExistingId(id: String?): String
    suspend fun save(profile: ConfigProfile)
    suspend fun delete(id: String)
    suspend fun select(id: String)
}

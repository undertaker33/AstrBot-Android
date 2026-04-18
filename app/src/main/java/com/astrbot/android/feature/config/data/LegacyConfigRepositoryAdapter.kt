package com.astrbot.android.feature.config.data

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.model.ConfigProfile
import kotlinx.coroutines.flow.StateFlow

class LegacyConfigRepositoryAdapter : ConfigRepositoryPort {

    override val profiles: StateFlow<List<ConfigProfile>>
        get() = ConfigRepository.profiles

    override val selectedProfileId: StateFlow<String>
        get() = ConfigRepository.selectedProfileId

    override fun snapshotProfiles(): List<ConfigProfile> =
        ConfigRepository.snapshotProfiles()

    override fun resolve(id: String): ConfigProfile =
        ConfigRepository.resolve(id)

    override fun resolveExistingId(id: String?): String =
        ConfigRepository.resolveExistingId(id)

    override suspend fun save(profile: ConfigProfile) {
        ConfigRepository.save(profile)
    }

    override suspend fun delete(id: String) {
        ConfigRepository.delete(id)
    }

    override suspend fun select(id: String) {
        ConfigRepository.select(id)
    }
}

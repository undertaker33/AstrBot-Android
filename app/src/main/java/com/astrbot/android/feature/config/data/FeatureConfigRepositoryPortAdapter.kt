package com.astrbot.android.feature.config.data

import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.model.ConfigProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.StateFlow

@Singleton
class FeatureConfigRepositoryPortAdapter @Inject constructor(
    private val repository: FeatureConfigRepositoryStore,
) : ConfigRepositoryPort {

    override val profiles: StateFlow<List<ConfigProfile>>
        get() = repository.profiles

    override val selectedProfileId: StateFlow<String>
        get() = repository.selectedProfileId

    override fun snapshotProfiles(): List<ConfigProfile> =
        repository.snapshotProfiles()

    override fun create(name: String): ConfigProfile =
        repository.create(name)

    override fun resolve(id: String): ConfigProfile =
        repository.resolve(id)

    override fun resolveExistingId(id: String?): String =
        repository.resolveExistingId(id)

    override suspend fun save(profile: ConfigProfile) {
        repository.save(profile)
    }

    override suspend fun delete(id: String) {
        repository.delete(id)
    }

    override suspend fun select(id: String) {
        repository.select(id)
    }
}

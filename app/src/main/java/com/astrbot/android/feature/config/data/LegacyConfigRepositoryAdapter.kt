package com.astrbot.android.feature.config.data

import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.model.ConfigProfile
import kotlinx.coroutines.flow.StateFlow

@Suppress("DEPRECATION")
open class FeatureConfigRepositoryPortAdapter : ConfigRepositoryPort {

    override val profiles: StateFlow<List<ConfigProfile>>
        get() = FeatureConfigRepository.profiles

    override val selectedProfileId: StateFlow<String>
        get() = FeatureConfigRepository.selectedProfileId

    override fun snapshotProfiles(): List<ConfigProfile> =
        FeatureConfigRepository.snapshotProfiles()

    override fun create(name: String): ConfigProfile =
        FeatureConfigRepository.create(name)

    override fun resolve(id: String): ConfigProfile =
        FeatureConfigRepository.resolve(id)

    override fun resolveExistingId(id: String?): String =
        FeatureConfigRepository.resolveExistingId(id)

    override suspend fun save(profile: ConfigProfile) {
        FeatureConfigRepository.save(profile)
    }

    override suspend fun delete(id: String) {
        FeatureConfigRepository.delete(id)
    }

    override suspend fun select(id: String) {
        FeatureConfigRepository.select(id)
    }
}

/**
 * Compat-only adapter for targeted tests and transitional callers.
 * Production mainline uses [FeatureConfigRepositoryPortAdapter].
 */
@Deprecated(
    "Compat-only seam. Production mainline uses FeatureConfigRepositoryPortAdapter.",
    level = DeprecationLevel.WARNING,
)
class LegacyConfigRepositoryAdapter : FeatureConfigRepositoryPortAdapter()

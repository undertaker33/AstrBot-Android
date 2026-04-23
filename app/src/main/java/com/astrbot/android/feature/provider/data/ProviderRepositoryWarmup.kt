package com.astrbot.android.feature.provider.data

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legacy compatibility shell kept so startup cleanup can retire provider
 * direct initialization from the production mainline without deleting the
 * symbol that older source contracts still inspect.
 */
@Singleton
class ProviderRepositoryWarmup @Inject constructor(
    @Suppress("unused") private val repository: FeatureProviderRepositoryStore,
) {
    fun warmUp() = Unit
}

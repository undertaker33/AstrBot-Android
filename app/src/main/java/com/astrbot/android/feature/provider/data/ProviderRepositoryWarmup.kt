package com.astrbot.android.feature.provider.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin warmup service that initialises [FeatureProviderRepository] during app bootstrap.
 * [AppBootstrapper] injects this and calls [warmUp] once at startup instead of
 * going through [InitializationCoordinator].
 */
@Singleton
class ProviderRepositoryWarmup @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    fun warmUp() {
        FeatureProviderRepository.initialize(appContext)
    }
}

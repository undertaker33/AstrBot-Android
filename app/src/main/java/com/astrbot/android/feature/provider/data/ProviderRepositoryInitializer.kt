package com.astrbot.android.feature.provider.data

import android.content.Context
import com.astrbot.android.core.di.AppInitializer
import com.astrbot.android.feature.provider.data.FeatureProviderRepository

class ProviderRepositoryInitializer : AppInitializer {
    override val key: String = "provider"
    override val dependencies: Set<String> = emptySet()

    override fun initialize(context: Context) {
        FeatureProviderRepository.initialize(context)
    }
}




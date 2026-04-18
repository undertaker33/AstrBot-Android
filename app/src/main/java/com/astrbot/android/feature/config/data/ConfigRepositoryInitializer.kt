package com.astrbot.android.feature.config.data

import android.content.Context
import com.astrbot.android.core.di.AppInitializer
import com.astrbot.android.feature.config.data.FeatureConfigRepository

class ConfigRepositoryInitializer : AppInitializer {
    override val key: String = "config"
    override val dependencies: Set<String> = emptySet()

    override fun initialize(context: Context) {
        FeatureConfigRepository.initialize(context)
    }
}



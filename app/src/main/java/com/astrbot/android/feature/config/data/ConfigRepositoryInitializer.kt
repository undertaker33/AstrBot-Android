package com.astrbot.android.feature.config.data

import android.content.Context
import com.astrbot.android.core.di.AppInitializer
import com.astrbot.android.data.ConfigRepository

class ConfigRepositoryInitializer : AppInitializer {
    override val key: String = "config"
    override val dependencies: Set<String> = emptySet()

    override fun initialize(context: Context) {
        ConfigRepository.initialize(context)
    }
}

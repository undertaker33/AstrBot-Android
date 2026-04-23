package com.astrbot.android.feature.config.data

import android.content.Context
import com.astrbot.android.core.di.AppInitializer
import javax.inject.Inject

class ConfigRepositoryInitializer @Inject constructor(
    @Suppress("unused") private val repository: FeatureConfigRepositoryStore,
) : AppInitializer {
    override val key: String = "config"
    override val dependencies: Set<String> = emptySet()

    override fun initialize(context: Context) = Unit
}



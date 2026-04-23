package com.astrbot.android.feature.persona.data

import android.content.Context
import com.astrbot.android.core.di.AppInitializer
import javax.inject.Inject

class PersonaRepositoryInitializer @Inject constructor(
    @Suppress("unused") private val repository: FeaturePersonaRepositoryStore,
) : AppInitializer {
    override val key: String = "persona"
    override val dependencies: Set<String> = emptySet()

    override fun initialize(context: Context) = Unit
}




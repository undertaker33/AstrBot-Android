package com.astrbot.android.feature.persona.data

import android.content.Context
import com.astrbot.android.core.di.AppInitializer
import com.astrbot.android.data.PersonaRepository

class PersonaRepositoryInitializer : AppInitializer {
    override val key: String = "persona"
    override val dependencies: Set<String> = emptySet()

    override fun initialize(context: Context) {
        PersonaRepository.initialize(context)
    }
}

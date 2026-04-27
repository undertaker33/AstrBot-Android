package com.astrbot.android.feature.persona.domain.model

data class PersonaToolEnablementSnapshot(
    val personaId: String,
    val enabled: Boolean = true,
    val enabledTools: Set<String> = emptySet(),
)

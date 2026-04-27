package com.astrbot.android.feature.persona.domain.model

data class PersonaProfile(
    val id: String,
    val name: String,
    val tag: String = "",
    val systemPrompt: String,
    val enabledTools: Set<String>,
    val defaultProviderId: String = "",
    val maxContextMessages: Int = 12,
    val enabled: Boolean = true,
)

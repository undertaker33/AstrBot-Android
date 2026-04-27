package com.astrbot.android.feature.persona.domain

const val DefaultPersonaEnabledTool: String = "web_search"

fun defaultPersonaEnabledTools(): Set<String> = setOf(DefaultPersonaEnabledTool)

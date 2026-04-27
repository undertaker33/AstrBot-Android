package com.astrbot.android.model

data class McpServerEntry(
    val serverId: String = "",
    val name: String = "",
    val url: String = "",
    val transport: String = "streamable_http",
    val command: String = "",
    val args: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val timeoutSeconds: Int = 30,
    val active: Boolean = true,
)

data class SkillEntry(
    val skillId: String = "",
    val name: String = "",
    val description: String = "",
    val content: String = "",
    val priority: Int = 0,
    val active: Boolean = true,
)

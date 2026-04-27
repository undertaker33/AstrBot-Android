package com.astrbot.android.model

enum class ResourceCenterKind {
    MCP_SERVER,
    SKILL,
    TOOL,
}

enum class SkillResourceKind {
    PROMPT,
    TOOL,
}

data class ResourceCenterItem(
    val resourceId: String = "",
    val kind: ResourceCenterKind = ResourceCenterKind.SKILL,
    val skillKind: SkillResourceKind? = null,
    val name: String = "",
    val description: String = "",
    val content: String = "",
    val payloadJson: String = "{}",
    val source: String = "local",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class ConfigResourceProjection(
    val configId: String = "",
    val resourceId: String = "",
    val kind: ResourceCenterKind = ResourceCenterKind.SKILL,
    val active: Boolean = true,
    val priority: Int = 0,
    val sortIndex: Int = 0,
    val configJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class PromptSkillProjection(
    val resourceId: String,
    val configId: String,
    val name: String,
    val description: String,
    val content: String,
    val active: Boolean,
    val priority: Int,
    val sortIndex: Int,
)

data class ToolSkillProjection(
    val resourceId: String,
    val configId: String,
    val name: String,
    val description: String,
    val payloadJson: String,
    val active: Boolean,
    val priority: Int,
    val sortIndex: Int,
)

data class McpResourceProjection(
    val resourceId: String,
    val configId: String,
    val name: String,
    val description: String,
    val payloadJson: String,
    val active: Boolean,
    val sortIndex: Int,
)

data class ResourceCenterCompatibilitySnapshot(
    val resources: List<ResourceCenterItem>,
    val projections: List<ConfigResourceProjection>,
)

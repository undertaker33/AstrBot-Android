package com.astrbot.android.core.runtime.context

import com.astrbot.android.core.runtime.tool.ToolJsonValueNormalizer
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillEntry
import com.astrbot.android.model.SkillResourceKind
import org.json.JSONArray
import org.json.JSONObject

data class PromptSkillProjection(
    val skillId: String,
    val name: String,
    val content: String,
    val priority: Int,
    val sortIndex: Int = 0,
    val scope: String = PromptSkillScope.GLOBAL,
    val conflictPolicy: String = PromptSkillConflictPolicy.APPEND,
    val budgetChars: Int = 0,
    val active: Boolean = true,
)

data class ToolSkillProjection(
    val skillId: String,
    val name: String,
    val description: String,
    val inputSchema: Map<String, Any?> = mapOf("type" to "object"),
    val resultTemplate: String = "",
    val sortIndex: Int = 0,
    val active: Boolean = true,
)

data class RuntimeResourceProjectionSnapshot(
    val mcpServers: List<McpServerEntry>,
    val promptSkills: List<PromptSkillProjection>,
    val toolSkills: List<ToolSkillProjection>,
)

object PromptSkillScope {
    const val GLOBAL = "global"
    const val APP_CHAT = "app_chat"
    const val QQ_PRIVATE = "qq_private"
    const val QQ_GROUP = "qq_group"
    const val SCHEDULED_TASK = "scheduled_task"
}

object PromptSkillConflictPolicy {
    const val APPEND = "append"
    const val OVERRIDE_SAME_ID = "override_same_id"
    const val EXCLUSIVE_SCOPE = "exclusive_scope"
}

object RuntimeSkillProjectionResolver {
    fun fromResourceCenterSnapshot(
        snapshot: ResourceCenterCompatibilitySnapshot,
        platform: RuntimePlatform,
        trigger: IngressTrigger,
    ): RuntimeResourceProjectionSnapshot {
        val resourcesById = snapshot.resources.associateBy { it.resourceId }
        val projections = snapshot.projections.sortedWith(
            compareBy<ConfigResourceProjection> { it.kind.name }
                .thenBy { it.sortIndex }
                .thenByDescending { it.priority }
                .thenBy { it.resourceId },
        )
        return RuntimeResourceProjectionSnapshot(
            mcpServers = projections
                .filter { it.kind == ResourceCenterKind.MCP_SERVER }
                .mapNotNull { projection ->
                    resourcesById[projection.resourceId]?.toMcpServerEntry(projection)
                },
            promptSkills = projections
                .filter { it.kind == ResourceCenterKind.SKILL }
                .mapNotNull { projection ->
                    resourcesById[projection.resourceId]?.toPromptSkillProjection(
                        projection = projection,
                        platform = platform,
                        trigger = trigger,
                    )
                }
                .sortedWith(compareByDescending<PromptSkillProjection> { it.priority }.thenBy { it.sortIndex }.thenBy { it.skillId }),
            toolSkills = projections
                .filter { it.kind == ResourceCenterKind.TOOL || it.kind == ResourceCenterKind.SKILL }
                .mapNotNull { projection ->
                    resourcesById[projection.resourceId]?.toToolSkillProjection(projection)
                }
                .sortedWith(compareBy<ToolSkillProjection> { it.sortIndex }.thenBy { it.skillId }),
        )
    }

    fun promptSkills(
        skills: List<SkillEntry>,
        platform: RuntimePlatform,
        trigger: IngressTrigger,
    ): List<PromptSkillProjection> {
        return skills
            .asSequence()
            .filter { it.active && it.content.isNotBlank() }
            .map { skill ->
                PromptSkillProjection(
                    skillId = skill.skillId,
                    name = skill.name.ifBlank { skill.skillId },
                    content = skill.content.trim(),
                    priority = skill.priority,
                    sortIndex = 0,
                    scope = scopeFor(platform, trigger),
                    active = skill.active,
                )
            }
            .sortedWith(compareByDescending<PromptSkillProjection> { it.priority }.thenBy { it.skillId })
            .toList()
    }

    fun toolSkills(
        skills: List<SkillEntry>,
    ): List<ToolSkillProjection> {
        return skills
            .asSequence()
            .filter { it.active && it.content.isBlank() && it.description.isNotBlank() }
            .map { skill ->
                ToolSkillProjection(
                    skillId = skill.skillId,
                    name = skill.skillId,
                    description = skill.description.ifBlank { "Skill: ${skill.name}" },
                    active = skill.active,
                )
            }
            .toList()
    }

    private fun scopeFor(
        platform: RuntimePlatform,
        trigger: IngressTrigger,
    ): String {
        if (trigger == IngressTrigger.SCHEDULED_TASK) return PromptSkillScope.SCHEDULED_TASK
        return when (platform) {
            RuntimePlatform.APP_CHAT -> PromptSkillScope.APP_CHAT
            RuntimePlatform.QQ_ONEBOT -> PromptSkillScope.GLOBAL
        }
    }

    private fun ResourceCenterItem.toMcpServerEntry(
        projection: ConfigResourceProjection,
    ): McpServerEntry? {
        if (kind != ResourceCenterKind.MCP_SERVER) return null
        val payload = mergedPayload(projection)
        val url = payload.optString("url", "").trim()
        val transport = payload.optString("transport", "streamable_http")
            .ifBlank { "streamable_http" }
            .trim()
        if (url.isBlank()) return null
        if (transport != "streamable_http") return null
        return McpServerEntry(
            serverId = resourceId,
            name = name.ifBlank { resourceId },
            url = url,
            transport = transport,
            command = payload.optString("command", ""),
            args = payload.optJSONArray("args").toStringList(),
            headers = payload.optJSONObject("headers").toStringMap(),
            timeoutSeconds = payload.optInt("timeoutSeconds", 30).coerceAtLeast(1),
            active = enabled && projection.active,
        )
    }

    private fun ResourceCenterItem.toPromptSkillProjection(
        projection: ConfigResourceProjection,
        platform: RuntimePlatform,
        trigger: IngressTrigger,
    ): PromptSkillProjection? {
        if (kind != ResourceCenterKind.SKILL || skillKind == SkillResourceKind.TOOL) return null
        return PromptSkillProjection(
            skillId = resourceId,
            name = name.ifBlank { resourceId },
            content = content.trim(),
            priority = projection.priority,
            sortIndex = projection.sortIndex,
            scope = scopeFor(platform, trigger),
            active = enabled && projection.active,
        )
    }

    private fun ResourceCenterItem.toToolSkillProjection(
        projection: ConfigResourceProjection,
    ): ToolSkillProjection? {
        val isTool = kind == ResourceCenterKind.TOOL || (kind == ResourceCenterKind.SKILL && skillKind == SkillResourceKind.TOOL)
        if (!isTool) return null
        val payload = mergedPayload(projection)
        val inputSchema = ToolJsonValueNormalizer.normalizeObject(payload.optJSONObject("inputSchema"))
            .ifEmpty { mapOf("type" to "object") }
        return ToolSkillProjection(
            skillId = resourceId,
            name = name.ifBlank { resourceId },
            description = description.ifBlank { "Tool Skill: ${name.ifBlank { resourceId }}" },
            inputSchema = inputSchema,
            resultTemplate = payload.optString("resultTemplate", ""),
            sortIndex = projection.sortIndex,
            active = enabled && projection.active,
        )
    }

    private fun ResourceCenterItem.mergedPayload(
        projection: ConfigResourceProjection,
    ): JSONObject {
        val base = parseObject(payloadJson)
        val overlay = parseObject(projection.configJson)
        overlay.keys().asSequence().forEach { key -> base.put(key, overlay.opt(key)) }
        return base
    }

    private fun parseObject(raw: String): JSONObject {
        return runCatching {
            raw.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
        }.getOrDefault(JSONObject())
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index -> optString(index).takeIf { it.isNotBlank() } }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return keys().asSequence().associateWith { key -> optString(key, "") }
    }

}

package com.astrbot.android.feature.resource.data

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillEntry
import com.astrbot.android.model.SkillResourceKind
import org.json.JSONArray
import org.json.JSONObject

object ResourceCenterCompatibility {
    fun projectionsFromConfigProfile(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot {
        val mcpResources = profile.mcpServers.map { entry -> entry.toResource() }
        val skillResources = profile.skills.map { entry -> entry.toPromptSkillResource() }
        val mcpProjections = profile.mcpServers.mapIndexed { index, entry ->
            ConfigResourceProjection(
                configId = profile.id,
                resourceId = entry.serverId,
                kind = ResourceCenterKind.MCP_SERVER,
                active = entry.active,
                priority = 0,
                sortIndex = index,
                configJson = entry.toPayloadJson(),
            )
        }
        val skillProjections = profile.skills.mapIndexed { index, entry ->
            ConfigResourceProjection(
                configId = profile.id,
                resourceId = entry.skillId,
                kind = ResourceCenterKind.SKILL,
                active = entry.active,
                priority = entry.priority,
                sortIndex = index,
                configJson = "{}",
            )
        }
        return ResourceCenterCompatibilitySnapshot(
            resources = (mcpResources + skillResources).distinctBy { it.resourceId },
            projections = mcpProjections + skillProjections,
        )
    }

    private fun McpServerEntry.toResource(): ResourceCenterItem {
        return ResourceCenterItem(
            resourceId = serverId,
            kind = ResourceCenterKind.MCP_SERVER,
            skillKind = null,
            name = name,
            description = when {
                url.isNotBlank() -> url
                command.isNotBlank() -> command
                else -> transport
            },
            content = "",
            payloadJson = toPayloadJson(),
            source = "legacy_config",
            enabled = active,
        )
    }

    private fun SkillEntry.toPromptSkillResource(): ResourceCenterItem {
        return ResourceCenterItem(
            resourceId = skillId,
            kind = ResourceCenterKind.SKILL,
            skillKind = SkillResourceKind.PROMPT,
            name = name,
            description = description,
            content = content,
            payloadJson = "{}",
            source = "legacy_config",
            enabled = active,
        )
    }

    private fun McpServerEntry.toPayloadJson(): String {
        return JSONObject()
            .put("url", url)
            .put("transport", transport)
            .put("command", command)
            .put("args", JSONArray(args))
            .put("headers", JSONObject(headers))
            .put("timeoutSeconds", timeoutSeconds)
            .toString()
    }
}

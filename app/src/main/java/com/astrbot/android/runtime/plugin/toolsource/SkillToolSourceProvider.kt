package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.model.SkillEntry
import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolResult
import com.astrbot.android.runtime.plugin.PluginToolResultStatus
import com.astrbot.android.runtime.plugin.PluginToolSourceKind
import com.astrbot.android.runtime.plugin.PluginToolVisibility

/**
 * Skill tool source provider.
 *
 * Skills are lightweight prompt-injection units (analogous to AstrBot SKILL.md).
 * Each active skill per-config is projected as a tool descriptor so it can be
 * persona-filtered and rendered in the availability chain.
 */
class SkillToolSourceProvider : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.SKILL

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        return configProfile.skills.filter { it.active }.map { skill -> buildBinding(skill) }
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        val skill = configProfile.skills.firstOrNull { "skill.${it.skillId}" == identity.ownerId }
        return if (skill != null && skill.active) {
            ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = true,
            )
        } else {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = true,
                detailCode = "skill_inactive",
                detailMessage = "Skill is not configured or inactive.",
            )
        }
    }

    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        // Skills are prompt-injection only — they augment the system prompt,
        // not invoked as function tools. Return an informational result.
        return ToolSourceInvokeResult(
            result = PluginToolResult(
                toolCallId = request.args.toolCallId,
                requestId = request.args.requestId,
                toolId = request.args.toolId,
                status = PluginToolResultStatus.SUCCESS,
                text = "Skill content injected into system prompt.",
            ),
        )
    }

    private fun buildBinding(skill: SkillEntry): ToolSourceDescriptorBinding {
        val ownerId = "skill.${skill.skillId}"
        val identity = ToolSourceIdentity(
            sourceKind = PluginToolSourceKind.SKILL,
            ownerId = ownerId,
            sourceRef = skill.skillId,
            displayName = skill.name.ifBlank { skill.skillId },
        )
        val descriptor = PluginToolDescriptor(
            pluginId = ownerId,
            name = skill.skillId,
            description = skill.description.ifBlank { "Skill: ${skill.name}" },
            visibility = PluginToolVisibility.HOST_INTERNAL,
            sourceKind = PluginToolSourceKind.SKILL,
            inputSchema = mapOf("type" to "object" as Any),
        )
        return ToolSourceDescriptorBinding(
            identity = identity,
            descriptor = descriptor,
        )
    }
}

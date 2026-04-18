package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.runtime.context.ToolSkillProjection
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginToolVisibility

/**
 * Skill tool source provider.
 *
 * Tool Skills are distinct from Prompt Skills. Prompt Skills are consumed by
 * PromptAssembler; only explicit tool skill projections are registered here.
 */
class SkillToolSourceProvider : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.SKILL

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        return context.toolSourceContext.toolSkills.filter { it.active }.map { skill -> buildBinding(skill) }
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val skill = context.toolSourceContext.toolSkills.firstOrNull { "skill.${it.skillId}" == identity.ownerId }
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
        val skill = request.toolSourceContext?.toolSkills
            ?.firstOrNull { "skill.${it.skillId}" == request.identity.ownerId && it.active }
        if (skill == null) {
            return ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "tool_skill_unavailable",
                    text = "Tool Skill is not configured or inactive.",
                ),
            )
        }
        val rendered = renderTemplate(skill.resultTemplate, request.args.payload)
        return ToolSourceInvokeResult(
            result = PluginToolResult(
                toolCallId = request.args.toolCallId,
                requestId = request.args.requestId,
                toolId = request.args.toolId,
                status = PluginToolResultStatus.SUCCESS,
                text = rendered.ifBlank { "Tool Skill '${skill.name}' executed." },
            ),
        )
    }

    private fun buildBinding(skill: ToolSkillProjection): ToolSourceDescriptorBinding {
        val ownerId = "skill.${skill.skillId}"
        val identity = ToolSourceIdentity(
            sourceKind = PluginToolSourceKind.SKILL,
            ownerId = ownerId,
            sourceRef = skill.skillId,
            displayName = skill.name.ifBlank { skill.skillId },
        )
        val descriptor = PluginToolDescriptor(
            pluginId = ownerId,
            name = skill.name.ifBlank { skill.skillId },
            description = skill.description.ifBlank { "Tool Skill: ${skill.name}" },
            visibility = PluginToolVisibility.LLM_VISIBLE,
            sourceKind = PluginToolSourceKind.SKILL,
            inputSchema = skill.inputSchema,
        )
        return ToolSourceDescriptorBinding(
            identity = identity,
            descriptor = descriptor,
        )
    }

    private fun renderTemplate(template: String, payload: Map<String, Any?>): String {
        return payload.entries.fold(template) { current, (key, value) ->
            current.replace("{{${key}}}", value?.toString().orEmpty())
        }
    }
}

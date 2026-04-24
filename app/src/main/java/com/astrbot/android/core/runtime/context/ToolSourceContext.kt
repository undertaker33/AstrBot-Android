package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.McpServerEntry

data class ToolSourceContext(
    val requestId: String,
    val platform: RuntimePlatform,
    val configProfileId: String,
    val webSearchEnabled: Boolean,
    val activeCapabilityEnabled: Boolean,
    val mcpServers: List<McpServerEntry>,
    val promptSkills: List<PromptSkillProjection>,
    val toolSkills: List<ToolSkillProjection>,
    val conversationId: String,
    val contextLimitStrategy: String = "",
    val runtimePermissions: Map<String, Any?> = emptyMap(),
    val networkPolicy: Map<String, Any?> = emptyMap(),
    val ingressTrigger: IngressTrigger = IngressTrigger.USER_MESSAGE,
) {
    companion object {
        fun fromConfigProfile(
            config: ConfigProfile,
            requestId: String = "",
            platform: RuntimePlatform = RuntimePlatform.APP_CHAT,
            conversationId: String = "",
            mcpServers: List<McpServerEntry> = config.mcpServers,
            promptSkills: List<PromptSkillProjection> = emptyList(),
            toolSkills: List<ToolSkillProjection> = emptyList(),
            ingressTrigger: IngressTrigger = IngressTrigger.USER_MESSAGE,
        ): ToolSourceContext {
            return ToolSourceContext(
                requestId = requestId,
                platform = platform,
                configProfileId = config.id,
                webSearchEnabled = config.webSearchEnabled,
                activeCapabilityEnabled = config.proactiveEnabled,
                mcpServers = mcpServers,
                promptSkills = promptSkills,
                toolSkills = toolSkills,
                conversationId = conversationId,
                contextLimitStrategy = config.contextLimitStrategy,
                ingressTrigger = ingressTrigger,
            )
        }
    }
}

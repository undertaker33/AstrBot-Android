package com.astrbot.android.core.runtime.tool

data class ToolDescriptor(
    val id: String,
    val ownerId: String,
    val name: String,
    val description: String,
    val inputSchemaJson: String,
    val source: ToolSourceKind,
    val visibility: ToolDescriptorVisibility = ToolDescriptorVisibility.LLM_VISIBLE,
)

enum class ToolDescriptorVisibility {
    LLM_VISIBLE,
    HOST_INTERNAL,
}

enum class ToolSourceKind {
    PLUGIN,
    MCP,
    SKILL,
    WEB_SEARCH,
    ACTIVE_CAPABILITY,
    CONTEXT_STRATEGY,
}

interface ToolSourceProviderPort {
    val kind: ToolSourceKind
    suspend fun descriptors(context: ToolSourceRequestContext): List<ToolDescriptor>
}

data class ToolSourceRequestContext(
    val botId: String,
    val configId: String,
    val personaId: String,
    val conversationId: String,
)

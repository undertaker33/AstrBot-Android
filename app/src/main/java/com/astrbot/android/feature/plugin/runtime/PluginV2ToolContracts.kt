package com.astrbot.android.feature.plugin.runtime

enum class PluginToolVisibility {
    LLM_VISIBLE,
    HOST_INTERNAL,
}

enum class PluginToolSourceKind(
    val reservedOnly: Boolean,
) {
    PLUGIN_V2(reservedOnly = false),
    HOST_BUILTIN(reservedOnly = false),
    MCP(reservedOnly = true),
    SKILL(reservedOnly = true),
    ACTIVE_CAPABILITY(reservedOnly = true),
    CONTEXT_STRATEGY(reservedOnly = true),
    WEB_SEARCH(reservedOnly = true),
}

enum class PluginToolResultStatus {
    SUCCESS,
    ERROR,
}

class PluginToolDescriptor(
    pluginId: String,
    name: String,
    description: String = "",
    val visibility: PluginToolVisibility = PluginToolVisibility.LLM_VISIBLE,
    val sourceKind: PluginToolSourceKind = PluginToolSourceKind.PLUGIN_V2,
    inputSchema: JsonLikeMap,
    metadata: JsonLikeMap? = null,
) {
    val pluginId: String = pluginId.trim()
    val name: String = name.trim()
    val description: String = description.trim()
    val toolId: String = buildToolId(pluginId = this.pluginId, name = this.name)
    val inputSchema: JsonLikeMap = PluginV2ValueSanitizer.requireAllowedMap(inputSchema)
    val metadata: JsonLikeMap? = metadata?.let(PluginV2ValueSanitizer::requireAllowedMap)
}

class PluginToolArgs(
    val toolCallId: String,
    val requestId: String,
    val toolId: String,
    val attemptIndex: Int = 0,
    payload: JsonLikeMap,
    metadata: JsonLikeMap? = null,
) {
    val payload: JsonLikeMap = PluginV2ValueSanitizer.requireAllowedMap(payload)
    val metadata: JsonLikeMap? = metadata?.let(PluginV2ValueSanitizer::requireAllowedMap)

    init {
        require(toolCallId.isNotBlank()) { "toolCallId must not be blank." }
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(toolId.isNotBlank()) { "toolId must not be blank." }
        require(attemptIndex >= 0) { "attemptIndex must not be negative." }
    }
}

class PluginToolResult(
    val toolCallId: String,
    val requestId: String,
    val toolId: String,
    val status: PluginToolResultStatus,
    errorCode: String? = null,
    text: String? = null,
    structuredContent: JsonLikeMap? = null,
    metadata: JsonLikeMap? = null,
) {
    val errorCode: String? = errorCode?.trim()?.takeIf { it.isNotBlank() }
    val text: String? = text?.trim()
    val structuredContent: JsonLikeMap? = structuredContent?.let(PluginV2ValueSanitizer::requireAllowedMap)
    val metadata: JsonLikeMap? = metadata?.let(PluginV2ValueSanitizer::requireAllowedMap)

    init {
        require(toolCallId.isNotBlank()) { "toolCallId must not be blank." }
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(toolId.isNotBlank()) { "toolId must not be blank." }
        require(status != PluginToolResultStatus.ERROR || this.errorCode != null) {
            "errorCode must not be blank when status=ERROR."
        }
    }
}

internal fun buildToolId(pluginId: String, name: String): String {
    return "${pluginId.trim()}:${name.trim()}"
}

internal fun requireToolSchema(schema: JsonLikeMap): JsonLikeMap {
    val normalized = PluginV2ValueSanitizer.requireAllowedMap(schema)
    val type = normalized["type"]
    require(type is String && type.isNotBlank()) {
        "inputSchema.type must be a non-blank string."
    }
    return normalized
}

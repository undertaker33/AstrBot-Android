package com.astrbot.android.model.plugin

enum class PluginRuntimeLogCategory(
    val wireValue: String,
) {
    Dispatcher("dispatcher"),
    Execution("execution"),
    HostAction("host_action"),
    FailureGuard("failure_guard"),
    Scheduler("scheduler"),
    ResultMerger("result_merger"),
    WorkspaceApi("workspace_api"),
}

enum class PluginRuntimeLogLevel(
    val wireValue: String,
) {
    Debug("debug"),
    Info("info"),
    Warning("warning"),
    Error("error"),
}

data class PluginRuntimeLogRecord(
    val occurredAtEpochMillis: Long,
    val pluginId: String,
    val pluginVersion: String = "",
    val trigger: PluginTriggerSource? = null,
    val category: PluginRuntimeLogCategory,
    val level: PluginRuntimeLogLevel,
    val code: String,
    val message: String = "",
    val succeeded: Boolean? = null,
    val durationMillis: Long? = null,
    val hostAction: PluginHostAction? = null,
    val resultType: String = "",
    val metadata: Map<String, String> = emptyMap(),
) {
    val runtimeSessionId: String
        get() = metadata.resolveStructuredValue("runtimeSessionId", "sessionInstanceId")

    val requestId: String
        get() = metadata.resolveStructuredValue("requestId")

    val stage: String
        get() = metadata.resolveStructuredValue("stage")

    val handlerName: String
        get() = metadata.resolveStructuredValue("handlerName", "handlerId")

    val toolId: String
        get() = metadata.resolveStructuredValue("toolId")

    val toolCallId: String
        get() = metadata.resolveStructuredValue("toolCallId")

    val outcome: String
        get() = metadata.resolveStructuredValue("outcome")
}

private fun Map<String, String>.resolveStructuredValue(
    primaryKey: String,
    fallbackKey: String? = null,
): String {
    val primary = this[primaryKey].orEmpty()
    if (primary.isNotBlank()) {
        return primary
    }
    return fallbackKey?.let { key -> this[key].orEmpty() }.orEmpty()
}

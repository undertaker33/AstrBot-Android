package com.astrbot.android.runtime.plugin

enum class PluginV2DispatchStage {
    AdapterMessage,
    Command,
    Regex,
    Lifecycle,
    LlmWaiting,
    LlmRequest,
    LlmResponse,
    UsingLlmTool,
    ToolExecution,
    LlmToolRespond,
    ResultDecorating,
    AfterMessageSent,
}

enum class PluginV2PayloadKind {
    OpaqueRef,
    MessageEvent,
    LlmRequestPayload,
    LlmResponsePayload,
    UsingLlmTool,
    ToolExecution,
    LlmToolRespond,
    LlmResultPayload,
    LlmAfterSentPayload,
}

data class PluginV2DispatchPayloadRef(
    val kind: PluginV2PayloadKind,
    val refId: String,
    val attributes: Map<String, String> = emptyMap(),
) {
    init {
        require(refId.isNotBlank()) { "refId must not be blank." }
        require(attributes.keys.none(String::isBlank)) { "attributes keys must not be blank." }
    }
}

data class PluginV2DispatchEnvelope(
    val stage: PluginV2DispatchStage,
    val callbackToken: PluginV2CallbackToken,
    val payloadRef: PluginV2DispatchPayloadRef,
    val traceId: String,
) {
    init {
        require(traceId.isNotBlank()) { "traceId must not be blank." }
    }
}

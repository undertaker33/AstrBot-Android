package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.AppChatLlm
import com.astrbot.android.model.plugin.PluginV2StreamingMode

typealias JsonLikeMap = Map<String, AllowedValue>

data class LlmPipelineAdmission(
    val requestId: String,
    val conversationId: String,
    val messageIds: List<String>,
    val llmInputSnapshot: String,
    val routingTarget: AppChatLlm,
    val streamingMode: PluginV2StreamingMode,
) {
    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(conversationId.isNotBlank()) { "conversationId must not be blank." }
        require(messageIds.isNotEmpty()) { "messageIds must not be empty." }
        require(messageIds.none(String::isBlank)) { "messageIds must not contain blank values." }
        require(llmInputSnapshot.isNotBlank()) { "llmInputSnapshot must not be blank." }
    }
}

enum class PluginProviderMessageRole(
    val wireValue: String,
    val canBeWrittenByPlugin: Boolean,
) {
    SYSTEM("system", true),
    USER("user", true),
    ASSISTANT("assistant", true),
    TOOL("tool", false);

    companion object {
        fun fromWireValue(value: String): PluginProviderMessageRole? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

sealed interface PluginProviderMessagePartDto {
    data class TextPart(
        val text: String,
    ) : PluginProviderMessagePartDto {
        // Phase 5 tool reinjection requires TextPart("") as a placeholder when both text and structured content are empty.
    }

    data class MediaRefPart(
        val uri: String,
        val mimeType: String,
    ) : PluginProviderMessagePartDto {
        init {
            require(uri.isNotBlank()) { "uri must not be blank." }
            require(mimeType.isNotBlank()) { "mimeType must not be blank." }
        }
    }
}

class PluginProviderMessageDto(
    val role: PluginProviderMessageRole,
    parts: List<PluginProviderMessagePartDto>,
    name: String? = null,
    metadata: JsonLikeMap? = null,
) {
    val parts: List<PluginProviderMessagePartDto> = parts.toList()
    val name: String? = name?.trim()?.takeIf { it.isNotBlank() }
    val metadata: JsonLikeMap? = metadata?.let(PluginV2ValueSanitizer::requireAllowedMap)

    init {
        require(this.parts.isNotEmpty()) { "parts must not be empty." }
        require(
            role == PluginProviderMessageRole.TOOL ||
                this.parts.none { it is PluginProviderMessagePartDto.TextPart && it.text.isBlank() },
        ) {
            "parts must not contain blank text values unless role=TOOL."
        }
        require(this.parts.none { it is PluginProviderMessagePartDto.MediaRefPart && it.uri.isBlank() }) {
            "parts must not contain blank media references."
        }
        require(this.parts.none { it is PluginProviderMessagePartDto.MediaRefPart && it.mimeType.isBlank() }) {
            "parts must not contain blank media mime types."
        }
        if (role == PluginProviderMessageRole.TOOL) {
            require(this.name != null) { "role=TOOL requires name." }
            require(extractHostToolCallId(this.metadata) != null) {
                "role=TOOL requires metadata.__host.toolCallId."
            }
        }
    }
}

class PluginProviderRequest(
    val requestId: String,
    availableProviderIds: List<String>,
    availableModelIdsByProvider: Map<String, List<String>>,
    val conversationId: String,
    messageIds: List<String>,
    val llmInputSnapshot: String,
    selectedProviderId: String = "",
    selectedModelId: String = "",
    systemPrompt: String? = null,
    messages: List<PluginProviderMessageDto> = emptyList(),
    temperature: Double? = null,
    topP: Double? = null,
    maxTokens: Int? = null,
    streamingEnabled: Boolean = false,
    metadata: JsonLikeMap? = null,
    private val allowHostToolMessages: Boolean = false,
) {
    val availableProviderIds: List<String> = sanitizeIdList(availableProviderIds, "availableProviderIds")
    val availableModelIdsByProvider: Map<String, List<String>> = sanitizeModelMap(
        availableModelIdsByProvider,
        "availableModelIdsByProvider",
    )
    val messageIds: List<String> = sanitizeIdList(messageIds, "messageIds")

    var selectedProviderId: String = normalizeSelectedProviderId(selectedProviderId)
        set(value) {
            field = normalizeSelectedProviderId(value)
            selectedModelId = coerceSelectedModelIdForProvider(field, selectedModelId)
        }

    var selectedModelId: String = normalizeSelectedModelId(selectedModelId)
        set(value) {
            field = normalizeSelectedModelId(value)
        }

    var systemPrompt: String? = sanitizeOptionalString(systemPrompt)
        set(value) {
            field = sanitizeOptionalString(value)
        }

    private val preservedHostToolMessages: List<PluginProviderMessageDto> =
        sanitizeInitialHostToolMessages(messages, allowHostToolMessages)

    var messages: List<PluginProviderMessageDto> = sanitizeInitialMessages(
        values = messages,
        preservedHostToolMessages = preservedHostToolMessages,
    )
        set(value) {
            // Phase 5: plugins must not author TOOL role messages via request hooks.
            // Host-authored TOOL messages (tool reinjection) must remain intact across plugin mutations.
            val nextMessages = sanitizeMessages(value)
            field = nextMessages.filter { it.role != PluginProviderMessageRole.TOOL } + preservedHostToolMessages
        }

    var temperature: Double? = sanitizeTemperature(temperature)
        set(value) {
            field = sanitizeTemperature(value)
        }

    var topP: Double? = sanitizeTopP(topP)
        set(value) {
            field = sanitizeTopP(value)
        }

    var maxTokens: Int? = sanitizeMaxTokens(maxTokens)
        set(value) {
            field = sanitizeMaxTokens(value)
        }

    var streamingEnabled: Boolean = streamingEnabled

    var metadata: JsonLikeMap? = sanitizeMetadata(metadata)
        set(value) {
            field = sanitizeMetadata(value)
        }

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(conversationId.isNotBlank()) { "conversationId must not be blank." }
        require(llmInputSnapshot.isNotBlank()) { "llmInputSnapshot must not be blank." }

        if (availableProviderIds.isNotEmpty()) {
            require(selectedProviderId in availableProviderIds) {
                "selectedProviderId must be one of availableProviderIds."
            }
        } else {
            require(selectedProviderId.isBlank()) {
                "selectedProviderId must be blank when no providers are available."
            }
        }

        if (selectedModelId.isNotBlank()) {
            require(selectedModelId in availableModelIdsByProvider[selectedProviderId].orEmpty()) {
                "selectedModelId must be one of availableModelIdsByProvider[selectedProviderId]."
            }
        }
    }

    private fun normalizeSelectedProviderId(value: String): String {
        val normalized = value.trim()
        if (normalized.isBlank()) {
            return availableProviderIds.firstOrNull().orEmpty()
        }
        require(normalized in availableProviderIds) {
            "selectedProviderId must be one of availableProviderIds."
        }
        return normalized
    }

    private fun normalizeSelectedModelId(value: String): String {
        val normalized = value.trim()
        val allowedModels = availableModelIdsByProvider[selectedProviderId].orEmpty()
        if (normalized.isBlank()) {
            return allowedModels.firstOrNull().orEmpty()
        }
        require(normalized in allowedModels) {
            "selectedModelId must be one of availableModelIdsByProvider[selectedProviderId]."
        }
        return normalized
    }

    private fun coerceSelectedModelIdForProvider(providerId: String, currentModelId: String): String {
        val allowedModels = availableModelIdsByProvider[providerId].orEmpty()
        return when {
            currentModelId.isNotBlank() && currentModelId in allowedModels -> currentModelId
            allowedModels.isNotEmpty() -> allowedModels.first()
            else -> ""
        }
    }
}

data class PluginLlmUsageSnapshot(
    val promptTokens: Int? = null,
    val completionTokens: Int? = null,
    val totalTokens: Int? = null,
    val inputCostMicros: Long? = null,
    val outputCostMicros: Long? = null,
    val currencyCode: String? = null,
) {
    init {
        require(promptTokens == null || promptTokens >= 0) { "promptTokens must not be negative." }
        require(completionTokens == null || completionTokens >= 0) { "completionTokens must not be negative." }
        require(totalTokens == null || totalTokens >= 0) { "totalTokens must not be negative." }
        require(inputCostMicros == null || inputCostMicros >= 0L) { "inputCostMicros must not be negative." }
        require(outputCostMicros == null || outputCostMicros >= 0L) { "outputCostMicros must not be negative." }
    }

    val normalizedCurrencyCode: String?
        get() = currencyCode?.trim()?.uppercase()?.takeIf { it.isNotBlank() }
}

class PluginLlmResponse(
    val requestId: String,
    val providerId: String,
    val modelId: String,
    val usage: PluginLlmUsageSnapshot? = null,
    val finishReason: String? = null,
    text: String = "",
    markdown: Boolean = false,
    toolCalls: List<PluginLlmToolCall> = emptyList(),
    metadata: JsonLikeMap? = null,
) {
    var text: String = text
        set(value) {
            field = value
        }

    var markdown: Boolean = markdown
        set(value) {
            field = value
        }

    var metadata: JsonLikeMap? = sanitizeMetadata(metadata)
        set(value) {
            field = sanitizeMetadata(value)
        }

    var toolCalls: List<PluginLlmToolCall> = sanitizeToolCalls(toolCalls)
        set(value) {
            field = sanitizeToolCalls(value)
        }

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(providerId.isNotBlank()) { "providerId must not be blank." }
        require(modelId.isNotBlank()) { "modelId must not be blank." }
    }
}

data class PluginLlmToolCall(
    val toolName: String,
    val arguments: JsonLikeMap = emptyMap(),
    val metadata: JsonLikeMap? = null,
) {
    val normalizedToolName: String = toolName.trim()
    val normalizedArguments: JsonLikeMap = PluginV2ValueSanitizer.requireAllowedMap(arguments)
    val normalizedMetadata: JsonLikeMap? = metadata?.let(PluginV2ValueSanitizer::requireAllowedMap)

    init {
        require(normalizedToolName.isNotBlank()) { "toolName must not be blank." }
    }
}

data class PluginLlmToolCallDelta(
    val index: Int,
    val toolName: String,
    val arguments: JsonLikeMap = emptyMap(),
) {
    init {
        require(index >= 0) { "index must not be negative." }
        require(toolName.isNotBlank()) { "toolName must not be blank." }
    }
}

class PluginMessageEventResult(
    val requestId: String,
    val conversationId: String,
    text: String = "",
    markdown: Boolean = false,
    attachments: List<Attachment> = emptyList(),
    shouldSend: Boolean = true,
    attachmentMutationIntent: AttachmentMutationIntent? = null,
) {
    enum class AttachmentMutationIntent {
        UNTOUCHED,
        REPLACED,
        REPLACED_EMPTY,
        APPENDED,
        CLEARED,
    }

    data class Attachment(
        val uri: String,
        val mimeType: String = "",
    ) {
        init {
            require(uri.isNotBlank()) { "uri must not be blank." }
        }
    }

    private var textState: String = text
    private var markdownState: Boolean = markdown
    private var attachmentsState: List<Attachment> = attachments.toList()
    private var attachmentMutationIntentState: AttachmentMutationIntent =
        attachmentMutationIntent ?: if (attachmentsState.isEmpty()) {
            AttachmentMutationIntent.UNTOUCHED
        } else {
            AttachmentMutationIntent.REPLACED
        }

    var shouldSend: Boolean = shouldSend
        private set

    private var stopped: Boolean = false

    val text: String
        get() = textState

    var markdown: Boolean
        get() = markdownState
        set(value) {
            markdownState = value
        }

    val attachments: List<Attachment>
        get() = attachmentsState.toList()

    val attachmentMutationIntent: AttachmentMutationIntent
        get() = attachmentMutationIntentState

    val isStopped: Boolean
        get() = stopped

    init {
        require(requestId.isNotBlank()) { "requestId must not be blank." }
        require(conversationId.isNotBlank()) { "conversationId must not be blank." }
    }

    fun replaceText(value: String) {
        textState = value
    }

    fun appendText(value: String) {
        textState += value
    }

    fun clearText() {
        textState = ""
    }

    fun replaceAttachments(value: List<Attachment>) {
        attachmentsState = value.toList()
        attachmentMutationIntentState = if (attachmentsState.isEmpty()) {
            AttachmentMutationIntent.REPLACED_EMPTY
        } else {
            AttachmentMutationIntent.REPLACED
        }
    }

    fun appendAttachment(value: Attachment) {
        attachmentsState = attachmentsState + value
        attachmentMutationIntentState = AttachmentMutationIntent.APPENDED
    }

    fun clearAttachments() {
        attachmentsState = emptyList()
        attachmentMutationIntentState = AttachmentMutationIntent.CLEARED
    }

    fun setShouldSend(value: Boolean) {
        shouldSend = value
    }

    fun stop() {
        stopped = true
    }
}

private fun sanitizeIdList(values: List<String>, path: String): List<String> {
    return values.mapIndexed { index, value ->
        val normalized = value.trim()
        require(normalized.isNotBlank()) {
            "$path[$index] must not be blank."
        }
        normalized
    }
}

private fun sanitizeModelMap(values: Map<String, List<String>>, path: String): Map<String, List<String>> {
    val sanitized = linkedMapOf<String, List<String>>()
    values.forEach { (providerId, modelIds) ->
        val normalizedProviderId = providerId.trim()
        require(normalizedProviderId.isNotBlank()) {
            "$path contains a blank provider id."
        }
        sanitized[normalizedProviderId] = sanitizeIdList(modelIds, "$path['$normalizedProviderId']")
    }
    return sanitized
}

private fun sanitizeOptionalString(value: String?): String? {
    return value?.trim()?.takeIf { it.isNotBlank() }
}

private fun sanitizeMessages(values: List<PluginProviderMessageDto>): List<PluginProviderMessageDto> {
    return values.toList()
}

private fun sanitizeInitialMessages(
    values: List<PluginProviderMessageDto>,
    preservedHostToolMessages: List<PluginProviderMessageDto>,
): List<PluginProviderMessageDto> {
    val sanitized = sanitizeMessages(values)
    return sanitized.filter { it.role != PluginProviderMessageRole.TOOL } + preservedHostToolMessages
}

private fun sanitizeInitialHostToolMessages(
    values: List<PluginProviderMessageDto>,
    allowHostToolMessages: Boolean,
): List<PluginProviderMessageDto> {
    val sanitized = sanitizeMessages(values)
    val toolMessages = sanitized.filter { it.role == PluginProviderMessageRole.TOOL }
    if (!allowHostToolMessages) {
        return emptyList()
    }
    toolMessages.forEach { message ->
        require(extractHostToolCallId(message.metadata) != null) {
            "host-preserved role=TOOL messages require metadata.__host.toolCallId."
        }
    }
    return toolMessages
}

private fun extractHostToolCallId(metadata: JsonLikeMap?): String? {
    val hostMetadata = metadata?.get("__host") as? Map<*, *> ?: return null
    return (hostMetadata["toolCallId"] as? String)?.trim()?.takeIf { it.isNotBlank() }
}

private fun sanitizeToolCalls(values: List<PluginLlmToolCall>): List<PluginLlmToolCall> {
    return values.map { call ->
        PluginLlmToolCall(
            toolName = call.normalizedToolName,
            arguments = call.normalizedArguments,
            metadata = call.normalizedMetadata,
        )
    }
}

private fun sanitizeTemperature(value: Double?): Double? {
    if (value == null) return null
    require(value.isFinite()) { "temperature must be finite." }
    require(value in 0.0..2.0) { "temperature must be between 0.0 and 2.0." }
    return value
}

private fun sanitizeTopP(value: Double?): Double? {
    if (value == null) return null
    require(value.isFinite()) { "topP must be finite." }
    require(value in 0.0..1.0) { "topP must be between 0.0 and 1.0." }
    return value
}

private fun sanitizeMaxTokens(value: Int?): Int? {
    if (value == null) return null
    require(value > 0) { "maxTokens must be positive." }
    return value
}

private fun sanitizeMetadata(value: JsonLikeMap?): JsonLikeMap? {
    return value?.let(PluginV2ValueSanitizer::requireAllowedMap)
}

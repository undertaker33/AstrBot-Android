package com.astrbot.android.runtime.plugin

import java.util.Collections

enum class PluginV2InternalStage {
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

enum class PluginV2LlmHookSurface(
    val wireValue: String,
    val stage: PluginV2InternalStage,
) {
    OnWaitingLlmRequest(
        wireValue = "on_waiting_llm_request",
        stage = PluginV2InternalStage.LlmWaiting,
    ),
    OnLlmRequest(
        wireValue = "on_llm_request",
        stage = PluginV2InternalStage.LlmRequest,
    ),
    OnLlmResponse(
        wireValue = "on_llm_response",
        stage = PluginV2InternalStage.LlmResponse,
    ),
    OnUsingLlmTool(
        wireValue = "on_using_llm_tool",
        stage = PluginV2InternalStage.UsingLlmTool,
    ),
    OnLlmToolRespond(
        wireValue = "on_llm_tool_respond",
        stage = PluginV2InternalStage.LlmToolRespond,
    ),
    OnDecoratingResult(
        wireValue = "on_decorating_result",
        stage = PluginV2InternalStage.ResultDecorating,
    ),
    AfterMessageSent(
        wireValue = "after_message_sent",
        stage = PluginV2InternalStage.AfterMessageSent,
    );

    companion object {
        fun fromWireValue(value: String): PluginV2LlmHookSurface? {
            return entries.firstOrNull { candidate ->
                candidate.wireValue.equals(value.trim(), ignoreCase = true)
            }
        }
    }
}

data class PluginV2CompiledFilterAttachment(
    val kind: BootstrapFilterKind,
    val arguments: Map<String, String> = emptyMap(),
    val sourceRegistrationKey: String,
)

interface PluginV2CompiledHandlerDescriptor {
    val pluginId: String
    val registrationKind: String
    val registrationKey: String
    val normalizedRegistrationKey: String
    val handlerId: String
    val callbackToken: PluginV2CallbackToken
    val priority: Int
    val filterAttachments: List<PluginV2CompiledFilterAttachment>
    val metadata: BootstrapRegistrationMetadata
    val sourceOrder: Int
}

data class PluginV2CompiledMessageHandler(
    override val pluginId: String,
    override val registrationKind: String,
    override val registrationKey: String,
    override val normalizedRegistrationKey: String,
    override val handlerId: String,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val filterAttachments: List<PluginV2CompiledFilterAttachment>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
) : PluginV2CompiledHandlerDescriptor

data class PluginV2CompiledCommandHandler(
    override val pluginId: String,
    override val registrationKind: String,
    override val registrationKey: String,
    override val normalizedRegistrationKey: String,
    override val handlerId: String,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val filterAttachments: List<PluginV2CompiledFilterAttachment>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val command: String,
    val aliases: List<String>,
    val groupPath: List<String>,
    val commandPath: List<String>,
    val aliasPaths: List<List<String>>,
) : PluginV2CompiledHandlerDescriptor

data class PluginV2CommandBucket(
    val commandPath: List<String>,
    val commandPathKey: String,
    val handlers: List<PluginV2CompiledCommandHandler>,
    val aliasPaths: List<List<String>> = emptyList(),
)

data class PluginV2CompiledRegexHandler(
    override val pluginId: String,
    override val registrationKind: String,
    override val registrationKey: String,
    override val normalizedRegistrationKey: String,
    override val handlerId: String,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val filterAttachments: List<PluginV2CompiledFilterAttachment>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val pattern: String,
    val flags: Set<String>,
    val compiledPattern: Regex,
    val namedGroupNames: List<String>,
) : PluginV2CompiledHandlerDescriptor

data class PluginV2CompiledLifecycleHandler(
    override val pluginId: String,
    override val registrationKind: String,
    override val registrationKey: String,
    override val normalizedRegistrationKey: String,
    override val handlerId: String,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val filterAttachments: List<PluginV2CompiledFilterAttachment>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val hook: String,
) : PluginV2CompiledHandlerDescriptor

data class PluginV2CompiledLlmHookHandler(
    override val pluginId: String,
    override val registrationKind: String,
    override val registrationKey: String,
    override val normalizedRegistrationKey: String,
    override val handlerId: String,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val filterAttachments: List<PluginV2CompiledFilterAttachment>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val hook: String,
    val surface: PluginV2LlmHookSurface,
) : PluginV2CompiledHandlerDescriptor

data class PluginV2HandlerRegistry(
    val messageHandlers: List<PluginV2CompiledMessageHandler> = emptyList(),
    val commandHandlers: List<PluginV2CompiledCommandHandler> = emptyList(),
    val commandBuckets: List<PluginV2CommandBucket> = emptyList(),
    val commandAliasIndex: Map<String, String> = emptyMap(),
    val regexHandlers: List<PluginV2CompiledRegexHandler> = emptyList(),
    val lifecycleHandlers: List<PluginV2CompiledLifecycleHandler> = emptyList(),
    val llmHookHandlers: List<PluginV2CompiledLlmHookHandler> = emptyList(),
) {
    val totalHandlerCount: Int
        get() = messageHandlers.size +
            commandHandlers.size +
            regexHandlers.size +
            lifecycleHandlers.size +
            llmHookHandlers.size
}

data class PluginV2StageIndex(
    val handlerIdsByStage: Map<PluginV2InternalStage, List<String>> = emptyMap(),
)

data class PluginV2CompiledRegistrySnapshot(
    val schemaVersion: Int = 1,
    val handlerRegistry: PluginV2HandlerRegistry,
    val dispatchIndex: PluginV2StageIndex,
) : PluginV2CompiledRegistry

internal fun PluginV2CompiledRegistrySnapshot.frozenCopy(): PluginV2CompiledRegistrySnapshot {
    return copy(
        handlerRegistry = handlerRegistry.frozenCopy(),
        dispatchIndex = dispatchIndex.frozenCopy(),
    )
}

private fun PluginV2HandlerRegistry.frozenCopy(): PluginV2HandlerRegistry {
    return copy(
        messageHandlers = messageHandlers.map(PluginV2CompiledMessageHandler::frozenCopy).toFrozenList(),
        commandHandlers = commandHandlers.map(PluginV2CompiledCommandHandler::frozenCopy).toFrozenList(),
        commandBuckets = commandBuckets.map(PluginV2CommandBucket::frozenCopy).toFrozenList(),
        commandAliasIndex = LinkedHashMap(commandAliasIndex).toFrozenMap(),
        regexHandlers = regexHandlers.map(PluginV2CompiledRegexHandler::frozenCopy).toFrozenList(),
        lifecycleHandlers = lifecycleHandlers.map(PluginV2CompiledLifecycleHandler::frozenCopy).toFrozenList(),
        llmHookHandlers = llmHookHandlers.map(PluginV2CompiledLlmHookHandler::frozenCopy).toFrozenList(),
    )
}

private fun PluginV2StageIndex.frozenCopy(): PluginV2StageIndex {
    return copy(
        handlerIdsByStage = LinkedHashMap<PluginV2InternalStage, List<String>>().also { index ->
            handlerIdsByStage.forEach { (stage, handlerIds) ->
                index[stage] = handlerIds.toList().toFrozenList()
            }
        }.toFrozenMap(),
    )
}

private fun PluginV2CompiledMessageHandler.frozenCopy(): PluginV2CompiledMessageHandler {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
    )
}

private fun PluginV2CompiledCommandHandler.frozenCopy(): PluginV2CompiledCommandHandler {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
        aliases = aliases.toFrozenList(),
        groupPath = groupPath.toFrozenList(),
        commandPath = commandPath.toFrozenList(),
        aliasPaths = aliasPaths.map(List<String>::toFrozenList).toFrozenList(),
    )
}

private fun PluginV2CommandBucket.frozenCopy(): PluginV2CommandBucket {
    return copy(
        commandPath = commandPath.toFrozenList(),
        handlers = handlers.map(PluginV2CompiledCommandHandler::frozenCopy).toFrozenList(),
        aliasPaths = aliasPaths.map(List<String>::toFrozenList).toFrozenList(),
    )
}

private fun PluginV2CompiledRegexHandler.frozenCopy(): PluginV2CompiledRegexHandler {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
        flags = flags.toFrozenSet(),
        namedGroupNames = namedGroupNames.toFrozenList(),
    )
}

private fun PluginV2CompiledLifecycleHandler.frozenCopy(): PluginV2CompiledLifecycleHandler {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
    )
}

private fun PluginV2CompiledLlmHookHandler.frozenCopy(): PluginV2CompiledLlmHookHandler {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
    )
}

private fun BootstrapRegistrationMetadata.frozenCopy(): BootstrapRegistrationMetadata {
    return copy(
        values = LinkedHashMap(values).toFrozenMap(),
    )
}

private fun List<PluginV2CompiledFilterAttachment>.frozenFilterAttachments(): List<PluginV2CompiledFilterAttachment> {
    return map { attachment ->
        attachment.copy(
            arguments = LinkedHashMap(attachment.arguments).toFrozenMap(),
        )
    }.toFrozenList()
}

private fun <T> List<T>.toFrozenList(): List<T> {
    return Collections.unmodifiableList(ArrayList(this))
}

private fun <K, V> Map<K, V>.toFrozenMap(): Map<K, V> {
    return Collections.unmodifiableMap(LinkedHashMap(this))
}

private fun <T> Set<T>.toFrozenSet(): Set<T> {
    return Collections.unmodifiableSet(LinkedHashSet(this))
}

internal const val COMMAND_PATH_KEY_SEPARATOR: Char = '\u0001'

internal fun List<String>.toCommandPathKey(): String {
    return joinToString(separator = COMMAND_PATH_KEY_SEPARATOR.toString())
}

internal fun String.toCommandPathTokens(): List<String> {
    return if (isBlank()) {
        emptyList()
    } else {
        split(COMMAND_PATH_KEY_SEPARATOR)
    }
}

internal fun List<String>.toCommandPathText(): String {
    return joinToString(separator = " ")
}

internal fun String.toCommandPathTokensFromText(): List<String> {
    return trim()
        .split(Regex("\\s+"))
        .map(String::trim)
        .filter(String::isNotBlank)
}

internal fun extractNamedGroupNames(pattern: String): List<String> {
    return NAMED_GROUP_PATTERN.findAll(pattern)
        .map { groupMatch -> groupMatch.groupValues[1] }
        .distinct()
        .toList()
}

internal fun PluginV2CompiledRegexHandler.namedGroups(match: MatchResult): Map<String, String> {
    if (namedGroupNames.isEmpty()) {
        return emptyMap()
    }
    val snapshots = linkedMapOf<String, String>()
    namedGroupNames.forEach { name ->
        snapshots[name] = match.groups[name]?.value.orEmpty()
    }
    return snapshots
}

private val NAMED_GROUP_PATTERN = Regex("\\(\\?<([A-Za-z][A-Za-z0-9_]*)>")

internal fun List<PluginV2CompiledCommandHandler>.sortedForCommandDispatch(): List<PluginV2CompiledCommandHandler> {
    return sortedWith(
        compareByDescending<PluginV2CompiledCommandHandler> { it.priority }
            .thenBy { it.handlerId },
    )
}

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
    ResultDecorating,
    AfterMessageSent,
    ToolUse,
    ToolRespond,
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
) : PluginV2CompiledHandlerDescriptor

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

data class PluginV2CompiledLlmHook(
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

data class PluginV2CompiledTool(
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
    val toolDescriptor: PluginV2ToolDescriptor,
) : PluginV2CompiledHandlerDescriptor

data class PluginV2CompiledToolLifecycleHook(
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

data class PluginV2HandlerRegistry(
    val messageHandlers: List<PluginV2CompiledMessageHandler> = emptyList(),
    val commandHandlers: List<PluginV2CompiledCommandHandler> = emptyList(),
    val regexHandlers: List<PluginV2CompiledRegexHandler> = emptyList(),
    val lifecycleHandlers: List<PluginV2CompiledLifecycleHandler> = emptyList(),
    val llmHooks: List<PluginV2CompiledLlmHook> = emptyList(),
    val tools: List<PluginV2CompiledTool> = emptyList(),
    val toolLifecycleHooks: List<PluginV2CompiledToolLifecycleHook> = emptyList(),
) {
    val totalHandlerCount: Int
        get() = messageHandlers.size +
            commandHandlers.size +
            regexHandlers.size +
            lifecycleHandlers.size +
            llmHooks.size +
            tools.size +
            toolLifecycleHooks.size
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
        regexHandlers = regexHandlers.map(PluginV2CompiledRegexHandler::frozenCopy).toFrozenList(),
        lifecycleHandlers = lifecycleHandlers.map(PluginV2CompiledLifecycleHandler::frozenCopy).toFrozenList(),
        llmHooks = llmHooks.map(PluginV2CompiledLlmHook::frozenCopy).toFrozenList(),
        tools = tools.map(PluginV2CompiledTool::frozenCopy).toFrozenList(),
        toolLifecycleHooks = toolLifecycleHooks.map(PluginV2CompiledToolLifecycleHook::frozenCopy).toFrozenList(),
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
    )
}

private fun PluginV2CompiledRegexHandler.frozenCopy(): PluginV2CompiledRegexHandler {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
        flags = flags.toFrozenSet(),
    )
}

private fun PluginV2CompiledLifecycleHandler.frozenCopy(): PluginV2CompiledLifecycleHandler {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
    )
}

private fun PluginV2CompiledLlmHook.frozenCopy(): PluginV2CompiledLlmHook {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
    )
}

private fun PluginV2CompiledTool.frozenCopy(): PluginV2CompiledTool {
    return copy(
        filterAttachments = filterAttachments.frozenFilterAttachments(),
        metadata = metadata.frozenCopy(),
    )
}

private fun PluginV2CompiledToolLifecycleHook.frozenCopy(): PluginV2CompiledToolLifecycleHook {
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

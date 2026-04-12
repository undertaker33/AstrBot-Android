package com.astrbot.android.runtime.plugin

enum class BootstrapFilterKind {
    Message,
    Command,
    Regex,
}

data class BootstrapFilterDescriptor(
    val kind: BootstrapFilterKind,
    val value: String,
) {
    companion object {
        fun message(value: String): BootstrapFilterDescriptor {
            return BootstrapFilterDescriptor(kind = BootstrapFilterKind.Message, value = value)
        }

        fun command(value: String): BootstrapFilterDescriptor {
            return BootstrapFilterDescriptor(kind = BootstrapFilterKind.Command, value = value)
        }

        fun regex(value: String): BootstrapFilterDescriptor {
            return BootstrapFilterDescriptor(kind = BootstrapFilterKind.Regex, value = value)
        }
    }
}

fun interface PluginV2CallbackHandle {
    fun invoke()
}

data class BootstrapRegistrationMetadata(
    val values: Map<String, String> = emptyMap(),
)

data class BaseHandlerRegistrationInput(
    val registrationKey: String? = null,
    val declaredFilters: List<BootstrapFilterDescriptor> = emptyList(),
    val priority: Int = 0,
    val metadata: BootstrapRegistrationMetadata = BootstrapRegistrationMetadata(),
)

data class MessageHandlerRegistrationInput(
    val base: BaseHandlerRegistrationInput = BaseHandlerRegistrationInput(),
    val handler: PluginV2CallbackHandle,
)

data class CommandHandlerRegistrationInput(
    val base: BaseHandlerRegistrationInput = BaseHandlerRegistrationInput(),
    val command: String,
    val aliases: List<String> = emptyList(),
    val groupPath: List<String> = emptyList(),
    val handler: PluginV2CallbackHandle,
)

data class RegexHandlerRegistrationInput(
    val base: BaseHandlerRegistrationInput = BaseHandlerRegistrationInput(),
    val pattern: String,
    val flags: Set<String> = emptySet(),
    val handler: PluginV2CallbackHandle,
)

data class LifecycleHandlerRegistrationInput(
    val registrationKey: String? = null,
    val hook: String,
    val priority: Int = 0,
    val declaredFilters: List<BootstrapFilterDescriptor> = emptyList(),
    val metadata: BootstrapRegistrationMetadata = BootstrapRegistrationMetadata(),
    val handler: PluginV2CallbackHandle,
)

data class LlmHookRegistrationInput(
    val registrationKey: String? = null,
    val hook: String,
    val priority: Int = 0,
    val declaredFilters: List<BootstrapFilterDescriptor> = emptyList(),
    val metadata: BootstrapRegistrationMetadata = BootstrapRegistrationMetadata(),
    val handler: PluginV2CallbackHandle,
)

internal data class PluginV2ToolDescriptor(
    val name: String,
    val description: String = "",
)

internal data class ToolRegistrationInput(
    val registrationKey: String? = null,
    val toolDescriptor: PluginV2ToolDescriptor,
    val declaredFilters: List<BootstrapFilterDescriptor> = emptyList(),
    val metadata: BootstrapRegistrationMetadata = BootstrapRegistrationMetadata(),
    val handler: PluginV2CallbackHandle,
)

data class ToolLifecycleHookRegistrationInput(
    val registrationKey: String? = null,
    val hook: String,
    val priority: Int = 0,
    val declaredFilters: List<BootstrapFilterDescriptor> = emptyList(),
    val metadata: BootstrapRegistrationMetadata = BootstrapRegistrationMetadata(),
    val handler: PluginV2CallbackHandle,
)

interface PluginV2RawRegistrationEntry {
    val pluginId: String
    val registrationKey: String?
    val callbackToken: PluginV2CallbackToken
    val priority: Int
    val declaredFilters: List<BootstrapFilterDescriptor>
    val metadata: BootstrapRegistrationMetadata
    val sourceOrder: Int
}

data class MessageHandlerRawRegistration(
    override val pluginId: String,
    override val registrationKey: String?,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val declaredFilters: List<BootstrapFilterDescriptor>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val descriptor: MessageHandlerRegistrationInput,
) : PluginV2RawRegistrationEntry

data class CommandHandlerRawRegistration(
    override val pluginId: String,
    override val registrationKey: String?,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val declaredFilters: List<BootstrapFilterDescriptor>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val descriptor: CommandHandlerRegistrationInput,
) : PluginV2RawRegistrationEntry

data class RegexHandlerRawRegistration(
    override val pluginId: String,
    override val registrationKey: String?,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val declaredFilters: List<BootstrapFilterDescriptor>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val descriptor: RegexHandlerRegistrationInput,
) : PluginV2RawRegistrationEntry

data class LifecycleHandlerRawRegistration(
    override val pluginId: String,
    override val registrationKey: String?,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val declaredFilters: List<BootstrapFilterDescriptor>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val descriptor: LifecycleHandlerRegistrationInput,
) : PluginV2RawRegistrationEntry

data class LlmHookRawRegistration(
    override val pluginId: String,
    override val registrationKey: String?,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val declaredFilters: List<BootstrapFilterDescriptor>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val descriptor: LlmHookRegistrationInput,
) : PluginV2RawRegistrationEntry

internal data class ToolRawRegistration(
    override val pluginId: String,
    override val registrationKey: String?,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val declaredFilters: List<BootstrapFilterDescriptor>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val descriptor: PluginToolDescriptor,
) : PluginV2RawRegistrationEntry

internal data class ToolLifecycleHookRawRegistration(
    override val pluginId: String,
    override val registrationKey: String?,
    override val callbackToken: PluginV2CallbackToken,
    override val priority: Int,
    override val declaredFilters: List<BootstrapFilterDescriptor>,
    override val metadata: BootstrapRegistrationMetadata,
    override val sourceOrder: Int,
    val descriptor: ToolLifecycleHookRegistrationInput,
) : PluginV2RawRegistrationEntry

class PluginV2RawRegistry(
    val pluginId: String,
) {
    init {
        require(pluginId.isNotBlank()) { "pluginId must not be blank." }
    }

    val schemaVersion: Int = SCHEMA_VERSION

    private var nextSourceOrder: Int = 0
    private val messageHandlerRegistrations = mutableListOf<MessageHandlerRawRegistration>()
    private val commandHandlerRegistrations = mutableListOf<CommandHandlerRawRegistration>()
    private val regexHandlerRegistrations = mutableListOf<RegexHandlerRawRegistration>()
    private val lifecycleHandlerRegistrations = mutableListOf<LifecycleHandlerRawRegistration>()
    private val llmHookRegistrations = mutableListOf<LlmHookRawRegistration>()
    private val toolRegistrations = mutableListOf<ToolRawRegistration>()
    private val toolLifecycleHookRegistrations = mutableListOf<ToolLifecycleHookRawRegistration>()

    val messageHandlers: List<MessageHandlerRawRegistration>
        get() = messageHandlerRegistrations.toList()

    val commandHandlers: List<CommandHandlerRawRegistration>
        get() = commandHandlerRegistrations.toList()

    val regexHandlers: List<RegexHandlerRawRegistration>
        get() = regexHandlerRegistrations.toList()

    val lifecycleHandlers: List<LifecycleHandlerRawRegistration>
        get() = lifecycleHandlerRegistrations.toList()

    val llmHooks: List<LlmHookRawRegistration>
        get() = llmHookRegistrations.toList()

    internal val tools: List<ToolRawRegistration>
        get() = toolRegistrations.toList()

    internal val toolLifecycleHooks: List<ToolLifecycleHookRawRegistration>
        get() = toolLifecycleHookRegistrations.toList()

    internal fun appendMessageHandler(
        callbackToken: PluginV2CallbackToken,
        descriptor: MessageHandlerRegistrationInput,
    ): MessageHandlerRawRegistration {
        return MessageHandlerRawRegistration(
            pluginId = pluginId,
            registrationKey = descriptor.base.registrationKey,
            callbackToken = callbackToken,
            priority = descriptor.base.priority,
            declaredFilters = descriptor.base.declaredFilters.toList(),
            metadata = descriptor.base.metadata,
            sourceOrder = allocateSourceOrder(),
            descriptor = descriptor,
        ).also(messageHandlerRegistrations::add)
    }

    internal fun appendCommandHandler(
        callbackToken: PluginV2CallbackToken,
        descriptor: CommandHandlerRegistrationInput,
    ): CommandHandlerRawRegistration {
        return CommandHandlerRawRegistration(
            pluginId = pluginId,
            registrationKey = descriptor.base.registrationKey,
            callbackToken = callbackToken,
            priority = descriptor.base.priority,
            declaredFilters = descriptor.base.declaredFilters.toList(),
            metadata = descriptor.base.metadata,
            sourceOrder = allocateSourceOrder(),
            descriptor = descriptor,
        ).also(commandHandlerRegistrations::add)
    }

    internal fun appendRegexHandler(
        callbackToken: PluginV2CallbackToken,
        descriptor: RegexHandlerRegistrationInput,
    ): RegexHandlerRawRegistration {
        return RegexHandlerRawRegistration(
            pluginId = pluginId,
            registrationKey = descriptor.base.registrationKey,
            callbackToken = callbackToken,
            priority = descriptor.base.priority,
            declaredFilters = descriptor.base.declaredFilters.toList(),
            metadata = descriptor.base.metadata,
            sourceOrder = allocateSourceOrder(),
            descriptor = descriptor,
        ).also(regexHandlerRegistrations::add)
    }

    internal fun appendLifecycleHandler(
        callbackToken: PluginV2CallbackToken,
        descriptor: LifecycleHandlerRegistrationInput,
    ): LifecycleHandlerRawRegistration {
        return LifecycleHandlerRawRegistration(
            pluginId = pluginId,
            registrationKey = descriptor.registrationKey,
            callbackToken = callbackToken,
            priority = descriptor.priority,
            declaredFilters = descriptor.declaredFilters.toList(),
            metadata = descriptor.metadata,
            sourceOrder = allocateSourceOrder(),
            descriptor = descriptor,
        ).also(lifecycleHandlerRegistrations::add)
    }

    internal fun appendLlmHook(
        callbackToken: PluginV2CallbackToken,
        descriptor: LlmHookRegistrationInput,
    ): LlmHookRawRegistration {
        return LlmHookRawRegistration(
            pluginId = pluginId,
            registrationKey = descriptor.registrationKey,
            callbackToken = callbackToken,
            priority = descriptor.priority,
            declaredFilters = descriptor.declaredFilters.toList(),
            metadata = descriptor.metadata,
            sourceOrder = allocateSourceOrder(),
            descriptor = descriptor,
        ).also(llmHookRegistrations::add)
    }

    internal fun appendTool(
        callbackToken: PluginV2CallbackToken,
        descriptor: PluginToolDescriptor,
    ): ToolRawRegistration {
        return ToolRawRegistration(
            pluginId = pluginId,
            registrationKey = descriptor.toolId,
            callbackToken = callbackToken,
            priority = 0,
            declaredFilters = emptyList(),
            metadata = BootstrapRegistrationMetadata(),
            sourceOrder = allocateSourceOrder(),
            descriptor = descriptor,
        ).also(toolRegistrations::add)
    }

    internal fun appendTool(
        callbackToken: PluginV2CallbackToken,
        descriptor: ToolRegistrationInput,
    ): ToolRawRegistration {
        return appendTool(
            callbackToken = callbackToken,
            descriptor = PluginToolDescriptor(
                pluginId = pluginId,
                name = descriptor.toolDescriptor.name,
                description = descriptor.toolDescriptor.description,
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.PLUGIN_V2,
                inputSchema = linkedMapOf("type" to "object"),
                metadata = descriptor.metadata.values.takeIf { it.isNotEmpty() },
            ),
        )
    }

    internal fun appendToolLifecycleHook(
        callbackToken: PluginV2CallbackToken,
        descriptor: ToolLifecycleHookRegistrationInput,
    ): ToolLifecycleHookRawRegistration {
        return ToolLifecycleHookRawRegistration(
            pluginId = pluginId,
            registrationKey = descriptor.registrationKey,
            callbackToken = callbackToken,
            priority = descriptor.priority,
            declaredFilters = descriptor.declaredFilters.toList(),
            metadata = descriptor.metadata,
            sourceOrder = allocateSourceOrder(),
            descriptor = descriptor,
        ).also(toolLifecycleHookRegistrations::add)
    }

    fun isEmpty(): Boolean {
        return messageHandlerRegistrations.isEmpty() &&
            commandHandlerRegistrations.isEmpty() &&
            regexHandlerRegistrations.isEmpty() &&
            lifecycleHandlerRegistrations.isEmpty() &&
            llmHookRegistrations.isEmpty() &&
            toolRegistrations.isEmpty() &&
            toolLifecycleHookRegistrations.isEmpty()
    }

    private fun allocateSourceOrder(): Int {
        return nextSourceOrder++
    }

    companion object {
        const val SCHEMA_VERSION: Int = 1
    }
}

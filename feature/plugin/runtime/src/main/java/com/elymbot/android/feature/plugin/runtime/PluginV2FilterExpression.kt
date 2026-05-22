package com.elymbot.android.feature.plugin.runtime

enum class PluginV2BuiltinFilterKind(
    val wireKey: String,
    val reasonCode: String,
) {
    EventMessageType(
        wireKey = "eventMessageType",
        reasonCode = "event_message_type",
    ),
    PlatformAdapterType(
        wireKey = "platformAdapterType",
        reasonCode = "platform_adapter_type",
    ),
    PermissionType(
        wireKey = "permissionType",
        reasonCode = "permission_type",
    );

    companion object {
        fun fromWireKey(value: String): PluginV2BuiltinFilterKind? {
            val normalized = value.trim().replace("_", "").replace("-", "")
            return entries.firstOrNull { kind ->
                kind.wireKey.replace("_", "").replace("-", "").equals(normalized, ignoreCase = true) ||
                    kind.reasonCode.replace("_", "").replace("-", "").equals(normalized, ignoreCase = true)
            }
        }
    }
}

sealed interface PluginV2FilterExpression {
    data class AllOf(
        val children: List<PluginV2FilterExpression>,
    ) : PluginV2FilterExpression

    data class AnyOf(
        val children: List<PluginV2FilterExpression>,
    ) : PluginV2FilterExpression

    data class Not(
        val child: PluginV2FilterExpression,
    ) : PluginV2FilterExpression

    data class Builtin(
        val kind: PluginV2BuiltinFilterKind,
        val value: String,
    ) : PluginV2FilterExpression

    data class Custom(
        val name: String,
        val arguments: Map<String, String> = emptyMap(),
    ) : PluginV2FilterExpression
}

sealed interface PluginV2CompiledFilterExpression {
    data class AllOf(
        val children: List<PluginV2CompiledFilterExpression>,
    ) : PluginV2CompiledFilterExpression

    data class AnyOf(
        val children: List<PluginV2CompiledFilterExpression>,
    ) : PluginV2CompiledFilterExpression

    data class Not(
        val child: PluginV2CompiledFilterExpression,
    ) : PluginV2CompiledFilterExpression

    data class Builtin(
        val kind: PluginV2BuiltinFilterKind,
        val value: String,
    ) : PluginV2CompiledFilterExpression {
        val reasonCode: String
            get() = kind.reasonCode
    }

    data class Custom(
        val name: String,
        val arguments: Map<String, String> = emptyMap(),
    ) : PluginV2CompiledFilterExpression
}

internal fun emptyCompiledFilterExpression(): PluginV2CompiledFilterExpression {
    return PluginV2CompiledFilterExpression.AllOf(emptyList())
}

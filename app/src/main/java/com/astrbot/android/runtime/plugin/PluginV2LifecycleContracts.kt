package com.astrbot.android.runtime.plugin

sealed interface PluginLifecycleEventPayload : PluginErrorEventPayload

enum class PluginLifecycleHookSurface(
    val wireValue: String,
) {
    OnAstrbotLoaded("on_astrbot_loaded"),
    OnPlatformLoaded("on_platform_loaded"),
    OnPluginLoaded("on_plugin_loaded"),
    OnPluginUnloaded("on_plugin_unloaded"),
    OnPluginError("on_plugin_error");
}

data class PluginLifecycleMetadata(
    val pluginName: String,
    val pluginVersion: String,
) : PluginLifecycleEventPayload

data class PluginErrorHookArgs(
    val event: PluginErrorEventPayload,
    val plugin_name: String,
    val handler_name: String,
    val error: Throwable,
    val traceback_text: String,
) : PluginErrorEventPayload

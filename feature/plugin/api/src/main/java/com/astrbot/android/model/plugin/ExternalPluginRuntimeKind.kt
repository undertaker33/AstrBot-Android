package com.astrbot.android.model.plugin

enum class ExternalPluginRuntimeKind(
    val wireValue: String,
) {
    JsQuickJs("js_quickjs");

    companion object {
        fun fromWireValue(value: String): ExternalPluginRuntimeKind? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

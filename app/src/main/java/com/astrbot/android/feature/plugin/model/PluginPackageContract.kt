package com.astrbot.android.model.plugin

data class PluginPackageContract(
    val protocolVersion: Int,
    val runtime: PluginRuntimeDeclaration,
    val config: PluginConfigEntryPoints = PluginConfigEntryPoints(),
) {
    init {
        require(protocolVersion == SUPPORTED_PROTOCOL_VERSION) {
            "protocolVersion has unsupported value: $protocolVersion"
        }
    }

    companion object {
        const val SUPPORTED_PROTOCOL_VERSION = 2
    }
}

data class PluginRuntimeDeclaration(
    val kind: String,
    val bootstrap: String,
    val apiVersion: Int,
) {
    init {
        require(kind == ExternalPluginRuntimeKind.JsQuickJs.wireValue) {
            "runtime.kind has unsupported value: $kind"
        }
    }
}

data class PluginConfigEntryPoints(
    val staticSchema: String = "",
    val settingsSchema: String = "",
)

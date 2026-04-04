package com.astrbot.android.model.plugin

enum class ExternalPluginExecutionBindingStatus {
    READY,
    DISABLED,
    MISSING_CONTRACT,
    MISSING_ENTRY,
    INVALID_CONTRACT,
}

enum class ExternalPluginRuntimeKind(
    val wireValue: String,
) {
    PythonMain("python_main");

    companion object {
        fun fromWireValue(value: String): ExternalPluginRuntimeKind? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

data class ExternalPluginExecutionEntryPoint(
    val runtimeKind: ExternalPluginRuntimeKind,
    val path: String,
    val entrySymbol: String,
)

data class ExternalPluginExecutionContract(
    val contractVersion: Int,
    val enabled: Boolean = true,
    val entryPoint: ExternalPluginExecutionEntryPoint,
    val supportedTriggers: Set<PluginTriggerSource> = emptySet(),
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val DEFAULT_FILE_NAME = "android-execution.json"
    }
}

data class ExternalPluginRuntimeBinding(
    val installRecord: PluginInstallRecord,
    val status: ExternalPluginExecutionBindingStatus,
    val contractFileName: String = ExternalPluginExecutionContract.DEFAULT_FILE_NAME,
    val contract: ExternalPluginExecutionContract? = null,
    val entryAbsolutePath: String = "",
    val errorSummary: String = "",
) {
    val isReady: Boolean
        get() = status == ExternalPluginExecutionBindingStatus.READY
}

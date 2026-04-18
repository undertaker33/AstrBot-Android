package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import com.astrbot.android.model.plugin.ExternalPluginExecutionContract
import com.astrbot.android.model.plugin.ExternalPluginExecutionContractJson
import com.astrbot.android.model.plugin.ExternalPluginRuntimeBinding
import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import org.json.JSONObject

class ExternalPluginRuntimeBinder(
    private val contractFileName: String = ExternalPluginExecutionContract.DEFAULT_FILE_NAME,
) {
    fun bind(installRecord: PluginInstallRecord): ExternalPluginRuntimeBinding {
        if (installRecord.packageContractSnapshot?.protocolVersion == 2) {
            return binding(
                installRecord = installRecord,
                status = ExternalPluginExecutionBindingStatus.INVALID_CONTRACT,
                errorSummary = "Plugin v2 records are skipped by the legacy external runtime binder.",
            )
        }
        val pluginRoot = installRecord.extractedDir.trim()
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: return binding(
                installRecord = installRecord,
                status = ExternalPluginExecutionBindingStatus.INVALID_CONTRACT,
                errorSummary = "Plugin extracted directory is unavailable.",
            )

        val contractFile = File(pluginRoot, contractFileName)
        if (!contractFile.isFile) {
            return binding(
                installRecord = installRecord,
                status = ExternalPluginExecutionBindingStatus.MISSING_CONTRACT,
                errorSummary = "Missing external execution contract: $contractFileName",
            )
        }

        val contract = runCatching {
            ExternalPluginExecutionContractJson.decodeContract(
                JSONObject(contractFile.readText(Charsets.UTF_8)),
            )
        }.getOrElse { error ->
            return binding(
                installRecord = installRecord,
                status = ExternalPluginExecutionBindingStatus.INVALID_CONTRACT,
                errorSummary = error.message ?: "Invalid external execution contract.",
            )
        }

        if (!contract.enabled) {
            return binding(
                installRecord = installRecord,
                status = ExternalPluginExecutionBindingStatus.DISABLED,
                contract = contract,
            )
        }

        val entryFile = runCatching {
            resolveEntryFile(pluginRoot = pluginRoot, relativePath = contract.entryPoint.path)
        }.getOrElse { error ->
            return binding(
                installRecord = installRecord,
                status = ExternalPluginExecutionBindingStatus.INVALID_CONTRACT,
                contract = contract,
                errorSummary = error.message ?: "Invalid external execution entry path.",
            )
        }

        if (!entryFile.isFile) {
            return binding(
                installRecord = installRecord,
                status = ExternalPluginExecutionBindingStatus.MISSING_ENTRY,
                contract = contract,
                errorSummary = "Missing external entry file: ${contract.entryPoint.path}",
            )
        }

        return binding(
            installRecord = installRecord,
            status = ExternalPluginExecutionBindingStatus.READY,
            contract = contract,
            entryAbsolutePath = entryFile.absolutePath,
        )
    }

    private fun resolveEntryFile(
        pluginRoot: File,
        relativePath: String,
    ): File {
        val sanitizedRelativePath = relativePath.trim()
        require(sanitizedRelativePath.isNotBlank()) { "entryPoint.path is required" }
        val rootCanonical = pluginRoot.canonicalFile.toPath()
        val candidate = File(pluginRoot, sanitizedRelativePath).canonicalFile.toPath()
        require(candidate.startsWith(rootCanonical)) {
            "entryPoint.path escapes plugin root: $sanitizedRelativePath"
        }
        return candidate.toFile()
    }

    private fun binding(
        installRecord: PluginInstallRecord,
        status: ExternalPluginExecutionBindingStatus,
        contract: ExternalPluginExecutionContract? = null,
        entryAbsolutePath: String = "",
        errorSummary: String = "",
    ): ExternalPluginRuntimeBinding {
        return ExternalPluginRuntimeBinding(
            installRecord = installRecord,
            status = status,
            contractFileName = contractFileName,
            contract = contract,
            entryAbsolutePath = entryAbsolutePath,
            errorSummary = errorSummary,
        )
    }
}

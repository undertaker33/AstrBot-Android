package com.astrbot.android.feature.plugin.runtime

import com.astrbot.android.model.plugin.PluginInstallRecord
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal fun createQuickJsExternalPluginInstallRecord(
    extractedDir: File,
    pluginId: String,
    version: String = "1.0.0",
    supportedTriggers: List<String>,
): PluginInstallRecord {
    File(extractedDir, "runtime/index.js").apply {
        parentFile?.mkdirs()
        writeText(
            """
            export function handleEvent(contextJson) {
              return contextJson;
            }
            """.trimIndent(),
            Charsets.UTF_8,
        )
    }
    File(extractedDir, "android-execution.json").writeText(
        JSONObject(
            mapOf(
                "contractVersion" to 1,
                "enabled" to true,
                "entryPoint" to JSONObject(
                    mapOf(
                        "runtimeKind" to "js_quickjs",
                        "path" to "runtime/index.js",
                        "entrySymbol" to "handleEvent",
                    ),
                ),
                "supportedTriggers" to JSONArray(supportedTriggers),
            ),
        ).toString(),
        Charsets.UTF_8,
    )

    val current = samplePluginInstallRecord(
        pluginId = pluginId,
        version = version,
        lastUpdatedAt = 100L,
    )
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = current.manifestSnapshot,
        source = current.source,
        permissionSnapshot = current.permissionSnapshot,
        compatibilityState = current.compatibilityState,
        uninstallPolicy = current.uninstallPolicy,
        enabled = true,
        failureState = current.failureState,
        catalogSourceId = current.catalogSourceId,
        installedPackageUrl = current.installedPackageUrl,
        lastCatalogCheckAtEpochMillis = current.lastCatalogCheckAtEpochMillis,
        installedAt = current.installedAt,
        lastUpdatedAt = current.lastUpdatedAt,
        localPackagePath = current.localPackagePath,
        extractedDir = extractedDir.absolutePath,
    )
}

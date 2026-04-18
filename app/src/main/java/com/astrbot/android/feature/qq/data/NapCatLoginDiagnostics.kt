package com.astrbot.android.feature.qq.data

import java.io.File

internal object NapCatLoginDiagnostics {
    fun buildRuntimeDiagnosticsLines(
        filesDir: File,
        trigger: String,
        detail: String,
    ): List<String> {
        val runtimeDir = File(filesDir, "runtime")
        val configDir = File(runtimeDir, "rootfs/ubuntu/root/napcat/config")
        val webUiFile = File(configDir, "webui.json")
        val onebotFile = File(configDir, "onebot11.json")
        val napcatLogFile = File(runtimeDir, "logs/napcat.log")
        return buildList {
            add("QQ login runtime diag: trigger=$trigger detail=$detail runtimeDir=${runtimeDir.absolutePath}")
            add("QQ login runtime diag: webui.json exists=${webUiFile.exists()} snapshot=${webUiFile.readCompactSnapshot()}")
            add("QQ login runtime diag: onebot11.json exists=${onebotFile.exists()} snapshot=${onebotFile.readCompactSnapshot()}")
            add("QQ login runtime diag: napcat.log exists=${napcatLogFile.exists()} tail=${napcatLogFile.readTailSnapshot()}")
        }
    }

    private fun File.readCompactSnapshot(maxChars: Int = 500): String {
        if (!exists() || !isFile) {
            return "<missing>"
        }
        return runCatching {
            readText()
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(maxChars)
        }.getOrElse { "<unreadable:${it.message ?: it.javaClass.simpleName}>" }
    }

    private fun File.readTailSnapshot(maxLines: Int = 8, maxChars: Int = 500): String {
        if (!exists() || !isFile) {
            return "<missing>"
        }
        return runCatching {
            readLines()
                .takeLast(maxLines)
                .joinToString(" | ") { line -> line.trim() }
                .replace(Regex("\\s+"), " ")
                .trim()
                .take(maxChars)
        }.getOrElse { "<unreadable:${it.message ?: it.javaClass.simpleName}>" }
    }
}

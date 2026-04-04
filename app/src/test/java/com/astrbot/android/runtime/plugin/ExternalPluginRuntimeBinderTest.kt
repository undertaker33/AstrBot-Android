package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.ExternalPluginExecutionBindingStatus
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginTriggerSource
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginRuntimeBinderTest {

    @Test
    fun binder_reads_android_execution_contract_and_marks_binding_ready_when_entry_exists() {
        val tempDir = Files.createTempDirectory("external-plugin-binding-ready").toFile()
        try {
            val extractedDir = File(tempDir, "plugin").apply { mkdirs() }
            File(extractedDir, "runtime").mkdirs()
            File(extractedDir, "runtime/entry.py").writeText("print('ready')", Charsets.UTF_8)
            File(extractedDir, "android-execution.json").writeText(
                """
                {
                  "contractVersion": 1,
                  "entryPoint": {
                    "runtimeKind": "python_main",
                    "path": "runtime/entry.py",
                    "entrySymbol": "PluginMain"
                  },
                  "supportedTriggers": ["on_plugin_entry_click"]
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )

            val binding = ExternalPluginRuntimeBinder().bind(installedRecord(extractedDir))

            assertEquals(ExternalPluginExecutionBindingStatus.READY, binding.status)
            assertEquals("android-execution.json", binding.contractFileName)
            assertEquals(1, binding.contract?.contractVersion)
            assertEquals("runtime/entry.py", binding.contract?.entryPoint?.path)
            assertEquals(
                setOf(PluginTriggerSource.OnPluginEntryClick),
                binding.contract?.supportedTriggers,
            )
            assertEquals(extractedDir.resolve("runtime/entry.py").absolutePath, binding.entryAbsolutePath)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun binder_marks_disabled_when_contract_explicitly_turns_off_external_entry() {
        val tempDir = Files.createTempDirectory("external-plugin-binding-disabled").toFile()
        try {
            val extractedDir = File(tempDir, "plugin").apply { mkdirs() }
            File(extractedDir, "runtime").mkdirs()
            File(extractedDir, "runtime/entry.py").writeText("print('disabled')", Charsets.UTF_8)
            File(extractedDir, "android-execution.json").writeText(
                """
                {
                  "contractVersion": 1,
                  "enabled": false,
                  "entryPoint": {
                    "runtimeKind": "python_main",
                    "path": "runtime/entry.py",
                    "entrySymbol": "PluginMain"
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )

            val binding = ExternalPluginRuntimeBinder().bind(installedRecord(extractedDir))

            assertEquals(ExternalPluginExecutionBindingStatus.DISABLED, binding.status)
            assertTrue(binding.entryAbsolutePath.isBlank())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun binder_marks_missing_contract_when_android_execution_file_is_absent() {
        val tempDir = Files.createTempDirectory("external-plugin-binding-missing-contract").toFile()
        try {
            val extractedDir = File(tempDir, "plugin").apply { mkdirs() }

            val binding = ExternalPluginRuntimeBinder().bind(installedRecord(extractedDir))

            assertEquals(ExternalPluginExecutionBindingStatus.MISSING_CONTRACT, binding.status)
            assertTrue(binding.errorSummary.contains("android-execution.json"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun binder_marks_missing_entry_when_contract_points_to_nonexistent_file() {
        val tempDir = Files.createTempDirectory("external-plugin-binding-missing-entry").toFile()
        try {
            val extractedDir = File(tempDir, "plugin").apply { mkdirs() }
            File(extractedDir, "android-execution.json").writeText(
                """
                {
                  "contractVersion": 1,
                  "entryPoint": {
                    "runtimeKind": "python_main",
                    "path": "runtime/missing.py",
                    "entrySymbol": "PluginMain"
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )

            val binding = ExternalPluginRuntimeBinder().bind(installedRecord(extractedDir))

            assertEquals(ExternalPluginExecutionBindingStatus.MISSING_ENTRY, binding.status)
            assertTrue(binding.errorSummary.contains("runtime/missing.py"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun binder_rejects_contract_when_entry_path_escapes_plugin_root() {
        val tempDir = Files.createTempDirectory("external-plugin-binding-illegal-path").toFile()
        try {
            val extractedDir = File(tempDir, "plugin").apply { mkdirs() }
            File(extractedDir, "android-execution.json").writeText(
                """
                {
                  "contractVersion": 1,
                  "entryPoint": {
                    "runtimeKind": "python_main",
                    "path": "../escape.py",
                    "entrySymbol": "PluginMain"
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )

            val binding = ExternalPluginRuntimeBinder().bind(installedRecord(extractedDir))

            assertEquals(ExternalPluginExecutionBindingStatus.INVALID_CONTRACT, binding.status)
            assertTrue(binding.errorSummary.contains("../escape.py"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun binder_rejects_contract_when_contract_version_is_unsupported() {
        val tempDir = Files.createTempDirectory("external-plugin-binding-unsupported-version").toFile()
        try {
            val extractedDir = File(tempDir, "plugin").apply { mkdirs() }
            File(extractedDir, "android-execution.json").writeText(
                """
                {
                  "contractVersion": 2,
                  "entryPoint": {
                    "runtimeKind": "python_main",
                    "path": "runtime/entry.py",
                    "entrySymbol": "PluginMain"
                  }
                }
                """.trimIndent(),
                Charsets.UTF_8,
            )

            val binding = ExternalPluginRuntimeBinder().bind(installedRecord(extractedDir))

            assertEquals(ExternalPluginExecutionBindingStatus.INVALID_CONTRACT, binding.status)
            assertTrue(binding.errorSummary.contains("contractVersion"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun installedRecord(extractedDir: File): PluginInstallRecord {
        return PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = "com.astrbot.samples.external",
                version = "1.0.0",
                protocolVersion = 1,
                author = "AstrBot",
                title = "External Plugin",
                description = "External plugin runtime binding sample",
                permissions = emptyList(),
                minHostVersion = "0.4.0",
                maxHostVersion = "",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "External execution binding sample",
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = extractedDir.absolutePath,
                importedAt = 1L,
            ),
            extractedDir = extractedDir.absolutePath,
        )
    }
}

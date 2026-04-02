package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPackageValidatorTest {
    @Test
    fun validator_rejects_manifest_with_missing_required_field() {
        val tempDir = Files.createTempDirectory("plugin-validator-missing-field").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    remove("title")
                },
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.3.6",
                    supportedProtocolVersion = 1,
                ).validate(packageFile)
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.message?.contains("title") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_marks_protocol_incompatible_manifest() {
        val tempDir = Files.createTempDirectory("plugin-validator-protocol").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    put("protocolVersion", 2)
                },
            )

            val result = PluginPackageValidator(
                hostVersion = "0.3.6",
                supportedProtocolVersion = 1,
            ).validate(packageFile)

            assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, result.compatibilityState.status)
            assertEquals("com.example.demo", result.manifest.pluginId)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_non_numeric_protocol_version_instead_of_coercing_default() {
        val tempDir = Files.createTempDirectory("plugin-validator-protocol-type").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    put("protocolVersion", "v1")
                },
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.3.6",
                    supportedProtocolVersion = 1,
                ).validate(packageFile)
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.message?.contains("protocolVersion") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_marks_host_version_incompatible_when_minimum_is_not_met() {
        val tempDir = Files.createTempDirectory("plugin-validator-host-version").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    put("minHostVersion", "9.9.9")
                },
            )

            val result = PluginPackageValidator(
                hostVersion = "0.3.6",
                supportedProtocolVersion = 1,
            ).validate(packageFile)

            assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, result.compatibilityState.status)
            assertTrue(result.compatibilityState.notes.contains("below required minimum"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_manifest_when_permissions_field_is_missing() {
        val tempDir = Files.createTempDirectory("plugin-validator-permissions-missing").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    remove("permissions")
                },
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.3.6",
                    supportedProtocolVersion = 1,
                ).validate(packageFile)
            }.exceptionOrNull()

            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.message?.contains("permissions") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_unsafe_archive_entry_before_manifest() {
        val tempDir = Files.createTempDirectory("plugin-validator-unsafe-before").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                orderedEntries = listOf(
                    "../escape.txt" to "blocked",
                    "manifest.json" to validManifest().toString(2),
                    "assets/readme.txt" to "hello",
                ),
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.3.6",
                    supportedProtocolVersion = 1,
                ).validate(packageFile)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("unsafe") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_unsafe_archive_entry_after_manifest() {
        val tempDir = Files.createTempDirectory("plugin-validator-unsafe-after").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                orderedEntries = listOf(
                    "manifest.json" to validManifest().toString(2),
                    "../escape.txt" to "blocked",
                    "assets/readme.txt" to "hello",
                ),
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.3.6",
                    supportedProtocolVersion = 1,
                ).validate(packageFile)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("unsafe") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun validManifest(): JSONObject {
        return JSONObject()
            .put("pluginId", "com.example.demo")
            .put("version", "1.0.0")
            .put("protocolVersion", 1)
            .put("author", "AstrBot")
            .put("title", "Demo Plugin")
            .put("description", "Example plugin")
            .put("permissions", JSONArray())
            .put("minHostVersion", "0.3.0")
            .put("maxHostVersion", "")
            .put("sourceType", "LOCAL_FILE")
            .put("entrySummary", "Example entry")
            .put("riskLevel", "LOW")
    }

    private fun createPluginPackage(
        directory: File,
        manifest: JSONObject,
        extraEntries: Map<String, String> = mapOf("assets/readme.txt" to "hello"),
        orderedEntries: List<Pair<String, String>>? = null,
    ): File {
        val packageFile = File(directory, "plugin-package.zip")
        ZipOutputStream(packageFile.outputStream()).use { output ->
            val entries = orderedEntries ?: buildList {
                add("manifest.json" to manifest.toString(2))
                addAll(extraEntries.entries.map { it.key to it.value })
            }
            entries.forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path))
                output.write(content.toByteArray(Charsets.UTF_8))
                output.closeEntry()
            }
        }
        return packageFile
    }
}

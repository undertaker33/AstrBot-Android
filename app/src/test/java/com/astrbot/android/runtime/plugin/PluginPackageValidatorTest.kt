package com.astrbot.android.runtime.plugin

import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginPackageContract
import com.astrbot.android.model.plugin.PluginPackageValidationIssue
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPackageValidatorTest {
    @Test
    fun validator_returns_v2_shape_for_valid_package() {
        val tempDir = Files.createTempDirectory("plugin-validator-v2-shape").toFile()
        try {
            val packageFile = createV2PluginPackage(directory = tempDir)

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val packageContract = extractField<PluginPackageContract?>(result, "packageContract")
            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(2, packageContract?.protocolVersion)
            assertEquals("js_quickjs", packageContract?.runtime?.kind)
            assertEquals("runtime/index.js", packageContract?.runtime?.bootstrap)
            assertTrue(validationIssues.isEmpty())
            assertEquals(true, extractField<Boolean>(result, "installable"))
            assertEquals(PluginCompatibilityStatus.COMPATIBLE, result.compatibilityState.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_package_without_android_plugin_json() {
        val tempDir = Files.createTempDirectory("plugin-validator-missing-android-plugin").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                includeAndroidPlugin = false,
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertNull(extractField<PluginPackageContract?>(result, "packageContract"))
            assertTrue(validationIssues.any { it.message == "Damaged v2 plugin package: Missing android-plugin.json." })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_reports_legacy_contract_issue_when_android_execution_json_is_present() {
        val tempDir = Files.createTempDirectory("plugin-validator-legacy-contract").toFile()
        try {
            val packageFile = createLegacyV1PluginPackage(directory = tempDir)

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertTrue(validationIssues.any { it.code == "legacy_contract" })
            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, result.compatibilityState.status)
            assertNull(extractField<PluginPackageContract?>(result, "packageContract"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_v2_package_with_legacy_file_but_missing_android_plugin_json() {
        val tempDir = Files.createTempDirectory("plugin-validator-mixed-legacy").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest(protocolVersion = 2),
                includeAndroidPlugin = false,
                includeLegacyExecutionContract = true,
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertTrue(validationIssues.any { it.code == "legacy_contract" })
            assertTrue(validationIssues.any { it.message.contains("android-plugin.json") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_package_when_runtime_kind_is_not_js_quickjs() {
        val tempDir = Files.createTempDirectory("plugin-validator-runtime-kind").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                runtimeKind = "python",
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertNull(extractField<PluginPackageContract?>(result, "packageContract"))
            assertTrue(
                validationIssues.any {
                    it.message == "Damaged v2 plugin package: Android requires runtime.kind = js_quickjs, but found python."
                },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_reports_missing_runtime_index_js_in_validation_issues() {
        val tempDir = Files.createTempDirectory("plugin-validator-missing-runtime").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                includeRuntimeBootstrap = false,
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(true, result.compatibilityState.protocolSupported)
            assertEquals(true, result.compatibilityState.minHostVersionSatisfied)
            assertEquals(true, result.compatibilityState.maxHostVersionSatisfied)
            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertTrue(validationIssues.any { it.code == "missing_runtime_bootstrap" })
            assertTrue(
                validationIssues.any {
                    it.message == "Damaged v2 plugin package: Missing runtime bootstrap file: runtime/index.js"
                },
            )
            assertEquals(PluginCompatibilityStatus.COMPATIBLE, result.compatibilityState.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_does_not_translate_missing_runtime_bootstrap_into_host_version_too_high() {
        val tempDir = Files.createTempDirectory("plugin-validator-bootstrap-host-boundary").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                includeRuntimeBootstrap = false,
                manifest = validManifest().apply {
                    put("maxHostVersion", "9.9.9")
                },
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(true, result.compatibilityState.protocolSupported)
            assertEquals(true, result.compatibilityState.maxHostVersionSatisfied)
            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertTrue(validationIssues.any { it.code == "missing_runtime_bootstrap" })
            assertEquals(PluginCompatibilityStatus.COMPATIBLE, result.compatibilityState.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_reports_missing_schema_file_when_declared() {
        val tempDir = Files.createTempDirectory("plugin-validator-missing-schema").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                configStaticSchema = "schemas/static-config.schema.json",
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertTrue(validationIssues.any { it.code == "missing_schema_file" })
            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertEquals(PluginCompatibilityStatus.COMPATIBLE, result.compatibilityState.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_config_schema_path_that_escapes_plugin_root() {
        val tempDir = Files.createTempDirectory("plugin-validator-unsafe-config-path").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                configStaticSchema = "../schemas/static-config.schema.json",
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertTrue(validationIssues.any { it.message.contains("config.staticSchema") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_reports_protocol_mismatch_when_manifest_protocol_differs_from_android_plugin() {
        val tempDir = Files.createTempDirectory("plugin-validator-protocol-mismatch").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest(protocolVersion = 1),
                androidPluginProtocolVersion = 2,
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertTrue(validationIssues.any { it.code == "manifest_protocol_mismatch" })
            assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, result.compatibilityState.status)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_manifest_with_missing_required_field() {
        val tempDir = Files.createTempDirectory("plugin-validator-missing-field").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    remove("title")
                },
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.4.2",
                    supportedProtocolVersion = 2,
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
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    put("protocolVersion", 1)
                },
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(false, extractField<Boolean>(result, "installable"))
            assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, result.compatibilityState.status)
            assertEquals(1, validationIssues.size)
            assertTrue(validationIssues.any { it.code == "manifest_protocol_mismatch" })
            assertEquals(2, extractField<PluginPackageContract?>(result, "packageContract")?.protocolVersion)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_uses_generic_unsupported_protocol_wording_for_future_protocols() {
        val tempDir = Files.createTempDirectory("plugin-validator-future-protocol").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest(protocolVersion = 3),
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            assertEquals("Protocol version 3 is not supported.", result.compatibilityState.notes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_marks_host_version_incompatible_when_minimum_is_not_met() {
        val tempDir = Files.createTempDirectory("plugin-validator-host-version").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    put("minHostVersion", "9.9.9")
                },
            )

            val result = PluginPackageValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validate(packageFile)

            val validationIssues = extractField<List<PluginPackageValidationIssue>>(result, "validationIssues")

            assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, result.compatibilityState.status)
            assertTrue(result.compatibilityState.notes.contains("below required minimum"))
            assertTrue(validationIssues.isEmpty())
            assertEquals(false, extractField<Boolean>(result, "installable"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun validator_rejects_manifest_when_permissions_field_is_missing() {
        val tempDir = Files.createTempDirectory("plugin-validator-permissions-missing").toFile()
        try {
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest().apply {
                    remove("permissions")
                },
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.4.2",
                    supportedProtocolVersion = 2,
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
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                orderedEntries = listOf(
                    "../escape.txt" to "blocked",
                    "manifest.json" to validManifest().toString(2),
                    "android-plugin.json" to validAndroidPluginJson().toString(2),
                    "runtime/index.js" to "console.log('hello')",
                ),
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.4.2",
                    supportedProtocolVersion = 2,
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
            val packageFile = createV2PluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                orderedEntries = listOf(
                    "manifest.json" to validManifest().toString(2),
                    "android-plugin.json" to validAndroidPluginJson().toString(2),
                    "runtime/index.js" to "console.log('hello')",
                    "../escape.txt" to "blocked",
                ),
            )

            val failure = runCatching {
                PluginPackageValidator(
                    hostVersion = "0.4.2",
                    supportedProtocolVersion = 2,
                ).validate(packageFile)
            }.exceptionOrNull()

            assertTrue(failure is IllegalStateException)
            assertTrue(failure?.message?.contains("unsafe") == true)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun validManifest(protocolVersion: Int = 2): JSONObject {
        return JSONObject()
            .put("pluginId", "com.example.demo")
            .put("version", "1.0.0")
            .put("protocolVersion", protocolVersion)
            .put("author", "AstrBot")
            .put("title", "Demo Plugin")
            .put("description", "Example plugin")
            .put("permissions", org.json.JSONArray())
            .put("minHostVersion", "0.3.0")
            .put("maxHostVersion", "")
            .put("sourceType", "LOCAL_FILE")
            .put("entrySummary", "Example entry")
            .put("riskLevel", "LOW")
    }

    private fun validAndroidPluginJson(
        protocolVersion: Int = 2,
        configStaticSchema: String? = null,
        configSettingsSchema: String? = null,
        runtimeBootstrap: String = "runtime/index.js",
        runtimeKind: String = "js_quickjs",
    ): JSONObject {
        val runtime = JSONObject()
            .put("kind", runtimeKind)
            .put("bootstrap", runtimeBootstrap)
            .put("apiVersion", 1)
        val root = JSONObject()
            .put("protocolVersion", protocolVersion)
            .put("runtime", runtime)
        val config = JSONObject()
        if (configStaticSchema != null) {
            config.put("staticSchema", configStaticSchema)
        }
        if (configSettingsSchema != null) {
            config.put("settingsSchema", configSettingsSchema)
        }
        if (config.length() > 0) {
            root.put("config", config)
        }
        return root
    }

    private fun createV2PluginPackage(
        directory: File,
        manifest: JSONObject = validManifest(),
        includeAndroidPlugin: Boolean = true,
        includeLegacyExecutionContract: Boolean = false,
        includeRuntimeBootstrap: Boolean = true,
        androidPluginProtocolVersion: Int = 2,
        configStaticSchema: String? = null,
        configSettingsSchema: String? = null,
        runtimeBootstrap: String = "runtime/index.js",
        runtimeKind: String = "js_quickjs",
        extraEntries: Map<String, String> = mapOf("assets/readme.txt" to "hello"),
        orderedEntries: List<Pair<String, String>>? = null,
    ): File {
        val packageFile = File(directory, "plugin-package.zip")
        ZipOutputStream(packageFile.outputStream()).use { output ->
            val entries = orderedEntries ?: buildList {
                add("manifest.json" to manifest.toString(2))
                if (includeAndroidPlugin) {
                    add(
                        "android-plugin.json" to validAndroidPluginJson(
                            protocolVersion = androidPluginProtocolVersion,
                            configStaticSchema = configStaticSchema,
                            configSettingsSchema = configSettingsSchema,
                            runtimeBootstrap = runtimeBootstrap,
                            runtimeKind = runtimeKind,
                        ).toString(2),
                    )
                }
                if (includeLegacyExecutionContract) {
                    add("android-execution.json" to JSONObject().put("contractVersion", 1).toString(2))
                }
                if (includeRuntimeBootstrap) {
                    add(runtimeBootstrap to "console.log('hello')")
                }
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

    private fun createLegacyV1PluginPackage(directory: File): File {
        return createV2PluginPackage(
            directory = directory,
            manifest = validManifest(protocolVersion = 1),
            includeAndroidPlugin = false,
            includeLegacyExecutionContract = true,
            includeRuntimeBootstrap = false,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> extractField(instance: Any, fieldName: String): T {
        val field = instance.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(instance) as T
    }
}

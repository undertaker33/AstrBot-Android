package com.astrbot.android.feature.plugin.runtime.validation

import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginValidationSeverity
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPublishValidatorTest {

    @Test
    fun publish_validator_accepts_package_that_matches_android_import_contract() {
        val tempDir = Files.createTempDirectory("plugin-publish-validator-valid").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                extraEntries = mapOf(
                    "_conf_schema.json" to """
                        {
                          "greeting_prefix": {
                            "type": "string",
                            "description": "Greeting prefix",
                            "default": "Hello"
                          }
                        }
                    """.trimIndent(),
                    "assets/readme.txt" to "sample",
                ),
            )

            val report = PluginPublishValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validatePackage(packageFile)

            assertTrue(report.publishAllowed)
            assertEquals("com.example.publishable", report.pluginId)
            assertEquals("1.0.0", report.pluginVersion)
            assertEquals(0, report.errorCount)
            assertTrue(report.issues.isEmpty())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun publish_validator_blocks_package_when_static_schema_file_is_missing() {
        val tempDir = Files.createTempDirectory("plugin-publish-validator-missing-schema").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                extraEntries = mapOf(
                    "assets/readme.txt" to "sample",
                ),
            )

            val report = PluginPublishValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validatePackage(packageFile)

            assertFalse(report.publishAllowed)
            assertTrue(
                report.issues.any { issue ->
                    issue.rule.ruleId == "schema.file.missing" &&
                        issue.severity == PluginValidationSeverity.ERROR
                },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun publish_validator_blocks_package_when_static_schema_uses_unsupported_field_type() {
        val tempDir = Files.createTempDirectory("plugin-publish-validator-unsupported-schema").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                extraEntries = mapOf(
                    "_conf_schema.json" to """
                        {
                          "nested": {
                            "type": "object",
                            "description": "Not supported on Android"
                          }
                        }
                    """.trimIndent(),
                ),
            )

            val report = PluginPublishValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validatePackage(packageFile)

            assertFalse(report.publishAllowed)
            assertTrue(
                report.issues.any { issue ->
                    issue.rule.ruleId == "schema.decode.failed" &&
                        issue.severity == PluginValidationSeverity.ERROR &&
                        issue.message.contains("schema.nested.type")
                },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun publish_validator_warns_when_package_bundles_runtime_workspace_content() {
        val tempDir = Files.createTempDirectory("plugin-publish-validator-runtime-data").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                extraEntries = mapOf(
                    "_conf_schema.json" to """
                        {
                          "enabled": {
                            "type": "bool",
                            "description": "Enabled",
                            "default": true
                          }
                        }
                    """.trimIndent(),
                    "data/runtime-cache.json" to """{"cached":true}""",
                ),
            )

            val report = PluginPublishValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validatePackage(packageFile)

            assertTrue(report.publishAllowed)
            assertTrue(
                report.issues.any { issue ->
                    issue.rule.ruleId == "package.runtime.workspace.content" &&
                        issue.severity == PluginValidationSeverity.WARNING
                },
            )
            assertEquals(0, report.errorCount)
            assertEquals(1, report.warningCount)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun publish_validator_blocks_legacy_v1_package_with_upgrade_required_projection() {
        val tempDir = Files.createTempDirectory("plugin-publish-validator-legacy-v1").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(protocolVersion = 1),
                includeAndroidPlugin = false,
                includeLegacyExecutionContract = true,
                includeRuntimeBootstrap = false,
                extraEntries = mapOf(
                    "_conf_schema.json" to """
                        {
                          "enabled": {
                            "type": "bool",
                            "description": "Enabled",
                            "default": true
                          }
                        }
                    """.trimIndent(),
                ),
            )

            val report = PluginPublishValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validatePackage(packageFile)

            assertFalse(report.publishAllowed)
            assertTrue(report.issues.any { it.message.contains("legacy", ignoreCase = true) })
            assertTrue(report.issues.any { it.message.contains("protocol version 2", ignoreCase = true) })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun publish_validator_blocks_package_when_runtime_bootstrap_is_missing_even_with_valid_schema() {
        val tempDir = Files.createTempDirectory("plugin-publish-validator-missing-bootstrap").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                includeRuntimeBootstrap = false,
                extraEntries = mapOf(
                    "_conf_schema.json" to """
                        {
                          "enabled": {
                            "type": "bool",
                            "description": "Enabled",
                            "default": true
                          }
                        }
                    """.trimIndent(),
                ),
            )

            val report = PluginPublishValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validatePackage(packageFile)

            assertFalse(report.publishAllowed)
            assertTrue(
                report.issues.any {
                    it.message == "Damaged v2 plugin package: Missing runtime bootstrap file: runtime/index.js"
                },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun publish_validator_blocks_future_protocol_package_with_generic_unsupported_wording() {
        val tempDir = Files.createTempDirectory("plugin-publish-validator-future-protocol").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(protocolVersion = 3),
                extraEntries = mapOf(
                    "_conf_schema.json" to """
                        {
                          "enabled": {
                            "type": "bool",
                            "description": "Enabled",
                            "default": true
                          }
                        }
                    """.trimIndent(),
                ),
            )

            val report = PluginPublishValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validatePackage(packageFile)

            assertFalse(report.publishAllowed)
            assertTrue(report.issues.any { it.message == "Protocol version 3 is not supported." })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun publish_validator_uses_same_runtime_kind_message_family_as_installer() {
        val tempDir = Files.createTempDirectory("plugin-publish-validator-runtime-kind").toFile()
        try {
            val packageFile = createPluginPackage(
                directory = tempDir,
                manifest = validManifest(),
                runtimeKind = "python",
                extraEntries = mapOf(
                    "_conf_schema.json" to """
                        {
                          "enabled": {
                            "type": "bool",
                            "description": "Enabled",
                            "default": true
                          }
                        }
                    """.trimIndent(),
                ),
            )

            val report = PluginPublishValidator(
                hostVersion = "0.4.2",
                supportedProtocolVersion = 2,
            ).validatePackage(packageFile)

            assertFalse(report.publishAllowed)
            assertTrue(
                report.issues.any {
                    it.message == "Damaged v2 plugin package: Android requires runtime.kind = js_quickjs, but found python."
                },
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun validManifest(protocolVersion: Int = 2): JSONObject {
        return JSONObject()
            .put("pluginId", "com.example.publishable")
            .put("version", "1.0.0")
            .put("protocolVersion", protocolVersion)
            .put("author", "AstrBot")
            .put("title", "Publishable Plugin")
            .put("description", "Example plugin package for publish validation.")
            .put("permissions", JSONArray())
            .put("minHostVersion", "0.4.0")
            .put("maxHostVersion", "")
            .put("sourceType", PluginSourceType.LOCAL_FILE.name)
            .put("entrySummary", "Publish validation sample")
            .put("riskLevel", "LOW")
    }

    private fun createPluginPackage(
        directory: File,
        manifest: JSONObject,
        extraEntries: Map<String, String>,
        includeAndroidPlugin: Boolean = true,
        includeLegacyExecutionContract: Boolean = false,
        includeRuntimeBootstrap: Boolean = true,
        runtimeKind: String = "js_quickjs",
    ): File {
        val packageFile = File(directory, "publishable-plugin.zip")
        ZipOutputStream(packageFile.outputStream()).use { output ->
            writeEntry(output, "manifest.json", manifest.toString(2))
            if (includeAndroidPlugin) {
                writeEntry(
                    output,
                    "android-plugin.json",
                    JSONObject()
                        .put("protocolVersion", 2)
                        .put(
                            "runtime",
                            JSONObject()
                                .put("kind", runtimeKind)
                                .put("bootstrap", "runtime/index.js")
                                .put("apiVersion", 1),
                        )
                        .put(
                            "config",
                            JSONObject().put("staticSchema", "_conf_schema.json"),
                        )
                        .toString(2),
                )
            }
            if (includeLegacyExecutionContract) {
                writeEntry(output, "android-execution.json", """{"contractVersion":1}""")
            }
            if (includeRuntimeBootstrap) {
                writeEntry(output, "runtime/index.js", "console.log('hello')")
            }
            extraEntries.forEach { (path, content) ->
                writeEntry(output, path, content)
            }
        }
        return packageFile
    }

    private fun writeEntry(output: ZipOutputStream, path: String, content: String) {
        output.putNextEntry(ZipEntry(path))
        output.write(content.toByteArray(Charsets.UTF_8))
        output.closeEntry()
    }
}

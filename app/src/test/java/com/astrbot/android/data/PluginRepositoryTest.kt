package com.astrbot.android.data

import com.astrbot.android.data.db.PluginInstallAggregate
import com.astrbot.android.data.db.PluginInstallAggregateDao
import com.astrbot.android.data.db.PluginInstallRecordEntity
import com.astrbot.android.data.db.PluginInstallWriteModel
import com.astrbot.android.data.db.PluginCatalogDao
import com.astrbot.android.data.db.PluginCatalogEntryEntity
import com.astrbot.android.data.db.PluginCatalogSourceEntity
import com.astrbot.android.data.db.PluginCatalogVersionEntity
import com.astrbot.android.data.db.PluginConfigSnapshotDao
import com.astrbot.android.data.db.PluginConfigSnapshotEntity
import com.astrbot.android.data.db.PluginManifestPermissionEntity
import com.astrbot.android.data.db.PluginManifestSnapshotEntity
import com.astrbot.android.data.db.PluginPackageContractSnapshotEntity
import com.astrbot.android.data.db.PluginPermissionSnapshotEntity
import com.astrbot.android.data.db.toInstallRecord
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.plugin.PluginCompatibilityStatus
import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginCatalogEntry
import com.astrbot.android.model.plugin.PluginCatalogEntryRecord
import com.astrbot.android.model.plugin.PluginCatalogSyncState
import com.astrbot.android.model.plugin.PluginCatalogSyncStatus
import com.astrbot.android.model.plugin.PluginCatalogVersion
import com.astrbot.android.model.plugin.PluginConfigEntryPointsSnapshot
import com.astrbot.android.model.plugin.PluginConfigStoreSnapshot
import com.astrbot.android.model.plugin.PluginStaticConfigField
import com.astrbot.android.model.plugin.PluginStaticConfigFieldType
import com.astrbot.android.model.plugin.PluginStaticConfigSchema
import com.astrbot.android.model.plugin.PluginStaticConfigValue
import com.astrbot.android.model.plugin.toStorageBoundary
import com.astrbot.android.model.plugin.PluginInstallRecord
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPackageContractSnapshot
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginRepositorySource
import com.astrbot.android.model.plugin.PluginRuntimeDeclarationSnapshot
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUpdateAvailability
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import com.astrbot.android.model.plugin.PluginPackageValidationIssue
import com.astrbot.android.runtime.plugin.PluginPackageValidationResult
import com.astrbot.android.model.plugin.PluginStaticConfigJson
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRepositoryTest {
    @Test
    fun repository_rejects_reads_before_initialize_instead_of_silent_placeholder_success() {
        resetPluginRepositoryForTest(initialized = false)

        val failure = runCatching {
            PluginRepository.findByPluginId("com.example.demo")
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("initialize") == true)
    }

    @Test
    fun repository_rejects_writes_before_initialize_instead_of_silent_placeholder_success() {
        resetPluginRepositoryForTest(initialized = false)

        val failure = runCatching {
            PluginRepository.upsert(sampleRecord(version = "1.0.0"))
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("initialize") == true)
    }

    @Test
    fun repository_reads_existing_record_from_store_when_memory_cache_is_empty() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.2.0", enabled = true, installedAt = 111L, lastUpdatedAt = 222L)
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true)

        val found = PluginRepository.findByPluginId(record.pluginId)

        assertEquals(record, found)
    }

    @Test
    fun repository_reprojects_legacy_v1_record_from_store_as_incompatible() {
        val dao = InMemoryPluginInstallAggregateDao()
        val legacyRecord = sampleRecord(
            version = "1.0.0",
            protocolVersion = 1,
            enabled = false,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            packageContractSnapshot = null,
        )
        dao.seed(legacyRecord)
        resetPluginRepositoryForTest(dao = dao, initialized = true)

        val projected = PluginRepository.findByPluginId(legacyRecord.pluginId)

        assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, projected?.compatibilityState?.status)
        assertEquals(false, projected?.compatibilityState?.protocolSupported)
        assertTrue(projected?.compatibilityState?.notes?.contains("Protocol version 1 is not supported.") == true)
        assertEquals(projected, PluginRepository.records.value.single())
    }

    @Test
    fun repository_upsert_persists_record_through_store_and_updates_cache() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "2.0.0", enabled = true, installedAt = 333L, lastUpdatedAt = 444L)
        resetPluginRepositoryForTest(dao = dao, initialized = true)

        PluginRepository.upsert(record)

        assertEquals(record, runBlocking { dao.getPluginInstallAggregate(record.pluginId) }?.toInstallRecord())
        assertEquals(record, PluginRepository.records.value.single())
    }

    @Test
    fun repository_resolveConfigSnapshot_applies_defaults_and_saved_values_with_boundary_filtering() {
        val dao = InMemoryPluginInstallAggregateDao()
        val configDao = InMemoryPluginConfigSnapshotDao()
        val record = sampleRecord(version = "1.0.0", enabled = true)
        dao.seed(record)
        configDao.seed(
            PluginConfigSnapshotEntity(
                pluginId = record.pluginId,
                coreConfigJson = """{"token":"sk-live"}""",
                extensionConfigJson = """{"session_mode":"threaded","rogue":"drop-me"}""",
                updatedAt = 120L,
            ),
        )
        resetPluginRepositoryForTest(dao = dao, configDao = configDao, initialized = true)
        val boundary = PluginStaticConfigSchema(
            fields = listOf(
                PluginStaticConfigField(
                    fieldKey = "token",
                    fieldType = PluginStaticConfigFieldType.StringField,
                    defaultValue = PluginStaticConfigValue.StringValue("sk-demo"),
                ),
                PluginStaticConfigField(
                    fieldKey = "temperature",
                    fieldType = PluginStaticConfigFieldType.FloatField,
                    defaultValue = PluginStaticConfigValue.FloatValue(0.7),
                ),
            ),
        ).toStorageBoundary(extensionFieldKeys = setOf("session_mode"))

        val snapshot = PluginRepository.resolveConfigSnapshot(record.pluginId, boundary)

        assertEquals(
            PluginConfigStoreSnapshot(
                coreValues = mapOf(
                    "token" to PluginStaticConfigValue.StringValue("sk-live"),
                    "temperature" to PluginStaticConfigValue.FloatValue(0.7),
                ),
                extensionValues = mapOf(
                    "session_mode" to PluginStaticConfigValue.StringValue("threaded"),
                ),
            ),
            snapshot,
        )
    }

    @Test
    fun repository_saveCoreConfig_persists_snapshot_and_keeps_existing_extension_values() {
        val dao = InMemoryPluginInstallAggregateDao()
        val configDao = InMemoryPluginConfigSnapshotDao()
        val record = sampleRecord(version = "1.0.0", enabled = true)
        dao.seed(record)
        configDao.seed(
            PluginConfigSnapshotEntity(
                pluginId = record.pluginId,
                coreConfigJson = """{"token":"sk-demo"}""",
                extensionConfigJson = """{"session_mode":"threaded"}""",
                updatedAt = 100L,
            ),
        )
        resetPluginRepositoryForTest(dao = dao, configDao = configDao, initialized = true, now = 500L)
        val boundary = PluginStaticConfigSchema(
            fields = listOf(
                PluginStaticConfigField(
                    fieldKey = "token",
                    fieldType = PluginStaticConfigFieldType.StringField,
                ),
            ),
        ).toStorageBoundary(extensionFieldKeys = setOf("session_mode"))

        val snapshot = PluginRepository.saveCoreConfig(
            pluginId = record.pluginId,
            boundary = boundary,
            coreValues = mapOf("token" to PluginStaticConfigValue.StringValue("sk-live")),
        )

        assertEquals(
            PluginConfigStoreSnapshot(
                coreValues = mapOf("token" to PluginStaticConfigValue.StringValue("sk-live")),
                extensionValues = mapOf("session_mode" to PluginStaticConfigValue.StringValue("threaded")),
            ),
            snapshot,
        )
        assertEquals(500L, runBlocking { configDao.get(record.pluginId) }?.updatedAt)
    }

    @Test
    fun repository_reads_static_config_schema_from_installed_plugin_directory() {
        val tempDir = Files.createTempDirectory("plugin-static-schema-source").toFile()
        try {
            val dao = InMemoryPluginInstallAggregateDao()
            val expectedSchema = PluginStaticConfigSchema(
                fields = listOf(
                    PluginStaticConfigField(
                        fieldKey = "api_key",
                        fieldType = PluginStaticConfigFieldType.StringField,
                        description = "API key",
                        defaultValue = PluginStaticConfigValue.StringValue("demo-key"),
                    ),
                    PluginStaticConfigField(
                        fieldKey = "enabled",
                        fieldType = PluginStaticConfigFieldType.BoolField,
                        defaultValue = PluginStaticConfigValue.BoolValue(true),
                    ),
                ),
            )
            writeStaticSchemaFile(
                directory = tempDir,
                schema = expectedSchema,
                relativePath = "schemas/static.schema.json",
            )
            writeStaticSchemaFile(
                directory = tempDir,
                schema = PluginStaticConfigSchema(
                    fields = listOf(
                        PluginStaticConfigField(
                            fieldKey = "legacy_only",
                            fieldType = PluginStaticConfigFieldType.StringField,
                        ),
                    ),
                ),
            )
            val record = sampleRecord(
                version = "1.0.0",
                enabled = true,
                extractedDir = tempDir.absolutePath,
                packageContractSnapshot = PluginPackageContractSnapshot(
                    protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
                    runtime = PluginRuntimeDeclarationSnapshot(
                        kind = "js_quickjs",
                        bootstrap = "runtime/index.js",
                        apiVersion = 1,
                    ),
                    config = PluginConfigEntryPointsSnapshot(
                        staticSchema = "schemas/static.schema.json",
                        settingsSchema = "",
                    ),
                ),
            )
            dao.seed(record)
            resetPluginRepositoryForTest(dao = dao, initialized = true)

            val schema = PluginRepository.getInstalledStaticConfigSchema(record.pluginId)

            assertEquals(expectedSchema, schema)
        } finally {
            resetPluginRepositoryForTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun repository_returns_null_when_static_config_schema_file_is_missing() {
        val tempDir = Files.createTempDirectory("plugin-static-schema-missing").toFile()
        try {
            val dao = InMemoryPluginInstallAggregateDao()
            writeStaticSchemaFile(
                directory = tempDir,
                schema = PluginStaticConfigSchema(
                    fields = listOf(
                        PluginStaticConfigField(
                            fieldKey = "legacy_only",
                            fieldType = PluginStaticConfigFieldType.StringField,
                        ),
                    ),
                ),
            )
            val record = sampleRecord(
                version = "1.0.0",
                enabled = true,
                extractedDir = tempDir.absolutePath,
                packageContractSnapshot = PluginPackageContractSnapshot(
                    protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
                    runtime = PluginRuntimeDeclarationSnapshot(
                        kind = "js_quickjs",
                        bootstrap = "runtime/index.js",
                        apiVersion = 1,
                    ),
                    config = PluginConfigEntryPointsSnapshot(),
                ),
            )
            dao.seed(record)
            resetPluginRepositoryForTest(dao = dao, initialized = true)

            val schema = PluginRepository.getInstalledStaticConfigSchema(record.pluginId)

            assertEquals(null, schema)
            assertEquals(null, PluginRepository.resolveInstalledStaticConfigSchemaPath(record.pluginId))
        } finally {
            resetPluginRepositoryForTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun repository_returns_null_when_static_config_schema_file_is_invalid() {
        val tempDir = Files.createTempDirectory("plugin-static-schema-invalid").toFile()
        try {
            val dao = InMemoryPluginInstallAggregateDao()
            File(tempDir, "_conf_schema.json").writeText(
                """
                {
                  "api_key": {
                    "type": "unsupported"
                  }
                }
                """.trimIndent(),
            )
            val record = sampleRecord(
                version = "1.0.0",
                enabled = true,
                extractedDir = tempDir.absolutePath,
                packageContractSnapshot = PluginPackageContractSnapshot(
                    protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
                    runtime = PluginRuntimeDeclarationSnapshot(
                        kind = "js_quickjs",
                        bootstrap = "runtime/index.js",
                        apiVersion = 1,
                    ),
                    config = PluginConfigEntryPointsSnapshot(
                        staticSchema = "_conf_schema.json",
                        settingsSchema = "",
                    ),
                ),
            )
            dao.seed(record)
            resetPluginRepositoryForTest(dao = dao, initialized = true)

            val schema = PluginRepository.getInstalledStaticConfigSchema(record.pluginId)

            assertEquals(null, schema)
        } finally {
            resetPluginRepositoryForTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun repository_resolves_settings_schema_path_from_snapshot_under_extracted_dir() {
        val tempDir = Files.createTempDirectory("plugin-settings-schema-path").toFile()
        try {
            val dao = InMemoryPluginInstallAggregateDao()
            val settingsFile = File(tempDir, "schemas/settings-ui.schema.json").apply {
                parentFile?.mkdirs()
                writeText("""{"title":"Plugin Settings"}""")
            }
            val record = sampleRecord(
                version = "1.0.0",
                enabled = true,
                extractedDir = tempDir.absolutePath,
                packageContractSnapshot = PluginPackageContractSnapshot(
                    protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
                    runtime = PluginRuntimeDeclarationSnapshot(
                        kind = "js_quickjs",
                        bootstrap = "runtime/index.js",
                        apiVersion = 1,
                    ),
                    config = PluginConfigEntryPointsSnapshot(
                        staticSchema = "",
                        settingsSchema = "schemas/settings-ui.schema.json",
                    ),
                ),
            )
            dao.seed(record)
            resetPluginRepositoryForTest(dao = dao, initialized = true)

            assertEquals(
                settingsFile.absolutePath,
                PluginRepository.resolveInstalledSettingsSchemaPath(record.pluginId),
            )
        } finally {
            resetPluginRepositoryForTest(initialized = false)
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun repository_rejects_snapshot_schema_path_that_escapes_extracted_dir_without_fallback() {
        val tempDir = Files.createTempDirectory("plugin-static-schema-escape").toFile()
        val outsideFile = Files.createTempFile("plugin-static-schema-outside", ".json").toFile()
        try {
            val dao = InMemoryPluginInstallAggregateDao()
            outsideFile.writeText(
                PluginStaticConfigJson.encodeSchema(
                    PluginStaticConfigSchema(
                        fields = listOf(
                            PluginStaticConfigField(
                                fieldKey = "outside",
                                fieldType = PluginStaticConfigFieldType.StringField,
                            ),
                        ),
                    ),
                ).toString(2),
            )
            writeStaticSchemaFile(
                directory = tempDir,
                schema = PluginStaticConfigSchema(
                    fields = listOf(
                        PluginStaticConfigField(
                            fieldKey = "legacy_only",
                            fieldType = PluginStaticConfigFieldType.StringField,
                        ),
                    ),
                ),
            )
            val record = sampleRecord(
                version = "1.0.0",
                enabled = true,
                extractedDir = tempDir.absolutePath,
                packageContractSnapshot = PluginPackageContractSnapshot(
                    protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
                    runtime = PluginRuntimeDeclarationSnapshot(
                        kind = "js_quickjs",
                        bootstrap = "runtime/index.js",
                        apiVersion = 1,
                    ),
                    config = PluginConfigEntryPointsSnapshot(
                        staticSchema = "../${outsideFile.name}",
                        settingsSchema = "",
                    ),
                ),
            )
            dao.seed(record)
            resetPluginRepositoryForTest(dao = dao, initialized = true)

            assertEquals(null, PluginRepository.resolveInstalledStaticConfigSchemaPath(record.pluginId))
            assertEquals(null, PluginRepository.getInstalledStaticConfigSchema(record.pluginId))
        } finally {
            resetPluginRepositoryForTest(initialized = false)
            tempDir.deleteRecursively()
            outsideFile.delete()
        }
    }

    @Test
    fun repository_enables_compatible_plugin_and_updates_store() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.0.0", enabled = false, lastUpdatedAt = 10L)
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 500L)

        val updated = PluginRepository.setEnabled(record.pluginId, true)

        assertTrue(updated.enabled)
        assertEquals(500L, updated.lastUpdatedAt)
        assertTrue(runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord().enabled)
    }

    @Test
    fun repository_blocks_enabling_incompatible_plugin() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(
            version = "1.0.0",
            enabled = false,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = false,
                maxHostVersionSatisfied = true,
                notes = "Requires a newer host build.",
            ),
        )
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 700L)

        val failure = runCatching {
            PluginRepository.setEnabled(record.pluginId, true)
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("compatibility", ignoreCase = true) == true)
        assertEquals(record, runBlocking { dao.getPluginInstallAggregate(record.pluginId) }?.toInstallRecord())
    }

    @Test
    fun repository_blocks_enabling_legacy_v1_record_even_if_persisted_as_compatible() {
        val dao = InMemoryPluginInstallAggregateDao()
        val legacyRecord = sampleRecord(
            version = "1.0.0",
            protocolVersion = 1,
            enabled = false,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            packageContractSnapshot = null,
        )
        dao.seed(legacyRecord)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 750L)

        val failure = runCatching {
            PluginRepository.setEnabled(legacyRecord.pluginId, true)
        }.exceptionOrNull()
        val projected = PluginRepository.findByPluginId(legacyRecord.pluginId)

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message?.contains("Protocol version 1 is not supported.") == true)
        assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, projected?.compatibilityState?.status)
        assertEquals(legacyRecord, runBlocking { dao.getPluginInstallAggregate(legacyRecord.pluginId) }?.toInstallRecord())
    }

    @Test
    fun repository_maps_legacy_validation_issue_to_legacy_v1_import_message() {
        val validation = PluginPackageValidationResult(
            manifest = sampleRecord(version = "1.0.0").manifestSnapshot,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = false,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
                notes = "Protocol version 1 is not supported.",
            ),
            installable = false,
            validationIssues = listOf(
                PluginPackageValidationIssue(
                    code = "legacy_contract",
                    message = "android-execution.json is legacy and is no longer the installation truth source.",
                ),
            ),
        )

        val failure = PluginRepository.buildLocalPackageInstallBlockedException(validation)

        assertTrue(failure.message?.contains("Legacy v1", ignoreCase = true) == true)
    }

    @Test
    fun repository_maps_structure_validation_issue_to_damaged_v2_import_message() {
        val validation = PluginPackageValidationResult(
            manifest = sampleRecord(version = "1.0.0").manifestSnapshot,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
            installable = false,
            validationIssues = listOf(
                PluginPackageValidationIssue(
                    code = "missing_runtime_bootstrap",
                    message = "Missing runtime bootstrap file: runtime/index.js",
                ),
            ),
        )

        val failure = PluginRepository.buildLocalPackageInstallBlockedException(validation)

        assertTrue(failure.message?.contains("Damaged v2", ignoreCase = true) == true)
        assertTrue(failure.message?.contains("runtime/index.js") == true)
    }

    @Test
    fun repository_updates_failure_state_and_persists_it() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.0.0", lastUpdatedAt = 10L)
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 900L)

        val updated = PluginRepository.updateFailureState(
            pluginId = record.pluginId,
            failureState = PluginFailureState(
                consecutiveFailureCount = 2,
                lastFailureAtEpochMillis = 800L,
                lastErrorSummary = "socket timeout",
                suspendedUntilEpochMillis = 1_300L,
            ),
        )

        assertEquals(2, updated.failureState.consecutiveFailureCount)
        assertEquals(800L, updated.failureState.lastFailureAtEpochMillis)
        assertEquals("socket timeout", updated.failureState.lastErrorSummary)
        assertEquals(1_300L, updated.failureState.suspendedUntilEpochMillis)
        assertEquals(900L, updated.lastUpdatedAt)
        assertEquals(
            updated.failureState,
            runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord().failureState,
        )
    }

    @Test
    fun repository_clears_failure_state_and_persists_reset_snapshot() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(
            version = "1.0.0",
            lastUpdatedAt = 10L,
            failureState = PluginFailureState(
                consecutiveFailureCount = 3,
                lastFailureAtEpochMillis = 700L,
                lastErrorSummary = "plugin crashed",
                suspendedUntilEpochMillis = 1_100L,
            ),
        )
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 950L)

        val updated = PluginRepository.clearFailureState(record.pluginId)

        assertEquals(PluginFailureState.none(), updated.failureState)
        assertEquals(950L, updated.lastUpdatedAt)
        assertEquals(
            PluginFailureState.none(),
            runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord().failureState,
        )
    }

    @Test
    fun repository_uninstall_keep_data_skips_data_cleanup_and_removes_record() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(version = "1.0.0", uninstallPolicy = PluginUninstallPolicy.KEEP_DATA)
        dao.seed(record)
        val remover = FakePluginDataRemover()
        resetPluginRepositoryForTest(dao = dao, initialized = true, dataRemover = remover)

        val result = PluginRepository.uninstall(record.pluginId, PluginUninstallPolicy.KEEP_DATA)

        assertEquals(record.pluginId, result.pluginId)
        assertEquals(PluginUninstallPolicy.KEEP_DATA, result.policy)
        assertEquals(false, result.removedData)
        assertTrue(remover.removedRecords.isEmpty())
        assertEquals(null, runBlocking { dao.getPluginInstallAggregate(record.pluginId) })
    }

    @Test
    fun repository_uninstall_remove_data_triggers_cleanup_and_removes_record() {
        val dao = InMemoryPluginInstallAggregateDao()
        val configDao = InMemoryPluginConfigSnapshotDao()
        val packageFile = Files.createTempFile("plugin-package-", ".zip").toFile().apply {
            writeText("package")
        }
        val extractedDir = Files.createTempDirectory("plugin-extracted-").toFile().apply {
            File(this, "manifest.json").writeText("{}")
        }
        val record = sampleRecord(
            version = "1.0.0",
            uninstallPolicy = PluginUninstallPolicy.KEEP_DATA,
            localPackagePath = packageFile.absolutePath,
            extractedDir = extractedDir.absolutePath,
        )
        dao.seed(record)
        configDao.seed(
            PluginConfigSnapshotEntity(
                pluginId = record.pluginId,
                coreConfigJson = """{"enabled":true}""",
                extensionConfigJson = """{"token":"demo"}""",
                updatedAt = 123L,
            ),
        )
        val remover = FakePluginDataRemover()
        resetPluginRepositoryForTest(
            dao = dao,
            configDao = configDao,
            initialized = true,
            dataRemover = remover,
        )

        val result = PluginRepository.uninstall(record.pluginId, PluginUninstallPolicy.REMOVE_DATA)

        assertEquals(record.pluginId, result.pluginId)
        assertEquals(PluginUninstallPolicy.REMOVE_DATA, result.policy)
        assertEquals(true, result.removedData)
        assertEquals(listOf(record.pluginId), remover.removedRecords.map { it.pluginId })
        assertEquals(false, packageFile.exists())
        assertEquals(false, extractedDir.exists())
        assertEquals(null, runBlocking { configDao.get(record.pluginId) })
        assertEquals(null, runBlocking { dao.getPluginInstallAggregate(record.pluginId) })
    }

    @Test
    fun repository_round_trips_catalog_tracking_fields_on_install_records() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(
            version = "2.1.0",
            catalogSourceId = "official",
            installedPackageUrl = "https://repo.example.com/packages/demo-2.1.0.zip",
            lastCatalogCheckAtEpochMillis = 1_234L,
        )
        resetPluginRepositoryForTest(dao = dao, initialized = true)

        PluginRepository.upsert(record)

        val stored = runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord()
        assertEquals("official", stored.catalogSourceId)
        assertEquals("https://repo.example.com/packages/demo-2.1.0.zip", stored.installedPackageUrl)
        assertEquals(1_234L, stored.lastCatalogCheckAtEpochMillis)
    }

    @Test
    fun repository_round_trips_package_contract_snapshot_on_install_records() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(
            version = "2.2.0",
            packageContractSnapshot = PluginPackageContractSnapshot(
                protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
                runtime = PluginRuntimeDeclarationSnapshot(
                    kind = "js_quickjs",
                    bootstrap = "runtime/index.js",
                    apiVersion = 1,
                ),
                config = PluginConfigEntryPointsSnapshot(
                    staticSchema = "schemas/static.schema.json",
                    settingsSchema = "schemas/settings.schema.json",
                ),
            ),
        )
        resetPluginRepositoryForTest(dao = dao, initialized = true)

        PluginRepository.upsert(record)

        val stored = runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord()
        assertEquals(record.packageContractSnapshot, stored.packageContractSnapshot)
        assertEquals(
            "runtime/index.js",
            PluginRepository.findByPluginId(record.pluginId)!!.packageContractSnapshot!!.runtime.bootstrap,
        )
    }

    @Test
    fun repository_preserves_package_contract_snapshot_when_rewriting_enabled_state() {
        val dao = InMemoryPluginInstallAggregateDao()
        val snapshot = PluginPackageContractSnapshot(
            protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
            runtime = PluginRuntimeDeclarationSnapshot(
                kind = "js_quickjs",
                bootstrap = "runtime/index.js",
                apiVersion = 1,
            ),
            config = PluginConfigEntryPointsSnapshot(
                staticSchema = "schemas/static.schema.json",
                settingsSchema = "schemas/settings.schema.json",
            ),
        )
        val record = sampleRecord(
            version = "2.3.0",
            enabled = false,
            lastUpdatedAt = 10L,
            packageContractSnapshot = snapshot,
        )
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 510L)

        val updated = PluginRepository.setEnabled(record.pluginId, true)
        val stored = runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord()

        assertEquals(snapshot, updated.packageContractSnapshot)
        assertEquals(snapshot, stored.packageContractSnapshot)
        assertEquals(snapshot, PluginRepository.findByPluginId(record.pluginId)!!.packageContractSnapshot)
    }

    @Test
    fun repository_preserves_legacy_null_package_contract_snapshot_across_rewrite() {
        val dao = InMemoryPluginInstallAggregateDao()
        val record = sampleRecord(
            version = "1.9.0",
            enabled = false,
            lastUpdatedAt = 15L,
            packageContractSnapshot = null,
        )
        dao.seed(record)
        resetPluginRepositoryForTest(dao = dao, initialized = true, now = 515L)

        val beforeRewrite = PluginRepository.findByPluginId(record.pluginId)!!
        val updated = PluginRepository.setEnabled(record.pluginId, true)
        val stored = runBlocking { dao.getPluginInstallAggregate(record.pluginId) }!!.toInstallRecord()

        assertEquals(null, beforeRewrite.packageContractSnapshot)
        assertEquals(null, updated.packageContractSnapshot)
        assertEquals(null, stored.packageContractSnapshot)
        assertEquals(record.manifestSnapshot, stored.manifestSnapshot)
        assertEquals(record.permissionSnapshot, stored.permissionSnapshot)
        assertEquals(record.source, stored.source)
        assertEquals(true, updated.enabled)
        assertEquals(515L, updated.lastUpdatedAt)
    }

    @Test
    fun repository_replaces_catalog_source_and_exposes_nested_entries_and_versions() {
        val catalogDao = InMemoryPluginCatalogDao()
        resetPluginRepositoryForTest(
            dao = InMemoryPluginInstallAggregateDao(),
            catalogDao = catalogDao,
            initialized = true,
        )
        val source = sampleRepositorySource()

        PluginRepository.replaceRepositoryCatalog(source)

        assertEquals(source, PluginRepository.getRepositorySource(source.sourceId))
        assertEquals(source.plugins, PluginRepository.listCatalogEntries(source.sourceId))
        assertEquals(source.plugins.single().versions, PluginRepository.listCatalogVersions(source.sourceId, "com.example.weather"))
    }

    @Test
    fun repository_updates_catalog_source_metadata_without_dropping_entries() {
        val catalogDao = InMemoryPluginCatalogDao()
        resetPluginRepositoryForTest(
            dao = InMemoryPluginInstallAggregateDao(),
            catalogDao = catalogDao,
            initialized = true,
        )
        val original = sampleRepositorySource()
        PluginRepository.replaceRepositoryCatalog(original)

        PluginRepository.upsertRepositorySource(
            original.copy(
                title = "Official Repository Mirror",
                updatedAt = 2_000L,
                lastSyncAtEpochMillis = 2_500L,
                lastSyncStatus = PluginCatalogSyncStatus.SUCCESS,
                lastSyncErrorSummary = "",
                plugins = emptyList(),
            ),
        )

        assertEquals("Official Repository Mirror", PluginRepository.getRepositorySource(original.sourceId)?.title)
        assertEquals(
            PluginCatalogSyncState(
                sourceId = original.sourceId,
                lastSyncAtEpochMillis = 2_500L,
                lastSyncStatus = PluginCatalogSyncStatus.SUCCESS,
                lastSyncErrorSummary = "",
            ),
            PluginRepository.getRepositorySourceSyncState(original.sourceId),
        )
        assertEquals(original.plugins.single(), PluginRepository.getCatalogEntry(original.sourceId, "com.example.weather"))
    }

    @Test
    fun repository_lists_aggregated_catalog_entries_with_source_context() {
        val catalogDao = InMemoryPluginCatalogDao()
        resetPluginRepositoryForTest(
            dao = InMemoryPluginInstallAggregateDao(),
            catalogDao = catalogDao,
            initialized = true,
        )
        val official = sampleRepositorySource()
        val community = sampleRepositorySource(
            sourceId = "community",
            title = "Community Repository",
            catalogUrl = "https://community.example.com/catalog.json",
            pluginId = "com.example.translate",
            pluginTitle = "Translate",
            packageUrl = "./packages/translate-2.0.0.zip",
        )
        PluginRepository.replaceRepositoryCatalog(official)
        PluginRepository.replaceRepositoryCatalog(community)

        assertEquals(
            listOf(
                PluginCatalogEntryRecord(
                    sourceId = "community",
                    sourceTitle = "Community Repository",
                    catalogUrl = "https://community.example.com/catalog.json",
                    entry = community.plugins.single(),
                ),
                PluginCatalogEntryRecord(
                    sourceId = "official",
                    sourceTitle = "Official Repository",
                    catalogUrl = "https://repo.example.com/catalogs/stable/index.json",
                    entry = official.plugins.single(),
                ),
            ),
            PluginRepository.listAllCatalogEntries(),
        )
    }

    @Test
    fun repository_computes_update_availability_for_same_plugin_and_source_only() {
        val catalogDao = InMemoryPluginCatalogDao()
        val dao = InMemoryPluginInstallAggregateDao()
        resetPluginRepositoryForTest(
            dao = dao,
            catalogDao = catalogDao,
            initialized = true,
        )
        PluginRepository.upsert(
            sampleRecord(
                version = "1.0.0",
                sourceType = PluginSourceType.REPOSITORY,
                catalogSourceId = "official",
                installedPackageUrl = "https://repo.example.com/packages/demo-1.0.0.zip",
            ),
        )
        PluginRepository.replaceRepositoryCatalog(
            sampleRepositorySource(
                pluginId = "com.example.demo",
                versions = listOf(
                    catalogVersion(
                        version = "1.0.0",
                        packageUrl = "https://repo.example.com/packages/demo-1.0.0.zip",
                        publishedAt = 1_000L,
                        changelog = "Current release",
                    ),
                    catalogVersion(
                        version = "1.2.0",
                        packageUrl = "https://repo.example.com/packages/demo-1.2.0.zip",
                        publishedAt = 2_000L,
                        changelog = "Adds weather push notifications.\nFixes edge cache handling.",
                        permissions = listOf(
                            PluginPermissionDeclaration(
                                permissionId = "net.access",
                                title = "Network access",
                                description = "Allows outgoing requests",
                                riskLevel = PluginRiskLevel.MEDIUM,
                                required = true,
                            ),
                            PluginPermissionDeclaration(
                                permissionId = "fs.logs",
                                title = "Read logs",
                                description = "Reads local logs",
                                riskLevel = PluginRiskLevel.MEDIUM,
                                required = false,
                            ),
                        ),
                    ),
                ),
            ),
        )
        PluginRepository.replaceRepositoryCatalog(
            sampleRepositorySource(
                sourceId = "community",
                title = "Community Repository",
                catalogUrl = "https://community.example.com/catalog.json",
                pluginId = "com.example.demo",
                versions = listOf(
                    catalogVersion(
                        version = "9.9.9",
                        packageUrl = "https://community.example.com/demo-9.9.9.zip",
                        publishedAt = 3_000L,
                    ),
                ),
            ),
        )

        val availability = PluginRepository.getUpdateAvailability(
            pluginId = "com.example.demo",
            hostVersion = "0.3.6",
        )

        assertEquals(
            PluginUpdateAvailability(
                pluginId = "com.example.demo",
                installedVersion = "1.0.0",
                latestVersion = "1.2.0",
                updateAvailable = true,
                canUpgrade = true,
                publishedAt = 2_000L,
                changelogSummary = "Adds weather push notifications.",
                permissionDiff = availability!!.permissionDiff,
                sourceBadge = availability.sourceBadge,
                compatibilityState = availability.compatibilityState,
                incompatibilityReason = "",
                catalogSourceId = "official",
                packageUrl = "https://repo.example.com/packages/demo-1.2.0.zip",
            ),
            availability,
        )
        assertEquals(listOf("fs.logs"), availability.permissionDiff.added.map { it.permissionId })
        assertTrue(availability.permissionDiff.removed.isEmpty())
        assertTrue(availability.permissionDiff.riskUpgraded.isEmpty())
        assertTrue(availability.permissionDiff.requiresSecondaryConfirmation)
    }

    @Test
    fun repository_direct_link_without_source_id_does_not_produce_update_availability() {
        val catalogDao = InMemoryPluginCatalogDao()
        val dao = InMemoryPluginInstallAggregateDao()
        resetPluginRepositoryForTest(
            dao = dao,
            catalogDao = catalogDao,
            initialized = true,
        )
        PluginRepository.upsert(
            sampleRecord(
                version = "1.0.0",
                sourceType = PluginSourceType.DIRECT_LINK,
                catalogSourceId = null,
                installedPackageUrl = "https://plugins.example.com/demo-1.0.0.zip",
            ),
        )
        PluginRepository.replaceRepositoryCatalog(
            sampleRepositorySource(
                pluginId = "com.example.demo",
                versions = listOf(catalogVersion(version = "1.1.0")),
            ),
        )

        val availability = PluginRepository.getUpdateAvailability(
            pluginId = "com.example.demo",
            hostVersion = "0.3.6",
        )

        assertEquals(null, availability)
    }

    @Test
    fun repository_treats_bound_direct_link_plugin_as_updateable_after_repository_alignment() {
        val catalogDao = InMemoryPluginCatalogDao()
        val dao = InMemoryPluginInstallAggregateDao()
        resetPluginRepositoryForTest(
            dao = dao,
            catalogDao = catalogDao,
            initialized = true,
        )
        PluginRepository.upsert(
            sampleRecord(
                version = "1.0.0",
                sourceType = PluginSourceType.DIRECT_LINK,
                catalogSourceId = "official",
                installedPackageUrl = "https://plugins.example.com/demo-1.0.0.zip",
            ),
        )
        PluginRepository.replaceRepositoryCatalog(
            sampleRepositorySource(
                pluginId = "com.example.demo",
                versions = listOf(
                    catalogVersion(version = "1.0.0"),
                    catalogVersion(
                        version = "1.1.0",
                        packageUrl = "https://repo.example.com/packages/demo-1.1.0.zip",
                        publishedAt = 2_000L,
                        changelog = "Repository-aligned update",
                    ),
                ),
            ),
        )

        val availability = PluginRepository.getUpdateAvailability(
            pluginId = "com.example.demo",
            hostVersion = "0.3.6",
        )

        assertEquals("1.1.0", availability?.latestVersion)
        assertEquals("official", availability?.catalogSourceId)
        assertEquals("https://repo.example.com/packages/demo-1.1.0.zip", availability?.packageUrl)
    }

    @Test
    fun repository_marks_incompatible_target_version_as_blocked_upgrade() {
        val catalogDao = InMemoryPluginCatalogDao()
        val dao = InMemoryPluginInstallAggregateDao()
        resetPluginRepositoryForTest(
            dao = dao,
            catalogDao = catalogDao,
            initialized = true,
        )
        PluginRepository.upsert(
            sampleRecord(
                version = "1.0.0",
                sourceType = PluginSourceType.REPOSITORY,
                catalogSourceId = "official",
                installedPackageUrl = "https://repo.example.com/packages/demo-1.0.0.zip",
            ),
        )
        PluginRepository.replaceRepositoryCatalog(
            sampleRepositorySource(
                pluginId = "com.example.demo",
                versions = listOf(
                    catalogVersion(version = "1.0.0"),
                    catalogVersion(
                        version = "1.5.0",
                        minHostVersion = "9.0.0",
                        changelog = "Requires newer host",
                    ),
                ),
            ),
        )

        val availability = PluginRepository.getUpdateAvailability(
            pluginId = "com.example.demo",
            hostVersion = "0.3.6",
        )

        assertEquals(true, availability?.updateAvailable)
        assertEquals(false, availability?.canUpgrade)
        assertTrue(availability?.incompatibilityReason?.contains("Host version 0.3.6 is below required minimum 9.0.0.") == true)
    }

    @Test
    fun repository_prefers_highest_compatible_update_when_newest_version_is_incompatible() {
        val catalogDao = InMemoryPluginCatalogDao()
        val dao = InMemoryPluginInstallAggregateDao()
        resetPluginRepositoryForTest(
            dao = dao,
            catalogDao = catalogDao,
            initialized = true,
        )
        PluginRepository.upsert(
            sampleRecord(
                version = "1.0.0",
                sourceType = PluginSourceType.REPOSITORY,
                catalogSourceId = "official",
                installedPackageUrl = "https://repo.example.com/packages/demo-1.0.0.zip",
            ),
        )
        PluginRepository.replaceRepositoryCatalog(
            sampleRepositorySource(
                pluginId = "com.example.demo",
                versions = listOf(
                    catalogVersion(version = "1.0.0"),
                    catalogVersion(
                        version = "1.5.0",
                        packageUrl = "https://repo.example.com/packages/demo-1.5.0.zip",
                        minHostVersion = "0.3.0",
                        changelog = "Latest compatible build",
                    ),
                    catalogVersion(
                        version = "2.0.0",
                        packageUrl = "https://repo.example.com/packages/demo-2.0.0.zip",
                        minHostVersion = "9.0.0",
                        changelog = "Requires newer host",
                    ),
                ),
            ),
        )

        val availability = PluginRepository.getUpdateAvailability(
            pluginId = "com.example.demo",
            hostVersion = "0.3.6",
        )

        assertEquals(true, availability?.updateAvailable)
        assertEquals(true, availability?.canUpgrade)
        assertEquals("1.5.0", availability?.latestVersion)
        assertEquals("https://repo.example.com/packages/demo-1.5.0.zip", availability?.packageUrl)
        assertEquals("Latest compatible build", availability?.changelogSummary)
        assertEquals("", availability?.incompatibilityReason)
    }

    @Test
    fun repository_permission_diff_detects_risk_upgrades_and_secondary_confirmation() {
        val catalogDao = InMemoryPluginCatalogDao()
        val dao = InMemoryPluginInstallAggregateDao()
        resetPluginRepositoryForTest(
            dao = dao,
            catalogDao = catalogDao,
            initialized = true,
        )
        PluginRepository.upsert(
            sampleRecord(
                version = "1.0.0",
                sourceType = PluginSourceType.REPOSITORY,
                catalogSourceId = "official",
                permissions = listOf(
                    PluginPermissionDeclaration(
                        permissionId = "net.access",
                        title = "Network access",
                        description = "Allows outgoing requests",
                        riskLevel = PluginRiskLevel.LOW,
                        required = true,
                    ),
                ),
            ),
        )
        PluginRepository.replaceRepositoryCatalog(
            sampleRepositorySource(
                pluginId = "com.example.demo",
                versions = listOf(
                    catalogVersion(version = "1.0.0"),
                    catalogVersion(
                        version = "1.1.0",
                        permissions = listOf(
                            PluginPermissionDeclaration(
                                permissionId = "net.access",
                                title = "Network access",
                                description = "Allows outgoing requests",
                                riskLevel = PluginRiskLevel.HIGH,
                                required = true,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val availability = PluginRepository.getUpdateAvailability(
            pluginId = "com.example.demo",
            hostVersion = "0.3.6",
        )

        assertEquals(1, availability?.permissionDiff?.riskUpgraded?.size)
        assertEquals("net.access", availability?.permissionDiff?.riskUpgraded?.single()?.to?.permissionId)
        assertTrue(availability?.permissionDiff?.requiresSecondaryConfirmation == true)
    }
}

private class InMemoryPluginCatalogDao : PluginCatalogDao() {
    private val sources = linkedMapOf<String, PluginCatalogSourceEntity>()
    private val entries = linkedMapOf<String, MutableList<PluginCatalogEntryEntity>>()
    private val versions = linkedMapOf<String, MutableList<PluginCatalogVersionEntity>>()

    override suspend fun listSources(): List<PluginCatalogSourceEntity> = sources.values.sortedBy { it.sourceId }

    override suspend fun getSource(sourceId: String): PluginCatalogSourceEntity? = sources[sourceId]

    override suspend fun listEntries(sourceId: String): List<PluginCatalogEntryEntity> {
        return entries[sourceId].orEmpty().sortedBy { it.sortIndex }
    }

    override suspend fun getEntry(sourceId: String, pluginId: String): PluginCatalogEntryEntity? {
        return entries[sourceId].orEmpty().firstOrNull { it.pluginId == pluginId }
    }

    override suspend fun listVersions(sourceId: String, pluginId: String): List<PluginCatalogVersionEntity> {
        return versions["$sourceId::$pluginId"].orEmpty().sortedBy { it.sortIndex }
    }

    override suspend fun upsertSources(entities: List<PluginCatalogSourceEntity>) {
        entities.forEach { entity -> sources[entity.sourceId] = entity }
    }

    override suspend fun upsertEntries(entities: List<PluginCatalogEntryEntity>) {
        entities.groupBy { it.sourceId }.forEach { (sourceId, grouped) ->
            entries[sourceId] = grouped.sortedBy { it.sortIndex }.toMutableList()
        }
    }

    override suspend fun upsertVersions(entities: List<PluginCatalogVersionEntity>) {
        entities.groupBy { "${it.sourceId}::${it.pluginId}" }.forEach { (key, grouped) ->
            versions[key] = grouped.sortedBy { it.sortIndex }.toMutableList()
        }
    }

    override suspend fun deleteEntriesBySourceId(sourceId: String) {
        val pluginIds = entries[sourceId].orEmpty().map { it.pluginId }
        entries.remove(sourceId)
        pluginIds.forEach { pluginId -> versions.remove("$sourceId::$pluginId") }
    }

    override suspend fun deleteVersionsBySourceId(sourceId: String) {
        versions.keys.filter { it.startsWith("$sourceId::") }.toList().forEach(versions::remove)
    }
}

private class InMemoryPluginInstallAggregateDao : PluginInstallAggregateDao() {
    private val aggregates = linkedMapOf<String, PluginInstallAggregate>()
    private val state = MutableStateFlow<List<PluginInstallAggregate>>(emptyList())

    fun seed(record: PluginInstallRecord) {
        val writeModel = record.toWriteModel()
        aggregates[record.pluginId] = PluginInstallAggregate(
            record = writeModel.record,
            manifestSnapshots = listOf(writeModel.manifestSnapshot),
            packageContractSnapshots = listOfNotNull(writeModel.packageContractSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override fun observePluginInstallAggregates(): Flow<List<PluginInstallAggregate>> = state

    override suspend fun listPluginInstallAggregates(): List<PluginInstallAggregate> = state.value

    override fun observePluginInstallAggregate(pluginId: String): Flow<PluginInstallAggregate?> {
        return state.map { aggregates -> aggregates.firstOrNull { aggregate -> aggregate.record.pluginId == pluginId } }
    }

    override suspend fun getPluginInstallAggregate(pluginId: String): PluginInstallAggregate? = aggregates[pluginId]

    override suspend fun upsertRecord(writeModel: PluginInstallWriteModel) {
        aggregates[writeModel.record.pluginId] = PluginInstallAggregate(
            record = writeModel.record,
            manifestSnapshots = listOf(writeModel.manifestSnapshot),
            packageContractSnapshots = listOfNotNull(writeModel.packageContractSnapshot),
            manifestPermissions = writeModel.manifestPermissions,
            permissionSnapshots = writeModel.permissionSnapshots,
        )
        publish()
    }

    override suspend fun upsertRecords(entities: List<PluginInstallRecordEntity>) = Unit

    override suspend fun upsertManifestSnapshots(entities: List<PluginManifestSnapshotEntity>) = Unit

    override suspend fun upsertPackageContractSnapshots(entities: List<PluginPackageContractSnapshotEntity>) = Unit

    override suspend fun upsertManifestPermissions(entities: List<PluginManifestPermissionEntity>) = Unit

    override suspend fun upsertPermissionSnapshots(entities: List<PluginPermissionSnapshotEntity>) = Unit

    override suspend fun deleteManifestPermissions(pluginId: String) = Unit

    override suspend fun deletePackageContractSnapshots(pluginId: String) = Unit

    override suspend fun deletePermissionSnapshots(pluginId: String) = Unit

    override suspend fun delete(pluginId: String) {
        aggregates.remove(pluginId)
        publish()
    }

    override suspend fun count(): Int = aggregates.size

    private fun publish() {
        state.value = aggregates.values.sortedWith(
            compareByDescending<PluginInstallAggregate> { aggregate -> aggregate.record.lastUpdatedAt }
                .thenBy { aggregate -> aggregate.record.pluginId },
        )
    }
}

private class InMemoryPluginConfigSnapshotDao : PluginConfigSnapshotDao {
    private val entities = linkedMapOf<String, PluginConfigSnapshotEntity>()

    fun seed(entity: PluginConfigSnapshotEntity) {
        entities[entity.pluginId] = entity
    }

    override suspend fun get(pluginId: String): PluginConfigSnapshotEntity? = entities[pluginId]

    override suspend fun upsert(entity: PluginConfigSnapshotEntity) {
        entities[entity.pluginId] = entity
    }

    override suspend fun delete(pluginId: String) {
        entities.remove(pluginId)
    }
}

private fun sampleRecord(
    version: String,
    protocolVersion: Int = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
    sourceType: PluginSourceType = PluginSourceType.LOCAL_FILE,
    enabled: Boolean = false,
    installedAt: Long = 0L,
    lastUpdatedAt: Long = 0L,
    extractedDir: String = "/tmp/$version",
    compatibilityState: PluginCompatibilityState = PluginCompatibilityState.evaluated(
        protocolSupported = true,
        minHostVersionSatisfied = true,
        maxHostVersionSatisfied = true,
    ),
    uninstallPolicy: PluginUninstallPolicy = PluginUninstallPolicy.default(),
    failureState: PluginFailureState = PluginFailureState.none(),
    permissions: List<PluginPermissionDeclaration> = listOf(
        PluginPermissionDeclaration(
            permissionId = "net.access",
            title = "Network access",
            description = "Allows outgoing requests",
            riskLevel = PluginRiskLevel.MEDIUM,
            required = true,
        ),
    ),
    catalogSourceId: String? = null,
    installedPackageUrl: String = "",
    lastCatalogCheckAtEpochMillis: Long? = null,
    localPackagePath: String = "/tmp/$version.zip",
    packageContractSnapshot: PluginPackageContractSnapshot? = if (protocolVersion == PluginRepository.SUPPORTED_PROTOCOL_VERSION) {
        PluginPackageContractSnapshot(
            protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
            runtime = PluginRuntimeDeclarationSnapshot(
                kind = "js_quickjs",
                bootstrap = "runtime/index.js",
                apiVersion = 1,
            ),
            config = PluginConfigEntryPointsSnapshot(),
        )
    } else {
        null
    },
): PluginInstallRecord {
    val manifest = PluginManifest(
        pluginId = "com.example.demo",
        version = version,
        protocolVersion = protocolVersion,
        author = "AstrBot",
        title = "Demo Plugin",
        description = "Example plugin",
        permissions = permissions,
        minHostVersion = "0.3.0",
        maxHostVersion = "",
        sourceType = sourceType,
        entrySummary = "Example entry",
        riskLevel = PluginRiskLevel.LOW,
    )
    return PluginInstallRecord.restoreFromPersistedState(
        manifestSnapshot = manifest,
        source = PluginSource(
            sourceType = sourceType,
            location = "/tmp/$version.zip",
            importedAt = lastUpdatedAt,
        ),
        packageContractSnapshot = packageContractSnapshot,
        permissionSnapshot = manifest.permissions,
        compatibilityState = compatibilityState,
        uninstallPolicy = uninstallPolicy,
        failureState = failureState,
        catalogSourceId = catalogSourceId,
        installedPackageUrl = installedPackageUrl,
        lastCatalogCheckAtEpochMillis = lastCatalogCheckAtEpochMillis,
        enabled = enabled,
        installedAt = installedAt,
        lastUpdatedAt = lastUpdatedAt,
        localPackagePath = localPackagePath,
        extractedDir = extractedDir,
    )
}

private fun writeStaticSchemaFile(
    directory: File,
    schema: PluginStaticConfigSchema,
    relativePath: String = "_conf_schema.json",
) {
    File(directory, relativePath).apply {
        parentFile?.mkdirs()
        writeText(PluginStaticConfigJson.encodeSchema(schema).toString(2))
    }
}

private fun sampleRepositorySource(
    sourceId: String = "official",
    title: String = "Official Repository",
    catalogUrl: String = "https://repo.example.com/catalogs/stable/index.json",
    pluginId: String = "com.example.weather",
    pluginTitle: String = "Weather",
    packageUrl: String = "../packages/weather-1.4.0.zip",
    versions: List<PluginCatalogVersion> = listOf(
        catalogVersion(
            version = "1.4.0",
            packageUrl = packageUrl,
            publishedAt = 1_700L,
            protocolVersion = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
            minHostVersion = "0.3.0",
            maxHostVersion = "0.4.0",
            permissions = listOf(
                PluginPermissionDeclaration(
                    permissionId = "network.http",
                    title = "Network",
                    description = "Fetches weather APIs",
                    riskLevel = PluginRiskLevel.MEDIUM,
                    required = true,
                ),
            ),
            changelog = "Adds severe weather alerts.",
        ),
    ),
): PluginRepositorySource {
    return PluginRepositorySource(
        sourceId = sourceId,
        title = title,
        catalogUrl = catalogUrl,
        updatedAt = 1_800L,
        plugins = listOf(
            PluginCatalogEntry(
                pluginId = pluginId,
                title = pluginTitle,
                author = "AstrBot",
                description = "Shows current weather.",
                entrySummary = "Weather commands",
                scenarios = listOf("forecast", "alerts"),
                versions = versions,
            ),
        ),
    )
}

private fun catalogVersion(
    version: String,
    packageUrl: String = "https://repo.example.com/packages/demo-$version.zip",
    publishedAt: Long = 1_000L,
    protocolVersion: Int = PluginRepository.SUPPORTED_PROTOCOL_VERSION,
    minHostVersion: String = "0.3.0",
    maxHostVersion: String = "",
    permissions: List<PluginPermissionDeclaration> = listOf(
        PluginPermissionDeclaration(
            permissionId = "net.access",
            title = "Network access",
            description = "Allows outgoing requests",
            riskLevel = PluginRiskLevel.MEDIUM,
            required = true,
        ),
    ),
    changelog: String = "Improves plugin behavior.",
): PluginCatalogVersion {
    return PluginCatalogVersion(
        version = version,
        packageUrl = packageUrl,
        publishedAt = publishedAt,
        protocolVersion = protocolVersion,
        minHostVersion = minHostVersion,
        maxHostVersion = maxHostVersion,
        permissions = permissions,
        changelog = changelog,
    )
}

private fun resetPluginRepositoryForTest(
    dao: PluginInstallAggregateDao = InMemoryPluginInstallAggregateDao(),
    catalogDao: PluginCatalogDao = InMemoryPluginCatalogDao(),
    configDao: PluginConfigSnapshotDao = InMemoryPluginConfigSnapshotDao(),
    initialized: Boolean,
    now: Long = 0L,
    dataRemover: PluginDataRemover = NoOpPluginDataRemover,
) {
    val repositoryClass = PluginRepository::class.java
    repositoryClass.getDeclaredField("pluginDao").apply {
        isAccessible = true
        set(PluginRepository, dao)
    }
    repositoryClass.getDeclaredField("pluginCatalogDao").apply {
        isAccessible = true
        set(PluginRepository, catalogDao)
    }
    repositoryClass.getDeclaredField("pluginConfigDao").apply {
        isAccessible = true
        set(PluginRepository, configDao)
    }
    repositoryClass.getDeclaredField("appContext").apply {
        isAccessible = true
        set(PluginRepository, null)
    }

    @Suppress("UNCHECKED_CAST")
    val recordsField = repositoryClass.getDeclaredField("_records").apply {
        isAccessible = true
    }.get(PluginRepository) as MutableStateFlow<List<PluginInstallRecord>>
    recordsField.value = emptyList()

    val initializedField = repositoryClass.getDeclaredField("initialized").apply {
        isAccessible = true
    }.get(PluginRepository) as AtomicBoolean
    initializedField.set(initialized)

    repositoryClass.getDeclaredField("timeProvider").apply {
        isAccessible = true
        set(PluginRepository, { now })
    }
    repositoryClass.getDeclaredField("pluginDataRemover").apply {
        isAccessible = true
        set(PluginRepository, dataRemover)
    }
}

private class FakePluginDataRemover : PluginDataRemover {
    val removedRecords = mutableListOf<PluginInstallRecord>()

    override fun removePluginData(record: PluginInstallRecord) {
        removedRecords += record
    }
}

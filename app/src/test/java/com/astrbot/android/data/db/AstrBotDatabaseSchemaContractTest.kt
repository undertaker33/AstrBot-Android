package com.astrbot.android.data.db

import com.astrbot.android.model.plugin.PluginCompatibilityState
import com.astrbot.android.model.plugin.PluginFailureState
import com.astrbot.android.model.plugin.PluginManifest
import com.astrbot.android.model.plugin.PluginPermissionDeclaration
import com.astrbot.android.model.plugin.PluginRiskLevel
import com.astrbot.android.model.plugin.PluginSource
import com.astrbot.android.model.plugin.PluginSourceType
import com.astrbot.android.model.plugin.PluginUninstallPolicy
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AstrBotDatabaseSchemaContractTest {
    @Test
    fun migrations_include8To9Step() {
        assertTrue(
            AstrBotDatabase.allMigrations.any { migration ->
                migration.startVersion == 8 && migration.endVersion == 9
            },
        )
    }

    @Test
    fun migrations_include9To10Step() {
        assertTrue(
            AstrBotDatabase.allMigrations.any { migration ->
                migration.startVersion == 9 && migration.endVersion == 10
            },
        )
    }

    @Test
    fun migrations_include10To11Step() {
        assertTrue(
            AstrBotDatabase.allMigrations.any { migration ->
                migration.startVersion == 10 && migration.endVersion == 11
            },
        )
    }

    @Test
    fun migrations_include11To12Step() {
        assertTrue(
            AstrBotDatabase.allMigrations.any { migration ->
                migration.startVersion == 11 && migration.endVersion == 12
            },
        )
    }

    @Test
    fun migrations_include12To13Step() {
        assertTrue(
            AstrBotDatabase.allMigrations.any { migration ->
                migration.startVersion == 12 && migration.endVersion == 13
            },
        )
    }

    @Test
    fun migrations_include13To14Step() {
        assertTrue(
            AstrBotDatabase.allMigrations.any { migration ->
                migration.startVersion == 13 && migration.endVersion == 14
            },
        )
    }

    @Test
    fun migrations_include14To15Step() {
        assertTrue(
            AstrBotDatabase.allMigrations.any { migration ->
                migration.startVersion == 14 && migration.endVersion == 15
            },
        )
    }

    @Test
    fun latestMigration_targetsVersion15() {
        assertTrue(AstrBotDatabase.allMigrations.maxOf { it.endVersion } == 15)
    }

    @Test
    fun version10Schema_removesLegacyJsonColumns() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/10.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/10.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v10 was not found")
        val schema = schemaFile.readText()

        val legacyColumns = listOf(
            "messagesJson",
            "clipsJson",
            "providerBindingsJson",
            "boundQqUinsJson",
            "triggerWordsCsv",
            "capabilitiesJson",
            "ttsVoiceOptionsJson",
            "enabledToolsJson",
            "adminUidsJson",
            "wakeWordsJson",
            "whitelistEntriesJson",
            "keywordPatternsJson",
        )

        legacyColumns.forEach { column ->
            assertTrue("Expected $column to be absent from v10 schema", column !in schema)
        }
    }

    @Test
    fun version10Schema_containsPluginTables() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/10.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/10.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v10 was not found")
        val schema = schemaFile.readText()

        listOf(
            "plugin_install_records",
            "plugin_manifest_snapshots",
            "plugin_manifest_permissions",
            "plugin_permission_snapshots",
        ).forEach { tableName ->
            assertTrue("Expected $tableName to exist in v10 schema", tableName in schema)
        }
    }

    @Test
    fun version11Schema_containsPluginFailureStateColumns() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/11.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/11.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v11 was not found")
        val schema = schemaFile.readText()

        listOf(
            "consecutiveFailureCount",
            "lastFailureAtEpochMillis",
            "lastErrorSummary",
            "suspendedUntilEpochMillis",
        ).forEach { columnName ->
            assertTrue("Expected $columnName to exist in v11 schema", columnName in schema)
        }
    }

    @Test
    fun version12Schema_containsPluginCatalogTrackingColumns() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/12.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/12.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v12 was not found")
        val schema = schemaFile.readText()

        listOf(
            "catalogSourceId",
            "installedPackageUrl",
            "lastCatalogCheckAtEpochMillis",
        ).forEach { columnName ->
            assertTrue("Expected $columnName to exist in v12 schema", columnName in schema)
        }
    }

    @Test
    fun version12Schema_containsPluginCatalogTables() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/12.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/12.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v12 was not found")
        val schema = schemaFile.readText()

        listOf(
            "plugin_catalog_sources",
            "plugin_catalog_entries",
            "plugin_catalog_versions",
        ).forEach { tableName ->
            assertTrue("Expected $tableName to exist in v12 schema", tableName in schema)
        }
    }

    @Test
    fun version13Schema_containsPluginCatalogSyncColumns() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/13.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/13.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v13 was not found")
        val schema = schemaFile.readText()

        listOf(
            "lastSyncAtEpochMillis",
            "lastSyncStatus",
            "lastSyncErrorSummary",
        ).forEach { columnName ->
            assertTrue("Expected $columnName to exist in v13 schema", columnName in schema)
        }
    }

    @Test
    fun version14Schema_containsPluginConfigSnapshotTable() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/14.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/14.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v14 was not found")
        val schema = schemaFile.readText()

        listOf(
            "plugin_config_snapshots",
            "coreConfigJson",
            "extensionConfigJson",
            "updatedAt",
        ).forEach { token ->
            assertTrue("Expected $token to exist in v14 schema", token in schema)
        }
    }

    @Test
    fun version15Schema_containsDownloadTasksTable() {
        val schemaFile = listOf(
            File("schemas/com.astrbot.android.data.db.AstrBotDatabase/15.json"),
            File("app/schemas/com.astrbot.android.data.db.AstrBotDatabase/15.json"),
        ).firstOrNull { it.exists() } ?: error("Room schema file for v15 was not found")
        val schema = schemaFile.readText()

        listOf(
            "download_tasks",
            "taskKey",
            "ownerType",
            "url",
            "targetFilePath",
            "status",
            "etag",
            "downloadedBytes",
            "totalBytes",
        ).forEach { token ->
            assertTrue("Expected $token to exist in v15 schema", token in schema)
        }
    }

    @Test
    fun pluginAggregate_mapper_fallsBackToManifestPermissions_whenPermissionSnapshotsAreMissing() {
        val aggregate = PluginInstallAggregate(
            record = PluginInstallRecordEntity(
                pluginId = "plugin.demo",
                sourceType = PluginSourceType.LOCAL_FILE.name,
                sourceLocation = "/plugins/demo.zip",
                sourceImportedAt = 100L,
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
                compatibilityNotes = "",
                uninstallPolicy = PluginUninstallPolicy.KEEP_DATA.name,
                consecutiveFailureCount = 0,
                lastFailureAtEpochMillis = null,
                lastErrorSummary = "",
                suspendedUntilEpochMillis = null,
                catalogSourceId = null,
                installedPackageUrl = "",
                lastCatalogCheckAtEpochMillis = null,
                enabled = true,
                installedAt = 100L,
                lastUpdatedAt = 200L,
                localPackagePath = "/plugins/packages/demo.zip",
                extractedDir = "/plugins/extracted/plugin.demo",
            ),
            manifestSnapshots = listOf(
                PluginManifestSnapshotEntity(
                    pluginId = "plugin.demo",
                    version = "1.0.0",
                    protocolVersion = 1,
                    author = "AstrBot",
                    title = "Demo",
                    description = "Demo plugin",
                    minHostVersion = "0.3.0",
                    maxHostVersion = "",
                    sourceType = PluginSourceType.LOCAL_FILE.name,
                    entrySummary = "Entry summary",
                    riskLevel = PluginRiskLevel.LOW.name,
                ),
            ),
            manifestPermissions = listOf(
                PluginManifestPermissionEntity(
                    pluginId = "plugin.demo",
                    permissionId = "net.access",
                    title = "Network access",
                    description = "Allows outgoing requests",
                    riskLevel = PluginRiskLevel.MEDIUM.name,
                    required = true,
                    sortIndex = 0,
                ),
            ),
            permissionSnapshots = emptyList(),
        )

        val restored = aggregate.toInstallRecord()

        assertEquals(1, restored.permissionSnapshot.size)
        assertEquals("net.access", restored.permissionSnapshot.single().permissionId)
    }

    @Test
    fun pluginAggregate_mapper_roundTripsWriteModelFields() {
        val record = com.astrbot.android.model.plugin.PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = PluginManifest(
                pluginId = "plugin.demo",
                version = "1.2.0",
                protocolVersion = 1,
                author = "AstrBot",
                title = "Demo",
                description = "Demo plugin",
                permissions = listOf(
                    PluginPermissionDeclaration(
                        permissionId = "storage.read",
                        title = "Storage read",
                        description = "Reads plugin assets",
                        riskLevel = PluginRiskLevel.LOW,
                        required = true,
                    ),
                    PluginPermissionDeclaration(
                        permissionId = "net.access",
                        title = "Network access",
                        description = "Allows outgoing requests",
                        riskLevel = PluginRiskLevel.MEDIUM,
                        required = false,
                    ),
                ),
                minHostVersion = "0.3.0",
                maxHostVersion = "0.4.0",
                sourceType = PluginSourceType.LOCAL_FILE,
                entrySummary = "Entry summary",
                riskLevel = PluginRiskLevel.MEDIUM,
            ),
            source = PluginSource(
                sourceType = PluginSourceType.LOCAL_FILE,
                location = "/plugins/demo.zip",
                importedAt = 100L,
            ),
            permissionSnapshot = listOf(
                PluginPermissionDeclaration(
                    permissionId = "net.access",
                    title = "Network access",
                    description = "Allows outgoing requests",
                    riskLevel = PluginRiskLevel.MEDIUM,
                    required = false,
                ),
            ),
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = false,
                notes = "Host is newer than tested range.",
            ),
            uninstallPolicy = PluginUninstallPolicy.REMOVE_DATA,
            failureState = PluginFailureState(
                consecutiveFailureCount = 2,
                lastFailureAtEpochMillis = 222L,
                lastErrorSummary = "network timeout",
                suspendedUntilEpochMillis = 555L,
            ),
            catalogSourceId = "official",
            installedPackageUrl = "https://repo.example.com/packages/demo-1.2.0.zip",
            lastCatalogCheckAtEpochMillis = 333L,
            enabled = true,
            installedAt = 100L,
            lastUpdatedAt = 200L,
            localPackagePath = "/plugins/packages/demo.zip",
            extractedDir = "/plugins/extracted/plugin.demo",
        )

        val restored = record.toWriteModel().let { writeModel ->
            PluginInstallAggregate(
                record = writeModel.record,
                manifestSnapshots = listOf(writeModel.manifestSnapshot),
                manifestPermissions = writeModel.manifestPermissions,
                permissionSnapshots = writeModel.permissionSnapshots,
            ).toInstallRecord()
        }

        assertEquals(record, restored)
    }
}

package com.astrbot.android.model.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginModelsTest {
    @Test
    fun plugin_source_defaults_to_local_file_and_empty_metadata() {
        val source = PluginSource()

        assertEquals(PluginSourceType.LOCAL_FILE, source.sourceType)
        assertEquals("", source.location)
        assertEquals(0L, source.importedAt)
    }

    @Test
    fun plugin_source_type_supports_repository_and_direct_link_sources() {
        assertTrue(PluginSourceType.values().contains(PluginSourceType.REPOSITORY))
        assertTrue(PluginSourceType.values().contains(PluginSourceType.DIRECT_LINK))
    }

    @Test
    fun plugin_permission_declaration_defaults_to_medium_and_required() {
        val permission = PluginPermissionDeclaration(
            permissionId = "net.access",
            title = "Network access",
            description = "Allows the plugin to reach remote endpoints.",
        )

        assertEquals(PluginRiskLevel.MEDIUM, permission.riskLevel)
        assertTrue(permission.required)
    }

    @Test
    fun plugin_manifest_defaults_optional_fields_and_low_risk_level() {
        val manifest = PluginManifest(
            pluginId = "com.example.demo",
            version = "1.2.3",
            protocolVersion = 1,
            author = "AstrBot",
            title = "Demo Plugin",
            description = "Example plugin manifest",
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Main entry point",
        )

        assertEquals(emptyList<PluginPermissionDeclaration>(), manifest.permissions)
        assertEquals("", manifest.maxHostVersion)
        assertEquals(PluginRiskLevel.LOW, manifest.riskLevel)
    }

    @Test
    fun plugin_uninstall_policy_defaults_to_keep_data() {
        assertEquals(PluginUninstallPolicy.KEEP_DATA, PluginUninstallPolicy.default())
        assertTrue(PluginUninstallPolicy.KEEP_DATA.retainUserData)
        assertFalse(PluginUninstallPolicy.REMOVE_DATA.retainUserData)
    }

    @Test
    fun plugin_install_state_defaults_to_not_installed_and_disabled() {
        val state = PluginInstallState()

        assertEquals(PluginInstallStatus.NOT_INSTALLED, state.status)
        assertEquals("", state.installedVersion)
        assertEquals(PluginSource(), state.source)
        assertEquals(null, state.manifestSnapshot)
        assertEquals(emptyList<PluginPermissionDeclaration>(), state.permissionSnapshot)
        assertEquals(PluginCompatibilityStatus.UNKNOWN, state.compatibilityState.status)
        assertFalse(state.enabled)
        assertEquals(0L, state.lastInstalledAt)
        assertEquals(0L, state.lastUpdatedAt)
        assertEquals("", state.localPackagePath)
        assertEquals("", state.extractedDir)
    }

    @Test
    fun plugin_compatibility_state_unknown_uses_null_raw_checks() {
        val unknown = PluginCompatibilityState.unknown()

        assertEquals(PluginCompatibilityStatus.UNKNOWN, unknown.status)
        assertNull(unknown.protocolSupported)
        assertNull(unknown.minHostVersionSatisfied)
        assertNull(unknown.maxHostVersionSatisfied)
        assertFalse(unknown.isEvaluated)
        assertFalse(unknown.isCompatible())
    }

    @Test
    fun plugin_compatibility_state_treats_mixed_nullable_checks_as_unknown() {
        val mixed = PluginCompatibilityState.fromChecks(
            protocolSupported = true,
            minHostVersionSatisfied = null,
            maxHostVersionSatisfied = true,
        )
        val allNull = PluginCompatibilityState.fromChecks(
            protocolSupported = null,
            minHostVersionSatisfied = null,
            maxHostVersionSatisfied = null,
        )

        assertEquals(PluginCompatibilityStatus.UNKNOWN, mixed.status)
        assertFalse(mixed.isEvaluated)
        assertEquals(PluginCompatibilityStatus.UNKNOWN, allNull.status)
        assertFalse(allNull.isEvaluated)
    }

    @Test
    fun plugin_compatibility_state_treats_any_false_check_as_incompatible_even_when_partial() {
        val partialFalse = PluginCompatibilityState.fromChecks(
            protocolSupported = null,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = false,
        )

        assertEquals(PluginCompatibilityStatus.INCOMPATIBLE, partialFalse.status)
        assertTrue(partialFalse.isEvaluated)
        assertFalse(partialFalse.isCompatible())
    }

    @Test
    fun plugin_compatibility_state_treats_all_true_checks_as_compatible() {
        val compatible = PluginCompatibilityState.fromChecks(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        )
        val compatibleCopy = PluginCompatibilityState.fromChecks(
            protocolSupported = true,
            minHostVersionSatisfied = true,
            maxHostVersionSatisfied = true,
        )

        assertEquals(PluginCompatibilityStatus.COMPATIBLE, compatible.status)
        assertTrue(compatible.isEvaluated)
        assertTrue(compatible.isCompatible())
        assertEquals(compatible, compatibleCopy)
        assertEquals(compatible.hashCode(), compatibleCopy.hashCode())
    }

    @Test
    fun plugin_install_record_does_not_expose_public_copy_api() {
        val publicMethodNames = PluginInstallRecord::class.java.methods.map { it.name }

        assertFalse(publicMethodNames.contains("copy"))
    }

    @Test
    fun plugin_install_record_install_factory_and_restore_factory_have_clear_boundaries() {
        val manifest = PluginManifest(
            pluginId = "com.example.demo",
            version = "1.2.3",
            protocolVersion = 1,
            author = "AstrBot",
            title = "Demo Plugin",
            description = "Example plugin manifest",
            permissions = emptyList(),
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.LOCAL_FILE,
            entrySummary = "Main entry point",
        )
        val installRecord = PluginInstallRecord.installFromManifest(
            manifestSnapshot = manifest,
            source = PluginSource(sourceType = PluginSourceType.LOCAL_FILE),
        )
        val restoredRecord = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(sourceType = PluginSourceType.LOCAL_FILE),
            permissionSnapshot = manifest.permissions,
            compatibilityState = PluginCompatibilityState.unknown(),
            uninstallPolicy = PluginUninstallPolicy.default(),
            enabled = false,
            installedAt = 0L,
            lastUpdatedAt = 0L,
            localPackagePath = "",
            extractedDir = "",
        )

        assertEquals(installRecord, restoredRecord)
        assertEquals(installRecord.hashCode(), restoredRecord.hashCode())
        assertEquals("com.example.demo", installRecord.pluginId)
        assertEquals("1.2.3", installRecord.installedVersion)
        assertTrue(installRecord.isSourceTypeAligned)
        assertEquals(PluginInstallRecord.installFromManifest(manifest, PluginSource(PluginSourceType.LOCAL_FILE)), installRecord)
    }

    @Test
    fun plugin_install_record_preserves_catalog_tracking_metadata() {
        val manifest = PluginManifest(
            pluginId = "com.example.catalog",
            version = "3.1.0",
            protocolVersion = 1,
            author = "AstrBot",
            title = "Catalog Plugin",
            description = "Catalog tracking test",
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.REPOSITORY,
            entrySummary = "Catalog entry",
        )

        val record = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(
                sourceType = PluginSourceType.REPOSITORY,
                location = "https://repo.example.com/catalog.json",
                importedAt = 100L,
            ),
            catalogSourceId = "official",
            installedPackageUrl = "https://repo.example.com/packages/catalog-plugin-3.1.0.zip",
            lastCatalogCheckAtEpochMillis = 200L,
        )

        assertEquals("official", record.catalogSourceId)
        assertEquals("https://repo.example.com/packages/catalog-plugin-3.1.0.zip", record.installedPackageUrl)
        assertEquals(200L, record.lastCatalogCheckAtEpochMillis)
    }

    @Test
    fun plugin_catalog_version_resolves_relative_package_url_against_catalog_url() {
        val version = PluginCatalogVersion(
            version = "1.2.0",
            packageUrl = "../packages/demo-plugin-1.2.0.zip",
            publishedAt = 100L,
            protocolVersion = 1,
            minHostVersion = "0.3.0",
        )

        val resolved = version.resolvePackageUrl("https://repo.example.com/catalogs/stable/index.json")

        assertEquals("https://repo.example.com/catalogs/packages/demo-plugin-1.2.0.zip", resolved)
    }

    @Test
    fun plugin_catalog_version_keeps_absolute_package_url_unchanged() {
        val version = PluginCatalogVersion(
            version = "1.2.0",
            packageUrl = "https://cdn.example.com/demo-plugin-1.2.0.zip",
            publishedAt = 100L,
            protocolVersion = 1,
            minHostVersion = "0.3.0",
        )

        val resolved = version.resolvePackageUrl("https://repo.example.com/catalogs/stable/index.json")

        assertEquals("https://cdn.example.com/demo-plugin-1.2.0.zip", resolved)
    }

    @Test
    fun plugin_install_intent_normalizes_github_blob_catalog_url_to_raw_url() {
        val intent = PluginInstallIntent.repositoryUrl(
            " https://github.com/undertaker33/astrbot_android_plugin_memes/blob/main/publish/0.1.0/repository/catalog.json ",
        )

        assertEquals(
            "https://raw.githubusercontent.com/undertaker33/astrbot_android_plugin_memes/main/publish/0.1.0/repository/catalog.json",
            intent.url,
        )
    }

    @Test
    fun plugin_download_progress_formats_known_total_download_state() {
        val progress = PluginDownloadProgress.downloading(
            bytesDownloaded = 1_048_576L,
            totalBytes = 3_145_728L,
            bytesPerSecond = 2_097_152L,
        )

        assertEquals(PluginDownloadProgressStage.DOWNLOADING, progress.stage)
        assertEquals(0.333f, progress.progressFraction ?: -1f, 0.001f)
        assertEquals("1.0 MB", progress.downloadedMegabytesLabel)
        assertEquals("3.0 MB", progress.totalMegabytesLabel)
        assertEquals("2.0 MB/s", progress.speedLabel)
        assertFalse(progress.isIndeterminate)
    }

    @Test
    fun plugin_download_progress_formats_unknown_total_download_state() {
        val progress = PluginDownloadProgress.downloading(
            bytesDownloaded = 524_288L,
            totalBytes = -1L,
            bytesPerSecond = 0L,
        )

        assertNull(progress.progressFraction)
        assertTrue(progress.isIndeterminate)
        assertEquals("0.5 MB", progress.downloadedMegabytesLabel)
        assertEquals("-- MB", progress.totalMegabytesLabel)
        assertEquals("-- MB/s", progress.speedLabel)
    }

    @Test
    fun plugin_download_progress_formats_installing_state_as_complete_when_total_is_known() {
        val progress = PluginDownloadProgress.installing(
            bytesDownloaded = 4_194_304L,
            totalBytes = 4_194_304L,
        )

        assertEquals(PluginDownloadProgressStage.INSTALLING, progress.stage)
        assertEquals(1f, progress.progressFraction ?: -1f, 0.001f)
        assertEquals("4.0 MB", progress.downloadedMegabytesLabel)
        assertEquals("4.0 MB", progress.totalMegabytesLabel)
        assertEquals("-- MB/s", progress.speedLabel)
    }

    @Test
    fun plugin_install_record_makes_manifest_permissions_and_permission_snapshot_independent() {
        val manifestPermissions = mutableListOf(
            PluginPermissionDeclaration(
                permissionId = "fs.read",
                title = "Read local files",
                description = "Allows the plugin to read files from its sandbox.",
            ),
        )
        val installPermissions = mutableListOf(
            PluginPermissionDeclaration(
                permissionId = "net.access",
                title = "Network access",
                description = "Allows the plugin to reach remote endpoints.",
            ),
        )
        val manifest = PluginManifest(
            pluginId = "com.example.snapshot",
            version = "2.0.0",
            protocolVersion = 1,
            author = "AstrBot",
            title = "Snapshot Plugin",
            description = "Snapshot test",
            permissions = manifestPermissions,
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.MANUAL_IMPORT,
            entrySummary = "Snapshot entry",
        )

        val record = PluginInstallRecord.restoreFromPersistedState(
            manifestSnapshot = manifest,
            source = PluginSource(sourceType = PluginSourceType.MANUAL_IMPORT),
            permissionSnapshot = installPermissions,
            compatibilityState = PluginCompatibilityState.evaluated(
                protocolSupported = true,
                minHostVersionSatisfied = true,
                maxHostVersionSatisfied = true,
            ),
        )

        manifestPermissions += PluginPermissionDeclaration(
            permissionId = "fs.write",
            title = "Write local files",
            description = "Allows writing to the sandbox.",
        )
        installPermissions += PluginPermissionDeclaration(
            permissionId = "sys.exec",
            title = "Execute commands",
            description = "Allows command execution.",
        )

        assertEquals(1, record.manifestSnapshot.permissions.size)
        assertEquals("fs.read", record.manifestSnapshot.permissions.single().permissionId)
        assertEquals(1, record.permissionSnapshot.size)
        assertEquals("net.access", record.permissionSnapshot.single().permissionId)
        assertTrue(record.isSourceTypeAligned)
    }

    @Test
    fun plugin_install_state_is_activated_only_when_installed_and_enabled() {
        val installedAndEnabled = PluginInstallState(
            status = PluginInstallStatus.INSTALLED,
            enabled = true,
        )
        val installedButDisabled = PluginInstallState(
            status = PluginInstallStatus.INSTALLED,
            enabled = false,
        )
        val notInstalled = PluginInstallState(
            status = PluginInstallStatus.NOT_INSTALLED,
            enabled = true,
        )

        assertTrue(installedAndEnabled.isActivated())
        assertFalse(installedButDisabled.isActivated())
        assertFalse(notInstalled.isActivated())
    }

    @Test
    fun plugin_install_record_rejects_source_type_mismatch() {
        val manifest = PluginManifest(
            pluginId = "com.example.mismatch",
            version = "1.0.0",
            protocolVersion = 1,
            author = "AstrBot",
            title = "Mismatch Plugin",
            description = "Mismatch test",
            minHostVersion = "0.3.0",
            sourceType = PluginSourceType.MANUAL_IMPORT,
            entrySummary = "Mismatch entry",
        )

        try {
            PluginInstallRecord.installFromManifest(
                manifestSnapshot = manifest,
                source = PluginSource(sourceType = PluginSourceType.LOCAL_FILE),
            )
        } catch (e: IllegalArgumentException) {
            return
        }

        throw AssertionError("Expected source type mismatch to fail")
    }

    @Test
    fun plugin_risk_level_is_blocking_only_for_high_risk_levels() {
        assertFalse(PluginRiskLevel.LOW.isBlocking())
        assertFalse(PluginRiskLevel.MEDIUM.isBlocking())
        assertTrue(PluginRiskLevel.HIGH.isBlocking())
        assertTrue(PluginRiskLevel.CRITICAL.isBlocking())
    }
}

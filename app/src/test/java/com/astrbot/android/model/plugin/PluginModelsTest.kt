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

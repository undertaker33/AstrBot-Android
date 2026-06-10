package com.elymbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceArchitectureContractTest {
    private val projectRoot: Path = detectProjectRoot()
    // skipcq: KT-W1042
    private val settingsFile: Path = projectRoot.resolve("settings.gradle.kts")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")
    private val appIntegrationBuildFile: Path = projectRoot.resolve("app-integration/build.gradle.kts")
    private val databaseModuleFile: Path =
        projectRoot.resolve("app-integration/src/main/java/com/elymbot/android/app/integration/db/DatabaseModule.kt")
    private val geofenceRuleDaoFile: Path =
        projectRoot.resolve("core/db/src/main/java/com/elymbot/android/data/db/geofence/GeofenceRuleDao.kt")
    private val geofenceApiBuildFile: Path = projectRoot.resolve("feature/geofence/api/build.gradle.kts")
    private val geofenceApiRoot: Path = projectRoot.resolve("feature/geofence/api/src/main/java")
    private val configPresentationRoot: Path = projectRoot.resolve("feature/config/presentation/src/main/java")
    private val configProfileFile: Path =
        projectRoot.resolve("feature/config/api/src/main/java/com/elymbot/android/feature/config/domain/model/ConfigProfile.kt")
    private val configDetailScreenFile: Path =
        projectRoot.resolve("feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/ConfigDetailScreen.kt")
    private val resourceCenterModelsFile: Path =
        projectRoot.resolve("feature/resource/api/src/main/java/com/elymbot/android/model/ResourceCenterModels.kt")
    private val pluginRoots: List<Path> = listOf(
        projectRoot.resolve("feature/plugin/api/src/main/java"),
        projectRoot.resolve("feature/plugin/runtime/src/main/java"),
    )

    @Test
    fun geofence_modules_are_registered_and_scanned() {
        val settingsText = settingsFile.readText()
        val rootBuildText = rootBuildFile.readText()
        val modules = listOf(
            ":feature:geofence:api",
            ":feature:geofence:data",
            ":feature:geofence:impl",
            ":feature:geofence:runtime",
        )

        val missingSettings = modules.filterNot { module -> settingsText.contains("""include("$module")""") }
        val missingRoots = modules
            .map { module -> module.removePrefix(":").replace(':', '/') + "/src/main/java" }
            .filterNot { sourceRoot -> rootBuildText.contains("\"$sourceRoot\"") }

        assertTrue("Geofence modules must be registered in settings.gradle.kts: $missingSettings", missingSettings.isEmpty())
        assertTrue("Geofence source roots must be scanned by architecture tasks: $missingRoots", missingRoots.isEmpty())
        assertTrue("moduleGeofenceCheck must be registered.", rootBuildText.contains("""taskPrefix = "moduleGeofence""""))
    }

    @Test
    fun geofence_api_has_no_android_room_google_play_services_maps_or_compose_dependencies() {
        val text = geofenceApiBuildFile.readText()
        val forbiddenTokens = listOf(
            "com.android.",
            "androidx.room",
            "play-services",
            "maps",
            "compose",
        )
        val violations = forbiddenTokens.filter(text::contains)

        assertTrue(
            "Geofence API must stay free of Android UI, Room, Google Play services, Maps SDK, and Compose dependencies: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun geofence_api_source_has_no_android_room_google_or_compose_imports() {
        val forbiddenImports = Regex("""import\s+(android\.|androidx\.room|androidx\.compose|com\.google\.android\.gms|com\.google\.maps)""")
        val violations = kotlinFilesUnder(geofenceApiRoot)
            .filter { file -> forbiddenImports.containsMatchIn(file.readText()) }
            .map { file -> relativePath(file) }

        assertTrue(
            "Geofence API source must not import Android, Room, Google Play services, Maps SDK, or Compose types: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_shell_does_not_directly_depend_on_geofence_data_or_impl() {
        val text = appBuildFile.readText()
        val forbiddenTokens = listOf(
            """project(":feature:geofence:data")""",
            """project(":feature:geofence:impl")""",
        )
        val violations = forbiddenTokens.filter(text::contains)

        assertTrue(
            "App shell must not directly depend on geofence data/impl; wiring belongs in app-integration: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun app_integration_wires_geofence_api_and_data_without_leaking_data_as_api() {
        val text = appIntegrationBuildFile.readText()

        assertTrue(
            "app-integration must expose the geofence API contract to app.",
            text.contains("""api(project(":feature:geofence:api"))"""),
        )
        assertTrue(
            "app-integration must wire geofence data internally.",
            text.contains("""implementation(project(":feature:geofence:data"))"""),
        )
        assertTrue(
            "app-integration must wire geofence runtime internally.",
            text.contains("""implementation(project(":feature:geofence:runtime"))"""),
        )
        assertTrue(
            "app-integration must not expose geofence data implementation as api.",
            !text.contains("""api(project(":feature:geofence:data"))"""),
        )
        assertTrue(
            "app-integration must not expose geofence runtime implementation as api.",
            !text.contains("""api(project(":feature:geofence:runtime"))"""),
        )
    }

    @Test
    fun geofence_runtime_depends_on_location_and_workmanager_without_presentation_or_maps() {
        val runtimeBuildFile = projectRoot.resolve("feature/geofence/runtime/build.gradle.kts")
        val text = runtimeBuildFile.readText()

        assertTrue(
            "Geofence runtime must depend on geofence API.",
            text.contains("""implementation(project(":feature:geofence:api"))"""),
        )
        assertTrue(
            "Geofence runtime must depend on WorkManager.",
            text.contains("androidx.work:work-runtime-ktx"),
        )
        assertTrue(
            "Geofence runtime must depend on Hilt Worker.",
            text.contains("androidx.hilt:hilt-work"),
        )
        assertTrue(
            "Geofence runtime must depend on Google Play services location.",
            text.contains("com.google.android.gms:play-services-location"),
        )
        assertTrue(
            "Geofence runtime must not depend on presentation or Maps SDK.",
            !text.contains("""project(":feature:geofence:presentation")""") &&
                !text.contains("play-services-maps") &&
                !text.contains("maps-compose"),
        )
    }

    @Test
    fun database_module_provides_geofence_rule_dao_for_hilt_graph() {
        val text = databaseModuleFile.readText()

        assertTrue(
            "DatabaseModule must import GeofenceRuleDao for FeatureGeofenceRuleRepositoryStore injection.",
            text.contains("import com.elymbot.android.data.db.geofence.GeofenceRuleDao"),
        )
        assertTrue(
            "DatabaseModule must provide database.geofenceRuleDao() to the Hilt graph.",
            text.contains("fun provideGeofenceRuleDao(") &&
                text.contains("): GeofenceRuleDao = database.geofenceRuleDao()"),
        )
    }

    @Test
    fun geofence_rule_delete_must_remove_owned_regions_and_config_bindings_transactionally() {
        val text = geofenceRuleDaoFile.readText().replace("\r\n", "\n")
        val beforeDeleteRule = text.substringBefore("suspend fun deleteRule(ruleId: String)")
        val deleteRuleBody = text.substringAfter("suspend fun deleteRule(ruleId: String)")
            .substringBefore("@Query(\"DELETE FROM geofence_rules WHERE ruleId = :ruleId\")")

        assertTrue(
            "GeofenceRuleDao.deleteRule must be a transactional method body, not a single-table @Query.",
            beforeDeleteRule.substringAfterLast("@Query").contains("@Transaction") &&
                !text.contains("@Query(\"DELETE FROM geofence_rules WHERE ruleId = :ruleId\")\n    suspend fun deleteRule("),
        )
        assertTrue(
            "GeofenceRuleDao.deleteRule must delete child geofence_regions before removing the rule.",
            deleteRuleBody.contains("deleteRegionsForRule(ruleId)"),
        )
        assertTrue(
            "GeofenceRuleDao.deleteRule must delete config_geofence_bindings for the removed rule.",
            deleteRuleBody.contains("deleteConfigBindingsForRule(ruleId)"),
        )
        assertTrue(
            "GeofenceRuleDao.deleteRule must remove the parent geofence_rules row after child rows.",
            deleteRuleBody.contains("deleteRuleEntity(ruleId)"),
        )
    }

    @Test
    fun plugin_api_and_runtime_do_not_expose_geofence_host_api() {
        val violations = pluginRoots
            .filter { root -> root.exists() }
            .flatMap(::kotlinFilesUnder)
            .filter { file -> file.readText().contains("hostApi.geofence") || file.readText().contains("geofence.") }
            .map(::relativePath)

        assertTrue(
            "Plugin API/runtime must not expose hostApi.geofence.* or direct geofence API surface: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun plugin_geofence_management_symbols_are_internal_active_capability_only() {
        val allowedInternalFiles = setOf(
            "feature/plugin/api/src/main/java/com/elymbot/android/feature/plugin/domain/runtime/GeofenceActiveCapabilityFacade.kt",
            "feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/ActiveCapabilityToolSourceProvider.kt",
        )
        val pluginVisibleGeofenceSymbols = Regex(
            "hostApi\\.geofence|hostApi\\[\"geofence\"\\]|GeofenceRule|GeofenceRegion|" +
                "GeofenceExecutionRecord|GeofenceActiveCapabilityFacade|create_geofence_rule|" +
                "update_geofence_rule|delete_geofence_rule|pause_geofence_rule|resume_geofence_rule",
        )
        val violations = pluginRoots
            .filter { root -> root.exists() }
            .flatMap(::kotlinFilesUnder)
            .filter { file -> pluginVisibleGeofenceSymbols.containsMatchIn(file.readText()) }
            .map(::relativePath)
            .filterNot(allowedInternalFiles::contains)

        assertTrue(
            "Geofence management symbols must remain limited to the internal active-capability bridge: $violations",
            violations.isEmpty(),
        )

        val facadeFile = projectRoot.resolve(allowedInternalFiles.first()).readText()
        assertTrue(
            "GeofenceActiveCapabilityFacade must be explicitly documented as internal and not a plugin Host API surface.",
            facadeFile.contains("INTERNAL_ACTIVE_CAPABILITY_ONLY") &&
                facadeFile.contains("not a plugin Host API"),
        )
    }

    @Test
    fun core_modules_do_not_depend_on_geofence_feature() {
        val violations = buildFilesUnder(projectRoot.resolve("core"))
            .filter { file -> file.readText().contains("""project(":feature:geofence""") }
            .map(::relativePath)

        assertTrue(
            "Core modules must not depend on the geofence feature: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun config_presentation_uses_only_geofence_api_ports_for_binding_management() {
        val forbiddenTokens = listOf(
            "com.elymbot.android.data.db.geofence",
            "GeofenceRuleDao",
            "FeatureGeofenceRuleRepositoryStore",
            "FeatureGeofenceRuleRepositoryPortAdapter",
            "feature.geofence.data",
        )
        val violations = kotlinFilesUnder(configPresentationRoot)
            .flatMap { file ->
                val text = file.readText()
                forbiddenTokens
                    .filter(text::contains)
                    .map { token -> "${relativePath(file)} contains $token" }
            }

        assertTrue(
            "Config presentation must not import geofence DAO/data implementation for ConfigDetail bindings: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun config_profile_does_not_embed_geofence_rule_content_fields() {
        val text = configProfileFile.readText()
        val forbiddenTokens = listOf(
            "geofenceLatitude",
            "geofenceLongitude",
            "geofenceRadius",
            "geofenceActionPrompt",
            "geofenceRegions",
            "ConfigGeofenceBinding",
        )
        val violations = forbiddenTokens.filter(text::contains)

        assertTrue(
            "ConfigProfile must not embed geofence coordinates, radius, action prompt, or binding fields: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun resource_center_projection_is_not_reused_for_geofence_bindings() {
        val configDetailText = configDetailScreenFile.readText()
        val resourceModelText = resourceCenterModelsFile.readText()

        assertTrue(
            "ConfigResourceProjection must not grow geofence-specific binding fields.",
            !resourceModelText.contains("geofence", ignoreCase = true),
        )
        assertTrue(
            "ConfigDetail buildProjectionUpdates must remain limited to Resource Center resources, not geofence bindings.",
            !configDetailText
                .substringAfter("private fun buildProjectionUpdates(")
                .substringBefore("private fun ResourceCenterItem.toMcpServerEntry")
                .contains("geofence", ignoreCase = true),
        )
    }

    @Test
    fun config_detail_exposes_geofence_section_dialog_and_navigation_callback() {
        val configDetailText = configDetailScreenFile.readText()
        val navModelsText = projectRoot
            .resolve("feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/detail/ConfigNavModels.kt")
            .readText()
        val drawerTreeText = projectRoot
            .resolve("feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/detail/ConfigDrawerTree.kt")
            .readText()
            .replace("\r\n", "\n")
        val appNavText = projectRoot
            .resolve("app/src/main/java/com/elymbot/android/ui/navigation/ElymBotAppScaffoldParts.kt")
            .readText()

        assertTrue("ConfigSection.Geofence must exist.", navModelsText.contains("Geofence(R.string.config_section_geofence)"))
        assertTrue(
            "Platform settings drawer must include ConfigSection.Geofence near whitelist settings.",
            drawerTreeText.contains("ConfigSection.Whitelist,\n                ConfigSection.Geofence,"),
        )
        assertTrue(
            "ConfigDetailScreen must accept onOpenGeofenceRules callback.",
            configDetailText.contains("onOpenGeofenceRules: () -> Unit"),
        )
        assertTrue(
            "ConfigDetail must expose stable geofence test tags for section, manage button, dialog, and open-rules action.",
            listOf(
                "config-geofence-section",
                "config-geofence-manage",
                "config-geofence-dialog",
                "config-geofence-empty",
                "config-geofence-open-rules",
            ).all(configDetailText::contains),
        )
        assertTrue(
            "App nav must route ConfigDetail geofence callback to AppDestination.GeofenceRules.",
            appNavText.contains("onOpenGeofenceRules = { AppNavigator.open(navController, AppDestination.GeofenceRules.route) }"),
        )
    }

    private fun buildFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString() == "build.gradle.kts" }
                .toList()
        }
    }

    private fun kotlinFilesUnder(root: Path): List<Path> {
        if (!root.exists()) {
            return emptyList()
        }
        return Files.walk(root).use { stream ->
            stream
                .filter { path -> path.isRegularFile() && path.fileName.toString().endsWith(".kt") }
                .toList()
        }
    }

    private fun relativePath(file: Path): String =
        projectRoot.relativize(file).toString().replace('\\', '/')

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}

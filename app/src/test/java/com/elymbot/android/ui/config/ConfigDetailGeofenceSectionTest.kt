package com.elymbot.android.ui.config

import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.ui.config.geofence.buildConfigGeofenceBindingPresentation
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigDetailGeofenceSectionTest {
    private val projectRoot: Path = detectProjectRoot()
    private val configDetailScreen = projectRoot.resolve(
        "feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/ConfigDetailScreen.kt",
    )
    private val bindingPresentation = projectRoot.resolve(
        "feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/geofence/ConfigGeofenceBindingPresentation.kt",
    )
    private val drawerTree = projectRoot.resolve(
        "feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/detail/ConfigDrawerTree.kt",
    )
    private val englishStrings = projectRoot.resolve("app/src/main/res/values/strings.xml")
    private val chineseStrings = projectRoot.resolve("app/src/main/res/values-zh/strings.xml")

    @Test
    fun platform_settings_drawer_contains_geofence() {
        val source = drawerTree.readText().replace("\r\n", "\n")

        assertTrue(source.contains("ConfigSection.Whitelist,\n                ConfigSection.Geofence,"))
    }

    @Test
    fun section_presentation_shows_loaded_count_and_first_two_summaries() {
        val presentation = buildConfigGeofenceBindingPresentation(
            // skipcq: KT-W1042
            configId = "config-1",
            rules = listOf(rule("rule-a", "Office"), rule("rule-b", "Home"), rule("rule-c", "Station")),
            bindings = listOf(
                ConfigGeofenceBinding(configId = "config-1", ruleId = "rule-a", enabled = true, sortIndex = 0),
                ConfigGeofenceBinding(configId = "config-1", ruleId = "rule-b", enabled = false, sortIndex = 1),
                ConfigGeofenceBinding(configId = "config-1", ruleId = "rule-c", enabled = true, sortIndex = 2),
            ),
        )

        assertEquals(3, presentation.loadedCount)
        assertEquals(2, presentation.summaryItems.size)
        assertEquals("Office", presentation.summaryItems[0].ruleName)
        assertEquals("Home", presentation.summaryItems[1].ruleName)
    }

    @Test
    fun manage_dialog_can_open() {
        val source = configDetailScreen.readText()

        assertTrue(source.contains("showGeofenceBindingDialog = true"))
        assertTrue(source.contains("GeofenceBindingDialog("))
        assertTrue(source.contains("config-geofence-manage"))
    }

    @Test
    fun empty_state_jump_wires_open_rules_callback() {
        val source = configDetailScreen.readText()

        assertTrue(source.contains("onOpenGeofenceRules: () -> Unit"))
        assertTrue(source.contains("ConfigPendingExit.GeofenceRules"))
        assertTrue(source.contains("config-geofence-open-rules"))
    }

    @Test
    fun dialog_draft_is_session_keyed_and_merges_live_rule_availability() {
        val source = configDetailScreen.readText()

        assertTrue(source.contains("draftSessionKey = \"${'$'}{profile.id}:${'$'}geofenceBindingDialogSession\""))
        assertTrue(source.contains("var draft by remember(draftSessionKey)"))
        assertTrue(source.contains("LaunchedEffect(draftSessionKey, availableRuleIds)"))
        assertTrue(source.contains("draft.mergeAvailableRules(availableRuleIds)"))
        assertTrue(!source.contains("remember(presentation)"))
    }

    @Test
    fun geofence_summaries_are_localized_in_compose_resources() {
        val presentationSource = bindingPresentation.readText()
        val screenSource = configDetailScreen.readText()
        val english = englishStrings.readText()
        val chinese = chineseStrings.readText()
        val forbiddenPresentationLiterals = listOf(
            "\"enabled\"",
            "\"disabled\"",
            "\"Region\"",
            "\"enter\"",
            "\"exit\"",
            "\"dwell\"",
            "persistedValue",
        )
        val requiredStringKeys = listOf(
            "config_geofence_binding_state_enabled",
            "config_geofence_binding_state_disabled",
            "config_geofence_region_fallback",
            "config_geofence_trigger_enter",
            "config_geofence_trigger_exit",
            "config_geofence_trigger_dwell",
            "config_geofence_action_weather_forecast",
        )

        assertTrue(forbiddenPresentationLiterals.none(presentationSource::contains))
        assertTrue(screenSource.contains("localizedGeofenceBindingSummary(summary)"))
        assertTrue(screenSource.contains("localizedGeofenceRuleDetail(item.summary)"))
        assertTrue(requiredStringKeys.all(english::contains))
        assertTrue(requiredStringKeys.all(chinese::contains))
    }

    private fun rule(ruleId: String, name: String): GeofenceRule =
        GeofenceRule(
            ruleId = ruleId,
            name = name,
            enabled = true,
            triggerEnter = true,
            actionPrompt = "Notify",
            regions = listOf(
                GeofenceRegion(
                    regionId = "$ruleId-region",
                    ruleId = ruleId,
                    label = "$name area",
                    latitude = 31.2304,
                    longitude = 121.4737,
                    radiusMeters = 150f,
                ),
            ),
        )

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("settings.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("settings.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}

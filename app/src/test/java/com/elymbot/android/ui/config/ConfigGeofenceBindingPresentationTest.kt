package com.elymbot.android.ui.config

import com.elymbot.android.feature.geofence.domain.GeofenceRuleRepositoryPort
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationSummary
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceRuntimeReconciliationPort
import com.elymbot.android.ui.config.geofence.ConfigGeofenceBindingController
import com.elymbot.android.ui.config.geofence.ConfigGeofenceBindingDraft
import com.elymbot.android.ui.config.geofence.buildConfigGeofenceBindingPresentation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigGeofenceBindingPresentationTest {
    @Test
    fun no_rules_shows_empty_state_and_open_rules_prompt() {
        val presentation = buildConfigGeofenceBindingPresentation(
            // skipcq: KT-W1042
            configId = "config-1",
            rules = emptyList(),
            bindings = emptyList(),
        )

        assertTrue(presentation.emptyRules)
        assertTrue(presentation.showOpenRulesAction)
        assertEquals(0, presentation.loadedCount)
        assertEquals(emptyList<String>(), presentation.summaryItems)
        assertEquals(emptyList<String>(), presentation.selectedRuleIds)
    }

    @Test
    fun existing_rules_can_be_multi_selected_for_loading() {
        val presentation = buildConfigGeofenceBindingPresentation(
            configId = "config-1",
            // skipcq: KT-W1042
            rules = listOf(rule("rule-a"), rule("rule-b")),
            bindings = emptyList(),
        )

        val draft = ConfigGeofenceBindingDraft.fromPresentation(presentation)
            .withRuleSelected("rule-a", true)
            .withRuleSelected("rule-b", true)
            .withBindingEnabled("rule-b", false)

        assertEquals(listOf("rule-a", "rule-b"), draft.selectedRuleIds)
        assertTrue(draft.enabledByRuleId.getValue("rule-a"))
        assertFalse(draft.enabledByRuleId.getValue("rule-b"))
    }

    @Test
    fun save_only_writes_bindings_and_triggers_reconciliation() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(rule("rule-a"), rule("rule-b")),
            initialBindings = listOf(binding("config-1", "rule-a", enabled = true)),
        )
        val reconciliation = FakeGeofenceRuntimeReconciliationPort()
        val controller = ConfigGeofenceBindingController(repository, reconciliation)
        val draft = ConfigGeofenceBindingDraft(
            entries = listOf(
                ConfigGeofenceBindingDraft.Entry(ruleId = "rule-a", enabled = false, sortIndex = 0),
                ConfigGeofenceBindingDraft.Entry(ruleId = "rule-b", enabled = true, sortIndex = 1),
            ),
        )

        controller.saveDraft(configId = "config-1", draft = draft).getOrThrow()

        assertEquals(emptyList<String>(), repository.ruleWriteOperations)
        assertEquals(
            listOf(
                ConfigGeofenceBinding(configId = "config-1", ruleId = "rule-a", enabled = false, sortIndex = 0),
                ConfigGeofenceBinding(configId = "config-1", ruleId = "rule-b", enabled = true, sortIndex = 1),
            ),
            repository.upsertedBindings,
        )
        assertEquals(emptyList<Pair<String, String>>(), repository.deletedBindings)
        assertEquals(1, reconciliation.reconcileNowCalls)
    }

    @Test
    fun disabled_binding_save_does_not_modify_rule_body() = runTest {
        val originalRule = rule("rule-a", name = "Morning commute", prompt = "Send weather")
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(originalRule),
            initialBindings = listOf(binding("config-1", "rule-a", enabled = true)),
        )
        val controller = ConfigGeofenceBindingController(repository, FakeGeofenceRuntimeReconciliationPort())

        controller.saveDraft(
            configId = "config-1",
            draft = ConfigGeofenceBindingDraft(
                entries = listOf(ConfigGeofenceBindingDraft.Entry(ruleId = "rule-a", enabled = false, sortIndex = 0)),
            ),
        ).getOrThrow()

        assertEquals(originalRule, repository.rules.value.single())
        assertEquals(emptyList<String>(), repository.ruleWriteOperations)
        assertEquals(false, repository.upsertedBindings.single().enabled)
    }

    @Test
    fun stale_binding_is_safe_in_presentation_and_removed_on_save() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(rule("rule-a")),
            initialBindings = listOf(
                binding("config-1", "rule-a", enabled = true),
                // skipcq: KT-W1042
                binding("config-1", "missing-rule", enabled = true),
            ),
        )
        val presentation = buildConfigGeofenceBindingPresentation(
            configId = "config-1",
            rules = repository.rules.value,
            bindings = repository.bindings.value,
        )
        val controller = ConfigGeofenceBindingController(repository, FakeGeofenceRuntimeReconciliationPort())

        assertEquals(listOf("missing-rule"), presentation.staleBindingRuleIds)
        assertEquals(listOf("rule-a"), presentation.selectedRuleIds)

        controller.saveDraft(
            configId = "config-1",
            draft = ConfigGeofenceBindingDraft.fromPresentation(presentation),
        ).getOrThrow()

        assertEquals(listOf("config-1" to "missing-rule"), repository.deletedBindings)
        assertEquals(listOf("rule-a"), repository.bindings.value.map { it.ruleId })
    }

    @Test
    fun draft_survives_live_binding_emission_and_late_save_failure() = runTest {
        val repository = FakeGeofenceRuleRepositoryPort(
            initialRules = listOf(rule("rule-a"), rule("rule-b")),
            initialBindings = listOf(binding("config-1", "rule-a", enabled = true)),
        )
        val draft = ConfigGeofenceBindingDraft(
            entries = listOf(
                ConfigGeofenceBindingDraft.Entry(ruleId = "rule-b", enabled = false, sortIndex = 0),
            ),
        )
        val externalBindingEmission = buildConfigGeofenceBindingPresentation(
            configId = "config-1",
            rules = repository.rules.value,
            bindings = repository.bindings.value,
        )

        val mergedDraft = draft.mergeAvailableRules(externalBindingEmission)

        assertEquals(draft, mergedDraft)

        val result = ConfigGeofenceBindingController(
            repository = repository,
            reconciliationPort = FakeGeofenceRuntimeReconciliationPort(failOnReconcile = true),
        ).saveDraft(configId = "config-1", draft = mergedDraft)

        assertTrue(result.isFailure)
        assertEquals(listOf("config-1" to "rule-a"), repository.deletedBindings)
        assertEquals(
            listOf(ConfigGeofenceBinding(configId = "config-1", ruleId = "rule-b", enabled = false, sortIndex = 0)),
            repository.upsertedBindings,
        )

        val afterFailedSaveEmission = buildConfigGeofenceBindingPresentation(
            configId = "config-1",
            rules = repository.rules.value,
            bindings = repository.bindings.value,
        )

        assertEquals(mergedDraft, mergedDraft.mergeAvailableRules(afterFailedSaveEmission))
    }

    @Test
    fun presentation_carries_structured_summary_fields_for_localized_rendering() {
        val presentation = buildConfigGeofenceBindingPresentation(
            configId = "config-1",
            rules = listOf(
                rule(
                    ruleId = "rule-a",
                    // skipcq: KT-W1042
                    name = "Office",
                    actionType = GeofenceActionType.WEATHER_FORECAST,
                    prompt = "Bring umbrella",
                ),
            ),
            bindings = listOf(binding("config-1", "rule-a", enabled = false)),
        )

        val summary = presentation.summaryItems.single()

        assertEquals("Office", summary.ruleName)
        assertFalse(summary.bindingEnabled)
        assertEquals("Office", summary.regionLabel)
        assertEquals(150, summary.radiusMeters)
        assertTrue(summary.triggerEnter)
        assertFalse(summary.triggerExit)
        assertFalse(summary.triggerDwell)
        assertEquals(GeofenceActionType.WEATHER_FORECAST, summary.actionType)
        assertEquals("Bring umbrella", summary.actionPromptPreview)
    }

    private fun rule(
        ruleId: String,
        name: String = ruleId,
        actionType: GeofenceActionType = GeofenceActionType.AGENT_PROMPT,
        prompt: String = "Notify",
    ): GeofenceRule =
        GeofenceRule(
            ruleId = ruleId,
            name = name,
            enabled = true,
            triggerEnter = true,
            actionType = actionType,
            actionPrompt = prompt,
            regions = listOf(
                GeofenceRegion(
                    regionId = "$ruleId-region",
                    ruleId = ruleId,
                    label = "Office",
                    latitude = 31.2304,
                    longitude = 121.4737,
                    radiusMeters = 150f,
                ),
            ),
        )

    private fun binding(
        configId: String,
        ruleId: String,
        enabled: Boolean,
    ): ConfigGeofenceBinding =
        ConfigGeofenceBinding(configId = configId, ruleId = ruleId, enabled = enabled)

    private class FakeGeofenceRuleRepositoryPort(
        initialRules: List<GeofenceRule> = emptyList(),
        initialBindings: List<ConfigGeofenceBinding> = emptyList(),
    ) : GeofenceRuleRepositoryPort {
        private val mutableRules = MutableStateFlow(initialRules)
        private val mutableBindings = MutableStateFlow(initialBindings)

        val upsertedBindings = mutableListOf<ConfigGeofenceBinding>()
        val deletedBindings = mutableListOf<Pair<String, String>>()
        val ruleWriteOperations = mutableListOf<String>()

        override val rules: StateFlow<List<GeofenceRule>> = mutableRules
        override val bindings: StateFlow<List<ConfigGeofenceBinding>> = mutableBindings

        override suspend fun createRule(rule: GeofenceRule, regions: List<GeofenceRegion>): GeofenceRule {
            ruleWriteOperations += "createRule"
            return rule
        }

        override suspend fun updateRule(rule: GeofenceRule): GeofenceRule {
            ruleWriteOperations += "updateRule"
            return rule
        }

        override suspend fun deleteRule(ruleId: String) {
            ruleWriteOperations += "deleteRule"
        }

        override suspend fun pauseRule(ruleId: String): GeofenceRule? {
            ruleWriteOperations += "pauseRule"
            return mutableRules.value.firstOrNull { it.ruleId == ruleId }
        }

        override suspend fun resumeRule(ruleId: String): GeofenceRule? {
            ruleWriteOperations += "resumeRule"
            return mutableRules.value.firstOrNull { it.ruleId == ruleId }
        }

        override suspend fun getRule(ruleId: String): GeofenceRule? =
            mutableRules.value.firstOrNull { it.ruleId == ruleId }

        override suspend fun listRules(): List<GeofenceRule> = mutableRules.value

        override suspend fun replaceRegions(ruleId: String, regions: List<GeofenceRegion>): GeofenceRule? {
            ruleWriteOperations += "replaceRegions"
            return mutableRules.value.firstOrNull { it.ruleId == ruleId }
        }

        override suspend fun upsertRegion(region: GeofenceRegion): GeofenceRegion {
            ruleWriteOperations += "upsertRegion"
            return region
        }

        override suspend fun deleteRegion(regionId: String) {
            ruleWriteOperations += "deleteRegion"
        }

        override suspend fun listAllConfigBindings(): List<ConfigGeofenceBinding> = mutableBindings.value

        override suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBinding> =
            mutableBindings.value.filter { it.configId == configId }

        override suspend fun upsertConfigBinding(binding: ConfigGeofenceBinding): ConfigGeofenceBinding {
            val normalized = binding.copy(createdAt = 0L, updatedAt = 0L)
            upsertedBindings += normalized
            mutableBindings.value = mutableBindings.value
                .filterNot { it.configId == binding.configId && it.ruleId == binding.ruleId } + normalized
            return normalized
        }

        override suspend fun deleteConfigBinding(configId: String, ruleId: String) {
            deletedBindings += configId to ruleId
            mutableBindings.value = mutableBindings.value
                .filterNot { it.configId == configId && it.ruleId == ruleId }
        }

        override suspend fun recordExecution(record: GeofenceExecutionRecord): GeofenceExecutionRecord = record

        override suspend fun listRecentExecutionRecords(ruleId: String, limit: Int): List<GeofenceExecutionRecord> =
            emptyList()

        override suspend fun latestExecutionRecord(ruleId: String): GeofenceExecutionRecord? = null
    }

    private class FakeGeofenceRuntimeReconciliationPort(
        private val failOnReconcile: Boolean = false,
    ) : GeofenceRuntimeReconciliationPort {
        var reconcileNowCalls: Int = 0

        override fun reconcileAsync(scope: CoroutineScope) = Unit

        override suspend fun reconcileNow(): GeofenceRegistrationSummary {
            reconcileNowCalls += 1
            if (failOnReconcile) {
                error("late reconciliation failure")
            }
            return GeofenceRegistrationSummary(
                status = com.elymbot.android.feature.geofence.domain.runtime.GeofenceRegistrationStatus.NO_ACTIVE_REGIONS,
            )
        }
    }
}

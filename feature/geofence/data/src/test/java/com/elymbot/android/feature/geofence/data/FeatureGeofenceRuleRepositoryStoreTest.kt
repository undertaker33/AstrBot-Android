package com.elymbot.android.feature.geofence.data

import com.elymbot.android.data.db.geofence.ConfigGeofenceBindingEntity
import com.elymbot.android.data.db.geofence.GeofenceExecutionRecordEntity
import com.elymbot.android.data.db.geofence.GeofenceRegionEntity
import com.elymbot.android.data.db.geofence.GeofenceRuleAggregate
import com.elymbot.android.data.db.geofence.GeofenceRuleDao
import com.elymbot.android.data.db.geofence.GeofenceRuleEntity
import com.elymbot.android.feature.geofence.domain.model.ConfigGeofenceBinding
import com.elymbot.android.feature.geofence.domain.model.GeofenceActionType
import com.elymbot.android.feature.geofence.domain.model.GeofenceExecutionRecord
import com.elymbot.android.feature.geofence.domain.model.GeofenceRegion
import com.elymbot.android.feature.geofence.domain.model.GeofenceRule
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeatureGeofenceRuleRepositoryStoreTest {
    @Test
    fun create_update_and_delete_rule() = runBlocking {
        val dao = InMemoryGeofenceRuleDao()
        val store = FeatureGeofenceRuleRepositoryStore(dao)
        val rule = validRule(name = "Office")

        store.createRule(rule, regions = listOf(validRegion(sortIndex = 0)))
        // skipcq: KT-W1042
        dao.configIds += "config-1"
        store.upsertConfigBinding(ConfigGeofenceBinding(configId = "config-1", ruleId = rule.ruleId, enabled = true))
        store.updateRule(rule.copy(name = "Office updated"))
        store.deleteRule(rule.ruleId)

        assertNull(store.getRule(rule.ruleId))
        assertEquals(emptyList<GeofenceRegionEntity>(), dao.regionEntities.values.toList())
        assertEquals(emptyList<ConfigGeofenceBindingEntity>(), dao.bindingEntities)
    }

    @Test
    fun returns_regions_with_stable_sort_index_order() = runBlocking {
        val store = FeatureGeofenceRuleRepositoryStore(InMemoryGeofenceRuleDao())
        val rule = validRule()
        store.createRule(
            rule,
            regions = listOf(
                validRegion(regionId = "region-2", sortIndex = 20),
                // skipcq: KT-W1042
                validRegion(regionId = "region-1", sortIndex = 10),
            ),
        )

        val regions = store.getRule(rule.ruleId)?.regions.orEmpty()

        assertEquals(listOf("region-1", "region-2"), regions.map { it.regionId })
    }

    @Test
    fun config_binding_requires_existing_rule_and_config() = runBlocking {
        val store = FeatureGeofenceRuleRepositoryStore(InMemoryGeofenceRuleDao())

        val result = runCatching {
            store.upsertConfigBinding(
                ConfigGeofenceBinding(
                    configId = "config-1",
                    // skipcq: KT-W1042
                    ruleId = "rule-1",
                    enabled = true,
                    sortIndex = 5,
                ),
            )
        }

        assertEquals(true, result.exceptionOrNull() is GeofenceReferenceException)
    }

    @Test
    fun config_binding_persists_existing_references() = runBlocking {
        val dao = InMemoryGeofenceRuleDao()
        val store = FeatureGeofenceRuleRepositoryStore(dao)
        dao.configIds += "config-1"
        store.createRule(validRule(), regions = listOf(validRegion()))

        store.upsertConfigBinding(
            ConfigGeofenceBinding(
                configId = "config-1",
                ruleId = "rule-1",
                enabled = true,
                sortIndex = 5,
            ),
        )

        val entity = dao.bindingEntities.single()
        assertEquals("config-1", entity.configId)
        assertEquals("rule-1", entity.ruleId)
        assertEquals(true, entity.enabled)
        assertEquals(5, entity.sortIndex)
    }

    @Test
    fun list_all_config_bindings_reads_current_dao_state_without_waiting_for_observed_flow() = runBlocking {
        val dao = InMemoryGeofenceRuleDao()
        val store = FeatureGeofenceRuleRepositoryStore(dao)
        dao.bindingEntities += ConfigGeofenceBindingEntity(
            configId = "config-1",
            ruleId = "rule-1",
            enabled = true,
            sortIndex = 5,
            createdAt = 10L,
            updatedAt = 20L,
        )

        val bindings = store.listAllConfigBindings()

        assertEquals(listOf("rule-1"), bindings.map { it.ruleId })
        assertEquals(emptyList<ConfigGeofenceBinding>(), store.bindings.value)
    }

    @Test
    fun execution_records_are_queryable_by_recent_rule_history() = runBlocking {
        val dao = InMemoryGeofenceRuleDao()
        val store = FeatureGeofenceRuleRepositoryStore(dao)
        dao.configIds += "config-1"
        store.createRule(validRule(), regions = listOf(validRegion()))
        store.upsertConfigBinding(ConfigGeofenceBinding(configId = "config-1", ruleId = "rule-1", enabled = true))
        store.recordExecution(
            validExecution(executionId = "old", startedAt = 10L),
        )
        store.recordExecution(
            validExecution(executionId = "new", startedAt = 20L),
        )

        val records = store.listRecentExecutionRecords(ruleId = "rule-1", limit = 1)

        assertEquals(listOf("new"), records.map { it.executionId })
    }

    @Test
    fun failed_stale_trigger_execution_records_do_not_require_live_references() = runBlocking {
        val store = FeatureGeofenceRuleRepositoryStore(InMemoryGeofenceRuleDao())
        val staleRecord = GeofenceExecutionRecord(
            executionId = "stale-trigger",
            ruleId = "missing-rule",
            regionId = "missing-region",
            configId = "geofence-audit",
            transition = GeofenceTransition.ENTER,
            startedAt = 100L,
            completedAt = 100L,
            status = "failed",
            errorCode = "missing_rule",
        )

        store.recordExecution(staleRecord)

        val records = store.listRecentExecutionRecords(ruleId = "missing-rule", limit = 1)
        assertEquals(listOf("stale-trigger"), records.map { it.executionId })
    }

    private fun validRule(name: String = "Office reminder"): GeofenceRule =
        GeofenceRule(
            ruleId = "rule-1",
            name = name,
            description = "Trigger at office",
            triggerEnter = true,
            actionType = GeofenceActionType.AGENT_PROMPT,
            actionPrompt = "Remind me",
        )

    private fun validRegion(
        regionId: String = "region-1",
        sortIndex: Int = 0,
    ): GeofenceRegion =
        GeofenceRegion(
            regionId = regionId,
            ruleId = "rule-1",
            label = regionId,
            latitude = 31.2304,
            longitude = 121.4737,
            radiusMeters = 100f,
            sortIndex = sortIndex,
        )

    private fun validExecution(
        executionId: String,
        startedAt: Long,
    ): GeofenceExecutionRecord =
        GeofenceExecutionRecord(
            executionId = executionId,
            ruleId = "rule-1",
            regionId = "region-1",
            configId = "config-1",
            transition = GeofenceTransition.ENTER,
            startedAt = startedAt,
        )
}

private class InMemoryGeofenceRuleDao : GeofenceRuleDao {
    private val ruleEntities = linkedMapOf<String, GeofenceRuleEntity>()
    val regionEntities = linkedMapOf<String, GeofenceRegionEntity>()
    val bindingEntities = mutableListOf<ConfigGeofenceBindingEntity>()
    val configIds = mutableSetOf<String>()
    private val executionEntities = linkedMapOf<String, GeofenceExecutionRecordEntity>()
    private val observedRules = MutableStateFlow<List<GeofenceRuleAggregate>>(emptyList())
    private val observedBindings = MutableStateFlow<List<ConfigGeofenceBindingEntity>>(emptyList())

    override fun observeRulesWithRegions(): Flow<List<GeofenceRuleAggregate>> = observedRules

    override suspend fun listRulesWithRegions(): List<GeofenceRuleAggregate> =
        aggregates()

    override suspend fun getRuleWithRegions(ruleId: String): GeofenceRuleAggregate? =
        aggregates().firstOrNull { it.rule.ruleId == ruleId }

    override suspend fun upsertRule(entity: GeofenceRuleEntity) {
        ruleEntities[entity.ruleId] = entity
        refreshRules()
    }

    override suspend fun deleteRule(ruleId: String) {
        deleteRegionsForRule(ruleId)
        deleteConfigBindingsForRule(ruleId)
        deleteRuleEntity(ruleId)
    }

    override suspend fun deleteRuleEntity(ruleId: String) {
        ruleEntities.remove(ruleId)
        refreshRules()
    }

    override suspend fun replaceRegionsForRule(ruleId: String, regions: List<GeofenceRegionEntity>) {
        regionEntities.values.removeAll { it.ruleId == ruleId }
        regions.forEach { entity -> regionEntities[entity.regionId] = entity }
        refreshRules()
    }

    override suspend fun upsertRegion(entity: GeofenceRegionEntity) {
        regionEntities[entity.regionId] = entity
        refreshRules()
    }

    override suspend fun upsertRegions(entities: List<GeofenceRegionEntity>) {
        entities.forEach { entity -> regionEntities[entity.regionId] = entity }
        refreshRules()
    }

    override suspend fun deleteRegionsForRule(ruleId: String) {
        regionEntities.values.removeAll { it.ruleId == ruleId }
        refreshRules()
    }

    override suspend fun deleteRegion(regionId: String) {
        regionEntities.remove(regionId)
        refreshRules()
    }

    override suspend fun ruleExists(ruleId: String): Boolean =
        ruleEntities.containsKey(ruleId)

    override suspend fun regionBelongsToRule(ruleId: String, regionId: String): Boolean =
        regionEntities[regionId]?.ruleId == ruleId

    override suspend fun configExists(configId: String): Boolean =
        configIds.contains(configId)

    override fun observeConfigBindings(): Flow<List<ConfigGeofenceBindingEntity>> = observedBindings

    override suspend fun listAllConfigBindings(): List<ConfigGeofenceBindingEntity> =
        bindingEntities.sortedWith(compareBy({ it.configId }, { it.sortIndex }, { it.ruleId }))

    override suspend fun listConfigBindings(configId: String): List<ConfigGeofenceBindingEntity> =
        bindingEntities.filter { it.configId == configId }.sortedBy { it.sortIndex }

    override suspend fun upsertConfigBinding(entity: ConfigGeofenceBindingEntity) {
        bindingEntities.removeAll { it.configId == entity.configId && it.ruleId == entity.ruleId }
        bindingEntities += entity
        observedBindings.value = bindingEntities.sortedWith(compareBy({ it.configId }, { it.sortIndex }, { it.ruleId }))
    }

    override suspend fun deleteConfigBinding(configId: String, ruleId: String) {
        bindingEntities.removeAll { it.configId == configId && it.ruleId == ruleId }
        observedBindings.value = bindingEntities.toList()
    }

    override suspend fun deleteConfigBindingsForRule(ruleId: String) {
        bindingEntities.removeAll { it.ruleId == ruleId }
        observedBindings.value = bindingEntities.toList()
    }

    override suspend fun configBindingExists(configId: String, ruleId: String): Boolean =
        bindingEntities.any { it.configId == configId && it.ruleId == ruleId }

    override suspend fun insertExecutionRecord(entity: GeofenceExecutionRecordEntity) {
        executionEntities[entity.executionId] = entity
    }

    override suspend fun listRecentExecutionRecordsForRule(
        ruleId: String,
        limit: Int,
    ): List<GeofenceExecutionRecordEntity> =
        executionEntities.values
            .filter { it.ruleId == ruleId }
            .sortedByDescending { it.startedAt }
            .take(limit)

    override suspend fun latestExecutionRecordForRule(ruleId: String): GeofenceExecutionRecordEntity? =
        listRecentExecutionRecordsForRule(ruleId, 1).firstOrNull()

    private fun refreshRules() {
        observedRules.value = aggregates()
    }

    private fun aggregates(): List<GeofenceRuleAggregate> =
        ruleEntities.values
            .map { rule ->
                GeofenceRuleAggregate(
                    rule = rule,
                    regions = regionEntities.values.filter { it.ruleId == rule.ruleId },
                )
            }
            .sortedByDescending { it.rule.updatedAt }
}

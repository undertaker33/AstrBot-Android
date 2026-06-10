package com.elymbot.android.feature.geofence.runtime

import com.elymbot.android.core.common.logging.RuntimeLogger
import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceTransitionWorkerTest {
    @Test
    fun request_id_codec_round_trips_rule_and_region_ids() {
        val requestId = GeofenceRequestIdCodec.encode(ruleId = "rule:one", regionId = "region/1")

        val decoded = GeofenceRequestIdCodec.decode(requestId)

        assertEquals("rule:one", decoded?.ruleId)
        assertEquals("region/1", decoded?.regionId)
    }

    @Test
    fun processor_writes_execution_start_and_complete_records() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("rule-1", "region-1")
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
        )
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = sequenceClock(100L, 150L),
            executionIdFactory = { "execution-1" },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.completedCount)
        assertEquals(listOf("started", "complete"), repository.records.map { it.status })
        assertEquals(150L, repository.records.last().completedAt)
    }

    @Test
    fun processor_uses_current_bindings_instead_of_stale_flow_snapshot() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("rule-1", "region-1")
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding()),
            observedBindings = emptyList(),
        )
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = sequenceClock(100L, 150L),
            executionIdFactory = { "execution-1" },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.completedCount)
        assertEquals(listOf("started", "complete"), repository.records.map { it.status })
    }

    @Test
    fun processor_records_throttled_when_minimum_interval_has_not_elapsed() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("rule-1", "region-1")
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(
                rule(
                    regions = listOf(region()),
                    minimumTriggerIntervalMillis = 500L,
                ),
            ),
            bindings = listOf(binding()),
        )
        repository.recordExecution(
            geofenceExecutionRecord(
                executionId = "previous",
                startedAt = 100L,
                completedAt = 110L,
                status = "complete",
            ),
        )
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = sequenceClock(200L, 220L),
            executionIdFactory = { "execution-2" },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 200L,
        )

        assertEquals(1, summary.throttledCount)
        assertEquals("throttled", repository.records.last().status)
    }

    @Test
    fun processor_writes_structured_log_for_invalid_request_id() = runBlocking {
        val repository = FakeGeofenceRuleRepository()
        val runtimeLogger = RecordingRuntimeLogger()
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = sequenceClock(100L),
            executionIdFactory = { "execution-invalid" },
            runtimeLogger = runtimeLogger,
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf("not-a-geofence-id"),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.failedCount)
        assertEquals(emptyList<String>(), repository.records.map { it.executionId })
        assertTrue(
            runtimeLogger.messages.any { message ->
                message.contains("audit=geofence_transition") &&
                    message.contains("reason=invalid_request_id")
            },
        )
    }

    @Test
    fun processor_records_failed_audit_when_rule_is_missing() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("missing-rule", "region-1")
        val repository = FakeGeofenceRuleRepository()
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = sequenceClock(100L),
            executionIdFactory = { "execution-missing-rule" },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.failedCount)
        val record = repository.records.single()
        assertEquals("execution-missing-rule", record.executionId)
        assertEquals("missing-rule", record.ruleId)
        assertEquals("region-1", record.regionId)
        assertEquals("failed", record.status)
        assertEquals("missing_rule", record.errorCode)
    }

    @Test
    fun processor_records_failed_audit_when_region_is_missing() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("rule-1", "missing-region")
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = emptyList())),
            bindings = listOf(binding()),
        )
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = sequenceClock(100L),
            executionIdFactory = { "execution-missing-region" },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.failedCount)
        val record = repository.records.single()
        assertEquals("rule-1", record.ruleId)
        assertEquals("missing-region", record.regionId)
        assertEquals("failed", record.status)
        assertEquals("missing_region", record.errorCode)
    }

    @Test
    fun processor_records_failed_audit_when_enabled_binding_is_missing() = runBlocking {
        val requestId = GeofenceRequestIdCodec.encode("rule-1", "region-1")
        val repository = FakeGeofenceRuleRepository(
            rules = listOf(rule(regions = listOf(region()))),
            bindings = listOf(binding(enabled = false)),
        )
        val processor = GeofenceTransitionProcessor(
            repository = repository,
            clock = sequenceClock(100L),
            executionIdFactory = { "execution-missing-binding" },
        )

        val summary = processor.processTransition(
            transition = GeofenceTransition.ENTER,
            geofenceRequestIds = listOf(requestId),
            occurredAtMillis = 90L,
        )

        assertEquals(1, summary.failedCount)
        val record = repository.records.single()
        assertEquals("config-1", record.configId)
        assertEquals("failed", record.status)
        assertEquals("missing_binding", record.errorCode)
    }
}

private class RecordingRuntimeLogger : RuntimeLogger {
    val messages = mutableListOf<String>()

    override fun append(message: String) {
        messages += message
    }
}

private fun sequenceClock(vararg values: Long): () -> Long {
    var index = 0
    return {
        val value = values.getOrElse(index) { values.last() }
        index += 1
        value
    }
}

package com.elymbot.android.feature.geofence.runtime

import com.elymbot.android.feature.geofence.domain.model.GeofenceTransition
import com.elymbot.android.feature.geofence.domain.runtime.GeofenceTransitionEnqueuePort
import com.google.android.gms.location.Geofence
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeofenceTransitionReceiverTest {
    @Test
    fun receiver_enqueue_worker_preserves_transition_and_geofence_ids() {
        val port = RecordingTransitionEnqueuePort()

        val accepted = GeofenceTransitionReceiver.enqueueParsedTransition(
            transitionType = Geofence.GEOFENCE_TRANSITION_DWELL,
            requestIds = listOf("rule.region"),
            enqueuePort = port,
            occurredAtMillis = 200L,
        )

        assertEquals(true, accepted)
        assertEquals(GeofenceTransition.DWELL, port.transition)
        assertEquals(listOf("rule.region"), port.requestIds)
        assertEquals(200L, port.occurredAtMillis)
    }

    @Test
    fun receiver_source_does_not_directly_execute_llm_network_or_database_work() {
        val source = listOf(
            Path.of("src/main/java/com/elymbot/android/feature/geofence/runtime/GeofenceTransitionReceiver.kt"),
            Path.of("feature/geofence/runtime/src/main/java/com/elymbot/android/feature/geofence/runtime/GeofenceTransitionReceiver.kt"),
        ).first { it.exists() }.readText()
        val forbidden = listOf("RuntimeLlm", "LlmClient", "OkHttp", "Room", "Dao", "RepositoryPort")
            .filter(source::contains)

        assertTrue("Receiver must only parse and enqueue transition work: $forbidden", forbidden.isEmpty())
    }
}

private class RecordingTransitionEnqueuePort : GeofenceTransitionEnqueuePort {
    var transition: GeofenceTransition? = null
    var requestIds: List<String> = emptyList()
    var occurredAtMillis: Long = 0L

    override fun enqueueTransition(
        transition: GeofenceTransition,
        geofenceRequestIds: List<String>,
        occurredAtMillis: Long,
    ) {
        this.transition = transition
        requestIds = geofenceRequestIds
        this.occurredAtMillis = occurredAtMillis
    }
}

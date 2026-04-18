package com.astrbot.android.feature.resource.presentation

import com.astrbot.android.feature.resource.domain.ResourceCenterPort
import com.astrbot.android.feature.resource.domain.ResourceCompatibilityUseCase
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class ResourceCenterPresentationControllerTest {

    @Test
    fun compatibilitySnapshotForConfig_comes_from_compatibility_use_case_path() {
        val expected = ResourceCenterCompatibilitySnapshot(
            resources = listOf(ResourceCenterItem(resourceId = "mcp-1", kind = ResourceCenterKind.MCP_SERVER)),
            projections = listOf(ConfigResourceProjection(configId = "cfg", resourceId = "mcp-1")),
        )
        val port = FakeResourceCenterPort(
            snapshot = expected,
        )
        val controller = ResourceCenterPresentationController(
            port = port,
            compatibilityUseCase = ResourceCompatibilityUseCase(port),
        )

        val snapshot = controller.compatibilitySnapshotForConfig(ConfigProfile(id = "cfg"))

        assertEquals(expected, snapshot)
        assertEquals(1, port.compatibilityRequests.size)
    }

    private class FakeResourceCenterPort(
        private val snapshot: ResourceCenterCompatibilitySnapshot,
    ) : ResourceCenterPort {
        override val resources: StateFlow<List<ResourceCenterItem>> = MutableStateFlow(emptyList())
        override val projections: StateFlow<List<ConfigResourceProjection>> = MutableStateFlow(emptyList())
        val compatibilityRequests = mutableListOf<String>()

        override fun listResources(kind: ResourceCenterKind?): List<ResourceCenterItem> = emptyList()

        override fun saveResource(resource: ResourceCenterItem): ResourceCenterItem = resource

        override fun deleteResource(resourceId: String) = Unit

        override fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection = projection

        override fun projectionsForConfig(configId: String): List<ConfigResourceProjection> = emptyList()

        override fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot {
            compatibilityRequests += profile.id
            return snapshot
        }
    }
}

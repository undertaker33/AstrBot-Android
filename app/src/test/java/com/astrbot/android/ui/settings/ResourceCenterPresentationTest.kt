package com.astrbot.android.ui.settings

import com.astrbot.android.feature.resource.domain.ResourceCenterPort
import com.astrbot.android.feature.resource.domain.ResourceCompatibilityUseCase
import com.astrbot.android.feature.resource.presentation.ResourceCenterPresentationController
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillResourceKind
import com.astrbot.android.R
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResourceCenterPresentationTest {

    @Test
    fun `me entries include resource center without removing existing entries`() {
        val entries = buildMeEntryKinds()

        assertEquals(
            listOf(
                MeEntryKind.QqAccount,
                MeEntryKind.Settings,
                MeEntryKind.CronJobs,
                MeEntryKind.ResourceCenter,
                MeEntryKind.Logs,
                MeEntryKind.Assets,
                MeEntryKind.Backup,
            ),
            entries,
        )
    }

    @Test
    fun `resource center exposes mcp skill and tool entries`() {
        val presentation = buildResourceCenterPresentation(resourceCenterController())

        assertEquals(listOf(ResourceKind.MCP, ResourceKind.SKILL, ResourceKind.TOOL), presentation.entries.map { it.kind })
    }

    @Test
    fun `resource list page shows three cards and next state`() {
        val presentation = buildResourceListPresentation(
            kind = ResourceKind.MCP,
            resources = sampleResources(ResourceKind.MCP, count = 7),
            requestedPage = 1,
        )

        assertEquals(1, presentation.currentPage)
        assertEquals(3, presentation.totalPages)
        assertEquals(listOf("mcp-1", "mcp-2", "mcp-3"), presentation.visibleCards.map { it.id })
        assertFalse(presentation.canGoPrevious)
        assertTrue(presentation.canGoNext)
    }

    @Test
    fun `resource list page coerces page jump into available range`() {
        val presentation = buildResourceListPresentation(
            kind = ResourceKind.SKILL,
            resources = sampleResources(ResourceKind.SKILL, count = 4),
            requestedPage = 99,
        )

        assertEquals(2, presentation.currentPage)
        assertEquals(2, presentation.totalPages)
        assertEquals(listOf("skill-4"), presentation.visibleCards.map { it.id })
        assertTrue(presentation.canGoPrevious)
        assertFalse(presentation.canGoNext)
    }

    @Test
    fun `resource cards map repository resources into matching tertiary pages`() {
        val resources = listOf(
            ResourceCenterItem(
                resourceId = "prompt-1",
                kind = ResourceCenterKind.SKILL,
                skillKind = SkillResourceKind.PROMPT,
                name = "Prompt Skill",
                description = "Prompt detail",
                content = "Use concise tone.",
                enabled = true,
                source = "local",
            ),
            ResourceCenterItem(
                resourceId = "tool-skill-1",
                kind = ResourceCenterKind.SKILL,
                skillKind = SkillResourceKind.TOOL,
                name = "Tool Skill",
                description = "Tool detail",
                payloadJson = """{"resultTemplate":"weather={{weather}}"}""",
                enabled = false,
                source = "local",
            ),
        )

        val skillCards = buildResourceCards(ResourceKind.SKILL, resources, resourceCenterController(resources))

        assertEquals(listOf("prompt-1", "tool-skill-1"), skillCards.map { it.id })
        assertEquals(R.string.resource_status_enabled, skillCards.first().statusLabelRes)
        assertTrue(skillCards.first().detail.contains("Prompt detail"))
        assertEquals(R.string.resource_status_disabled, skillCards.last().statusLabelRes)
        assertEquals("weather={{weather}}", skillCards.last().detail)
    }

    @Test
    fun `tool page shows supported host tools instead of repository tool resources`() {
        val tools = buildSupportedHostToolCards()

        assertEquals(
            listOf(
                HostToolKind.WEB_SEARCH,
                HostToolKind.CREATE_FUTURE_TASK,
                HostToolKind.DELETE_FUTURE_TASK,
                HostToolKind.LIST_FUTURE_TASKS,
                HostToolKind.COMPRESS_CONTEXT,
                HostToolKind.SEND_MESSAGE,
                HostToolKind.SEND_NOTIFICATION,
                HostToolKind.OPEN_HOST_PAGE,
            ),
            tools.map { it.kind },
        )
    }

    @Test
    fun `tool page paginates host tools with five cards per page`() {
        val presentation = buildHostToolPagerPresentation()

        assertEquals(2, presentation.totalPages)
        assertEquals(5, presentation.pages.first().size)
        assertEquals(3, presentation.pages.last().size)
        assertEquals(
            listOf(
                HostToolKind.WEB_SEARCH,
                HostToolKind.CREATE_FUTURE_TASK,
                HostToolKind.DELETE_FUTURE_TASK,
                HostToolKind.LIST_FUTURE_TASKS,
                HostToolKind.COMPRESS_CONTEXT,
            ),
            presentation.pages.first().map { it.kind },
        )
    }

    private fun sampleResources(kind: ResourceKind, count: Int): List<ResourceCardPresentation> {
        return (1..count).map { index ->
            ResourceCardPresentation(
                id = "${kind.routeSegment}-$index",
                title = "${kind.routeSegment} $index",
                subtitle = "subtitle $index",
                detail = "detail $index",
                statusLabelRes = R.string.resource_status_local,
            )
        }
    }

    private fun resourceCenterController(
        initialResources: List<ResourceCenterItem> = emptyList(),
        initialProjections: List<ConfigResourceProjection> = emptyList(),
    ): ResourceCenterPresentationController {
        val port = object : ResourceCenterPort {
            override val resources = MutableStateFlow(initialResources)
            override val projections = MutableStateFlow(initialProjections)

            override fun listResources(kind: ResourceCenterKind?): List<ResourceCenterItem> {
                return resources.value.filter { resource -> kind == null || resource.kind == kind }
            }

            override fun saveResource(resource: ResourceCenterItem): ResourceCenterItem = resource

            override fun deleteResource(resourceId: String) = Unit

            override fun setProjection(projection: ConfigResourceProjection): ConfigResourceProjection = projection

            override fun projectionsForConfig(configId: String): List<ConfigResourceProjection> {
                return projections.value.filter { projection -> projection.configId == configId }
            }

            override fun compatibilitySnapshotForConfig(profile: ConfigProfile): ResourceCenterCompatibilitySnapshot {
                return ResourceCenterCompatibilitySnapshot(
                    resources = listResources(),
                    projections = projectionsForConfig(profile.id),
                )
            }
        }
        return ResourceCenterPresentationController(
            port = port,
            compatibilityUseCase = ResourceCompatibilityUseCase(port),
        )
    }
}

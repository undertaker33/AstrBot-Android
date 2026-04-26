package com.astrbot.android.ui.settings

import androidx.annotation.StringRes
import com.astrbot.android.R
import com.astrbot.android.feature.resource.presentation.ResourceCenterPresentationController
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ConfigResourceProjection
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import com.astrbot.android.model.ResourceCenterItem
import com.astrbot.android.model.ResourceCenterKind
import com.astrbot.android.model.SkillResourceKind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.json.JSONObject

enum class MeEntryKind {
    QqAccount,
    Settings,
    CronJobs,
    ResourceCenter,
    Logs,
    Assets,
    Backup,
}

enum class ResourceKind(val routeSegment: String) {
    MCP("mcp"),
    SKILL("skill"),
    TOOL("tool");

    companion object {
        fun fromRouteSegment(routeSegment: String): ResourceKind {
            return entries.firstOrNull { it.routeSegment == routeSegment } ?: MCP
        }
    }
}

data class ResourceCenterEntryPresentation(
    val kind: ResourceKind,
    val resourceCount: Int,
)

data class ResourceCenterPresentation(
    val entries: List<ResourceCenterEntryPresentation>,
)

data class ResourceCardPresentation(
    val id: String,
    val title: String,
    val subtitle: String,
    val detail: String,
    @StringRes val statusLabelRes: Int,
)

data class ResourceListPresentation(
    val kind: ResourceKind,
    val currentPage: Int,
    val totalPages: Int,
    val visibleCards: List<ResourceCardPresentation>,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
)

data class CronJobListItemPresentation(
    val jobId: String,
    val name: String,
    val cronExpression: String,
    val conversationId: String,
    val nextRunTime: Long,
    val lastRunAt: Long,
    val description: String,
    val enabled: Boolean,
    val runOnce: Boolean,
    val status: String,
)

data class CronJobsPagePresentation(
    val currentPage: Int,
    val totalPages: Int,
    val visibleJobs: List<CronJobListItemPresentation>,
    val canGoPrevious: Boolean,
    val canGoNext: Boolean,
)

data class CronJobRunPresentation(
    val executionId: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long,
    val attempt: Int,
    val trigger: String,
    val summary: String,
)

data class RemoteMcpServerDraft(
    val name: String = "",
    val description: String = "",
    val serverUrl: String = "",
    val timeoutSeconds: String = "30",
    val active: Boolean = true,
) {
    fun toResourceItem(): ResourceCenterItem {
        val trimmedName = name.trim()
        val trimmedUrl = serverUrl.trim()
        val normalizedTimeout = timeoutSeconds.trim().toIntOrNull()?.coerceAtLeast(1) ?: 30
        return ResourceCenterItem(
            resourceId = buildResourceId("mcp", trimmedName),
            kind = ResourceCenterKind.MCP_SERVER,
            name = trimmedName,
            description = description.trim(),
            content = "",
            payloadJson = JSONObject().apply {
                put("url", trimmedUrl)
                put("transport", "streamable_http")
                put("timeoutSeconds", normalizedTimeout)
            }.toString(),
            source = "local",
            enabled = active,
        )
    }
}

data class SkillResourceDraft(
    val name: String = "",
    val description: String = "",
    val content: String = "",
    val skillKind: SkillResourceKind = SkillResourceKind.PROMPT,
    val active: Boolean = true,
) {
    fun toResourceItem(): ResourceCenterItem {
        val trimmedName = name.trim()
        val trimmedContent = content.trim()
        return ResourceCenterItem(
            resourceId = buildResourceId("skill", trimmedName),
            kind = ResourceCenterKind.SKILL,
            skillKind = skillKind,
            name = trimmedName,
            description = description.trim(),
            content = if (skillKind == SkillResourceKind.PROMPT) trimmedContent else "",
            payloadJson = JSONObject().apply {
                put("skill_kind", skillKind.name.lowercase(Locale.US))
                put("active", active)
                if (skillKind == SkillResourceKind.TOOL) {
                    put("resultTemplate", trimmedContent)
                    put("inputSchema", JSONObject().put("type", "object"))
                }
            }.toString(),
            source = "local",
            enabled = active,
        )
    }
}

enum class HostToolKind(
    val toolId: String,
    @StringRes val titleRes: Int,
    @StringRes val descriptionRes: Int,
) {
    WEB_SEARCH(
        toolId = "web_search",
        titleRes = R.string.host_tool_web_search_title,
        descriptionRes = R.string.host_tool_web_search_description,
    ),
    CREATE_FUTURE_TASK(
        toolId = "create_future_task",
        titleRes = R.string.host_tool_create_future_task_title,
        descriptionRes = R.string.host_tool_create_future_task_description,
    ),
    DELETE_FUTURE_TASK(
        toolId = "delete_future_task",
        titleRes = R.string.host_tool_delete_future_task_title,
        descriptionRes = R.string.host_tool_delete_future_task_description,
    ),
    LIST_FUTURE_TASKS(
        toolId = "list_future_tasks",
        titleRes = R.string.host_tool_list_future_tasks_title,
        descriptionRes = R.string.host_tool_list_future_tasks_description,
    ),
    COMPRESS_CONTEXT(
        toolId = "compress_context",
        titleRes = R.string.host_tool_compress_context_title,
        descriptionRes = R.string.host_tool_compress_context_description,
    ),
    SEND_MESSAGE(
        toolId = "send_message",
        titleRes = R.string.host_tool_send_message_title,
        descriptionRes = R.string.host_tool_send_message_description,
    ),
    SEND_NOTIFICATION(
        toolId = "send_notification",
        titleRes = R.string.host_tool_send_notification_title,
        descriptionRes = R.string.host_tool_send_notification_description,
    ),
    OPEN_HOST_PAGE(
        toolId = "open_host_page",
        titleRes = R.string.host_tool_open_host_page_title,
        descriptionRes = R.string.host_tool_open_host_page_description,
    ),
}

data class HostToolCardPresentation(
    val kind: HostToolKind,
)

data class HostToolPagerPresentation(
    val cards: List<HostToolCardPresentation>,
    val pages: List<List<HostToolCardPresentation>>,
    val totalPages: Int,
)

internal fun buildMeEntryKinds(): List<MeEntryKind> {
    return listOf(
        MeEntryKind.QqAccount,
        MeEntryKind.Settings,
        MeEntryKind.CronJobs,
        MeEntryKind.ResourceCenter,
        MeEntryKind.Logs,
        MeEntryKind.Assets,
        MeEntryKind.Backup,
    )
}

internal fun buildResourceCenterPresentation(
    controller: ResourceCenterPresentationController,
): ResourceCenterPresentation {
    val resources = controller.listResources()
    return ResourceCenterPresentation(
        entries = listOf(
            ResourceCenterEntryPresentation(
                kind = ResourceKind.MCP,
                resourceCount = resources.count { ResourceKind.MCP.matches(it) },
            ),
            ResourceCenterEntryPresentation(
                kind = ResourceKind.SKILL,
                resourceCount = resources.count { ResourceKind.SKILL.matches(it) },
            ),
            ResourceCenterEntryPresentation(
                kind = ResourceKind.TOOL,
                resourceCount = resources.count { ResourceKind.TOOL.matches(it) },
            ),
        ),
    )
}

internal fun buildSupportedHostToolCards(): List<HostToolCardPresentation> {
    return listOf(
        HostToolCardPresentation(HostToolKind.WEB_SEARCH),
        HostToolCardPresentation(HostToolKind.CREATE_FUTURE_TASK),
        HostToolCardPresentation(HostToolKind.DELETE_FUTURE_TASK),
        HostToolCardPresentation(HostToolKind.LIST_FUTURE_TASKS),
        HostToolCardPresentation(HostToolKind.COMPRESS_CONTEXT),
        HostToolCardPresentation(HostToolKind.SEND_MESSAGE),
        HostToolCardPresentation(HostToolKind.SEND_NOTIFICATION),
        HostToolCardPresentation(HostToolKind.OPEN_HOST_PAGE),
    )
}

internal fun buildHostToolPagerPresentation(
    cards: List<HostToolCardPresentation> = buildSupportedHostToolCards(),
    pageSize: Int = 5,
): HostToolPagerPresentation {
    require(pageSize > 0) { "pageSize must be greater than 0." }
    val pages = cards.chunked(pageSize).ifEmpty { listOf(emptyList()) }
    return HostToolPagerPresentation(
        cards = cards,
        pages = pages,
        totalPages = pages.size,
    )
}

internal fun buildResourceListPresentation(
    kind: ResourceKind,
    resources: List<ResourceCardPresentation>,
    requestedPage: Int,
    pageSize: Int = 3,
): ResourceListPresentation {
    require(pageSize > 0) { "pageSize must be greater than 0." }
    val totalPages = maxOf(1, (resources.size + pageSize - 1) / pageSize)
    val currentPage = requestedPage.coerceIn(1, totalPages)
    val startIndex = (currentPage - 1) * pageSize
    return ResourceListPresentation(
        kind = kind,
        currentPage = currentPage,
        totalPages = totalPages,
        visibleCards = resources.drop(startIndex).take(pageSize),
        canGoPrevious = currentPage > 1,
        canGoNext = currentPage < totalPages,
    )
}

internal fun buildResourceCards(
    kind: ResourceKind,
    resources: List<ResourceCenterItem>,
    controller: ResourceCenterPresentationController,
): List<ResourceCardPresentation> {
    val liveResources = controller.listResources()
    val sourceResources = if (liveResources.isNotEmpty()) liveResources else resources
    return sourceResources
        .filter { resource -> kind.matches(resource) }
        .sortedWith(compareBy<ResourceCenterItem> { it.name.lowercase() }.thenBy { it.resourceId })
        .map { resource ->
            ResourceCardPresentation(
                id = resource.resourceId,
                title = resource.name.ifBlank { resource.resourceId },
                subtitle = buildResourceSubtitle(resource),
                detail = buildResourceDetail(resource),
                statusLabelRes = if (resource.enabled) R.string.resource_status_enabled else R.string.resource_status_disabled,
            )
        }
}

internal fun sampleResourceCards(kind: ResourceKind): List<ResourceCardPresentation> {
    val prefix = kind.routeSegment
    return (1..7).map { index ->
        ResourceCardPresentation(
            id = "$prefix-$index",
            title = "${kind.name} Resource $index",
            subtitle = "Local ${kind.name.lowercase()} resource",
            detail = "Ready for repository-backed data wiring.",
            statusLabelRes = R.string.resource_status_local,
        )
    }
}

internal fun buildResourceCompatibilitySnapshotPresentation(
    profile: ConfigProfile,
    controller: ResourceCenterPresentationController,
) = controller.compatibilitySnapshotForConfig(profile)

internal fun buildCronJobsPresentation(
    jobs: List<CronJob>,
    requestedPage: Int,
    pageSize: Int = 2,
): CronJobsPagePresentation {
    require(pageSize > 0) { "pageSize must be greater than 0." }
    val totalPages = maxOf(1, (jobs.size + pageSize - 1) / pageSize)
    val currentPage = requestedPage.coerceIn(1, totalPages)
    val startIndex = (currentPage - 1) * pageSize
    val visibleJobs = jobs.drop(startIndex).take(pageSize).map { job ->
        CronJobListItemPresentation(
            jobId = job.jobId,
            name = job.name.ifBlank { job.jobId },
            cronExpression = job.cronExpression.ifBlank { "-" },
            conversationId = job.conversationId,
            nextRunTime = job.nextRunTime,
            lastRunAt = job.lastRunAt,
            description = job.description,
            enabled = job.enabled,
            runOnce = job.runOnce,
            status = job.status,
        )
    }
    return CronJobsPagePresentation(
        currentPage = currentPage,
        totalPages = totalPages,
        visibleJobs = visibleJobs,
        canGoPrevious = currentPage > 1,
        canGoNext = currentPage < totalPages,
    )
}

internal fun buildCronJobRunPresentations(
    records: List<CronJobExecutionRecord>,
): List<CronJobRunPresentation> {
    return records.map { record ->
        CronJobRunPresentation(
            executionId = record.executionId,
            status = record.status.ifBlank { "-" },
            startedAt = record.startedAt,
            completedAt = record.completedAt,
            attempt = record.attempt,
            trigger = record.trigger,
            summary = record.deliverySummary
                .ifBlank { record.errorMessage }
                .ifBlank { record.errorCode },
        )
    }
}

private fun ResourceKind.matches(resource: ResourceCenterItem): Boolean {
    return when (this) {
        ResourceKind.MCP -> resource.kind == ResourceCenterKind.MCP_SERVER
        ResourceKind.SKILL -> resource.kind == ResourceCenterKind.SKILL
        ResourceKind.TOOL -> resource.kind == ResourceCenterKind.TOOL ||
            (resource.kind == ResourceCenterKind.SKILL && resource.skillKind == SkillResourceKind.TOOL)
    }
}

private fun buildResourceSubtitle(resource: ResourceCenterItem): String {
    return buildList {
        when (resource.kind) {
            ResourceCenterKind.MCP_SERVER -> add("MCP")
            ResourceCenterKind.SKILL -> add(if (resource.skillKind == SkillResourceKind.TOOL) "Tool Skill" else "Prompt Skill")
            ResourceCenterKind.TOOL -> add("Tool")
        }
        resource.source.takeIf { it.isNotBlank() }?.let(::add)
    }.joinToString(" · ")
}

private fun buildResourceDetail(resource: ResourceCenterItem): String {
    if (resource.kind == ResourceCenterKind.SKILL && resource.skillKind == SkillResourceKind.TOOL) {
        val resultTemplate = runCatching {
            JSONObject(resource.payloadJson).optString("resultTemplate", "")
        }.getOrDefault("")
        if (resultTemplate.isNotBlank()) return resultTemplate
    }
    return resource.description.takeIf { it.isNotBlank() }
        ?: resource.content.takeIf { it.isNotBlank() }
        ?: resource.payloadJson.takeIf { it.isNotBlank() }
        ?: resource.resourceId
}

private fun buildResourceId(prefix: String, value: String): String {
    val slug = value
        .lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')
    return if (slug.isNotBlank()) {
        "$prefix-$slug"
    } else {
        "$prefix-${System.currentTimeMillis()}"
    }
}


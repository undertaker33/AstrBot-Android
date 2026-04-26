package com.astrbot.android.feature.plugin.runtime.toolsource

import com.astrbot.android.core.runtime.context.IngressTrigger
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.cron.domain.CronJobRepositoryPort
import com.astrbot.android.feature.cron.domain.CronJobRunNowPort
import com.astrbot.android.feature.cron.domain.CronJobRunNowResult
import com.astrbot.android.feature.cron.domain.CronSchedulerPort
import com.astrbot.android.feature.plugin.runtime.PluginToolArgs
import com.astrbot.android.feature.plugin.runtime.PluginToolResultStatus
import com.astrbot.android.model.CronJob
import com.astrbot.android.model.CronJobExecutionRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveCapabilityToolSourceProviderTest {

    @Test
    fun list_bindings_hides_active_capability_tools_during_scheduled_task_wakeup() = runBlocking {
        val provider = ActiveCapabilityToolSourceProvider(
            facade = ActiveCapabilityRuntimeFacade(
                repository = InMemoryActiveCapabilityTaskRepositoryForProvider(),
                scheduler = RecordingActiveCapabilitySchedulerForProvider(),
            promptStrings = com.astrbot.android.feature.cron.runtime.TestActiveCapabilityPromptStrings,
            ),
            promptStrings = com.astrbot.android.feature.cron.runtime.TestActiveCapabilityPromptStrings,
            contextResolver = noopContextResolverForProvider,
        )

        val bindings = provider.listBindings(
            ToolSourceRegistryIngestContext(
                toolSourceContext = ToolSourceContext(
                    requestId = "req-1",
                    platform = RuntimePlatform.APP_CHAT,
                    configProfileId = "config-1",
                    webSearchEnabled = false,
                    activeCapabilityEnabled = true,
                    mcpServers = emptyList(),
                    promptSkills = emptyList(),
                    toolSkills = emptyList(),
                    conversationId = "conversation-1",
                    ingressTrigger = IngressTrigger.SCHEDULED_TASK,
                ),
            ),
        )

        assertTrue(bindings.isEmpty())
    }

    @Test
    fun list_bindings_exposes_only_scheduled_task_management_tools_when_enabled() = runBlocking {
        val provider = ActiveCapabilityToolSourceProvider(
            facade = ActiveCapabilityRuntimeFacade(
                repository = InMemoryActiveCapabilityTaskRepositoryForProvider(),
                scheduler = RecordingActiveCapabilitySchedulerForProvider(),
            promptStrings = com.astrbot.android.feature.cron.runtime.TestActiveCapabilityPromptStrings,
            ),
            promptStrings = com.astrbot.android.feature.cron.runtime.TestActiveCapabilityPromptStrings,
            contextResolver = noopContextResolverForProvider,
        )

        val bindings = provider.listBindings(
            ToolSourceRegistryIngestContext(
                toolSourceContext = ToolSourceContext(
                    requestId = "req-1",
                    platform = RuntimePlatform.APP_CHAT,
                    configProfileId = "config-1",
                    webSearchEnabled = false,
                    activeCapabilityEnabled = true,
                    mcpServers = emptyList(),
                    promptSkills = emptyList(),
                    toolSkills = emptyList(),
                    conversationId = "conversation-1",
                    ingressTrigger = IngressTrigger.USER_MESSAGE,
                ),
            ),
        )

        assertEquals(
            listOf(
                "create_future_task",
                "delete_future_task",
                "list_future_tasks",
                "pause_future_task",
                "resume_future_task",
                "list_future_task_runs",
                "update_future_task",
                "run_future_task_now",
            ),
            bindings.map { it.descriptor.name },
        )
    }

    @Test
    fun invoke_update_future_task_routes_to_facade_and_returns_success_json() = runBlocking {
        val repository = InMemoryActiveCapabilityTaskRepositoryForProvider(
            initialJobs = listOf(CronJob(jobId = "job-1", name = "Old", enabled = true)),
        )
        val provider = ActiveCapabilityToolSourceProvider(
            facade = ActiveCapabilityRuntimeFacade(
                repository = repository,
                scheduler = RecordingActiveCapabilitySchedulerForProvider(),
            promptStrings = com.astrbot.android.feature.cron.runtime.TestActiveCapabilityPromptStrings,
                clock = { 10_000L },
            ),
            promptStrings = com.astrbot.android.feature.cron.runtime.TestActiveCapabilityPromptStrings,
            contextResolver = noopContextResolverForProvider,
        )

        val result = provider.invoke(
            invokeRequest(
                provider = provider,
                toolName = "update_future_task",
                payload = mapOf(
                    "job_id" to "job-1",
                    "name" to "Updated",
                    "enabled" to false,
                ),
            ),
        ).result

        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        assertTrue(result.text.orEmpty().contains("\"job_id\": \"job-1\""))
        assertEquals("Updated", repository.jobsSnapshot.single().name)
        assertEquals(false, repository.jobsSnapshot.single().enabled)
    }

    @Test
    fun invoke_run_future_task_now_routes_to_injected_runner() = runBlocking {
        val runner = RecordingRunNowPortForProvider(
            CronJobRunNowResult(
                success = true,
                status = "succeeded",
                message = "Run completed.",
            ),
        )
        val provider = ActiveCapabilityToolSourceProvider(
            facade = ActiveCapabilityRuntimeFacade(
                repository = InMemoryActiveCapabilityTaskRepositoryForProvider(),
                scheduler = RecordingActiveCapabilitySchedulerForProvider(),
            promptStrings = com.astrbot.android.feature.cron.runtime.TestActiveCapabilityPromptStrings,
                runNowPort = runner,
            ),
            promptStrings = com.astrbot.android.feature.cron.runtime.TestActiveCapabilityPromptStrings,
            contextResolver = noopContextResolverForProvider,
        )

        val result = provider.invoke(
            invokeRequest(
                provider = provider,
                toolName = "run_future_task_now",
                payload = mapOf("job_id" to "job-1"),
            ),
        ).result

        assertEquals(PluginToolResultStatus.SUCCESS, result.status)
        assertTrue(result.text.orEmpty().contains("\"status\": \"succeeded\""))
        assertEquals(listOf("job-1"), runner.requestedJobIds)
    }

    private fun invokeRequest(
        provider: ActiveCapabilityToolSourceProvider,
        toolName: String,
        payload: Map<String, Any?>,
    ): ToolSourceInvokeRequest {
        return ToolSourceInvokeRequest(
            identity = ToolSourceIdentity(
                sourceKind = provider.sourceKind,
                ownerId = "cap.schedule",
                sourceRef = toolName,
                displayName = toolName,
            ),
            args = PluginToolArgs(
                toolCallId = "call-1",
                requestId = "req-1",
                toolId = "cap.schedule:$toolName",
                payload = payload,
            ),
            timeoutMs = 1_000L,
            toolSourceContext = ToolSourceContext(
                requestId = "req-1",
                platform = RuntimePlatform.APP_CHAT,
                configProfileId = "config-1",
                webSearchEnabled = false,
                activeCapabilityEnabled = true,
                mcpServers = emptyList(),
                promptSkills = emptyList(),
                toolSkills = emptyList(),
                conversationId = "conversation-1",
                ingressTrigger = IngressTrigger.USER_MESSAGE,
            ),
        )
    }
}

private class InMemoryActiveCapabilityTaskRepositoryForProvider(
    initialJobs: List<CronJob> = emptyList(),
) : CronJobRepositoryPort {
    val jobsSnapshot = initialJobs.toMutableList()
    override val jobs: StateFlow<List<CronJob>> = MutableStateFlow(initialJobs)
    override suspend fun create(job: CronJob): CronJob = job
    override suspend fun update(job: CronJob): CronJob {
        val index = jobsSnapshot.indexOfFirst { it.jobId == job.jobId }
        if (index >= 0) {
            jobsSnapshot[index] = job
        } else {
            jobsSnapshot += job
        }
        return job
    }
    override suspend fun delete(jobId: String) = Unit
    override suspend fun getByJobId(jobId: String): CronJob? = jobsSnapshot.firstOrNull { it.jobId == jobId }
    override suspend fun listAll(): List<CronJob> = jobsSnapshot
    override suspend fun listEnabled(): List<CronJob> = jobsSnapshot.filter(CronJob::enabled)
    override suspend fun updateStatus(jobId: String, status: String, lastRunAt: Long?, lastError: String?) = Unit
    override suspend fun recordExecutionStarted(record: CronJobExecutionRecord): CronJobExecutionRecord = record
    override suspend fun updateExecutionRecord(record: CronJobExecutionRecord): CronJobExecutionRecord = record
    override suspend fun listRecentExecutionRecords(jobId: String, limit: Int): List<CronJobExecutionRecord> = emptyList()
    override suspend fun latestExecutionRecord(jobId: String): CronJobExecutionRecord? = null
}

private class RecordingActiveCapabilitySchedulerForProvider : CronSchedulerPort {
    override fun schedule(job: CronJob) = Unit
    override fun cancel(jobId: String) = Unit
    override fun cancelAll() = Unit
}

private class RecordingRunNowPortForProvider(
    private val result: CronJobRunNowResult,
) : CronJobRunNowPort {
    val requestedJobIds = mutableListOf<String>()

    override suspend fun runNow(jobId: String): CronJobRunNowResult {
        requestedJobIds += jobId
        return result
    }
}

private val noopContextResolverForProvider = object : FutureToolSourceContextResolver {
    override fun resolveForConfig(configProfileId: String): ToolSourceContext {
        return ToolSourceContext(
            requestId = "req-1",
            platform = RuntimePlatform.APP_CHAT,
            configProfileId = configProfileId,
            webSearchEnabled = false,
            activeCapabilityEnabled = false,
            mcpServers = emptyList(),
            promptSkills = emptyList(),
            toolSkills = emptyList(),
            conversationId = "conversation-1",
        )
    }
}

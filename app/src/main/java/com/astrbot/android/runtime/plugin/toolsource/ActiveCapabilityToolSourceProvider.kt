package com.astrbot.android.runtime.plugin.toolsource

import com.astrbot.android.data.ConfigRepository
import com.astrbot.android.data.CronJobRepository
import com.astrbot.android.model.CronJob
import com.astrbot.android.runtime.RuntimeLogRepository
import com.astrbot.android.runtime.cron.CronExpressionParser
import com.astrbot.android.runtime.cron.CronJobScheduler
import com.astrbot.android.runtime.plugin.PluginToolDescriptor
import com.astrbot.android.runtime.plugin.PluginToolResult
import com.astrbot.android.runtime.plugin.PluginToolResultStatus
import com.astrbot.android.runtime.plugin.PluginToolSourceKind
import com.astrbot.android.runtime.plugin.PluginToolVisibility
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Active capability tool source provider.
 *
 * Analogous to AstrBot's cron / proactive tools. When `proactiveEnabled` is set
 * on the config profile, this provider exports scheduling-related tools so the LLM
 * can create/delete/list future tasks.
 */
class ActiveCapabilityToolSourceProvider : FutureToolSourceProvider {
    override val sourceKind: PluginToolSourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY

    /** Application context, resolved from the global holder or set per-instance. */
    var appContext: android.content.Context? = globalAppContext
        set(value) {
            field = value ?: globalAppContext
        }

    override suspend fun listBindings(
        context: ToolSourceRegistryIngestContext,
    ): List<ToolSourceDescriptorBinding> {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        if (!configProfile.proactiveEnabled) return emptyList()

        return listOf(
            buildCreateTaskBinding(),
            buildDeleteTaskBinding(),
            buildListTasksBinding(),
        )
    }

    override suspend fun availabilityOf(
        identity: ToolSourceIdentity,
        context: ToolSourceAvailabilityContext,
    ): ToolSourceAvailability {
        val configProfile = ConfigRepository.resolve(context.configProfileId)
        return if (configProfile.proactiveEnabled) {
            ToolSourceAvailability(
                providerReachable = true,
                permissionGranted = true,
                capabilityAllowed = true,
            )
        } else {
            ToolSourceAvailability(
                providerReachable = false,
                permissionGranted = true,
                capabilityAllowed = false,
                detailCode = "proactive_disabled",
                detailMessage = "Proactive capability is disabled in this config profile.",
            )
        }
    }

    override suspend fun invoke(
        request: ToolSourceInvokeRequest,
    ): ToolSourceInvokeResult {
        // toolId format is "pluginId:name", extract the tool name
        val toolName = request.args.toolId.substringAfter(":")
        val payload = request.args.payload
        return try {
            val text = when (toolName) {
                "create_future_task" -> handleCreateFutureTask(payload)
                "delete_future_task" -> handleDeleteFutureTask(payload)
                "list_future_tasks" -> handleListFutureTasks()
                else -> throw IllegalArgumentException("Unknown active capability tool: $toolName")
            }
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.SUCCESS,
                    text = text,
                ),
            )
        } catch (e: Exception) {
            RuntimeLogRepository.append("ActiveCapability invoke error: ${e.message}")
            ToolSourceInvokeResult(
                result = PluginToolResult(
                    toolCallId = request.args.toolCallId,
                    requestId = request.args.requestId,
                    toolId = request.args.toolId,
                    status = PluginToolResultStatus.ERROR,
                    errorCode = "active_capability_error",
                    text = "Error: ${e.message}",
                ),
            )
        }
    }

    // ── Tool handlers ───────────────────────────────────────────────────

    private suspend fun handleCreateFutureTask(payload: Map<String, Any?>): String {
        val name = (payload["name"] as? String)?.ifBlank { null } ?: "Unnamed Task"
        val cronExpression = (payload["cron_expression"] as? String) ?: ""
        val runAtStr = (payload["run_at"] as? String) ?: ""
        val note = (payload["note"] as? String) ?: ""
        val runOnce = (payload["run_once"] as? Boolean) ?: runAtStr.isNotBlank()

        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // Determine scheduling
        val resolvedCron: String
        val nextRunTime: Long
        if (runAtStr.isNotBlank()) {
            // One-time task with ISO datetime
            nextRunTime = OffsetDateTime.parse(runAtStr).toInstant().toEpochMilli()
            resolvedCron = "" // No cron for one-time tasks
        } else if (cronExpression.isNotBlank()) {
            resolvedCron = cronExpression
            nextRunTime = CronExpressionParser.nextFireTime(resolvedCron, now, "")
        } else {
            throw IllegalArgumentException("Either 'cron_expression' or 'run_at' must be provided.")
        }

        val jobPayload = JSONObject().apply {
            put("note", note)
            if (runAtStr.isNotBlank()) put("run_at", runAtStr)
            // Capture session context so the worker knows where to reply
            put("session", request_sessionId ?: "")
        }

        val job = CronJob(
            jobId = jobId,
            name = name,
            description = note,
            jobType = "active_agent",
            cronExpression = resolvedCron,
            payloadJson = jobPayload.toString(),
            enabled = true,
            runOnce = runOnce,
            status = "scheduled",
            nextRunTime = nextRunTime,
            createdAt = now,
            updatedAt = now,
        )
        CronJobRepository.create(job)
        val ctx = appContext
            ?: throw IllegalStateException(
                "Cannot schedule cron job: application context not available. " +
                    "Ensure ActiveCapabilityToolSourceProvider.initialize() is called at app startup.",
            )
        CronJobScheduler.scheduleJob(ctx, job)

        return JSONObject().apply {
            put("success", true)
            put("job_id", jobId)
            put("name", name)
            put("next_run_time", java.time.Instant.ofEpochMilli(nextRunTime).toString())
            put("run_once", runOnce)
        }.toString(2)
    }

    private suspend fun handleDeleteFutureTask(payload: Map<String, Any?>): String {
        val jobId = (payload["job_id"] as? String) ?: ""
        if (jobId.isBlank()) {
            throw IllegalArgumentException("'job_id' is required to delete a task.")
        }
        val existing = CronJobRepository.getByJobId(jobId)
            ?: throw IllegalArgumentException("Task with job_id '$jobId' not found.")
        CronJobRepository.delete(jobId)
        appContext?.let { CronJobScheduler.cancelJob(it, jobId) }
            ?: RuntimeLogRepository.append("ActiveCapability: cannot cancel WorkManager job, no app context")
        return JSONObject().apply {
            put("success", true)
            put("deleted_job_id", jobId)
            put("deleted_name", existing.name)
        }.toString(2)
    }

    private suspend fun handleListFutureTasks(): String {
        val jobs = CronJobRepository.listAll()
        val arr = JSONArray()
        for (job in jobs) {
            arr.put(JSONObject().apply {
                put("job_id", job.jobId)
                put("name", job.name)
                put("description", job.description)
                put("job_type", job.jobType)
                put("cron_expression", job.cronExpression)
                put("enabled", job.enabled)
                put("run_once", job.runOnce)
                put("status", job.status)
                put("next_run_time", if (job.nextRunTime > 0) {
                    java.time.Instant.ofEpochMilli(job.nextRunTime).toString()
                } else "")
                put("last_run_at", if (job.lastRunAt > 0) {
                    java.time.Instant.ofEpochMilli(job.lastRunAt).toString()
                } else "")
            })
        }
        return JSONObject().apply {
            put("count", jobs.size)
            put("tasks", arr)
        }.toString(2)
    }

    // ── Tool descriptor bindings (aligned with AstrBot-master) ──────────

    private fun buildCreateTaskBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "create_future_task",
                displayName = "Schedule Future Task",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "create_future_task",
                description = "Schedule a task to be executed in the future. Provide cron_expression for recurring or run_at (ISO 8601) for one-time.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object" as Any,
                    "properties" to mapOf(
                        "name" to mapOf("type" to "string", "description" to "Task name"),
                        "cron_expression" to mapOf("type" to "string", "description" to "Cron expression (minute hour day month weekday). For recurring tasks."),
                        "run_at" to mapOf("type" to "string", "description" to "ISO 8601 datetime for one-time execution. e.g. 2025-01-15T09:00:00+08:00"),
                        "note" to mapOf("type" to "string", "description" to "Task description / instructions for the agent when it wakes."),
                        "run_once" to mapOf("type" to "boolean", "description" to "If true, task runs only once. Defaults to true when run_at is provided."),
                    ),
                    "required" to listOf("name"),
                ),
            ),
        )
    }

    private fun buildDeleteTaskBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "delete_future_task",
                displayName = "Cancel Future Task",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "delete_future_task",
                description = "Cancel and delete a previously scheduled future task.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf(
                    "type" to "object" as Any,
                    "properties" to mapOf(
                        "job_id" to mapOf("type" to "string", "description" to "The job_id of the task to cancel"),
                    ),
                    "required" to listOf("job_id"),
                ),
            ),
        )
    }

    private fun buildListTasksBinding(): ToolSourceDescriptorBinding {
        val ownerId = "cap.schedule"
        return ToolSourceDescriptorBinding(
            identity = ToolSourceIdentity(
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                ownerId = ownerId,
                sourceRef = "list_future_tasks",
                displayName = "List Future Tasks",
            ),
            descriptor = PluginToolDescriptor(
                pluginId = ownerId,
                name = "list_future_tasks",
                description = "List all scheduled future tasks with their status and next run time.",
                visibility = PluginToolVisibility.LLM_VISIBLE,
                sourceKind = PluginToolSourceKind.ACTIVE_CAPABILITY,
                inputSchema = mapOf("type" to "object" as Any),
            ),
        )
    }

    companion object {
        /**
         * Thread-local session ID, set by the tool executor before invoking
         * so the created task knows which conversation to target.
         */
        internal var request_sessionId: String? = null

        /**
         * Global application context, shared across all instances.
         * Set once at startup via [initialize].
         */
        @Volatile
        private var globalAppContext: android.content.Context? = null

        /**
         * Call during app bootstrap to make the application context available
         * to all instances created by [FutureToolSourceRegistry.defaultProviders].
         */
        fun initialize(context: android.content.Context) {
            globalAppContext = context.applicationContext
        }
    }
}

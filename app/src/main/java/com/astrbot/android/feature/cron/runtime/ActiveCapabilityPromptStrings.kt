package com.astrbot.android.feature.cron.runtime

import android.content.Context
import com.astrbot.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface ActiveCapabilityPromptStrings {
    val activeCapabilityHiddenDuringScheduledTask: String
    val proactiveCapabilityDisabled: String
    val createFutureTaskDisplayName: String
    val createFutureTaskDescription: String
    val deleteFutureTaskDisplayName: String
    val deleteFutureTaskDescription: String
    val listFutureTasksDisplayName: String
    val listFutureTasksDescription: String
    val pauseFutureTaskDisplayName: String
    val pauseFutureTaskDescription: String
    val resumeFutureTaskDisplayName: String
    val resumeFutureTaskDescription: String
    val listFutureTaskRunsDisplayName: String
    val listFutureTaskRunsDescription: String
    val updateFutureTaskDisplayName: String
    val updateFutureTaskDescription: String
    val runFutureTaskNowDisplayName: String
    val runFutureTaskNowDescription: String
    val schemaJobIdCancelDescription: String
    val schemaJobIdDescription: String
    val schemaRunsLimitDescription: String
    val schemaUpdatedShortTitleDescription: String
    val schemaUpdatedTaskInstructionDescription: String
    val schemaTaskEnabledDescription: String
    val schemaUpdatedTaskStatusDescription: String
    val schemaUpdatedRunAtDescription: String
    val schemaUpdatedCronExpressionDescription: String
    val schemaUpdatedTimezoneDescription: String
    val schemaCreateRunOnceDescription: String
    val schemaCreateNameDescription: String
    val schemaCreateNoteDescription: String
    val schemaCreateCronExpressionDescription: String
    val schemaCreateRunAtDescription: String
    val schemaCreateSessionDescription: String
    val schemaCreateTimezoneDescription: String
    val schemaCreateEnabledDescription: String
    val schemaCreateAllowPastImmediateDescription: String
    val schemaCreatePlatformDescription: String
    val schemaCreateConversationIdDescription: String
    val schemaCreateBotIdDescription: String
    val schemaCreateConfigProfileIdDescription: String
    val schemaCreatePersonaIdDescription: String
    val schemaCreateProviderIdDescription: String
    val schemaCreateOriginDescription: String
    val defaultTaskName: String
    val missingNoteMessage: String
    val invalidScheduleMessage: String
    val pastScheduleMessage: String
    val deleteMissingJobIdMessage: String
    val updateMissingJobIdMessage: String
    val pauseMissingJobIdMessage: String
    val resumeMissingJobIdMessage: String
    val listRunsMissingJobIdMessage: String
    val runNowMissingJobIdMessage: String
    val runNowUnavailableMessage: String
    val guardCreatedReply: String
    val guardPastScheduleReply: String
    val guardReminderNotePrefix: String

    fun activeCapabilityToolError(message: String): String
    fun missingContextMessage(fields: String): String
    fun taskNotFoundMessage(jobId: String): String
    fun guardFailedReply(message: String): String
    fun fallbackCreatedInstruction(jobId: String): String
    fun fallbackFailedInstruction(code: String, replyText: String): String
}

class EmptyActiveCapabilityPromptStrings : ActiveCapabilityPromptStrings {
    override val activeCapabilityHiddenDuringScheduledTask = ""
    override val proactiveCapabilityDisabled = ""
    override val createFutureTaskDisplayName = ""
    override val createFutureTaskDescription = ""
    override val deleteFutureTaskDisplayName = ""
    override val deleteFutureTaskDescription = ""
    override val listFutureTasksDisplayName = ""
    override val listFutureTasksDescription = ""
    override val pauseFutureTaskDisplayName = ""
    override val pauseFutureTaskDescription = ""
    override val resumeFutureTaskDisplayName = ""
    override val resumeFutureTaskDescription = ""
    override val listFutureTaskRunsDisplayName = ""
    override val listFutureTaskRunsDescription = ""
    override val updateFutureTaskDisplayName = ""
    override val updateFutureTaskDescription = ""
    override val runFutureTaskNowDisplayName = ""
    override val runFutureTaskNowDescription = ""
    override val schemaJobIdCancelDescription = ""
    override val schemaJobIdDescription = ""
    override val schemaRunsLimitDescription = ""
    override val schemaUpdatedShortTitleDescription = ""
    override val schemaUpdatedTaskInstructionDescription = ""
    override val schemaTaskEnabledDescription = ""
    override val schemaUpdatedTaskStatusDescription = ""
    override val schemaUpdatedRunAtDescription = ""
    override val schemaUpdatedCronExpressionDescription = ""
    override val schemaUpdatedTimezoneDescription = ""
    override val schemaCreateRunOnceDescription = ""
    override val schemaCreateNameDescription = ""
    override val schemaCreateNoteDescription = ""
    override val schemaCreateCronExpressionDescription = ""
    override val schemaCreateRunAtDescription = ""
    override val schemaCreateSessionDescription = ""
    override val schemaCreateTimezoneDescription = ""
    override val schemaCreateEnabledDescription = ""
    override val schemaCreateAllowPastImmediateDescription = ""
    override val schemaCreatePlatformDescription = ""
    override val schemaCreateConversationIdDescription = ""
    override val schemaCreateBotIdDescription = ""
    override val schemaCreateConfigProfileIdDescription = ""
    override val schemaCreatePersonaIdDescription = ""
    override val schemaCreateProviderIdDescription = ""
    override val schemaCreateOriginDescription = ""
    override val defaultTaskName = ""
    override val missingNoteMessage = ""
    override val invalidScheduleMessage = ""
    override val pastScheduleMessage = ""
    override val deleteMissingJobIdMessage = ""
    override val updateMissingJobIdMessage = ""
    override val pauseMissingJobIdMessage = ""
    override val resumeMissingJobIdMessage = ""
    override val listRunsMissingJobIdMessage = ""
    override val runNowMissingJobIdMessage = ""
    override val runNowUnavailableMessage = ""
    override val guardCreatedReply = ""
    override val guardPastScheduleReply = ""
    override val guardReminderNotePrefix = ""

    override fun activeCapabilityToolError(message: String): String = message
    override fun missingContextMessage(fields: String): String = fields
    override fun taskNotFoundMessage(jobId: String): String = jobId
    override fun guardFailedReply(message: String): String = message
    override fun fallbackCreatedInstruction(jobId: String): String = jobId
    override fun fallbackFailedInstruction(code: String, replyText: String): String = "$code $replyText"
}

class AndroidActiveCapabilityPromptStrings @Inject constructor(
    @ApplicationContext private val context: Context,
) : ActiveCapabilityPromptStrings {
    override val activeCapabilityHiddenDuringScheduledTask: String
        get() = context.getString(R.string.runtime_prompt_active_capability_hidden_during_scheduled_task)
    override val proactiveCapabilityDisabled: String
        get() = context.getString(R.string.runtime_prompt_active_capability_proactive_disabled)
    override val createFutureTaskDisplayName: String
        get() = context.getString(R.string.runtime_prompt_tool_create_future_task_display_name)
    override val createFutureTaskDescription: String
        get() = context.getString(R.string.runtime_prompt_tool_create_future_task_description)
    override val deleteFutureTaskDisplayName: String
        get() = context.getString(R.string.runtime_prompt_tool_delete_future_task_display_name)
    override val deleteFutureTaskDescription: String
        get() = context.getString(R.string.runtime_prompt_tool_delete_future_task_description)
    override val listFutureTasksDisplayName: String
        get() = context.getString(R.string.runtime_prompt_tool_list_future_tasks_display_name)
    override val listFutureTasksDescription: String
        get() = context.getString(R.string.runtime_prompt_tool_list_future_tasks_description)
    override val pauseFutureTaskDisplayName: String
        get() = context.getString(R.string.runtime_prompt_tool_pause_future_task_display_name)
    override val pauseFutureTaskDescription: String
        get() = context.getString(R.string.runtime_prompt_tool_pause_future_task_description)
    override val resumeFutureTaskDisplayName: String
        get() = context.getString(R.string.runtime_prompt_tool_resume_future_task_display_name)
    override val resumeFutureTaskDescription: String
        get() = context.getString(R.string.runtime_prompt_tool_resume_future_task_description)
    override val listFutureTaskRunsDisplayName: String
        get() = context.getString(R.string.runtime_prompt_tool_list_future_task_runs_display_name)
    override val listFutureTaskRunsDescription: String
        get() = context.getString(R.string.runtime_prompt_tool_list_future_task_runs_description)
    override val updateFutureTaskDisplayName: String
        get() = context.getString(R.string.runtime_prompt_tool_update_future_task_display_name)
    override val updateFutureTaskDescription: String
        get() = context.getString(R.string.runtime_prompt_tool_update_future_task_description)
    override val runFutureTaskNowDisplayName: String
        get() = context.getString(R.string.runtime_prompt_tool_run_future_task_now_display_name)
    override val runFutureTaskNowDescription: String
        get() = context.getString(R.string.runtime_prompt_tool_run_future_task_now_description)
    override val schemaJobIdCancelDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_job_id_cancel_description)
    override val schemaJobIdDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_job_id_description)
    override val schemaRunsLimitDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_runs_limit_description)
    override val schemaUpdatedShortTitleDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_updated_short_title_description)
    override val schemaUpdatedTaskInstructionDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_updated_task_instruction_description)
    override val schemaTaskEnabledDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_task_enabled_description)
    override val schemaUpdatedTaskStatusDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_updated_task_status_description)
    override val schemaUpdatedRunAtDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_updated_run_at_description)
    override val schemaUpdatedCronExpressionDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_updated_cron_expression_description)
    override val schemaUpdatedTimezoneDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_updated_timezone_description)
    override val schemaCreateRunOnceDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_run_once_description)
    override val schemaCreateNameDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_name_description)
    override val schemaCreateNoteDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_note_description)
    override val schemaCreateCronExpressionDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_cron_expression_description)
    override val schemaCreateRunAtDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_run_at_description)
    override val schemaCreateSessionDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_session_description)
    override val schemaCreateTimezoneDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_timezone_description)
    override val schemaCreateEnabledDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_enabled_description)
    override val schemaCreateAllowPastImmediateDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_allow_past_immediate_description)
    override val schemaCreatePlatformDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_platform_description)
    override val schemaCreateConversationIdDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_conversation_id_description)
    override val schemaCreateBotIdDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_bot_id_description)
    override val schemaCreateConfigProfileIdDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_config_profile_id_description)
    override val schemaCreatePersonaIdDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_persona_id_description)
    override val schemaCreateProviderIdDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_provider_id_description)
    override val schemaCreateOriginDescription: String
        get() = context.getString(R.string.runtime_prompt_schema_create_origin_description)
    override val defaultTaskName: String
        get() = context.getString(R.string.runtime_prompt_active_capability_default_task_name)
    override val missingNoteMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_missing_note)
    override val invalidScheduleMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_invalid_schedule)
    override val pastScheduleMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_past_schedule)
    override val deleteMissingJobIdMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_delete_missing_job_id)
    override val updateMissingJobIdMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_update_missing_job_id)
    override val pauseMissingJobIdMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_pause_missing_job_id)
    override val resumeMissingJobIdMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_resume_missing_job_id)
    override val listRunsMissingJobIdMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_list_runs_missing_job_id)
    override val runNowMissingJobIdMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_run_now_missing_job_id)
    override val runNowUnavailableMessage: String
        get() = context.getString(R.string.runtime_prompt_active_capability_run_now_unavailable)
    override val guardCreatedReply: String
        get() = context.getString(R.string.runtime_prompt_guard_created_reply)
    override val guardPastScheduleReply: String
        get() = context.getString(R.string.runtime_prompt_guard_past_schedule_reply)
    override val guardReminderNotePrefix: String
        get() = context.getString(R.string.runtime_prompt_guard_reminder_note_prefix)

    override fun activeCapabilityToolError(message: String): String =
        context.getString(R.string.runtime_prompt_active_capability_tool_error, message)

    override fun missingContextMessage(fields: String): String =
        context.getString(R.string.runtime_prompt_active_capability_missing_context, fields)

    override fun taskNotFoundMessage(jobId: String): String =
        context.getString(R.string.runtime_prompt_active_capability_task_not_found, jobId)

    override fun guardFailedReply(message: String): String =
        context.getString(R.string.runtime_prompt_guard_failed_reply, message)

    override fun fallbackCreatedInstruction(jobId: String): String =
        context.getString(R.string.runtime_prompt_scheduled_task_fallback_created_instruction, jobId)

    override fun fallbackFailedInstruction(code: String, replyText: String): String =
        context.getString(R.string.runtime_prompt_scheduled_task_fallback_failed_instruction, code, replyText)
}

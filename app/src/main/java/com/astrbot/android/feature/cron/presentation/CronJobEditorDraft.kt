package com.astrbot.android.ui.settings

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.CronJob
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTargetContext
import org.json.JSONObject

internal const val DefaultAppChatConversationId: String = "chat-main"

internal data class CronJobEditorDraft(
    val name: String = "",
    val note: String = "",
    val cronExpression: String = "",
    val runAt: String = "",
    val runOnce: Boolean = false,
    val platform: String = RuntimePlatform.APP_CHAT.wireValue,
    val conversationId: String = DefaultAppChatConversationId,
    val selectedBotId: String = "",
) {
    fun canSubmit(): Boolean = missingFields().isEmpty()

    fun missingFields(): List<String> {
        return buildList {
            if (name.trim().isBlank()) add("name")
            if (cronExpression.trim().isBlank() && runAt.trim().isBlank()) add("schedule")
            if (platform.trim().isBlank()) add("platform")
            if (conversationId.trim().isBlank()) add("conversation_id")
            if (selectedBotId.trim().isBlank()) add("bot_id")
        }
    }

    fun toTargetContext(
        selectedBot: BotProfile,
        origin: String = "ui",
    ): ActiveCapabilityTargetContext {
        return selectedBot.toCronJobTargetContext(
            platform = platform.trim(),
            conversationId = conversationId.trim(),
            origin = origin,
        )
    }

    fun toUpdatedCronJob(
        existing: CronJob,
        selectedBot: BotProfile,
        timezone: String,
        nextRunTime: Long,
        updatedAt: Long = System.currentTimeMillis(),
    ): CronJob {
        val target = toTargetContext(selectedBot)
        val trimmedRunAt = runAt.trim()
        val trimmedNote = note.trim()
        val payloadJson = JSONObject().apply {
            put("note", trimmedNote)
            if (trimmedRunAt.isNotBlank()) put("run_at", trimmedRunAt)
            put("timezone", timezone)
            put("enabled", existing.enabled)
            put("session", target.conversationId)
            put("target", target.toJson())
            put("platform", target.platform)
            put("conversation_id", target.conversationId)
            put("bot_id", target.botId)
            put("config_profile_id", target.configProfileId)
            put("persona_id", target.personaId)
            put("provider_id", target.providerId)
            put("origin", target.origin)
        }.toString()
        return existing.copy(
            name = name.trim(),
            description = trimmedNote,
            cronExpression = if (trimmedRunAt.isNotBlank()) "" else cronExpression.trim(),
            timezone = timezone,
            payloadJson = payloadJson,
            runOnce = runOnce || trimmedRunAt.isNotBlank(),
            platform = target.platform,
            conversationId = target.conversationId,
            botId = target.botId,
            configProfileId = target.configProfileId,
            personaId = target.personaId,
            providerId = target.providerId,
            origin = target.origin,
            status = if (existing.enabled) "scheduled" else "paused",
            nextRunTime = nextRunTime,
            lastError = "",
            updatedAt = updatedAt,
        )
    }

    companion object {
        fun fromCronJob(job: CronJob): CronJobEditorDraft {
            val payload = runCatching { JSONObject(job.payloadJson) }.getOrDefault(JSONObject())
            return CronJobEditorDraft(
                name = job.name,
                note = payload.optString("note", job.description),
                cronExpression = job.cronExpression,
                runAt = payload.optString("run_at", ""),
                runOnce = job.runOnce,
                platform = job.platform,
                conversationId = job.conversationId,
                selectedBotId = job.botId,
            )
        }

        fun fromTargetContext(targetContext: ActiveCapabilityTargetContext): CronJobEditorDraft {
            return CronJobEditorDraft(
                platform = targetContext.platform,
                conversationId = targetContext.conversationId,
                selectedBotId = targetContext.botId,
            )
        }

        fun fromBotProfile(
            botProfile: BotProfile,
            platform: String = RuntimePlatform.APP_CHAT.wireValue,
            conversationId: String = DefaultAppChatConversationId,
        ): CronJobEditorDraft {
            return CronJobEditorDraft(
                platform = platform,
                conversationId = conversationId,
                selectedBotId = botProfile.id,
            )
        }
    }
}


package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.SkillEntry
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import com.astrbot.android.core.runtime.search.WebSearchTriggerIntent
import com.astrbot.android.core.runtime.search.WebSearchTriggerRules

/**
 * Assembles the final system prompt from a [ResolvedRuntimeContext].
 *
 * This is the **single authoritative source** for all system prompt content.
 * Neither ChatViewModel nor QqOneBotBridgeServer should build prompts directly.
 *
 * Assembly ordering (stable contract):
 *   1. Persona system prompt — role identity
 *   2. Prompt Skill blocks — ordered by priority desc, then sortIndex
 *   3. Platform channel hint — QQ group / QQ private / none for App
 *   4. Runtime facts — real-world time awareness
 *
 * Prompt Skills with blank content are silently skipped.
 * Skills are subject to a total length budget; excess skills are trimmed
 * with a log note (future: observable metric).
 */
object PromptAssembler {

    private const val DEFAULT_SKILL_BUDGET_CHARS = 8000

    /**
     * Build the final system prompt for a full pipeline request.
     */
    fun assemble(ctx: ResolvedRuntimeContext): String? {
        val parts = mutableListOf<String>()

        // 1. Persona system prompt
        ctx.persona?.systemPrompt?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)

        // 2. Prompt Skill blocks
        assemblePromptSkillBlocks(ctx.promptSkills)?.let(parts::add)

        // 3. Platform channel hint
        channelHint(ctx)?.let(parts::add)

        // 4. Runtime facts
        if (ctx.realWorldTimeAwarenessEnabled) {
            val now = ZonedDateTime.now()
            parts += "Current local time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}."
        }

        scheduledTaskGuidance(ctx)?.let(parts::add)
        hostCapabilityGuidance(ctx)?.let(parts::add)

        return parts.joinToString("\n\n").ifBlank { null }
    }

    /**
     * Convenience for App chat direct provider path (Track B) where a full
     * [ResolvedRuntimeContext] is not available. Will be removed once Track B
     * also goes through the orchestrator.
     */
    fun assembleForAppChat(
        personaPrompt: String?,
        realWorldTimeAwarenessEnabled: Boolean,
        skills: List<SkillEntry> = emptyList(),
    ): String? {
        val parts = mutableListOf<String>()
        personaPrompt?.trim()?.takeIf { it.isNotBlank() }?.let(parts::add)
        assembleSkillBlocks(skills)?.let(parts::add)
        hostCapabilityGuidance(trigger = null)?.let(parts::add)
        if (realWorldTimeAwarenessEnabled) {
            val now = ZonedDateTime.now()
            parts += "Current local time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))}."
        }
        return parts.joinToString("\n\n").ifBlank { null }
    }

    /**
     * Assemble active Prompt Skill blocks, ordered by descending priority
     * then original list order. Skills with blank content are skipped.
     * The combined length is capped at [DEFAULT_SKILL_BUDGET_CHARS].
     */
    internal fun assembleSkillBlocks(skills: List<SkillEntry>): String? {
        return assemblePromptSkillBlocks(
            RuntimeSkillProjectionResolver.promptSkills(
                skills = skills,
                platform = RuntimePlatform.APP_CHAT,
                trigger = IngressTrigger.USER_MESSAGE,
            ),
        )
    }

    internal fun assemblePromptSkillBlocks(skills: List<PromptSkillProjection>): String? {
        val activeSkills = skills.filter { it.active && it.content.isNotBlank() }
        if (activeSkills.isEmpty()) return null
        val builder = StringBuilder()
        var budget = DEFAULT_SKILL_BUDGET_CHARS
        for (skill in activeSkills) {
            val block = skill.content.trim()
            val skillBudget = if (skill.budgetChars > 0) minOf(skill.budgetChars, budget) else budget
            if (block.length > skillBudget) break
            if (builder.isNotEmpty()) builder.append("\n\n")
            builder.append(block)
            budget -= block.length
        }
        return builder.toString().ifBlank { null }
    }

    private fun channelHint(ctx: ResolvedRuntimeContext): String? {
        if (ctx.ingressEvent.platform != RuntimePlatform.QQ_ONEBOT) return null
        return when (ctx.ingressEvent.messageType) {
            com.astrbot.android.model.chat.MessageType.GroupMessage ->
                "You are replying inside a QQ group chat. Keep the answer concise and natural, and focus on the latest message."
            else ->
                "You are replying inside a QQ private chat. Keep the answer concise and natural."
        }
    }

    private fun hostCapabilityGuidance(ctx: ResolvedRuntimeContext): String? {
        val realtimeGuidance = if (ctx.webSearchEnabled) {
            realtimeSearchGuidance(ctx.ingressEvent.text)
        } else {
            null
        }
        return hostCapabilityGuidance(ctx.ingressEvent.trigger, realtimeGuidance)
    }

    private fun hostCapabilityGuidance(
        trigger: IngressTrigger?,
        realtimeGuidance: String? = null,
    ): String? {
        val reminderRoutingRule = "When the user asks to remind, schedule a follow-up, set a timer, or repeat a task later, prefer create_future_task. Use web_search only when the user explicitly asks how reminders work, about reminder apps, or for related information/news."
        val baseGuidance = if (trigger == IngressTrigger.SCHEDULED_TASK) {
            "This turn was triggered by a scheduled task. It is not a normal chat turn. Do not greet, do not add filler, and do the scheduled work first. If this task exists to remind, notify, or follow up with the user, you must send the reminder now and must not silently suppress it."
        } else {
            reminderRoutingRule
        }
        return listOfNotNull(baseGuidance, realtimeGuidance).joinToString("\n")
    }

    private fun realtimeSearchGuidance(text: String): String? {
        val trigger = WebSearchTriggerRules.evaluate(text)
        return when (trigger.intent) {
            WebSearchTriggerIntent.NEWS ->
                "The latest user message matches a news/current-events intent. You must call web_search before answering. The host may send the factual news summary directly from tool results. After tool results are available, Do not repeat news items, dates, sources, or URLs. Provide only a brief evaluation or commentary on the confirmed news in the configured persona, and do not invent new facts."
            WebSearchTriggerIntent.WEATHER ->
                "The latest user message asks for weather or other live conditions. You must call web_search before answering and base the answer only on returned sources."
            WebSearchTriggerIntent.REALTIME ->
                "The latest user message appears to require current or live information. Prefer calling web_search before answering; do not invent fresh facts without sources."
            WebSearchTriggerIntent.NONE -> null
        }
    }

    private fun scheduledTaskGuidance(ctx: ResolvedRuntimeContext): String? {
        if (ctx.ingressEvent.trigger != IngressTrigger.SCHEDULED_TASK) return null
        val task = extractScheduledTaskPayload(ctx.ingressEvent.rawPlatformPayload)
        val note = task["note"].orEmpty().ifBlank { ctx.ingressEvent.text.trim() }
        val name = task["name"].orEmpty()
        val jobId = task["jobId"].orEmpty().ifBlank { ctx.ingressEvent.messageId.removePrefix("cron:") }
        val runAt = task["runAt"].orEmpty()
        val origin = task["origin"].orEmpty()
        return buildString {
            appendLine("Scheduled task scheduler metadata:")
            appendLine("- job_id: ${jobId.ifBlank { "unknown" }}")
            if (name.isNotBlank()) appendLine("- name: $name")
            if (runAt.isNotBlank()) appendLine("- run_at: $runAt")
            if (origin.isNotBlank()) appendLine("- origin: $origin")
            if (note.isNotBlank()) appendLine("- note: $note")
            appendLine()
            append("The note above is scheduler metadata, not as a new user message. ")
            append("Execute the scheduled task now in the assistant voice. ")
            append("If the task is a reminder or follow-up, remind the user now. ")
            append("You must not create another scheduled task for this same note unless the scheduler metadata explicitly asks for rescheduling.")
        }
    }

    private fun extractScheduledTaskPayload(payload: Any?): Map<String, String> {
        val root = payload as? Map<*, *> ?: return emptyMap()
        val task = root["scheduledTask"] as? Map<*, *> ?: return emptyMap()
        return task.mapNotNull { (key, value) ->
            val normalizedKey = key as? String ?: return@mapNotNull null
            normalizedKey to value.toString()
        }.toMap()
    }
}

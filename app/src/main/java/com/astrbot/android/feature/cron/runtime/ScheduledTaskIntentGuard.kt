package com.astrbot.android.feature.cron.runtime

import com.astrbot.android.feature.cron.domain.ActiveCapabilityTaskPort
import com.astrbot.android.feature.cron.domain.CronTaskCreateRequest
import com.astrbot.android.feature.cron.domain.CronTaskCreateResult
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityNaturalLanguageParser
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class ScheduledTaskIntentGuardContext(
    val proactiveEnabled: Boolean,
    val platform: String,
    val conversationId: String,
    val botId: String,
    val configProfileId: String,
    val personaId: String,
    val providerId: String,
    val timezone: String = ZoneId.systemDefault().id,
)

sealed interface ScheduledTaskIntentGuardResult {
    data class Created(
        val jobId: String,
        val replyText: String,
    ) : ScheduledTaskIntentGuardResult

    data class Failed(
        val code: String,
        val replyText: String,
    ) : ScheduledTaskIntentGuardResult
}

class ScheduledTaskIntentGuard(
    private val taskPort: ActiveCapabilityTaskPort,
    private val naturalLanguageParser: ActiveCapabilityNaturalLanguageParser,
    private val promptStrings: ActiveCapabilityPromptStrings,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    @Inject
    constructor(
        taskPort: ActiveCapabilityTaskPort,
        naturalLanguageParser: ActiveCapabilityNaturalLanguageParser,
        promptStrings: ActiveCapabilityPromptStrings,
    ) : this(
        taskPort = taskPort,
        naturalLanguageParser = naturalLanguageParser,
        promptStrings = promptStrings,
        clock = { System.currentTimeMillis() },
    )

    suspend fun tryCreateFallback(
        text: String,
        context: ScheduledTaskIntentGuardContext,
    ): ScheduledTaskIntentGuardResult? {
        val rawText = text.trim()
        if (!context.proactiveEnabled || rawText.isBlank()) return null
        if (!rawText.hasReminderIntent()) return null
        if (rawText.hasRecurringIntent()) {
            val recurringSchedule = rawText.inferRecurringSchedule(clock(), context.timezone)
                ?: return null
            val noteBody = rawText.extractReminderNote().ifBlank { rawText }
            val note = noteBody.toReminderNote(promptStrings.guardReminderNotePrefix)
            val request = CronTaskCreateRequest(
                payload = mapOf(
                    "run_once" to false,
                    "name" to note.take(32),
                    "note" to note,
                    "cron_expression" to recurringSchedule.cronExpression,
                    "timezone" to context.timezone,
                    "enabled" to true,
                ),
                targetPlatform = context.platform,
                targetConversationId = context.conversationId,
                targetBotId = context.botId,
                targetConfigProfileId = context.configProfileId,
                targetPersonaId = context.personaId,
                targetProviderId = context.providerId,
                targetOrigin = "host_intent_guard",
            )
            return request.createWith(taskPort, promptStrings)
        }

        val runAt = naturalLanguageParser.inferRunAt(rawText, clock(), context.timezone)
            ?: return null
        val noteBody = rawText.extractReminderNote().ifBlank { rawText }
        val note = noteBody.toReminderNote(promptStrings.guardReminderNotePrefix)
        val request = CronTaskCreateRequest(
            payload = mapOf(
                "run_once" to true,
                "name" to note.take(32),
                "note" to note,
                "run_at" to runAt.toOffsetDateTime().toString(),
                "timezone" to context.timezone,
                "enabled" to true,
            ),
            targetPlatform = context.platform,
            targetConversationId = context.conversationId,
            targetBotId = context.botId,
            targetConfigProfileId = context.configProfileId,
            targetPersonaId = context.personaId,
            targetProviderId = context.providerId,
            targetOrigin = "host_intent_guard",
        )
        return request.createWith(taskPort, promptStrings)
    }
}

private suspend fun CronTaskCreateRequest.createWith(
    taskPort: ActiveCapabilityTaskPort,
    promptStrings: ActiveCapabilityPromptStrings,
): ScheduledTaskIntentGuardResult {
    return when (val result = taskPort.createFutureTask(this)) {
        is CronTaskCreateResult.Created -> ScheduledTaskIntentGuardResult.Created(
            jobId = result.jobId,
            replyText = promptStrings.guardCreatedReply,
        )
        is CronTaskCreateResult.Failed -> ScheduledTaskIntentGuardResult.Failed(
            code = result.code,
            replyText = when (result.code) {
                "past_schedule" -> promptStrings.guardPastScheduleReply
                else -> promptStrings.guardFailedReply(result.message)
            },
        )
    }
}

private fun String.toReminderNote(prefix: String): String {
    return if (startsWith(prefix)) this else "$prefix$this"
}

private data class RecurringSchedule(
    val cronExpression: String,
)

private fun String.inferRecurringSchedule(
    nowMillis: Long,
    timezone: String,
): RecurringSchedule? {
    val text = trim()
    val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
    val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val time = inferRecurringTime(text, LocalTime.of(now.hour, now.minute)) ?: return null
    val minute = time.minute
    val hour = time.hour

    if (workdayRegex.containsMatchIn(text)) {
        return RecurringSchedule("$minute $hour * * 1-5")
    }

    val monthlyDay = monthlyDayRegex.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toScheduleNumber()
        ?.toInt()
        ?.takeIf { it in 1..31 }
    if (monthlyRegex.containsMatchIn(text)) {
        return monthlyDay?.let { RecurringSchedule("$minute $hour $it * *") }
    }

    val dayOfWeek = weeklyDayRegex.find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toCronDayOfWeek()
    if (weeklyRegex.containsMatchIn(text) || dayOfWeek != null) {
        return dayOfWeek?.let { RecurringSchedule("$minute $hour * * $it") }
    }

    if (dailyRegex.containsMatchIn(text)) {
        return RecurringSchedule("$minute $hour * * *")
    }

    return null
}

private fun inferRecurringTime(
    text: String,
    currentTime: LocalTime,
): LocalTime? {
    if (currentTimeRegex.containsMatchIn(text)) return currentTime

    clockTimeRegex.find(text)?.let { match ->
        val hour = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val minute = match.groupValues.getOrNull(2)?.toIntOrNull() ?: return null
        return LocalTime.of(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    recurringTimeRegex.find(text)?.let { match ->
        val daypart = match.groupValues.getOrNull(1).orEmpty()
        val rawHour = match.groupValues.getOrNull(2).orEmpty()
        val rawMinute = match.groupValues.getOrNull(3).orEmpty()
        val hour = rawHour.toScheduleNumber()?.toInt() ?: return null
        val adjustedHour = adjustHourForDaypart(hour, daypart)
        val minute = when {
            rawMinute.contains("\u534a") -> 30
            rawMinute.isNotBlank() -> rawMinute.filter(Char::isDigit).toIntOrNull() ?: 0
            else -> 0
        }
        return LocalTime.of(adjustedHour.coerceIn(0, 23), minute.coerceIn(0, 59))
    }

    defaultDaypartTime(text)?.let { return it }

    return null
}

private fun adjustHourForDaypart(
    hour: Int,
    daypart: String,
): Int {
    return when {
        afternoonOrEveningRegex.containsMatchIn(daypart) && hour in 1..11 -> hour + 12
        noonRegex.containsMatchIn(daypart) && hour in 1..10 -> hour + 12
        else -> hour
    }
}

private fun defaultDaypartTime(text: String): LocalTime? {
    return when {
        eveningRegex.containsMatchIn(text) -> LocalTime.of(20, 0)
        noonRegex.containsMatchIn(text) -> LocalTime.of(12, 0)
        afternoonRegex.containsMatchIn(text) -> LocalTime.of(15, 0)
        morningRegex.containsMatchIn(text) -> LocalTime.of(9, 0)
        else -> null
    }
}

private fun String.toCronDayOfWeek(): Int? {
    return when (trim()) {
        "0", "\u65e5", "\u5929" -> 0
        "1", "\u4e00" -> 1
        "2", "\u4e8c" -> 2
        "3", "\u4e09" -> 3
        "4", "\u56db" -> 4
        "5", "\u4e94" -> 5
        "6", "\u516d" -> 6
        else -> null
    }
}

private fun String.toScheduleNumber(): Long? {
    return trim().toLongOrNull() ?: toSimpleScheduleChineseNumber()
}

private fun String.toSimpleScheduleChineseNumber(): Long? {
    val normalized = trim()
    if (normalized.isEmpty()) return null
    val digitMap = mapOf(
        '\u96f6' to 0,
        '\u3007' to 0,
        '\u4e00' to 1,
        '\u4e8c' to 2,
        '\u4e24' to 2,
        '\u4fe9' to 2,
        '\u4e09' to 3,
        '\u56db' to 4,
        '\u4e94' to 5,
        '\u516d' to 6,
        '\u4e03' to 7,
        '\u516b' to 8,
        '\u4e5d' to 9,
    )
    if (normalized == "\u5341") return 10L
    if ('\u5341' in normalized) {
        val parts = normalized.split('\u5341', limit = 2)
        val tens = parts[0].takeIf { it.isNotEmpty() }?.singleOrNull()?.let(digitMap::get) ?: 1
        val ones = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.singleOrNull()?.let(digitMap::get) ?: 0
        return (tens * 10 + ones).toLong()
    }
    return normalized.singleOrNull()?.let(digitMap::get)?.toLong()
}

private val numberTokenPattern = "[0-9]{1,2}|[\u96f6\u3007\u4e00\u4e8c\u4e24\u4fe9\u4e09\u56db\u4e94\u516d\u4e03\u516b\u4e5d\u5341]{1,3}"
private val currentTimeRegex = Regex("(\u8fd9\u4e2a\u65f6\u95f4|\u73b0\u5728)")
private val clockTimeRegex = Regex("\\b([01]?\\d|2[0-3])\\s*[:\uff1a]\\s*([0-5]\\d)\\b")
private val recurringTimeRegex = Regex(
    "(\u51cc\u6668|\u65e9\u4e0a|\u4e0a\u5348|\u4e2d\u5348|\u4e0b\u5348|\u665a\u4e0a|\u665a\u95f4|\u4eca\u665a)?\\s*($numberTokenPattern)\\s*\u70b9\\s*(\u534a|[0-5]?\\d\\s*\u5206?)?",
)
private val dailyRegex = Regex("(\u6bcf\u5929|\u6bcf\u65e5|daily)", RegexOption.IGNORE_CASE)
private val workdayRegex = Regex("(\u5de5\u4f5c\u65e5|\u5de5\u4f5c\u5929|weekdays?)", RegexOption.IGNORE_CASE)
private val weeklyRegex = Regex("(\u6bcf\u5468|\u6bcf\u661f\u671f|\u6bcf\u793c\u62dc|weekly)", RegexOption.IGNORE_CASE)
private val weeklyDayRegex = Regex("(?:\u6bcf)?(?:\u5468|\u661f\u671f|\u793c\u62dc)\\s*([0-6\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u65e5\u5929])")
private val monthlyRegex = Regex("(\u6bcf\u6708|monthly)", RegexOption.IGNORE_CASE)
private val monthlyDayRegex = Regex("\u6bcf\u6708\\s*($numberTokenPattern)\\s*(?:\u53f7|\u65e5)")
private val morningRegex = Regex("(\u65e9\u4e0a|\u4e0a\u5348)")
private val noonRegex = Regex("\u4e2d\u5348")
private val afternoonRegex = Regex("\u4e0b\u5348")
private val eveningRegex = Regex("(\u665a\u4e0a|\u665a\u95f4|\u4eca\u665a)")
private val afternoonOrEveningRegex = Regex("(\u4e0b\u5348|\u665a\u4e0a|\u665a\u95f4|\u4eca\u665a)")

private fun String.hasReminderIntent(): Boolean {
    return reminderIntentRegex.containsMatchIn(this)
}

private fun String.hasRecurringIntent(): Boolean {
    return recurringIntentRegex.containsMatchIn(this)
}

private fun String.extractReminderNote(): String {
    val normalized = trim()
    val afterReminder = reminderCommandRegex.find(normalized)
        ?.let { match -> normalized.substring(match.range.last + 1).trim() }
        .orEmpty()
    val candidate = afterReminder.ifBlank {
        normalized
            .replace(relativeTimePrefixRegex, "")
            .replace(minuteOfHourPrefixRegex, "")
            .trim()
    }
    return candidate
        .removePrefix("\u6211")
        .removePrefix("\u4e00\u4e0b")
        .trim()
}

private val reminderIntentRegex = Regex(
    "(\u63d0\u9192|\u53eb\u6211|\u901a\u77e5\u6211|\u5230\u70b9|\u5b9a\u65f6|\u95f9\u949f|remind|timer)",
    RegexOption.IGNORE_CASE,
)
private val recurringIntentRegex = Regex(
    "(\u6bcf\u5929|\u6bcf\u65e5|\u6bcf\u5468|\u6bcf\u6708|\u6bcf\u5e74|\u5de5\u4f5c\u65e5|\u5468[0-9\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u65e5\u5929]|\u661f\u671f[0-9\u4e00\u4e8c\u4e09\u56db\u4e94\u516d\u65e5\u5929]|daily|weekly|monthly)",
    RegexOption.IGNORE_CASE,
)
private val reminderCommandRegex = Regex(
    "(\u63d0\u9192\u6211|\u63d0\u9192\u7528\u6237|\u63d0\u9192|\u53eb\u6211|\u901a\u77e5\u6211|\u5230\u70b9\u53eb\u6211|\u5230\u70b9\u63d0\u9192\u6211)",
)
private val relativeTimePrefixRegex = Regex(
    "^.*?(\u540e|\u660e\u5929|\u540e\u5929|\u4eca\u665a|\u4e0a\u5348|\u4e2d\u5348|\u4e0b\u5348|\u665a\u4e0a)",
)
private val minuteOfHourPrefixRegex = Regex("^(?:\u5230|\u7b49\u5230)?\\s*[0-5]?\\d\\s*\u5206(?:\u7684\u65f6\u5019|\u949f)?")

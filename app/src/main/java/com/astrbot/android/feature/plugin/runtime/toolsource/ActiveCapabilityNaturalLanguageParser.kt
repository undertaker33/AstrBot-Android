package com.astrbot.android.feature.plugin.runtime.toolsource

import android.content.Context
import com.astrbot.android.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class ActiveCapabilityNaturalLanguageLexicon(
    val tomorrowTokens: List<String>,
    val dayAfterTomorrowTokens: List<String>,
    val tonightTokens: List<String>,
    val morningTokens: List<String>,
    val noonTokens: List<String>,
    val afternoonTokens: List<String>,
    val eveningTokens: List<String>,
    val halfHourTokens: List<String>,
    val oneAndHalfHourTokens: List<String>,
) {
    companion object {
        fun default(): ActiveCapabilityNaturalLanguageLexicon {
            return ActiveCapabilityNaturalLanguageLexicon(
                tomorrowTokens = listOf("明天", "明早", "明晚", "tomorrow"),
                dayAfterTomorrowTokens = listOf("后天", "day after tomorrow"),
                tonightTokens = listOf("今晚", "tonight"),
                morningTokens = listOf("早上", "上午", "明早", "morning"),
                noonTokens = listOf("中午", "noon"),
                afternoonTokens = listOf("下午", "afternoon"),
                eveningTokens = listOf("晚上", "晚间", "明晚", "evening"),
                halfHourTokens = listOf("半小时后", "半个小时后", "half an hour later", "in half an hour"),
                oneAndHalfHourTokens = listOf("一个半小时后", "一小时半后", "one and a half hours later", "in one and a half hours"),
            )
        }

        fun fromResources(@ApplicationContext context: Context): ActiveCapabilityNaturalLanguageLexicon {
            val resources = context.resources
            return ActiveCapabilityNaturalLanguageLexicon(
                tomorrowTokens = resources.getStringArray(R.array.active_capability_nl_tomorrow_tokens).toList(),
                dayAfterTomorrowTokens = resources.getStringArray(R.array.active_capability_nl_day_after_tomorrow_tokens).toList(),
                tonightTokens = resources.getStringArray(R.array.active_capability_nl_tonight_tokens).toList(),
                morningTokens = resources.getStringArray(R.array.active_capability_nl_morning_tokens).toList(),
                noonTokens = resources.getStringArray(R.array.active_capability_nl_noon_tokens).toList(),
                afternoonTokens = resources.getStringArray(R.array.active_capability_nl_afternoon_tokens).toList(),
                eveningTokens = resources.getStringArray(R.array.active_capability_nl_evening_tokens).toList(),
                halfHourTokens = resources.getStringArray(R.array.active_capability_nl_half_hour_tokens).toList(),
                oneAndHalfHourTokens = resources.getStringArray(R.array.active_capability_nl_one_and_half_hour_tokens).toList(),
            )
        }
    }
}

class ActiveCapabilityNaturalLanguageParser(
    private val lexicon: ActiveCapabilityNaturalLanguageLexicon = ActiveCapabilityNaturalLanguageLexicon.default(),
) {
    fun inferRunAt(
        text: String,
        now: Long,
        timezone: String,
    ): ZonedDateTime? {
        val normalized = text.trim()
        if (normalized.isBlank()) return null
        val zone = ZoneId.of(timezone)
        val nowAtZone = Instant.ofEpochMilli(now).atZone(zone)

        inferRelativeDelay(normalized, nowAtZone)?.let { return it }
        inferSpecificDayTime(normalized, nowAtZone)?.let { return it }
        return inferDaypart(normalized, nowAtZone)
    }

    private fun inferRelativeDelay(
        text: String,
        now: ZonedDateTime,
    ): ZonedDateTime? {
        if (lexicon.oneAndHalfHourTokens.anyTokenIn(text)) return now.plusMinutes(90)
        if (lexicon.halfHourTokens.anyTokenIn(text)) return now.plusMinutes(30)

        relativeDelayMatchers.firstNotNullOfOrNull { matcher ->
            matcher(text, now)
        }?.let { return it }

        return null
    }

    private fun inferSpecificDayTime(
        text: String,
        now: ZonedDateTime,
    ): ZonedDateTime? {
        val hour = chineseHourRegex.find(text)
            ?.groupValues
            ?.getOrNull(2)
            ?.toFlexibleLong()
            ?.toInt()
            ?: englishPmRegex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        if (hour == null) return null

        val dayOffset = dayOffsetFor(text)
        val adjustedHour = when {
            containsAny(text, lexicon.afternoonTokens + lexicon.eveningTokens + lexicon.tonightTokens) && hour in 1..11 -> hour + 12
            else -> hour
        }.coerceIn(0, 23)
        return now.plusDays(dayOffset).with(LocalTime.of(adjustedHour, 0))
    }

    private fun inferDaypart(
        text: String,
        now: ZonedDateTime,
    ): ZonedDateTime? {
        val dayOffset = dayOffsetFor(text)
        val mentionsDay = dayOffset > 0L || containsAny(text, lexicon.tonightTokens)
        if (!mentionsDay) return null
        val time = when {
            containsAny(text, lexicon.tonightTokens) -> LocalTime.of(20, 0)
            containsAny(text, lexicon.eveningTokens) -> LocalTime.of(20, 0)
            containsAny(text, lexicon.noonTokens) -> LocalTime.of(12, 0)
            containsAny(text, lexicon.afternoonTokens) -> LocalTime.of(15, 0)
            containsAny(text, lexicon.morningTokens) -> LocalTime.of(9, 0)
            else -> LocalTime.of(9, 0)
        }
        return now.plusDays(dayOffset).with(time)
    }

    private fun dayOffsetFor(text: String): Long {
        return when {
            containsAny(text, lexicon.dayAfterTomorrowTokens) -> 2L
            containsAny(text, lexicon.tomorrowTokens) -> 1L
            else -> 0L
        }
    }
}

private val chineseHourRegex = Regex("(上午|早上|中午|下午|晚上|今晚|明天|明早|明晚|后天)?\\s*([零一二两俩三四五六七八九十\\d]{1,3})\\s*点")
private val englishPmRegex = Regex("\\b(\\d{1,2})\\s*(pm|p\\.m\\.)\\b", RegexOption.IGNORE_CASE)

private val relativeDelayMatchers: List<(String, ZonedDateTime) -> ZonedDateTime?> = listOf(
    { text, now ->
        Regex("([零一二两俩三四五六七八九十百\\d]+)\\s*(分钟|min|mins|minute|minutes)后?", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFlexibleLong()
            ?.let(now::plusMinutes)
    },
    { text, now ->
        Regex("([零一二两俩三四五六七八九十百\\d]+)\\s*(小时|hr|hrs|hour|hours)后?", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFlexibleLong()
            ?.let(now::plusHours)
    },
    { text, now ->
        Regex("([零一二两俩三四五六七八九十百\\d]+)\\s*(天|day|days)后?", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toFlexibleLong()
            ?.let(now::plusDays)
    },
    { text, now ->
        Regex("in\\s+(\\d+)\\s*(min|mins|minute|minutes)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.let(now::plusMinutes)
    },
    { text, now ->
        Regex("in\\s+(\\d+)\\s*(hr|hrs|hour|hours)", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?.let(now::plusHours)
    },
)

private fun List<String>.anyTokenIn(text: String): Boolean = any { token ->
    token.isNotBlank() && text.contains(token, ignoreCase = true)
}

private fun containsAny(text: String, tokens: List<String>): Boolean = tokens.anyTokenIn(text)

private fun String.toFlexibleLong(): Long? {
    return toLongOrNull() ?: toSimpleChineseNumber()
}

private fun String.toSimpleChineseNumber(): Long? {
    val normalized = trim()
    if (normalized.isEmpty()) return null
    val digitMap = mapOf(
        '零' to 0,
        '一' to 1,
        '二' to 2,
        '两' to 2,
        '俩' to 2,
        '三' to 3,
        '四' to 4,
        '五' to 5,
        '六' to 6,
        '七' to 7,
        '八' to 8,
        '九' to 9,
    )
    if (normalized == "十") return 10L
    if ('十' in normalized) {
        val parts = normalized.split('十', limit = 2)
        val tens = parts[0].takeIf { it.isNotEmpty() }?.singleOrNull()?.let(digitMap::get) ?: 1
        val ones = parts.getOrNull(1)?.takeIf { it.isNotEmpty() }?.singleOrNull()?.let(digitMap::get) ?: 0
        return (tens * 10 + ones).toLong()
    }
    return normalized.singleOrNull()?.let(digitMap::get)?.toLong()
}

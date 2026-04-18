package com.astrbot.android.feature.cron.runtime

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Minimal cron expression parser supporting standard 5-field cron syntax:
 * `minute hour day-of-month month day-of-week`
 *
 * Supports: exact values, `*`, `,` lists, `-` ranges, and `/` step.
 * Does NOT support `L`, `W`, `#`, `?`, or 6-field (seconds) cron.
 */
internal object CronExpressionParser {

    fun nextFireTime(expression: String, afterEpochMillis: Long, timezone: String): Long {
        if (expression.isBlank()) return afterEpochMillis + 60_000L
        val zone = runCatching { ZoneId.of(timezone) }.getOrDefault(ZoneId.systemDefault())
        val parts = expression.trim().split("\\s+".toRegex())
        if (parts.size < 5) return afterEpochMillis + 60_000L

        val minuteField = parseField(parts[0], 0, 59)
        val hourField = parseField(parts[1], 0, 23)
        val dayField = parseField(parts[2], 1, 31)
        val monthField = parseField(parts[3], 1, 12)
        val dowField = parseField(parts[4], 0, 6) // 0=Sunday

        val start = ZonedDateTime.ofInstant(Instant.ofEpochMilli(afterEpochMillis), zone).plusMinutes(1)
            .withSecond(0).withNano(0)
        var candidate = start
        val limit = start.plusYears(1) // safety limit
        while (candidate.isBefore(limit)) {
            if (candidate.monthValue in monthField &&
                candidate.dayOfMonth in dayField &&
                candidate.dayOfWeekCronValue() in dowField &&
                candidate.hour in hourField &&
                candidate.minute in minuteField
            ) {
                return candidate.toInstant().toEpochMilli()
            }
            candidate = candidate.plusMinutes(1)
        }
        return afterEpochMillis + 3_600_000L // fallback 1 hour
    }

    private fun parseField(field: String, min: Int, max: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        for (token in field.split(",")) {
            val stepParts = token.split("/")
            val rangePart = stepParts[0]
            val step = stepParts.getOrNull(1)?.toIntOrNull() ?: 1

            if (rangePart == "*") {
                for (i in min..max step step) result.add(i)
            } else if ("-" in rangePart) {
                val (lo, hi) = rangePart.split("-").map { it.toInt() }
                for (i in lo..hi step step) result.add(i.coerceIn(min, max))
            } else {
                rangePart.toIntOrNull()?.let { result.add(it.coerceIn(min, max)) }
            }
        }
        return result
    }

    private fun ZonedDateTime.dayOfWeekCronValue(): Int {
        // java.time DayOfWeek: MONDAY=1 .. SUNDAY=7 → cron: SUNDAY=0 .. SATURDAY=6
        return dayOfWeek.value % 7
    }
}

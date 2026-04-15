package com.astrbot.android.ui.plugin.schema

import kotlin.math.min

internal fun resolveButtonGroupColumns(
    itemCount: Int,
    maxColumns: Int,
    maxVisibleLines: Int,
    lineCountsForColumns: (Int) -> List<Int>,
): Int {
    if (itemCount <= 0) return 1
    if (itemCount <= 2) return min(itemCount, maxColumns)

    var columns = min(itemCount, maxColumns)
    while (columns > 1) {
        val lineCounts = lineCountsForColumns(columns)
        if (lineCounts.size == itemCount && lineCounts.all { it > maxVisibleLines }) {
            columns -= 1
            continue
        }
        break
    }
    return columns
}

internal fun normalizeButtonRowLineCounts(
    lineCounts: List<Int>,
    columns: Int,
    maxVisibleLines: Int,
): List<Int> {
    if (lineCounts.isEmpty()) return emptyList()
    return lineCounts.chunked(columns.coerceAtLeast(1)).map { row ->
        row.maxOrNull()?.coerceIn(1, maxVisibleLines) ?: 1
    }
}

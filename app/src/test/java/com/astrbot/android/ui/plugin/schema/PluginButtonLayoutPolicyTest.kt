package com.astrbot.android.ui.plugin.schema

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginButtonLayoutPolicyTest {

    @Test
    fun `three or more buttons fall back to fewer columns when all labels exceed two lines`() {
        val columns = resolveButtonGroupColumns(
            itemCount = 4,
            maxColumns = 3,
            maxVisibleLines = 2,
        ) { requestedColumns ->
            when (requestedColumns) {
                3 -> listOf(3, 4, 3, 3)
                2 -> listOf(2, 2, 2, 2)
                else -> listOf(1, 1, 1, 1)
            }
        }

        assertEquals(2, columns)
    }

    @Test
    fun `two buttons keep the same row and normalize line count`() {
        val columns = resolveButtonGroupColumns(
            itemCount = 2,
            maxColumns = 3,
            maxVisibleLines = 2,
        ) { listOf(3, 3) }

        val rowLineCounts = normalizeButtonRowLineCounts(
            lineCounts = listOf(1, 2),
            columns = columns,
            maxVisibleLines = 2,
        )

        assertEquals(2, columns)
        assertEquals(listOf(2), rowLineCounts)
    }

    @Test
    fun `row line count is shared by parallel buttons`() {
        val rowLineCounts = normalizeButtonRowLineCounts(
            lineCounts = listOf(1, 2, 1, 1),
            columns = 2,
            maxVisibleLines = 2,
        )

        assertEquals(listOf(2, 1), rowLineCounts)
    }
}

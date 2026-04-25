package com.astrbot.android.feature.qq.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqNewsReplyFormatterTest {
    @Test
    fun formats_plain_news_text_as_bullet_points() {
        val segments = QqNewsReplyFormatter.formatSegments(
            "A news item happened. Another update followed.",
        )

        assertEquals(1, segments.size)
        assertTrue(segments.single().contains("1. A news item happened"))
        assertTrue(segments.single().contains("2. Another update followed"))
    }

    @Test
    fun splits_long_news_reply_on_bullet_boundaries() {
        val text = listOf(
            "1. ${"A".repeat(80)}",
            "2. ${"B".repeat(80)}",
            "3. ${"C".repeat(80)}",
        ).joinToString("\n")

        val segments = QqNewsReplyFormatter.formatSegments(text, maxChars = 150)

        assertEquals(3, segments.size)
        assertTrue(segments.all { it.length <= 150 })
        assertTrue(segments[0].startsWith("1."))
        assertTrue(segments[1].startsWith("2."))
        assertTrue(segments[2].startsWith("3."))
    }

    @Test
    fun keeps_single_existing_bullet_marker_without_nested_numbering() {
        val segments = QqNewsReplyFormatter.formatSegments(
            "1. Major update: confirmed fact.",
        )

        assertEquals(listOf("1. Major update: confirmed fact."), segments)
    }
}

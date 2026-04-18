package com.astrbot.android.feature.qq.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqKeywordDetectorTest {
    @Test
    fun invalid_regex_is_ignored() {
        val detector = QqKeywordDetector(listOf("[abc"))

        assertFalse(detector.matches("safe text"))
    }

    @Test
    fun matches_when_any_valid_pattern_is_hit() {
        val detector = QqKeywordDetector(listOf("forbidden", "密钥\\d+"))

        assertTrue(detector.matches("this contains forbidden content"))
        assertTrue(detector.matches("请不要输出密钥123"))
        assertFalse(detector.matches("all clear"))
    }
}

package com.astrbot.android.feature.qq.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqWakeWordPolicyTest {
    @Test
    fun matches_wake_word_for_normal_user_when_admin_only_disabled() {
        assertTrue(
            QqWakeWordPolicy.matches(
                text = "hello astrbot",
                wakeWords = listOf("astrbot"),
                adminOnlyEnabled = false,
                isAdmin = false,
            ),
        )
    }

    @Test
    fun rejects_wake_word_for_non_admin_when_admin_only_enabled() {
        assertFalse(
            QqWakeWordPolicy.matches(
                text = "hello astrbot",
                wakeWords = listOf("astrbot"),
                adminOnlyEnabled = true,
                isAdmin = false,
            ),
        )
    }
}

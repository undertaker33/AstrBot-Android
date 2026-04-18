package com.astrbot.android.feature.qq.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqWhitelistMatcherTest {
    @Test
    fun matches_user_group_and_user_group_entries() {
        val entries = listOf("10001", "20001", "10002_20002")

        assertTrue(QqWhitelistMatcher.isAllowed(entries, userId = "10001", groupId = "99999"))
        assertTrue(QqWhitelistMatcher.isAllowed(entries, userId = "99999", groupId = "20001"))
        assertTrue(QqWhitelistMatcher.isAllowed(entries, userId = "10002", groupId = "20002"))
        assertFalse(QqWhitelistMatcher.isAllowed(entries, userId = "99999", groupId = "99999"))
    }

    @Test
    fun empty_entries_do_not_match() {
        assertFalse(QqWhitelistMatcher.isAllowed(emptyList(), userId = "1", groupId = "2"))
    }
}

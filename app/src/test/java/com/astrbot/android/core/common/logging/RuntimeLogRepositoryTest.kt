package com.astrbot.android.core.common.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RuntimeLogRepositoryTest {
    @After
    fun tearDown() {
        RuntimeLogRepository.clear()
    }

    @Test
    fun append_retains_large_recent_buffer_for_runtime_diagnostics() {
        RuntimeLogRepository.clear()

        repeat(2_105) { index ->
            RuntimeLogRepository.append("entry-$index")
        }
        RuntimeLogRepository.flush()

        val snapshot = RuntimeLogRepository.logs.value
        assertEquals(2_000, snapshot.size)
        assertTrue(snapshot.first().contains("entry-105"))
        assertTrue(snapshot.last().contains("entry-2104"))
    }
}

package com.astrbot.android.feature.qq.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QqRateLimiterTest {
    @Test
    fun drops_when_limit_exceeded_inside_window() {
        val timestamps = ArrayDeque(listOf(1000L, 1000L, 1000L))
        val limiter = QqRateLimiter(now = { timestamps.removeFirst() })

        assertTrue(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 2).allowed)
        assertTrue(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 2).allowed)
        assertFalse(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 2).allowed)
    }

    @Test
    fun allows_again_after_window_expires() {
        val timestamps = ArrayDeque(listOf(1000L, 1000L, 1011L))
        val limiter = QqRateLimiter(now = { timestamps.removeFirst() })

        assertTrue(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1).allowed)
        assertFalse(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1).allowed)
        assertTrue(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1).allowed)
    }

    @Test
    fun stash_strategy_enqueues_and_releases_in_order_after_window() {
        var now = 1000L
        val limiter = QqRateLimiter(now = { now })

        val first = limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1, strategy = "stash", payload = "a")
        val second = limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1, strategy = "stash", payload = "b")
        val third = limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1, strategy = "stash", payload = "c")

        assertTrue(first.allowed)
        assertFalse(second.allowed)
        assertFalse(third.allowed)
        assertEquals(listOf("b", "c"), limiter.drainReady("source"))

        now = 1011L
        assertEquals(listOf("b"), limiter.releaseReady("source", windowSeconds = 10, maxCount = 1))
        assertEquals(listOf("c"), limiter.drainReady("source"))
    }

    @Test
    fun stash_strategy_releases_one_payload_per_available_slot() {
        var now = 1000L
        val limiter = QqRateLimiter(now = { now })

        assertTrue(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1, strategy = "stash", payload = "a").allowed)
        assertFalse(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1, strategy = "stash", payload = "b").allowed)
        assertFalse(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1, strategy = "stash", payload = "c").allowed)

        now = 1011L
        assertEquals(listOf("b"), limiter.releaseReady("source", windowSeconds = 10, maxCount = 1))
        assertEquals(listOf("c"), limiter.drainReady("source"))

        now = 1022L
        assertEquals(listOf("c"), limiter.releaseReady("source", windowSeconds = 10, maxCount = 1))
    }

    @Test
    fun drop_strategy_never_stashes_payloads() {
        val timestamps = ArrayDeque(listOf(1000L, 1000L))
        val limiter = QqRateLimiter(now = { timestamps.removeFirst() })

        assertTrue(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1, strategy = "drop", payload = "a").allowed)
        assertFalse(limiter.tryAcquire("source", windowSeconds = 10, maxCount = 1, strategy = "drop", payload = "b").allowed)
        assertTrue(limiter.drainReady("source").isEmpty())
    }
}

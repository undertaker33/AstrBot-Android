package com.astrbot.android.feature.qq.runtime

import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

class QqRateLimiter(
    private val now: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    private val buckets = ConcurrentHashMap<String, ArrayDeque<Long>>()
    private val stashes = ConcurrentHashMap<String, ArrayDeque<Any>>()

    fun tryAcquire(
        sourceKey: String,
        windowSeconds: Int,
        maxCount: Int,
        strategy: String = "drop",
        payload: Any? = null,
    ): QqRateLimitResult {
        if (windowSeconds <= 0 || maxCount <= 0) {
            return QqRateLimitResult(allowed = true)
        }

        val current = now()
        val bucket = buckets.getOrPut(sourceKey) { ArrayDeque() }
        synchronized(bucket) {
            while (bucket.isNotEmpty() && current - bucket.first() >= windowSeconds) {
                bucket.removeFirst()
            }
            if (bucket.size >= maxCount) {
                if (strategy == "stash" && payload != null) {
                    val stash = stashes.getOrPut(sourceKey) { ArrayDeque() }
                    synchronized(stash) {
                        stash.addLast(payload)
                    }
                }
                return QqRateLimitResult(
                    allowed = false,
                    stashed = strategy == "stash" && payload != null,
                )
            }
            bucket.addLast(current)
            return QqRateLimitResult(allowed = true)
        }
    }

    fun drainReady(sourceKey: String): List<Any> {
        val stash = stashes[sourceKey] ?: return emptyList()
        synchronized(stash) {
            return stash.toList()
        }
    }

    fun releaseReady(sourceKey: String, windowSeconds: Int, maxCount: Int): List<Any> {
        if (windowSeconds <= 0 || maxCount <= 0) return emptyList()
        val current = now()
        val bucket = buckets.getOrPut(sourceKey) { ArrayDeque() }
        val releaseCount = synchronized(bucket) {
            while (bucket.isNotEmpty() && current - bucket.first() >= windowSeconds) {
                bucket.removeFirst()
            }
            val availableSlots = (maxCount - bucket.size).coerceAtLeast(0)
            if (availableSlots == 0) {
                return emptyList()
            }
            availableSlots
        }
        val stash = stashes[sourceKey] ?: return emptyList()
        synchronized(stash) {
            if (stash.isEmpty()) return emptyList()
            val ready = buildList {
                repeat(releaseCount.coerceAtMost(stash.size)) {
                    add(stash.removeFirst())
                }
            }
            synchronized(bucket) {
                repeat(ready.size) {
                    bucket.addLast(current)
                }
            }
            return ready
        }
    }
}

data class QqRateLimitResult(
    val allowed: Boolean,
    val stashed: Boolean = false,
)

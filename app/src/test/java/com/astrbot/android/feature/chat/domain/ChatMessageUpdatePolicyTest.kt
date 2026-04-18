package com.astrbot.android.feature.chat.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageUpdatePolicyTest {
    private val policy = ChatMessageUpdatePolicy(minIntervalMs = 120L, minDeltaChars = 24)

    @Test
    fun first_delta_is_published() {
        assertTrue(
            policy.shouldPublish(
                previousPublishedContent = "",
                nextContent = "hello",
                elapsedSinceLastPublishMs = 0L,
            ),
        )
    }

    @Test
    fun small_delta_before_interval_is_not_published() {
        assertFalse(
            policy.shouldPublish(
                previousPublishedContent = "hello",
                nextContent = "hello world",
                elapsedSinceLastPublishMs = 60L,
            ),
        )
    }

    @Test
    fun large_delta_is_published_even_before_interval() {
        assertTrue(
            policy.shouldPublish(
                previousPublishedContent = "hello",
                nextContent = "hello this is a long enough assistant delta",
                elapsedSinceLastPublishMs = 60L,
            ),
        )
    }

    @Test
    fun final_content_is_always_published() {
        assertTrue(
            policy.shouldPublish(
                previousPublishedContent = "partial",
                nextContent = "partial final",
                elapsedSinceLastPublishMs = 0L,
                isFinal = true,
            ),
        )
    }
}

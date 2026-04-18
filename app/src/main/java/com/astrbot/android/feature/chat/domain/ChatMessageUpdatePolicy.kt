package com.astrbot.android.feature.chat.domain

class ChatMessageUpdatePolicy(
    private val minIntervalMs: Long = 120L,
    private val minDeltaChars: Int = 24,
) {
    fun shouldPublish(
        previousPublishedContent: String,
        nextContent: String,
        elapsedSinceLastPublishMs: Long,
        isFinal: Boolean = false,
    ): Boolean {
        if (isFinal) return true
        if (nextContent == previousPublishedContent) return false
        if (previousPublishedContent.isEmpty()) return true
        val deltaChars = nextContent.length - previousPublishedContent.length
        return elapsedSinceLastPublishMs >= minIntervalMs || deltaChars >= minDeltaChars
    }
}

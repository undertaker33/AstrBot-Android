package com.astrbot.android.runtime.search

import com.astrbot.android.core.runtime.search.WebSearchPromptGuidance
import com.astrbot.android.core.runtime.search.WebSearchPromptStringProvider
import com.astrbot.android.core.runtime.search.WebSearchTriggerIntent
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchPromptGuidanceTest {
    @Test
    fun newsGuidanceRequiresSearchAndCommentaryOnlyFollowup() {
        val guidance = WebSearchPromptGuidance.forMessage("\u4eca\u65e5\u65b0\u95fb", TestWebSearchPromptStrings)

        assertTrue(guidance!!.contains("must call web_search"))
        assertTrue(guidance.contains("do not repeat news items", ignoreCase = true))
        assertTrue(guidance.contains("brief evaluation or commentary"))
        assertTrue(guidance.contains("Do not invent current facts"))
    }

    @Test
    fun weatherGuidanceRequiresSearchAndSourceGrounding() {
        val guidance = WebSearchPromptGuidance.forMessage("\u798f\u5dde\u4eca\u5929\u5929\u6c14", TestWebSearchPromptStrings)

        assertTrue(guidance!!.contains("must call web_search"))
        assertTrue(guidance.contains("base the answer only on returned sources"))
        assertTrue(guidance.contains("weather"))
    }

    @Test
    fun ordinaryChatDoesNotInjectSearchGuidance() {
        assertNull(WebSearchPromptGuidance.forMessage("\u665a\u4e0a\u597d", TestWebSearchPromptStrings))
    }

    private object TestWebSearchPromptStrings : WebSearchPromptStringProvider {
        override fun guidanceFor(intent: WebSearchTriggerIntent): String? {
            return when (intent) {
                WebSearchTriggerIntent.NEWS ->
                    "Do not invent current facts. You must call web_search. do not repeat news items. brief evaluation or commentary."
                WebSearchTriggerIntent.WEATHER ->
                    "weather. You must call web_search and base the answer only on returned sources."
                WebSearchTriggerIntent.REALTIME -> "Prefer calling web_search."
                WebSearchTriggerIntent.NONE -> null
            }
        }

        override fun newsDirectDeliveryCommentary(
            factText: String,
            sent: Boolean,
        ): String = "Do not repeat the news items. Only provide a brief evaluation. $factText $sent"
    }
}

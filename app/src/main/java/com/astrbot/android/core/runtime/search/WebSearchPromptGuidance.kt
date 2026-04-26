package com.astrbot.android.core.runtime.search

import androidx.annotation.StringRes
import com.astrbot.android.AppStrings
import com.astrbot.android.R

/**
 * Centralized natural-language web-search guidance for LLM prompts.
 *
 * Keep this provider-agnostic: model-specific native search behavior must not
 * leak into runtime prompts. The host exposes one `web_search` tool surface.
 */
object WebSearchPromptGuidance {
    fun forMessage(
        text: String,
        strings: WebSearchPromptStringProvider = AndroidWebSearchPromptStringProvider(),
    ): String? {
        return forTrigger(WebSearchTriggerRules.evaluate(text), strings)
    }

    fun forTrigger(
        trigger: WebSearchTriggerRuleResult,
        strings: WebSearchPromptStringProvider = AndroidWebSearchPromptStringProvider(),
    ): String? {
        return when (trigger.intent) {
            WebSearchTriggerIntent.NEWS -> strings.guidanceFor(WebSearchTriggerIntent.NEWS)
            WebSearchTriggerIntent.WEATHER -> strings.guidanceFor(WebSearchTriggerIntent.WEATHER)
            WebSearchTriggerIntent.REALTIME -> strings.guidanceFor(WebSearchTriggerIntent.REALTIME)
            WebSearchTriggerIntent.NONE -> null
        }
    }
}

interface WebSearchPromptStringProvider {
    fun guidanceFor(intent: WebSearchTriggerIntent): String?

    fun newsDirectDeliveryCommentary(
        factText: String,
        sent: Boolean,
    ): String
}

class AndroidWebSearchPromptStringProvider : WebSearchPromptStringProvider {
    override fun guidanceFor(intent: WebSearchTriggerIntent): String? {
        val resId = when (intent) {
            WebSearchTriggerIntent.NEWS -> R.string.web_search_prompt_news_guidance
            WebSearchTriggerIntent.WEATHER -> R.string.web_search_prompt_weather_guidance
            WebSearchTriggerIntent.REALTIME -> R.string.web_search_prompt_realtime_guidance
            WebSearchTriggerIntent.NONE -> return null
        }
        return string(resId, WEB_SEARCH_TOOL_NAME).ifBlank { null }
    }

    override fun newsDirectDeliveryCommentary(
        factText: String,
        sent: Boolean,
    ): String {
        val status = string(
            if (sent) {
                R.string.web_search_prompt_direct_delivery_status_sent
            } else {
                R.string.web_search_prompt_direct_delivery_status_failed
            },
        )
        return string(
            R.string.web_search_prompt_news_direct_delivery_commentary,
            status,
            factText,
        )
    }

    private fun string(
        @StringRes resId: Int,
        vararg args: Any,
    ): String = AppStrings.get(resId, *args)

    private companion object {
        const val WEB_SEARCH_TOOL_NAME = "web_search"
    }
}

package com.astrbot.android.ui.settings

import com.astrbot.android.model.BotProfile
import com.astrbot.android.core.runtime.context.RuntimePlatform
import com.astrbot.android.feature.plugin.runtime.toolsource.ActiveCapabilityTargetContext

internal const val DefaultAppChatConversationId: String = "chat-main"

internal data class CronJobEditorDraft(
    val name: String = "",
    val note: String = "",
    val cronExpression: String = "",
    val runAt: String = "",
    val runOnce: Boolean = false,
    val platform: String = RuntimePlatform.APP_CHAT.wireValue,
    val conversationId: String = DefaultAppChatConversationId,
    val selectedBotId: String = "",
) {
    fun canSubmit(): Boolean = missingFields().isEmpty()

    fun missingFields(): List<String> {
        return buildList {
            if (name.trim().isBlank()) add("name")
            if (cronExpression.trim().isBlank() && runAt.trim().isBlank()) add("schedule")
            if (platform.trim().isBlank()) add("platform")
            if (conversationId.trim().isBlank()) add("conversation_id")
            if (selectedBotId.trim().isBlank()) add("bot_id")
        }
    }

    fun toTargetContext(
        selectedBot: BotProfile,
        origin: String = "ui",
    ): ActiveCapabilityTargetContext {
        return selectedBot.toCronJobTargetContext(
            platform = platform.trim(),
            conversationId = conversationId.trim(),
            origin = origin,
        )
    }

    companion object {
        fun fromTargetContext(targetContext: ActiveCapabilityTargetContext): CronJobEditorDraft {
            return CronJobEditorDraft(
                platform = targetContext.platform,
                conversationId = targetContext.conversationId,
                selectedBotId = targetContext.botId,
            )
        }

        fun fromBotProfile(
            botProfile: BotProfile,
            platform: String = RuntimePlatform.APP_CHAT.wireValue,
            conversationId: String = DefaultAppChatConversationId,
        ): CronJobEditorDraft {
            return CronJobEditorDraft(
                platform = platform,
                conversationId = conversationId,
                selectedBotId = botProfile.id,
            )
        }
    }
}


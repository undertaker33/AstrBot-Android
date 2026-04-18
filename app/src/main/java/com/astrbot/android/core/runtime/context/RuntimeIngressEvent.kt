package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.MessageType

/**
 * Unified platform ingress event. App chat, QQ OneBot, scheduled tasks, and future
 * platforms all produce one of these before entering the runtime pipeline.
 *
 * [conversationId] is the plugin-facing conversation identifier (e.g. "friend:xxx"
 * for QQ private chats). It is exposed to plugins via [PluginMessageEvent].
 *
 * [repositorySessionId] is the actual [ConversationRepository] session key where
 * messages are persisted. When blank, [RuntimeContextResolver] falls back to
 * [conversationId]. QQ sets this to the `qq-{botId}-private-{userId}` session key
 * so that the resolver reads the correct message window.
 */
data class RuntimeIngressEvent(
    val platform: RuntimePlatform,
    val conversationId: String,
    val repositorySessionId: String = "",
    val messageId: String,
    val sender: SenderInfo,
    val messageType: MessageType,
    val text: String,
    val attachments: List<ConversationAttachment> = emptyList(),
    val mentionsSelf: Boolean = false,
    val mentionsAll: Boolean = false,
    val rawPlatformPayload: Any? = null,
    val trigger: IngressTrigger = IngressTrigger.USER_MESSAGE,
)

enum class RuntimePlatform(val wireValue: String) {
    APP_CHAT("app_chat"),
    QQ_ONEBOT("qq_onebot"),
}

enum class IngressTrigger {
    USER_MESSAGE,
    COMMAND,
    SCHEDULED_TASK,
    PLUGIN_EVENT,
}

data class SenderInfo(
    val userId: String,
    val nickname: String = "",
    val groupId: String = "",
)

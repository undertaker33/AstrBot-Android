package com.elymbot.android.core.runtime.context

enum class RuntimePlatform(val wireValue: String) {
    APP_CHAT("app_chat"),
    QQ_ONEBOT("qq_onebot"),
}

enum class RuntimeMessageType(val wireValue: String) {
    FriendMessage("friend"),
    GroupMessage("group"),
    OtherMessage("other"),
}

enum class IngressTrigger {
    USER_MESSAGE,
    COMMAND,
    SCHEDULED_TASK,
    GEOFENCE_EVENT,
    PLUGIN_EVENT,
}

data class SenderInfo(
    val userId: String,
    val nickname: String = "",
    val groupId: String = "",
)

data class RuntimeConversationAttachment(
    val id: String,
    val type: String = "image",
    val mimeType: String = "image/jpeg",
    val fileName: String = "",
    val base64Data: String = "",
    val remoteUrl: String = "",
)

data class RuntimeIngressEvent(
    val platform: RuntimePlatform,
    val conversationId: String,
    val repositorySessionId: String = "",
    val messageId: String,
    val sender: SenderInfo,
    val messageType: RuntimeMessageType,
    val text: String,
    val attachments: List<RuntimeConversationAttachment> = emptyList(),
    val mentionsSelf: Boolean = false,
    val mentionsAll: Boolean = false,
    val rawPlatformPayload: Any? = null,
    val trigger: IngressTrigger = IngressTrigger.USER_MESSAGE,
)

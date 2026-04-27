package com.astrbot.android.feature.bot.domain.model

data class BotProfile(
    val id: String = "qq-main",
    val platformName: String = "QQ",
    val displayName: String = "Host Bot",
    val tag: String = "",
    val accountHint: String = "",
    val boundQqUins: List<String> = emptyList(),
    val triggerWords: List<String> = listOf("astrbot"),
    val autoReplyEnabled: Boolean = true,
    val persistConversationLocally: Boolean = false,
    val bridgeMode: String = "NapCat local bridge",
    val bridgeEndpoint: String = "ws://127.0.0.1:6199/ws",
    val defaultProviderId: String = "",
    val defaultPersonaId: String = "default",
    val configProfileId: String = "default",
    val status: String = "Idle",
)

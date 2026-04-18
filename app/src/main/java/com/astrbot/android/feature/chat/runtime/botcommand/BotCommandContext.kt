package com.astrbot.android.feature.chat.runtime.botcommand

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType

enum class BotCommandSource {
    APP_CHAT,
    QQ,
}

data class BotCommandContext(
    val source: BotCommandSource,
    val languageTag: String,
    val sessionId: String,
    val session: ConversationSession,
    val sessions: List<ConversationSession>,
    val bot: BotProfile,
    val availableBots: List<BotProfile> = emptyList(),
    val config: ConfigProfile,
    val activeProviderId: String,
    val availableProviders: List<ProviderProfile> = emptyList(),
    val currentPersona: PersonaProfile?,
    val availablePersonas: List<PersonaProfile>,
    val messageType: MessageType,
    val sourceUid: String = "",
    val sourceGroupId: String = "",
    val selfId: String = "",
    val createSession: (() -> ConversationSession)? = null,
    val deleteSession: ((String) -> Unit)? = null,
    val renameSession: ((String, String) -> Unit)? = null,
    val selectSession: ((String) -> Unit)? = null,
    val updateConfig: (ConfigProfile) -> Unit = {},
    val updateBot: (BotProfile) -> Unit = {},
    val updateProvider: (ProviderProfile) -> Unit = {},
    val updateSessionServiceFlags: (Boolean?, Boolean?) -> Unit = { _, _ -> },
    val replaceMessages: (List<ConversationMessage>) -> Unit = {},
    val updateSessionBindings: (String, String, String) -> Unit = { _, _, _ -> },
)

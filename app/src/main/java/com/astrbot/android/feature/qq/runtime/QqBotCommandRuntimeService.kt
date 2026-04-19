package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.bot.domain.BotRepositoryPort
import com.astrbot.android.feature.config.domain.ConfigRepositoryPort
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandContext
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandRouter
import com.astrbot.android.feature.chat.runtime.botcommand.BotCommandSource
import com.astrbot.android.feature.provider.domain.ProviderRepositoryPort
import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.feature.qq.domain.QqConversationPort
import com.astrbot.android.feature.qq.domain.QqReplyPayload
import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.runBlocking

internal class QqBotCommandRuntimeService(
    private val botPort: BotRepositoryPort,
    private val configPort: ConfigRepositoryPort,
    private val providerPort: ProviderRepositoryPort,
    private val conversationPort: QqConversationPort,
    private val replySender: QqReplySender,
    private val profileResolver: QqRuntimeProfileResolver,
    private val currentLanguageTag: () -> String,
    private val log: (String) -> Unit = {},
) {
    fun handle(
        message: IncomingQqMessage,
        bot: BotProfile,
        config: ConfigProfile,
        sessionId: String,
        session: ConversationSession,
        currentPersona: PersonaProfile?,
    ): Boolean {
        val trimmedText = message.text.trim()
        val result = BotCommandRouter.handle(
            input = trimmedText,
            context = BotCommandContext(
                source = BotCommandSource.QQ,
                languageTag = currentLanguageTag(),
                sessionId = sessionId,
                session = session,
                sessions = conversationPort.sessions(),
                bot = bot,
                availableBots = profileResolver.availableBots(),
                config = config,
                activeProviderId = profileResolver.resolveProvider(bot, session.providerId)?.id ?: session.providerId,
                availableProviders = profileResolver.availableChatProviders(),
                currentPersona = currentPersona,
                availablePersonas = profileResolver.availablePersonas(),
                messageType = message.messageType,
                sourceUid = message.senderId,
                sourceGroupId = message.groupIdOrBlank,
                selfId = message.selfId,
                deleteSession = { targetSessionId ->
                    conversationPort.deleteSession(targetSessionId)
                },
                renameSession = { targetSessionId, title ->
                    conversationPort.renameSession(targetSessionId, title)
                },
                updateConfig = { updatedConfig ->
                    runBlocking { configPort.save(updatedConfig) }
                },
                updateBot = { updatedBot ->
                    runBlocking { botPort.save(updatedBot) }
                },
                updateProvider = { updatedProvider ->
                    runBlocking { providerPort.save(updatedProvider) }
                },
                updateSessionServiceFlags = { sttEnabled, ttsEnabled ->
                    conversationPort.updateSessionServiceFlags(
                        sessionId = sessionId,
                        sessionSttEnabled = sttEnabled,
                        sessionTtsEnabled = ttsEnabled,
                    )
                },
                replaceMessages = { messages ->
                    conversationPort.replaceMessages(sessionId, messages)
                },
                updateSessionBindings = { providerId, personaId, botId ->
                    conversationPort.updateSessionBindings(
                        sessionId = sessionId,
                        providerId = providerId,
                        personaId = personaId,
                        botId = botId,
                    )
                },
            ),
        )
        if (!result.handled) {
            return false
        }
        result.replyText?.let { reply ->
            replySender.send(
                QqReplyPayload(
                    conversationId = message.conversationId,
                    messageType = message.messageType,
                    text = reply,
                ),
                message,
            )
        }
        log("Bot command handled via router: ${trimmedText.substringBefore(' ')} session=$sessionId")
        return result.stopModelDispatch
    }
}

package com.astrbot.android.feature.chat.runtime.botcommand

import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BotCommandSessionListing {
    private const val PAGE_SIZE = 10
    private val timestampFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    fun render(
        sessions: List<ConversationSession>,
        personas: List<PersonaProfile>,
        currentSessionId: String,
        page: Int,
        languageTag: String,
    ): String {
        val language = BotCommandResources.resolveLanguage(languageTag)
        val normalizedPage = page.coerceAtLeast(1)
        val totalPages = (sessions.size.coerceAtLeast(1) + PAGE_SIZE - 1) / PAGE_SIZE
        val pageIndex = normalizedPage.coerceAtMost(totalPages)
        val startIndex = (pageIndex - 1) * PAGE_SIZE
        val pageItems = sessions.drop(startIndex).take(PAGE_SIZE)
        val currentSession = sessions.firstOrNull { it.id == currentSessionId }

        return buildString {
            appendLine(
                when (language) {
                    BotCommandResources.resolveLanguage("zh") -> "会话列表："
                    else -> "Conversation list:"
                },
            )
            pageItems.forEachIndexed { index, session ->
                appendLine("${startIndex + index + 1}.${session.title}（${session.id}）")
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "  人格：${personaName(session.personaId, personas, languageTag)}"
                        else -> "  Persona: ${personaName(session.personaId, personas, languageTag)}"
                    },
                )
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "  上次聊天：${lastChatTime(session, languageTag)}"
                        else -> "  Last chat: ${lastChatTime(session, languageTag)}"
                    },
                )
            }
            currentSession?.let { session ->
                appendLine()
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "当前对话：${session.title}（${session.id}）"
                        else -> "Current conversation: ${session.title} (${session.id})"
                    },
                )
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "会话类型：${sessionTypeLabel(session, languageTag)}"
                        else -> "Session type: ${sessionTypeLabel(session, languageTag)}"
                    },
                )
            }
            if (totalPages > 1) {
                appendLine(
                    when (language) {
                        BotCommandResources.resolveLanguage("zh") -> "*输入/ls $pageIndex 跳转至第${pageIndex}页"
                        else -> "*Enter /ls $pageIndex to jump to page $pageIndex"
                    },
                )
            }
        }.trim()
    }

    private fun personaName(
        personaId: String,
        personas: List<PersonaProfile>,
        languageTag: String,
    ): String {
        return personas.firstOrNull { it.id == personaId }?.name ?: when (BotCommandResources.resolveLanguage(languageTag)) {
            BotCommandResources.resolveLanguage("zh") -> "未设置"
            else -> "Not set"
        }
    }

    private fun lastChatTime(
        session: ConversationSession,
        languageTag: String,
    ): String {
        val lastTimestamp = session.messages.maxOfOrNull { it.timestamp } ?: return when (BotCommandResources.resolveLanguage(languageTag)) {
            BotCommandResources.resolveLanguage("zh") -> "无消息"
            else -> "No messages"
        }
        return Instant.ofEpochMilli(lastTimestamp)
            .atZone(ZoneId.systemDefault())
            .format(timestampFormatter)
    }
}

internal fun sessionTypeLabel(
    session: ConversationSession,
    languageTag: String,
): String {
    val isChinese = !languageTag.startsWith("en", ignoreCase = true)
    return when (session.messageType) {
        MessageType.FriendMessage -> if (isChinese) "QQ私聊" else "QQ private chat"
        MessageType.GroupMessage -> {
            if (session.originSessionId.contains(":user:")) {
                if (isChinese) "群聊隔离" else "Isolated group chat"
            } else {
                if (isChinese) "QQ群聊" else "QQ group chat"
            }
        }

        MessageType.OtherMessage -> if (isChinese) "应用内聊天" else "In-app chat"
    }
}

internal fun umoSessionToken(session: ConversationSession): String {
    return when (session.messageType) {
        MessageType.FriendMessage -> session.originSessionId.substringAfter("friend:", session.originSessionId)
        MessageType.GroupMessage -> {
            val groupId = session.originSessionId.substringAfter("group:", session.originSessionId).substringBefore(":")
            val isolatedUserId = session.originSessionId.substringAfter("user:", "")
            if (isolatedUserId.isBlank()) groupId else "${isolatedUserId}_$groupId"
        }

        MessageType.OtherMessage -> session.originSessionId
    }
}

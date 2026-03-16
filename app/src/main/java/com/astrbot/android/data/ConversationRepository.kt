package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.model.ConversationMessage
import com.astrbot.android.model.ConversationSession
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object ConversationRepository {
    const val DEFAULT_SESSION_ID = "chat-main"
    const val DEFAULT_SESSION_TITLE = "新对话"

    private const val STORAGE_FILE_NAME = "persistent_conversations.json"

    private var storageFile: File? = null

    private val _sessions = MutableStateFlow(defaultSessions())
    val sessions: StateFlow<List<ConversationSession>> = _sessions.asStateFlow()

    fun initialize(context: Context) {
        if (storageFile != null) return
        storageFile = File(context.filesDir, STORAGE_FILE_NAME)
        val persistedSessions = loadPersistedSessions()
        if (persistedSessions.isNotEmpty()) {
            _sessions.value = mergeDefaultSession(persistedSessions)
        }
        RuntimeLogRepository.append("Conversation repository initialized: sessions=${_sessions.value.size}")
    }

    fun session(sessionId: String = DEFAULT_SESSION_ID): ConversationSession {
        return _sessions.value.firstOrNull { it.id == sessionId } ?: createMissingSession(sessionId)
    }

    fun createSession(
        title: String = DEFAULT_SESSION_TITLE,
        botId: String = BotRepository.selectedBotId.value,
    ): ConversationSession {
        val created = ConversationSession(
            id = UUID.randomUUID().toString(),
            title = title,
            botId = botId,
            personaId = "default",
            providerId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )
        _sessions.value = listOf(created) + _sessions.value
        persistSessions()
        RuntimeLogRepository.append("Conversation created: session=${created.id} bot=${created.botId}")
        return created
    }

    fun deleteSession(sessionId: String) {
        val before = _sessions.value.size
        _sessions.value = _sessions.value.filterNot { it.id == sessionId }
        if (_sessions.value.size != before) {
            persistSessions()
            RuntimeLogRepository.append("Conversation deleted: session=$sessionId")
        }
    }

    fun deleteSessionsForBot(botId: String) {
        val before = _sessions.value.size
        _sessions.value = _sessions.value.filterNot { it.botId == botId }
        if (_sessions.value.none { it.id == DEFAULT_SESSION_ID }) {
            _sessions.value = mergeDefaultSession(_sessions.value)
        }
        if (_sessions.value.size != before) {
            persistSessions()
            RuntimeLogRepository.append("Conversation deleted for bot: $botId")
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val cleaned = title.trim().ifBlank { DEFAULT_SESSION_TITLE }
        _sessions.value = _sessions.value.map { item ->
            if (item.id == sessionId) item.copy(title = cleaned) else item
        }
        persistSessions()
        RuntimeLogRepository.append("Conversation renamed: session=$sessionId title=$cleaned")
    }

    fun buildContextPreview(sessionId: String): String {
        val session = session(sessionId)
        return session.messages
            .takeLast(session.maxContextMessages)
            .joinToString(separator = "\n") { "${it.role}: ${it.content}" }
    }

    fun appendMessage(sessionId: String, role: String, content: String) {
        val currentSession = session(sessionId)
        val message = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
        )
        _sessions.value = _sessions.value.map { item ->
            if (item.id == currentSession.id) {
                item.copy(messages = item.messages + message)
            } else {
                item
            }
        }
        persistSessions()
        RuntimeLogRepository.append(
            "Conversation message appended: session=$sessionId role=$role chars=${content.length}",
        )
    }

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        val currentSession = session(sessionId)
        _sessions.value = _sessions.value.map { item ->
            if (item.id == currentSession.id) item.copy(messages = messages) else item
        }
        persistSessions()
        RuntimeLogRepository.append("Conversation replaced: session=$sessionId messages=${messages.size}")
    }

    fun updateSessionBindings(
        sessionId: String,
        providerId: String,
        personaId: String,
        botId: String,
    ) {
        val currentSession = session(sessionId)
        _sessions.value = _sessions.value.map { item ->
            if (item.id == currentSession.id) {
                item.copy(
                    botId = botId,
                    providerId = providerId,
                    personaId = personaId,
                )
            } else {
                item
            }
        }
        persistSessions()
        RuntimeLogRepository.append(
            "Conversation binding updated: session=$sessionId bot=$botId provider=${providerId.ifBlank { "none" }} persona=${personaId.ifBlank { "none" }}",
        )
    }

    fun syncPersistenceForBot(botId: String, persistConversationLocally: Boolean) {
        if (!persistConversationLocally) {
            persistSessions()
            return
        }
        persistSessions()
        RuntimeLogRepository.append("Conversation persistence enabled for bot=$botId")
    }

    private fun createMissingSession(sessionId: String): ConversationSession {
        val created = ConversationSession(
            id = sessionId,
            title = DEFAULT_SESSION_TITLE,
            botId = BotRepository.selectedBotId.value,
            personaId = "default",
            providerId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )
        _sessions.value = listOf(created) + _sessions.value
        persistSessions()
        RuntimeLogRepository.append("Conversation created: session=$sessionId")
        return created
    }

    private fun persistSessions() {
        val file = storageFile ?: return
        val persistable = _sessions.value.filter { session ->
            session.id != DEFAULT_SESSION_ID && BotRepository.shouldPersistConversation(session.botId)
        }
        val array = JSONArray()
        persistable.forEach { session ->
            array.put(
                JSONObject().apply {
                    put("id", session.id)
                    put("title", session.title)
                    put("botId", session.botId)
                    put("personaId", session.personaId)
                    put("providerId", session.providerId)
                    put("maxContextMessages", session.maxContextMessages)
                    put(
                        "messages",
                        JSONArray().apply {
                            session.messages.forEach { message ->
                                put(
                                    JSONObject().apply {
                                        put("id", message.id)
                                        put("role", message.role)
                                        put("content", message.content)
                                        put("timestamp", message.timestamp)
                                    },
                                )
                            }
                        },
                    )
                },
            )
        }
        runCatching {
            file.writeText(array.toString(), Charsets.UTF_8)
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "Conversation persistence failed: ${error.message ?: error.javaClass.simpleName}",
            )
        }
    }

    private fun loadPersistedSessions(): List<ConversationSession> {
        val file = storageFile ?: return emptyList()
        if (!file.exists()) return emptyList()
        return runCatching {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val sessionId = item.optString("id").trim()
                    if (sessionId.isBlank()) continue
                    val messages = buildList {
                        val messagesArray = item.optJSONArray("messages") ?: JSONArray()
                        for (messageIndex in 0 until messagesArray.length()) {
                            val message = messagesArray.optJSONObject(messageIndex) ?: continue
                            add(
                                ConversationMessage(
                                    id = message.optString("id").ifBlank { UUID.randomUUID().toString() },
                                    role = message.optString("role"),
                                    content = message.optString("content"),
                                    timestamp = message.optLong("timestamp", System.currentTimeMillis()),
                                ),
                            )
                        }
                    }
                    add(
                        ConversationSession(
                            id = sessionId,
                            title = item.optString("title").ifBlank { DEFAULT_SESSION_TITLE },
                            botId = item.optString("botId").ifBlank { "qq-main" },
                            personaId = item.optString("personaId").ifBlank { "default" },
                            providerId = item.optString("providerId"),
                            maxContextMessages = item.optInt("maxContextMessages", 12),
                            messages = messages,
                        ),
                    )
                }
            }
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "Conversation persistence load failed: ${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrDefault(emptyList())
    }

    private fun defaultSessions(): List<ConversationSession> {
        return listOf(
            ConversationSession(
                id = DEFAULT_SESSION_ID,
                title = DEFAULT_SESSION_TITLE,
                botId = BotRepository.selectedBotId.value,
                personaId = "default",
                providerId = "",
                maxContextMessages = 12,
                messages = listOf(
                    ConversationMessage(
                        id = UUID.randomUUID().toString(),
                        role = "assistant",
                        content = "对话已就绪。配置好模型后就可以开始聊天。",
                        timestamp = System.currentTimeMillis(),
                    ),
                ),
            ),
        )
    }

    private fun mergeDefaultSession(loadedSessions: List<ConversationSession>): List<ConversationSession> {
        val withoutDefault = loadedSessions.filterNot { it.id == DEFAULT_SESSION_ID }
        return defaultSessions() + withoutDefault
    }
}

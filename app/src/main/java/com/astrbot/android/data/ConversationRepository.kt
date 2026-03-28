package com.astrbot.android.data

import android.content.Context
import com.astrbot.android.data.chat.applySystemSessionTitle
import com.astrbot.android.data.chat.toConversationEntity
import com.astrbot.android.data.chat.toConversationSession
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.ConversationDao
import com.astrbot.android.data.db.ConversationEntity
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.chat.defaultSessionRefFor
import com.astrbot.android.model.chat.importDedupKey
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class ConversationImportPreview(
    val totalSessions: Int,
    val duplicateSessions: List<ConversationSession>,
    val newSessions: List<ConversationSession>,
)

data class ConversationImportResult(
    val importedCount: Int,
    val overwrittenCount: Int,
    val skippedCount: Int,
)

object ConversationRepository {
    const val DEFAULT_SESSION_ID = "chat-main"
    const val DEFAULT_SESSION_TITLE = "新对话"

    private const val LEGACY_STORAGE_FILE_NAME = "persistent_conversations.json"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private val initializationLoaded = AtomicBoolean(false)
    private val persistenceMutex = Mutex()

    private var legacyStorageFile: File? = null
    private var conversationDao: ConversationDao = ConversationDatabaseHolder.placeholder

    private val _sessions = MutableStateFlow(defaultSessions())
    val sessions: StateFlow<List<ConversationSession>> = _sessions.asStateFlow()
    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        initializationLoaded.set(false)

        legacyStorageFile = File(context.filesDir, LEGACY_STORAGE_FILE_NAME)
        conversationDao = AstrBotDatabase.get(context).conversationDao()

        repositoryScope.launch {
            runCatching {
                val importedLegacy = importLegacySessionsIfNeeded()
                if (conversationDao.count() == 0 && !importedLegacy) {
                    conversationDao.upsertAll(defaultSessions().map { it.toConversationEntity() })
                }
                val entities = conversationDao.listConversations()
                val sessionsFromDb = entities.mapNotNull { entity ->
                    runCatching {
                        entity.toConversationSession { sessionId ->
                            RuntimeLogRepository.append("Conversation messages reset for session=$sessionId because JSON was invalid")
                        }
                    }
                        .onFailure { error ->
                            RuntimeLogRepository.append(
                                "Conversation row skipped: id=${entity.id} reason=${error.message ?: error.javaClass.simpleName}",
                            )
                        }
                        .getOrNull()
                }
                _sessions.value = if (sessionsFromDb.isEmpty()) defaultSessions() else sessionsFromDb
                initializationLoaded.set(true)
                _isReady.value = true
                RuntimeLogRepository.append("Conversation database initialized: sessions=${_sessions.value.size}")
            }.onFailure { error ->
                _sessions.value = defaultSessions()
                initializationLoaded.set(true)
                _isReady.value = true
                RuntimeLogRepository.append(
                    "Conversation database init failed, fallback to defaults: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
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
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            pinned = false,
            titleCustomized = false,
            messages = emptyList(),
        )
        _sessions.update { current -> sortSessions(listOf(created) + current) }
        persistSessions()
        RuntimeLogRepository.append("Conversation created: session=${created.id} bot=${created.botId}")
        return created
    }

    fun deleteSession(sessionId: String) {
        var shouldPersist = false
        _sessions.update { current ->
            val filtered = current.filterNot { it.id == sessionId }
            shouldPersist = filtered.size != current.size || current.size == 1
            sortSessions(if (filtered.isEmpty()) defaultSessions() else filtered)
        }
        if (shouldPersist) {
            persistSessions()
            RuntimeLogRepository.append("Conversation deleted: session=$sessionId")
        }
    }

    fun deleteSessionsForBot(botId: String) {
        var shouldPersist = false
        _sessions.update { current ->
            val filtered = current.filterNot { it.botId == botId }
            shouldPersist = filtered.size != current.size
            sortSessions(if (filtered.isEmpty()) defaultSessions() else filtered)
        }
        if (shouldPersist) {
            persistSessions()
            RuntimeLogRepository.append("Conversation deleted for bot: $botId")
        }
    }

    fun renameSession(sessionId: String, title: String) {
        val cleaned = title.trim().ifBlank { DEFAULT_SESSION_TITLE }
        _sessions.update { current ->
            current.map { item ->
                if (item.id == sessionId) item.copy(title = cleaned, titleCustomized = true) else item
            }
        }
        persistSessions()
        RuntimeLogRepository.append("Conversation renamed: session=$sessionId title=$cleaned")
    }

    fun syncSystemSessionTitle(sessionId: String, title: String) {
        var updated = false
        _sessions.update { current ->
            current.map { item ->
                val synced = if (item.id == sessionId) {
                    applySystemSessionTitle(
                        session = item,
                        incomingTitle = title,
                        defaultTitle = DEFAULT_SESSION_TITLE,
                    )
                } else {
                    null
                }
                if (synced != null) {
                    updated = true
                    synced
                } else {
                    item
                }
            }
        }
        if (updated) {
            persistSessions()
            RuntimeLogRepository.append(
                "Conversation system title synced: session=$sessionId title=${title.trim().ifBlank { DEFAULT_SESSION_TITLE }}",
            )
        }
    }

    fun toggleSessionPinned(sessionId: String) {
        var pinned: Boolean? = null
        _sessions.update { current ->
            sortSessions(
                current.map { item ->
                    if (item.id == sessionId) {
                        val next = !item.pinned
                        pinned = next
                        item.copy(pinned = next)
                    } else {
                        item
                    }
                },
            )
        }
        if (pinned != null) {
            persistSessions()
            RuntimeLogRepository.append("Conversation pin toggled: session=$sessionId pinned=$pinned")
        }
    }

    fun buildContextPreview(sessionId: String): String {
        val currentSession = session(sessionId)
        return currentSession.messages
            .takeLast(currentSession.maxContextMessages)
            .joinToString(separator = "\n") { "${it.role}: ${it.content}" }
    }

    fun appendMessage(
        sessionId: String,
        role: String,
        content: String,
        attachments: List<ConversationAttachment> = emptyList(),
    ): String {
        val currentSession = session(sessionId)
        val message = ConversationMessage(
            id = UUID.randomUUID().toString(),
            role = role,
            content = content,
            timestamp = System.currentTimeMillis(),
            attachments = attachments,
        )
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) item.copy(messages = item.messages + message) else item
            }
        }.also {
            _sessions.value = sortSessions(_sessions.value)
        }
        persistSessions()
        RuntimeLogRepository.append(
            "Conversation message appended: session=$sessionId role=$role chars=${content.length} attachments=${attachments.size}",
        )
        return message.id
    }

    fun updateMessage(
        sessionId: String,
        messageId: String,
        content: String? = null,
        attachments: List<ConversationAttachment>? = null,
    ) {
        val currentSession = session(sessionId)
        var updated = false
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) {
                    item.copy(
                        messages = item.messages.map { message ->
                            if (message.id == messageId) {
                                updated = true
                                message.copy(
                                    content = content ?: message.content,
                                    attachments = attachments ?: message.attachments,
                                )
                            } else {
                                message
                            }
                        },
                    )
                } else {
                    item
                }
            }
        }
        if (!updated) {
            RuntimeLogRepository.append(
                "Conversation message update skipped: session=$sessionId message=$messageId not found",
            )
            return
        }
        persistSessions()
        RuntimeLogRepository.append(
            "Conversation message updated: session=$sessionId message=$messageId chars=${content?.length ?: -1} attachments=${attachments?.size ?: -1}",
        )
    }

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        val currentSession = session(sessionId)
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) item.copy(messages = messages) else item
            }
        }.also {
            _sessions.value = sortSessions(_sessions.value)
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
        _sessions.update { current ->
            current.map { item ->
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
        }
        persistSessions()
        RuntimeLogRepository.append(
            "Conversation binding updated: session=$sessionId bot=$botId provider=${providerId.ifBlank { "none" }} persona=${personaId.ifBlank { "none" }}",
        )
    }

    fun updateSessionServiceFlags(
        sessionId: String,
        sessionSttEnabled: Boolean? = null,
        sessionTtsEnabled: Boolean? = null,
    ) {
        val currentSession = session(sessionId)
        _sessions.update { current ->
            current.map { item ->
                if (item.id == currentSession.id) {
                    item.copy(
                        sessionSttEnabled = sessionSttEnabled ?: item.sessionSttEnabled,
                        sessionTtsEnabled = sessionTtsEnabled ?: item.sessionTtsEnabled,
                    )
                } else {
                    item
                }
            }
        }
        persistSessions()
        RuntimeLogRepository.append(
            "Conversation service flags updated: session=$sessionId stt=${sessionSttEnabled ?: currentSession.sessionSttEnabled} tts=${sessionTtsEnabled ?: currentSession.sessionTtsEnabled}",
        )
    }

    fun syncPersistenceForBot(botId: String, persistConversationLocally: Boolean) {
        persistSessions()
        RuntimeLogRepository.append(
            "Conversation persistence synced to database for bot=$botId legacyFlag=$persistConversationLocally",
        )
    }

    fun snapshotSessions(): List<ConversationSession> {
        return _sessions.value.map { session ->
            session.copy(
                messages = session.messages.map { message ->
                    message.copy(
                        attachments = message.attachments.map { attachment -> attachment.copy() },
                    )
                },
            )
        }
    }

    fun restoreSessions(restoredSessions: List<ConversationSession>) {
        val normalized = restoredSessions
            .map { session ->
                session.copy(
                    title = session.title.ifBlank { DEFAULT_SESSION_TITLE },
                    botId = session.botId.ifBlank { BotRepository.selectedBotId.value },
                    messages = session.messages.sortedBy { it.timestamp },
                )
            }
            .distinctBy { it.id }
            .ifEmpty { defaultSessions() }
            .let(::sortSessions)

        _sessions.value = normalized
        if (initializationLoaded.get()) {
            persistSessions()
        }
        RuntimeLogRepository.append("Conversation sessions restored: sessions=${normalized.size}")
    }

    fun previewImportedSessions(importedSessions: List<ConversationSession>): ConversationImportPreview {
        val existingKeys = _sessions.value.map { it.importDedupKey() }.toSet()
        val duplicates = importedSessions.filter { it.importDedupKey() in existingKeys }
        val newSessions = importedSessions.filterNot { it.importDedupKey() in existingKeys }
        return ConversationImportPreview(
            totalSessions = importedSessions.size,
            duplicateSessions = duplicates,
            newSessions = newSessions,
        )
    }

    fun importSessions(
        importedSessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): ConversationImportResult {
        val incoming = importedSessions
            .map { session ->
                session.copy(
                    title = session.title.ifBlank { DEFAULT_SESSION_TITLE },
                    botId = session.botId.ifBlank { BotRepository.selectedBotId.value },
                    messages = session.messages.sortedBy { it.timestamp },
                )
            }
            .distinctBy { it.id }

        val currentByKey = _sessions.value.associateBy { it.importDedupKey() }.toMutableMap()
        var importedCount = 0
        var overwrittenCount = 0
        var skippedCount = 0

        incoming.forEach { session ->
            val dedupKey = session.importDedupKey()
            val existingSession = currentByKey[dedupKey]
            when {
                existingSession == null -> {
                    currentByKey[dedupKey] = session
                    importedCount += 1
                }
                overwriteDuplicates -> {
                    currentByKey[dedupKey] = session.copy(id = existingSession.id)
                    overwrittenCount += 1
                }
                else -> skippedCount += 1
            }
        }

        val merged = currentByKey.values
            .toList()
            .ifEmpty { defaultSessions() }
            .let(::sortSessions)

        _sessions.value = merged
        if (initializationLoaded.get()) {
            persistSessions()
        }
        RuntimeLogRepository.append(
            "Conversation sessions imported: new=$importedCount overwritten=$overwrittenCount skipped=$skippedCount",
        )
        return ConversationImportResult(
            importedCount = importedCount,
            overwrittenCount = overwrittenCount,
            skippedCount = skippedCount,
        )
    }

    private fun createMissingSession(sessionId: String): ConversationSession {
        val created = ConversationSession(
            id = sessionId,
            title = DEFAULT_SESSION_TITLE,
            botId = BotRepository.selectedBotId.value,
            personaId = "",
            providerId = "",
            maxContextMessages = 12,
            sessionSttEnabled = true,
            sessionTtsEnabled = true,
            pinned = false,
            titleCustomized = false,
            messages = emptyList(),
        )
        _sessions.update { current -> sortSessions(listOf(created) + current) }
        persistSessions()
        RuntimeLogRepository.append("Conversation created: session=$sessionId")
        return created
    }

    private fun persistSessions() {
        if (!initializationLoaded.get()) {
            RuntimeLogRepository.append("Conversation persist skipped: repository is still loading initial sessions")
            return
        }
        repositoryScope.launch {
            persistenceMutex.withLock {
                runCatching {
                    val snapshot = _sessions.value
                    val entities = snapshot.map { it.toConversationEntity() }
                    if (entities.isEmpty()) {
                        conversationDao.clearAll()
                    } else {
                        conversationDao.upsertAll(entities)
                        conversationDao.deleteMissing(entities.map { it.id })
                    }
                }.onFailure { error ->
                    RuntimeLogRepository.append(
                        "Conversation database persist failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }
    }

    private suspend fun importLegacySessionsIfNeeded(): Boolean {
        val file = legacyStorageFile ?: return false
        if (!file.exists()) return false
        val legacySessions = loadLegacySessions(file)
        if (legacySessions.isEmpty()) return false
        val existingSessions = conversationDao.listConversations().mapNotNull { entity ->
            runCatching { entity.toConversationSession() }.getOrNull()
        }
        val mergedSessions = mergeSessions(existingSessions, legacySessions)
        conversationDao.upsertAll(mergedSessions.map { it.toConversationEntity() })
        RuntimeLogRepository.append(
            "Conversation legacy JSON imported into database: legacy=${legacySessions.size} merged=${mergedSessions.size}",
        )
        return true
    }

    private fun loadLegacySessions(file: File): List<ConversationSession> {
        return runCatching {
            val array = JSONArray(file.readText(Charsets.UTF_8))
            buildList {
                for (index in 0 until array.length()) {
                    add(
                        array.optJSONObject(index)?.toConversationSession(
                            defaultTitle = DEFAULT_SESSION_TITLE,
                        ) ?: continue,
                    )
                }
            }
        }.onFailure { error ->
            RuntimeLogRepository.append(
                "Conversation legacy migration failed: ${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrDefault(emptyList())
    }

    private fun defaultSessions(): List<ConversationSession> {
        return listOf(
            ConversationSession(
                id = DEFAULT_SESSION_ID,
                title = DEFAULT_SESSION_TITLE,
                botId = BotRepository.selectedBotId.value,
                personaId = "",
                providerId = "",
                maxContextMessages = 12,
                sessionSttEnabled = true,
                sessionTtsEnabled = true,
                pinned = false,
                titleCustomized = false,
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

    private fun mergeSessions(
        existingSessions: List<ConversationSession>,
        legacySessions: List<ConversationSession>,
    ): List<ConversationSession> {
        val merged = LinkedHashMap<String, ConversationSession>()
        mergeDefaultSession(existingSessions).forEach { session ->
            merged[session.id] = session
        }
        legacySessions.forEach { session ->
            val current = merged[session.id]
            merged[session.id] = when {
                current == null -> session
                session.messages.size > current.messages.size -> session
                session.messages.size == current.messages.size &&
                    session.messages.maxOfOrNull { it.timestamp } ?: 0L >
                    current.messages.maxOfOrNull { it.timestamp } ?: 0L -> session
                else -> current
            }
        }
        return mergeDefaultSession(
            merged.values
                .filterNot { it.id == DEFAULT_SESSION_ID }
                .let(::sortSessions),
        )
    }
}

private fun sortSessions(sessions: List<ConversationSession>): List<ConversationSession> {
    return sessions.sortedWith(
        compareByDescending<ConversationSession> { it.pinned }
            .thenByDescending { it.messages.maxOfOrNull { message -> message.timestamp } ?: 0L },
    )
}

private object ConversationDatabaseHolder {
    val placeholder = object : ConversationDao {
        override fun observeConversations() = flowOf(emptyList<ConversationEntity>())
        override suspend fun listConversations(): List<ConversationEntity> = emptyList()
        override suspend fun upsert(entity: ConversationEntity) = Unit
        override suspend fun upsertAll(entities: List<ConversationEntity>) = Unit
        override suspend fun deleteById(sessionId: String) = Unit
        override suspend fun clearAll() = Unit
        override suspend fun deleteMissing(ids: List<String>) = Unit
        override suspend fun count(): Int = 0
    }
}


package com.astrbot.android.data

import android.content.Context
import android.net.Uri
import com.astrbot.android.model.ConversationAttachment
import com.astrbot.android.model.ConversationMessage
import com.astrbot.android.model.ConversationSession
import com.astrbot.android.runtime.RuntimeLogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class ConversationBackupSettings(
    val autoBackupEnabled: Boolean = false,
    val autoBackupHour: Int = 3,
    val autoBackupMinute: Int = 0,
    val lastAutoBackupDate: String = "",
)

data class ConversationBackupItem(
    val id: String,
    val fileName: String,
    val createdAt: Long,
    val sessionCount: Int,
    val messageCount: Int,
    val trigger: String,
)

data class ConversationImportSource(
    val label: String,
    val sessions: List<ConversationSession>,
    val preview: ConversationImportPreview,
)

object ConversationBackupRepository {
    private const val PREFS_NAME = "conversation_backup_settings"
    private const val KEY_AUTO_ENABLED = "auto_enabled"
    private const val KEY_AUTO_HOUR = "auto_hour"
    private const val KEY_AUTO_MINUTE = "auto_minute"
    private const val KEY_LAST_AUTO_DATE = "last_auto_date"
    private const val BACKUP_SCHEMA = "conversation-backup-v1"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)
    private val autoBackupMutex = Mutex()
    private val timestampFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    private lateinit var backupDirectory: File
    private lateinit var prefs: android.content.SharedPreferences

    private val _settings = MutableStateFlow(ConversationBackupSettings())
    val settings: StateFlow<ConversationBackupSettings> = _settings.asStateFlow()

    private val _backups = MutableStateFlow<List<ConversationBackupItem>>(emptyList())
    val backups: StateFlow<List<ConversationBackupItem>> = _backups.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        backupDirectory = File(appContext.filesDir, "conversation-backups").apply { mkdirs() }
        prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _settings.value = loadSettings()
        refreshBackups()

        repositoryScope.launch {
            ConversationRepository.isReady.collectLatest { ready ->
                if (ready) {
                    maybeRunAutoBackup()
                }
            }
        }
        repositoryScope.launch {
            ConversationRepository.sessions.collectLatest {
                maybeRunAutoBackup()
            }
        }
    }

    suspend fun createBackup(trigger: String = "manual"): Result<ConversationBackupItem> = withContext(Dispatchers.IO) {
        runCatching {
            val sessions = ConversationRepository.snapshotSessions()
            val now = System.currentTimeMillis()
            val dateTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), ZoneId.systemDefault())
            val fileName = "conversation-backup-${timestampFormatter.format(dateTime)}-$trigger.json"
            val file = File(backupDirectory, fileName)
            file.writeText(
                JSONObject()
                    .put("schema", BACKUP_SCHEMA)
                    .put("createdAt", now)
                    .put("trigger", trigger)
                    .put("sessions", JSONArray().apply {
                        sessions.forEach { put(it.toJson()) }
                    })
                    .toString(2),
                Charsets.UTF_8,
            )
            refreshBackups()
            buildBackupItem(file)
                ?: error("Backup file was created but could not be read back")
        }.onFailure { error ->
            RuntimeLogRepository.append("Conversation backup create failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun restoreBackup(backupId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            val sessions = loadSessionsFromBackupFile(file)
            ConversationRepository.restoreSessions(sessions)
            sessions.size
        }.onFailure { error ->
            RuntimeLogRepository.append("Conversation backup restore failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            if (!file.delete()) error("Delete failed")
            refreshBackups()
        }.onFailure { error ->
            RuntimeLogRepository.append("Conversation backup delete failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun exportBackupToUri(
        context: Context,
        backupId: String,
        targetUri: Uri,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            val bytes = file.readBytes()
            context.contentResolver.openOutputStream(targetUri, "w")?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: error("Unable to open export target")
        }.onFailure { error ->
            RuntimeLogRepository.append("Conversation backup export failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun prepareImportFromBackup(backupId: String): Result<ConversationImportSource> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveBackupFile(backupId) ?: error("Backup not found")
            val sessions = loadSessionsFromBackupFile(file)
            ConversationImportSource(
                label = file.nameWithoutExtension,
                sessions = sessions,
                preview = ConversationRepository.previewImportedSessions(sessions),
            )
        }
    }

    suspend fun prepareImportFromUri(context: Context, uri: Uri): Result<ConversationImportSource> = withContext(Dispatchers.IO) {
        runCatching {
            val sessions = loadSessionsFromUri(context, uri)
            ConversationImportSource(
                label = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "external-backup" } ?: "external-backup",
                sessions = sessions,
                preview = ConversationRepository.previewImportedSessions(sessions),
            )
        }.onFailure { error ->
            RuntimeLogRepository.append("Conversation backup external import failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    suspend fun importSessions(
        sessions: List<ConversationSession>,
        overwriteDuplicates: Boolean,
    ): Result<ConversationImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            ConversationRepository.importSessions(
                importedSessions = sessions,
                overwriteDuplicates = overwriteDuplicates,
            )
        }.onFailure { error ->
            RuntimeLogRepository.append("Conversation backup import apply failed: ${error.message ?: error.javaClass.simpleName}")
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
        _settings.value = _settings.value.copy(autoBackupEnabled = enabled)
        if (enabled) {
            repositoryScope.launch {
                maybeRunAutoBackup()
            }
        }
    }

    fun setAutoBackupTime(hour: Int, minute: Int) {
        prefs.edit()
            .putInt(KEY_AUTO_HOUR, hour)
            .putInt(KEY_AUTO_MINUTE, minute)
            .apply()
        _settings.value = _settings.value.copy(autoBackupHour = hour, autoBackupMinute = minute)
        repositoryScope.launch {
            maybeRunAutoBackup()
        }
    }

    private fun refreshBackups() {
        _backups.value = backupDirectory
            .listFiles { file -> file.isFile && file.extension.equals("json", ignoreCase = true) }
            .orEmpty()
            .mapNotNull(::buildBackupItem)
            .sortedByDescending { it.createdAt }
    }

    private fun loadSettings(): ConversationBackupSettings {
        return ConversationBackupSettings(
            autoBackupEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, false),
            autoBackupHour = prefs.getInt(KEY_AUTO_HOUR, 3),
            autoBackupMinute = prefs.getInt(KEY_AUTO_MINUTE, 0),
            lastAutoBackupDate = prefs.getString(KEY_LAST_AUTO_DATE, "").orEmpty(),
        )
    }

    private fun saveLastAutoBackupDate(date: String) {
        prefs.edit().putString(KEY_LAST_AUTO_DATE, date).apply()
        _settings.value = _settings.value.copy(lastAutoBackupDate = date)
    }

    private suspend fun maybeRunAutoBackup() {
        if (!initialized.get()) return
        autoBackupMutex.withLock {
            val currentSettings = _settings.value
            if (!currentSettings.autoBackupEnabled) return
            if (!ConversationRepository.isReady.value) return
            val sessions = ConversationRepository.snapshotSessions()
            if (!hasMeaningfulConversationData(sessions)) return

            val today = LocalDate.now().toString()
            if (currentSettings.lastAutoBackupDate == today) return

            val now = LocalTime.now()
            val scheduledTime = LocalTime.of(currentSettings.autoBackupHour, currentSettings.autoBackupMinute)
            if (now.isBefore(scheduledTime)) return

            createBackup(trigger = "auto").onSuccess {
                saveLastAutoBackupDate(today)
                RuntimeLogRepository.append("Conversation auto backup created: file=${it.fileName}")
            }
        }
    }

    private fun hasMeaningfulConversationData(sessions: List<ConversationSession>): Boolean {
        return sessions.any { session ->
            session.messages.any { message ->
                message.content.isNotBlank() || message.attachments.isNotEmpty()
            }
        }
    }

    private fun buildBackupItem(file: File): ConversationBackupItem? {
        return runCatching {
            val payload = JSONObject(file.readText(Charsets.UTF_8))
            val sessions = payload.optJSONArray("sessions")
            ConversationBackupItem(
                id = file.name,
                fileName = file.nameWithoutExtension,
                createdAt = payload.optLong("createdAt", file.lastModified()),
                sessionCount = sessions?.length() ?: 0,
                messageCount = parseSessions(sessions ?: JSONArray()).sumOf { it.messages.size },
                trigger = payload.optString("trigger").ifBlank { "manual" },
            )
        }.getOrNull()
    }

    private fun resolveBackupFile(backupId: String): File? {
        val file = File(backupDirectory, backupId)
        return file.takeIf { it.exists() && it.isFile }
    }

    private fun loadSessionsFromBackupFile(file: File): List<ConversationSession> {
        val payload = JSONObject(file.readText(Charsets.UTF_8))
        return payload.optJSONArray("sessions")
            ?.let(::parseSessions)
            .orEmpty()
    }

    private fun loadSessionsFromUri(context: Context, uri: Uri): List<ConversationSession> {
        val json = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.readBytes().toString(Charsets.UTF_8)
        } ?: error("Unable to open import file")
        val payload = JSONObject(json)
        return payload.optJSONArray("sessions")
            ?.let(::parseSessions)
            .orEmpty()
    }

    private fun parseSessions(array: JSONArray): List<ConversationSession> {
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(item.toConversationSession())
            }
        }
    }
}

private fun ConversationSession.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("title", title)
        .put("botId", botId)
        .put("personaId", personaId)
        .put("providerId", providerId)
        .put("maxContextMessages", maxContextMessages)
        .put("sessionSttEnabled", sessionSttEnabled)
        .put("sessionTtsEnabled", sessionTtsEnabled)
        .put("messages", JSONArray().apply {
            messages.forEach { put(it.toJson()) }
        })
}

private fun ConversationMessage.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("role", role)
        .put("content", content)
        .put("timestamp", timestamp)
        .put("attachments", JSONArray().apply {
            attachments.forEach { put(it.toJson()) }
        })
}

private fun ConversationAttachment.toJson(): JSONObject {
    return JSONObject()
        .put("id", id)
        .put("type", type)
        .put("mimeType", mimeType)
        .put("fileName", fileName)
        .put("base64Data", base64Data)
        .put("remoteUrl", remoteUrl)
}

private fun JSONObject.toConversationSession(): ConversationSession {
    val messagesArray = optJSONArray("messages") ?: JSONArray()
    return ConversationSession(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        title = optString("title").ifBlank { ConversationRepository.DEFAULT_SESSION_TITLE },
        botId = optString("botId").ifBlank { BotRepository.selectedBotId.value },
        personaId = optString("personaId").takeUnless { it.isBlank() || it == "default" }.orEmpty(),
        providerId = optString("providerId"),
        maxContextMessages = optInt("maxContextMessages", 12),
        sessionSttEnabled = optBoolean("sessionSttEnabled", true),
        sessionTtsEnabled = optBoolean("sessionTtsEnabled", true),
        messages = buildList {
            for (index in 0 until messagesArray.length()) {
                val messageObject = messagesArray.optJSONObject(index) ?: continue
                add(messageObject.toConversationMessage())
            }
        },
    )
}

private fun JSONObject.toConversationMessage(): ConversationMessage {
    val attachmentsArray = optJSONArray("attachments") ?: JSONArray()
    return ConversationMessage(
        id = optString("id").ifBlank { UUID.randomUUID().toString() },
        role = optString("role"),
        content = optString("content"),
        timestamp = optLong("timestamp", System.currentTimeMillis()),
        attachments = buildList {
            for (index in 0 until attachmentsArray.length()) {
                val attachmentObject = attachmentsArray.optJSONObject(index) ?: continue
                add(
                    ConversationAttachment(
                        id = attachmentObject.optString("id").ifBlank { UUID.randomUUID().toString() },
                        type = attachmentObject.optString("type").ifBlank { "image" },
                        mimeType = attachmentObject.optString("mimeType").ifBlank { "image/jpeg" },
                        fileName = attachmentObject.optString("fileName"),
                        base64Data = attachmentObject.optString("base64Data"),
                        remoteUrl = attachmentObject.optString("remoteUrl"),
                    ),
                )
            }
        },
    )
}

package com.astrbot.android.core.db.backup

import com.astrbot.android.model.chat.ConversationSession
import org.json.JSONArray
import org.json.JSONObject

object AppBackupJson {
    const val FULL_BACKUP_SCHEMA = "astrbot-android-full-backup-v1"

    fun parseManifest(json: JSONObject): AppBackupManifest {
        val modulesJson = json.optJSONObject("modules") ?: JSONObject()
        return AppBackupManifest(
            schema = json.optString("schema").ifBlank { FULL_BACKUP_SCHEMA },
            createdAt = json.optLong("createdAt", 0L),
            trigger = json.optString("trigger").ifBlank { "manual" },
            modules = AppBackupModules(
                bots = modulesJson.parseModuleSnapshot("bots"),
                providers = modulesJson.parseModuleSnapshot("providers"),
                personas = modulesJson.parseModuleSnapshot("personas"),
                configs = modulesJson.parseModuleSnapshot("configs"),
                conversations = modulesJson.parseModuleSnapshot(
                    key = "conversations",
                    recordParser = { it.toConversationSession() },
                ),
                qqLogin = modulesJson.parseModuleSnapshot("qqLogin"),
                ttsAssets = modulesJson.parseModuleSnapshot("ttsAssets"),
            ),
            appState = json.optJSONObject("appState").toAppState(),
        )
    }
}

fun AppBackupManifest.toJson(): JSONObject {
    return JSONObject()
        .put("schema", schema)
        .put("createdAt", createdAt)
        .put("trigger", trigger)
        .put(
            "modules",
            JSONObject()
                .put("bots", modules.bots.toJson())
                .put("providers", modules.providers.toJson())
                .put("personas", modules.personas.toJson())
                .put("configs", modules.configs.toJson())
                .put("conversations", modules.conversations.toJson())
                .put("qqLogin", modules.qqLogin.toJson())
                .put("ttsAssets", modules.ttsAssets.toJson()),
        )
        .put("appState", appState.toJson())
}

private fun AppBackupModuleSnapshot.toJson(): JSONObject {
    return JSONObject()
        .put("count", count)
        .put("hasFiles", hasFiles)
        .put("files", JSONArray().apply { files.forEach(::put) })
        .put(
            "records",
            JSONArray().apply {
                records.forEach { record ->
                    when (record) {
                        is ConversationSession -> put(record.toConversationJson())
                        is JSONObject -> put(record)
                    }
                }
            },
        )
}

private fun JSONObject.parseModuleSnapshot(
    key: String,
    recordParser: ((JSONObject) -> Any)? = null,
): AppBackupModuleSnapshot {
    val moduleJson = optJSONObject(key) ?: JSONObject()
    return AppBackupModuleSnapshot(
        count = moduleJson.optInt("count", 0),
        hasFiles = moduleJson.optBoolean("hasFiles", false),
        files = moduleJson.optJSONArray("files").toStringList(),
        records = moduleJson.optJSONArray("records").toRecordList(recordParser),
    )
}

private fun JSONArray?.toStringList(): List<String> {
    val source = this ?: return emptyList()
    return buildList {
        for (index in 0 until source.length()) {
            add(source.optString(index))
        }
    }
}

private fun JSONArray?.toRecordList(recordParser: ((JSONObject) -> Any)?): List<Any> {
    val source = this ?: return emptyList()
    return buildList {
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index) ?: continue
            add(recordParser?.invoke(item) ?: item)
        }
    }
}

private fun JSONObject?.toAppState(): AppBackupAppState {
    val source = this ?: return AppBackupAppState()
    return AppBackupAppState(
        selectedBotId = source.optString("selectedBotId"),
        selectedConfigId = source.optString("selectedConfigId"),
        preferredChatProvider = source.optString("preferredChatProvider"),
        themeMode = source.optString("themeMode"),
    )
}

private fun AppBackupAppState.toJson(): JSONObject {
    return JSONObject()
        .put("selectedBotId", selectedBotId)
        .put("selectedConfigId", selectedConfigId)
        .put("preferredChatProvider", preferredChatProvider)
        .put("themeMode", themeMode)
}


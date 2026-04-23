package com.astrbot.android.feature.plugin.data.state

import com.astrbot.android.data.db.PluginStateEntryDao
import com.astrbot.android.data.db.PluginStateEntryEntity
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

enum class PluginStateScopeKind(
    val wireValue: String,
) {
    Plugin("plugin"),
    Session("session");

    companion object {
        fun fromWireValue(value: String): PluginStateScopeKind? {
            return entries.firstOrNull { kind -> kind.wireValue == value }
        }
    }
}

data class PluginStateScope(
    val kind: PluginStateScopeKind,
    val scopeId: String,
) {
    init {
        if (kind == PluginStateScopeKind.Plugin) {
            require(scopeId.isEmpty()) { "Plugin scope must use an empty scopeId." }
        }
        if (kind == PluginStateScopeKind.Session) {
            require(scopeId.isNotBlank()) { "Session scope requires a non-blank scopeId." }
        }
    }

    companion object {
        fun plugin(): PluginStateScope = PluginStateScope(
            kind = PluginStateScopeKind.Plugin,
            scopeId = "",
        )

        fun session(scopeId: String): PluginStateScope = PluginStateScope(
            kind = PluginStateScopeKind.Session,
            scopeId = scopeId.trim(),
        )
    }
}

interface PluginStateStore {
    fun getValueJson(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
    ): String?

    fun putValueJson(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
        valueJson: String,
    )

    fun listKeys(
        pluginId: String,
        scope: PluginStateScope,
        prefix: String = "",
    ): List<String>

    fun remove(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
    )

    fun clearScope(
        pluginId: String,
        scope: PluginStateScope,
        prefix: String = "",
    )

    fun deleteByPluginId(pluginId: String)
}

class InMemoryPluginStateStore : PluginStateStore {
    private data class EntryKey(
        val pluginId: String,
        val scopeKind: String,
        val scopeId: String,
        val key: String,
    )

    private val values = linkedMapOf<EntryKey, String>()

    override fun getValueJson(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
    ): String? = values[index(pluginId, scope, key)]

    override fun putValueJson(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
        valueJson: String,
    ) {
        values[index(pluginId, scope, key)] = valueJson
    }

    override fun listKeys(
        pluginId: String,
        scope: PluginStateScope,
        prefix: String,
    ): List<String> {
        return values.keys
            .filter { entry ->
                entry.pluginId == pluginId &&
                    entry.scopeKind == scope.kind.wireValue &&
                    entry.scopeId == scope.scopeId &&
                    entry.key.startsWith(prefix)
            }
            .map(EntryKey::key)
            .sorted()
    }

    override fun remove(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
    ) {
        values.remove(index(pluginId, scope, key))
    }

    override fun clearScope(
        pluginId: String,
        scope: PluginStateScope,
        prefix: String,
    ) {
        values.keys
            .filter { entry ->
                entry.pluginId == pluginId &&
                    entry.scopeKind == scope.kind.wireValue &&
                    entry.scopeId == scope.scopeId &&
                    entry.key.startsWith(prefix)
            }
            .toList()
            .forEach(values::remove)
    }

    override fun deleteByPluginId(pluginId: String) {
        values.keys
            .filter { entry -> entry.pluginId == pluginId }
            .toList()
            .forEach(values::remove)
    }

    private fun index(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
    ): EntryKey = EntryKey(
        pluginId = pluginId,
        scopeKind = scope.kind.wireValue,
        scopeId = scope.scopeId,
        key = key,
    )
}

@Singleton
class RoomPluginStateStore(
    private val dao: PluginStateEntryDao,
    private val clock: () -> Long = System::currentTimeMillis,
) : PluginStateStore {
    override fun getValueJson(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
    ): String? {
        validatePluginId(pluginId)
        val normalizedKey = validateKey(key)
        return runBlocking(Dispatchers.IO) {
            dao.get(
                pluginId = pluginId.trim(),
                scopeKind = scope.kind.wireValue,
                scopeId = scope.scopeId,
                key = normalizedKey,
            )?.valueJson
        }
    }

    override fun putValueJson(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
        valueJson: String,
    ) {
        validatePluginId(pluginId)
        val normalizedKey = validateKey(key)
        val normalizedValueJson = validateValueJson(valueJson)
        runBlocking(Dispatchers.IO) {
            dao.upsert(
                PluginStateEntryEntity(
                    pluginId = pluginId.trim(),
                    scopeKind = scope.kind.wireValue,
                    scopeId = scope.scopeId,
                    key = normalizedKey,
                    valueJson = normalizedValueJson,
                    updatedAt = clock(),
                ),
            )
        }
    }

    override fun listKeys(
        pluginId: String,
        scope: PluginStateScope,
        prefix: String,
    ): List<String> {
        validatePluginId(pluginId)
        val escapedPrefix = escapeLikePrefix(prefix)
        return runBlocking(Dispatchers.IO) {
            dao.listKeys(
                pluginId = pluginId.trim(),
                scopeKind = scope.kind.wireValue,
                scopeId = scope.scopeId,
                escapedPrefix = escapedPrefix,
            )
        }
    }

    override fun remove(
        pluginId: String,
        scope: PluginStateScope,
        key: String,
    ) {
        validatePluginId(pluginId)
        val normalizedKey = validateKey(key)
        runBlocking(Dispatchers.IO) {
            dao.delete(
                pluginId = pluginId.trim(),
                scopeKind = scope.kind.wireValue,
                scopeId = scope.scopeId,
                key = normalizedKey,
            )
        }
    }

    override fun clearScope(
        pluginId: String,
        scope: PluginStateScope,
        prefix: String,
    ) {
        validatePluginId(pluginId)
        val escapedPrefix = escapeLikePrefix(prefix)
        runBlocking(Dispatchers.IO) {
            dao.clearScope(
                pluginId = pluginId.trim(),
                scopeKind = scope.kind.wireValue,
                scopeId = scope.scopeId,
                escapedPrefix = escapedPrefix,
            )
        }
    }

    override fun deleteByPluginId(pluginId: String) {
        validatePluginId(pluginId)
        runBlocking(Dispatchers.IO) {
            dao.deleteByPluginId(pluginId.trim())
        }
    }

    private fun validatePluginId(pluginId: String) {
        require(pluginId.isNotBlank()) { "pluginId must not be blank." }
    }

    private fun validateKey(key: String): String {
        val normalized = key.trim()
        require(normalized.isNotEmpty()) { "key must not be blank." }
        require(normalized.length <= 128) { "key must be <= 128 characters." }
        return normalized
    }

    private fun validateValueJson(valueJson: String): String {
        val normalized = valueJson.trim()
        require(normalized.isNotEmpty()) { "valueJson must not be blank." }
        require(normalized.toByteArray(Charsets.UTF_8).size <= 64 * 1024) {
            "valueJson must be <= 64 KB."
        }
        return normalized
    }

    private fun escapeLikePrefix(prefix: String): String {
        return prefix.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
    }
}

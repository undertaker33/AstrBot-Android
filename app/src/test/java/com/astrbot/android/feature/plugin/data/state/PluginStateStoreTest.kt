package com.astrbot.android.feature.plugin.data.state

import com.astrbot.android.data.db.PluginStateEntryDao
import com.astrbot.android.data.db.PluginStateEntryEntity
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginStateStoreTest {
    @Test
    fun in_memory_store_preserves_keys_containing_scope_delimiters() {
        val store = InMemoryPluginStateStore()
        val pluginScope = PluginStateScope.plugin()
        val sessionScope = PluginStateScope.session("app::conversation::42")

        store.putValueJson(
            pluginId = "plugin.demo",
            scope = pluginScope,
            key = "alpha::beta",
            valueJson = "\"one\"",
        )
        store.putValueJson(
            pluginId = "plugin.demo",
            scope = sessionScope,
            key = "session::counter",
            valueJson = "{\"value\":2}",
        )

        assertEquals("\"one\"", store.getValueJson("plugin.demo", pluginScope, "alpha::beta"))
        assertEquals(listOf("alpha::beta"), store.listKeys("plugin.demo", pluginScope))
        assertEquals(listOf("session::counter"), store.listKeys("plugin.demo", sessionScope))

        store.clearScope("plugin.demo", pluginScope, prefix = "alpha::")
        store.clearScope("plugin.demo", sessionScope, prefix = "session::")

        assertNull(store.getValueJson("plugin.demo", pluginScope, "alpha::beta"))
        assertEquals(emptyList<String>(), store.listKeys("plugin.demo", sessionScope))
    }

    @Test
    fun stores_and_clears_plugin_and_session_scoped_entries() = runBlocking {
        val store = RoomPluginStateStore(
            dao = InMemoryPluginStateEntryDao(),
            clock = { 123L },
        )

        store.putValueJson(
            pluginId = "plugin.demo",
            scope = PluginStateScope.plugin(),
            key = "alpha",
            valueJson = "\"one\"",
        )
        store.putValueJson(
            pluginId = "plugin.demo",
            scope = PluginStateScope.session("app::conversation::42"),
            key = "beta",
            valueJson = "{\"value\":2}",
        )

        assertEquals("\"one\"", store.getValueJson("plugin.demo", PluginStateScope.plugin(), "alpha"))
        assertEquals(listOf("beta"), store.listKeys("plugin.demo", PluginStateScope.session("app::conversation::42")))

        store.deleteByPluginId("plugin.demo")

        assertNull(store.getValueJson("plugin.demo", PluginStateScope.plugin(), "alpha"))
        assertEquals(emptyList<String>(), store.listKeys("plugin.demo", PluginStateScope.session("app::conversation::42")))
    }

    @Test
    fun room_store_treats_percent_and_underscore_prefixes_as_literal_prefixes() = runBlocking {
        val store = RoomPluginStateStore(
            dao = InMemoryPluginStateEntryDao(),
            clock = { 123L },
        )
        val scope = PluginStateScope.session("app::conversation::42")

        store.putValueJson("plugin.demo", scope, "cache%primary", "\"percent\"")
        store.putValueJson("plugin.demo", scope, "cacheXprimary", "\"wildcard-percent\"")
        store.putValueJson("plugin.demo", scope, "cache_secondary", "\"underscore\"")
        store.putValueJson("plugin.demo", scope, "cacheXsecondary", "\"wildcard-underscore\"")

        assertEquals(
            listOf("cache%primary"),
            store.listKeys("plugin.demo", scope, prefix = "cache%"),
        )
        assertEquals(
            listOf("cache_secondary"),
            store.listKeys("plugin.demo", scope, prefix = "cache_"),
        )

        store.clearScope("plugin.demo", scope, prefix = "cache_")

        assertEquals("\"percent\"", store.getValueJson("plugin.demo", scope, "cache%primary"))
        assertNull(store.getValueJson("plugin.demo", scope, "cache_secondary"))
        assertEquals("\"wildcard-percent\"", store.getValueJson("plugin.demo", scope, "cacheXprimary"))
        assertEquals("\"wildcard-underscore\"", store.getValueJson("plugin.demo", scope, "cacheXsecondary"))
    }

    @Test
    fun plugin_state_entry_dao_like_queries_must_use_escape_clause() {
        val projectRoot = detectProjectRoot()
        val source = projectRoot
            .resolve("app/src/main/java/com/astrbot/android/data/db/plugin/PluginStateEntryDao.kt")
            .readText()

        val escapeToken = "LIKE :escapedPrefix || '%' ESCAPE '\\'"
        val escapeCount = source.split(escapeToken).size - 1

        assertTrue(
            "PluginStateEntryDao LIKE prefix queries must use ESCAPE for literal %/_ matching.",
            escapeCount >= 2,
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}

private class InMemoryPluginStateEntryDao : PluginStateEntryDao {
    private val entries = linkedMapOf<String, PluginStateEntryEntity>()

    override suspend fun get(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        key: String,
    ): PluginStateEntryEntity? = entries[index(pluginId, scopeKind, scopeId, key)]

    override suspend fun upsert(entity: PluginStateEntryEntity) {
        entries[index(entity.pluginId, entity.scopeKind, entity.scopeId, entity.key)] = entity
    }

    override suspend fun listKeys(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        escapedPrefix: String,
    ): List<String> {
        return entries.values
            .filter { entry ->
                entry.pluginId == pluginId &&
                    entry.scopeKind == scopeKind &&
                    entry.scopeId == scopeId &&
                    matchesSqlLikePrefix(entry.key, escapedPrefix)
            }
            .map(PluginStateEntryEntity::key)
            .sorted()
    }

    override suspend fun delete(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        key: String,
    ) {
        entries.remove(index(pluginId, scopeKind, scopeId, key))
    }

    override suspend fun clearScope(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        escapedPrefix: String,
    ) {
        entries.keys
            .filter { composite ->
                val entry = entries.getValue(composite)
                entry.pluginId == pluginId &&
                    entry.scopeKind == scopeKind &&
                    entry.scopeId == scopeId &&
                    matchesSqlLikePrefix(entry.key, escapedPrefix)
            }
            .toList()
            .forEach(entries::remove)
    }

    override suspend fun deleteByPluginId(pluginId: String) {
        entries.keys
            .filter { composite -> entries.getValue(composite).pluginId == pluginId }
            .toList()
            .forEach(entries::remove)
    }

    private fun index(
        pluginId: String,
        scopeKind: String,
        scopeId: String,
        key: String,
    ): String = listOf(pluginId, scopeKind, scopeId, key).joinToString(separator = "::")

    private fun matchesSqlLikePrefix(
        key: String,
        prefix: String,
    ): Boolean {
        if (prefix.isBlank()) return true
        val regex = buildString {
            append("^")
            var index = 0
            while (index < prefix.length) {
                val ch = prefix[index]
                when {
                    ch == '\\' && index + 1 < prefix.length -> {
                        append(Regex.escape(prefix[index + 1].toString()))
                        index += 2
                    }
                    ch == '%' -> {
                        append(".*")
                        index += 1
                    }
                    ch == '_' -> {
                        append(".")
                        index += 1
                    }
                    else -> {
                        append(Regex.escape(ch.toString()))
                        index += 1
                    }
                }
            }
        }
        return Regex(regex).containsMatchIn(key)
    }
}

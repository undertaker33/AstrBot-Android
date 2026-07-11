package com.elymbot.android.feature.persona.data

import com.elymbot.android.feature.persona.domain.model.PersonaProfile
import com.elymbot.android.feature.persona.domain.model.normalizePersonaTags
import org.json.JSONArray

internal fun parseLegacyPersonaProfiles(raw: String?): List<PersonaProfile> {
    val source = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
    val array = JSONArray(source)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                PersonaProfile(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    tags = normalizePersonaTags(
                        item.optJSONArray("tags")?.let { tags -> (0 until tags.length()).map { tags.optString(it) } }
                            ?: listOf(item.optString("tag")),
                    ),
                    systemPrompt = item.optString("systemPrompt"),
                    enabledTools = item.optStringList("enabledTools").toSet(),
                    defaultProviderId = item.optString("defaultProviderId"),
                    maxContextMessages = item.optInt("maxContextMessages", 12),
                    enabled = item.optBoolean("enabled", true),
                ),
            )
        }
    }
}

private fun org.json.JSONObject.optStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            add(array.opt(index)?.toString().orEmpty())
        }
    }
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

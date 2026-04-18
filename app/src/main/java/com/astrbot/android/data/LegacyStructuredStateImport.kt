package com.astrbot.android.data

import com.astrbot.android.feature.config.data.FeatureConfigRepository
import com.astrbot.android.model.SavedQqAccount
import org.json.JSONArray
import org.json.JSONObject

data class LegacyBotBindingState(
    val configProfileId: String = FeatureConfigRepository.DEFAULT_CONFIG_ID,
    val boundQqUins: List<String> = emptyList(),
    val persistConversationLocally: Boolean = false,
)

fun parseLegacyBotBindings(raw: String?): Map<String, LegacyBotBindingState> {
    val source = raw?.takeIf { it.isNotBlank() } ?: return emptyMap()
    val objectJson = JSONObject(source)
    return buildMap {
        objectJson.keys().forEach { key ->
            val rawValue = objectJson.opt(key)
            put(
                key,
                when (rawValue) {
                    is JSONObject -> LegacyBotBindingState(
                        configProfileId = rawValue.optString("configProfileId")
                            .ifBlank { FeatureConfigRepository.DEFAULT_CONFIG_ID },
                        boundQqUins = rawValue.optStringList("boundQqUins"),
                        persistConversationLocally = rawValue.optBoolean("persistConversationLocally", false),
                    )

                    else -> LegacyBotBindingState(
                        configProfileId = objectJson.optString(key).ifBlank { FeatureConfigRepository.DEFAULT_CONFIG_ID },
                    )
                },
            )
        }
    }
}

fun parseLegacySavedQqAccounts(raw: String?): List<SavedQqAccount> {
    val source = raw?.takeIf { it.isNotBlank() } ?: return emptyList()
    val array = JSONArray(source)
    return buildList {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val uin = item.optString("uin").trim()
            if (uin.isBlank()) continue
            add(
                SavedQqAccount(
                    uin = uin,
                    nickName = item.optString("nickName").trim(),
                    avatarUrl = item.optString("avatarUrl").trim(),
                ),
            )
        }
    }
}

private fun JSONObject.optStringList(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            add(array.optString(index).trim())
        }
    }.filter { it.isNotBlank() }
}

package com.astrbot.android.feature.qq.runtime

object QqWhitelistMatcher {
    fun isAllowed(entries: List<String>, userId: String, groupId: String?): Boolean {
        val normalized = entries
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toSet()

        if (userId in normalized) return true
        if (!groupId.isNullOrBlank() && groupId in normalized) return true
        if (!groupId.isNullOrBlank() && "${userId}_${groupId}" in normalized) return true
        return false
    }
}

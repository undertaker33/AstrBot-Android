package com.astrbot.android.feature.qq.runtime

class QqKeywordDetector(patterns: List<String>) {
    private val regexList = patterns.mapNotNull { pattern ->
        runCatching { Regex(pattern) }.getOrNull()
    }

    fun matches(text: String): Boolean {
        return regexList.any { regex -> regex.containsMatchIn(text) }
    }
}

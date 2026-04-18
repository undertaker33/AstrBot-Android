package com.astrbot.android.core.runtime.audio

import com.astrbot.android.core.runtime.audio.TtsStyleHints
import com.astrbot.android.core.runtime.audio.TtsStyleMappings
import org.json.JSONObject
import java.util.Locale

internal object TtsPromptFormatter {
    private val bracketPatterns = listOf(
        Regex("（([^（）]+)）"),
        Regex("\\(([^()]+)\\)"),
        Regex("【([^【】]+)】"),
        Regex("\\[([^\\[\\]]+)]"),
    )

    fun prepareRequest(
        text: String,
        readBracketedContent: Boolean,
    ): PreparedTtsRequest {
        val matches = collectBracketMatches(text)
        if (matches.isEmpty()) {
            return PreparedTtsRequest(spokenText = text.trim())
        }
        val stylePrompt = matches.joinToString("，") { it.inner }
        val spokenText = if (readBracketedContent) {
            buildReadableBracketSpeech(text, matches)
        } else {
            buildSpeechWithoutBracketDirections(text, matches)
        }.ifBlank {
            buildReadableBracketSpeech(text, matches)
        }
        return PreparedTtsRequest(
            spokenText = spokenText,
            stylePrompt = stylePrompt,
            styleHints = buildTtsStyleHints(stylePrompt),
        )
    }

    fun extractOpenAiStyleStreamingContent(line: String): String {
        val delta = JSONObject(line)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("delta")
            ?: return ""
        val rawContent = delta.opt("content")
        return if (rawContent == null || rawContent == JSONObject.NULL) {
            ""
        } else {
            rawContent.toString().takeUnless { it == "null" }.orEmpty()
        }
    }

    private fun collectBracketMatches(text: String): List<BracketMatch> {
        return bracketPatterns
            .flatMap { pattern ->
                pattern.findAll(text).mapNotNull { match ->
                    val inner = match.groupValues.getOrNull(1).orEmpty().trim()
                    inner.takeIf { it.isNotBlank() }?.let {
                        BracketMatch(
                            start = match.range.first,
                            endExclusive = match.range.last + 1,
                            inner = it,
                        )
                    }
                }.toList()
            }
            .sortedWith(compareBy<BracketMatch> { it.start }.thenBy { it.endExclusive })
            .fold(mutableListOf()) { acc, item ->
                val last = acc.lastOrNull()
                if (last == null || item.start >= last.endExclusive) {
                    acc += item
                }
                acc
            }
    }

    private fun buildReadableBracketSpeech(
        text: String,
        matches: List<BracketMatch>,
    ): String {
        val builder = StringBuilder()
        var cursor = 0
        matches.forEach { match ->
            builder.append(text.substring(cursor, match.start))
            val beforeChar = builder.lastOrNull()
            val afterChar = text.getOrNull(match.endExclusive)
            if (shouldInsertPause(beforeChar)) {
                builder.append('，')
            }
            builder.append(match.inner)
            if (shouldInsertPause(afterChar)) {
                builder.append('，')
            }
            cursor = match.endExclusive
        }
        builder.append(text.substring(cursor))
        return normalizeTtsText(builder.toString())
    }

    private fun buildSpeechWithoutBracketDirections(
        text: String,
        matches: List<BracketMatch>,
    ): String {
        val builder = StringBuilder()
        var cursor = 0
        matches.forEach { match ->
            builder.append(text.substring(cursor, match.start))
            cursor = match.endExclusive
        }
        builder.append(text.substring(cursor))
        return normalizeTtsText(builder.toString())
    }

    private fun normalizeTtsText(text: String): String {
        return text
            .replace(Regex("[\\t\\r\\n ]+"), " ")
            .replace(Regex("([，。！？!?:：；;])\\s+"), "$1")
            .replace(Regex("[，]{2,}"), "，")
            .replace(Regex("^[，\\s]+"), "")
            .replace(Regex("\\s+[，。！？!?:：；;]"), "")
            .trim()
    }

    private fun shouldInsertPause(char: Char?): Boolean {
        if (char == null) return false
        return !char.isWhitespace() && char !in setOf('，', ',', '。', '！', '？', '!', '?', '：', ':', '；', ';')
    }

    private fun buildTtsStyleHints(stylePrompt: String): TtsStyleHints {
        val normalized = stylePrompt.trim().lowercase(Locale.US)
        if (normalized.isBlank()) {
            return TtsStyleHints()
        }

        val matchedMappings = TtsStyleMappings.entries
            .filter { mapping -> mapping.keywords.any(normalized::contains) }
            .sortedByDescending { it.priority }
        if (matchedMappings.isEmpty()) {
            return TtsStyleHints(
                openAiInstruction = buildGenericOpenAiInstruction(stylePrompt),
                dashScopeInstruction = buildGenericDashScopeInstruction(stylePrompt),
            )
        }

        val openAiSegments = linkedSetOf<String>()
        val dashScopeSegments = linkedSetOf<String>()
        val miniMaxTags = linkedSetOf<String>()
        var miniMaxEmotion: String? = null

        matchedMappings.forEach { mapping ->
            mapping.openAiInstruction?.let(openAiSegments::add)
            mapping.dashScopeInstruction?.let(dashScopeSegments::add)
            mapping.miniMaxTags.forEach(miniMaxTags::add)
            if (miniMaxEmotion == null && !mapping.miniMaxEmotion.isNullOrBlank()) {
                miniMaxEmotion = mapping.miniMaxEmotion
            }
        }

        return TtsStyleHints(
            openAiInstruction = buildOpenAiInstructionFromSegments(openAiSegments, stylePrompt),
            dashScopeInstruction = buildDashScopeInstructionFromSegments(dashScopeSegments, stylePrompt),
            miniMaxEmotion = miniMaxEmotion,
            miniMaxTags = miniMaxTags.take(3),
        )
    }

    private fun buildOpenAiInstructionFromSegments(
        segments: Set<String>,
        rawPrompt: String,
    ): String {
        if (segments.isEmpty()) {
            return buildGenericOpenAiInstruction(rawPrompt)
        }
        return buildString {
            append("Do not read the bracketed directions aloud. Speak in Mandarin Chinese with ")
            append(segments.joinToString(separator = ", "))
            append(".")
        }
    }

    private fun buildDashScopeInstructionFromSegments(
        segments: Set<String>,
        rawPrompt: String,
    ): String {
        if (segments.isEmpty()) {
            return buildGenericDashScopeInstruction(rawPrompt)
        }
        return buildString {
            append("请不要直接朗读括号中的提示词。整体请用")
            append(segments.joinToString(separator = "、"))
            append("的方式来演绎这段中文台词。")
        }
    }

    private fun buildGenericOpenAiInstruction(stylePrompt: String): String {
        return "Do not read the bracketed directions aloud. Use them as style guidance only: $stylePrompt"
    }

    private fun buildGenericDashScopeInstruction(stylePrompt: String): String {
        return "请不要直接朗读括号中的提示词，而是将它们作为语气、情绪和演绎提示：$stylePrompt"
    }
}

internal data class PreparedTtsRequest(
    val spokenText: String,
    val stylePrompt: String = "",
    val styleHints: TtsStyleHints = TtsStyleHints(),
)

private data class BracketMatch(
    val start: Int,
    val endExclusive: Int,
    val inner: String,
)

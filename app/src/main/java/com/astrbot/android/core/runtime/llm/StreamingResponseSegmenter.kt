package com.astrbot.android.core.runtime.llm

import kotlin.math.abs

object LlmResponseSegmenter {
    private const val DEFAULT_MIN_SEGMENT_LENGTH = 10
    private const val DEFAULT_SOFT_SEGMENT_MIN_LENGTH = 18
    private const val DEFAULT_TARGET_SEGMENT_LENGTH = 30
    private const val DEFAULT_HARD_SEGMENT_LENGTH = 48

    private const val VOICE_MIN_SEGMENT_LENGTH = 8
    private const val VOICE_SOFT_SEGMENT_MIN_LENGTH = 12
    private const val VOICE_TARGET_SEGMENT_LENGTH = 22
    private const val VOICE_HARD_SEGMENT_LENGTH = 36

    private val strongBoundaries = setOf('\u3002', '\uFF01', '\uFF1F', '!', '?', '~', '\u2026', '\n')
    private val softBoundaries = setOf('\uFF0C', '\u3001', '\uFF1B', '\uFF1A', ',', ';', ':')
    private val openingWrappers = setOf('\u201C', '(', '\u300C', '[', '\u300E', '\u3010', '\uFF08', '\u2018', '\u300A', '{')
    private val closingWrappers = setOf('\u201D', ')', '\u300D', ']', '\u300F', '\u3011', '\uFF09', '\u2019', '\u300B', '}')
    private val boundarySuffixChars = strongBoundaries + softBoundaries + closingWrappers

    data class DrainResult(
        val segments: List<String>,
        val remainder: String,
    )

    private data class SegmentationConfig(
        val minSegmentLength: Int,
        val softSegmentMinLength: Int,
        val targetSegmentLength: Int,
        val hardSegmentLength: Int,
    )

    private val defaultConfig = SegmentationConfig(
        minSegmentLength = DEFAULT_MIN_SEGMENT_LENGTH,
        softSegmentMinLength = DEFAULT_SOFT_SEGMENT_MIN_LENGTH,
        targetSegmentLength = DEFAULT_TARGET_SEGMENT_LENGTH,
        hardSegmentLength = DEFAULT_HARD_SEGMENT_LENGTH,
    )

    private val voiceConfig = SegmentationConfig(
        minSegmentLength = VOICE_MIN_SEGMENT_LENGTH,
        softSegmentMinLength = VOICE_SOFT_SEGMENT_MIN_LENGTH,
        targetSegmentLength = VOICE_TARGET_SEGMENT_LENGTH,
        hardSegmentLength = VOICE_HARD_SEGMENT_LENGTH,
    )

    fun split(
        text: String,
        stripTrailingBoundaryPunctuation: Boolean = false,
    ): List<String> {
        return drainInternal(
            normalized = text.replace("\r\n", "\n").trim(),
            forceTail = true,
            stripTrailingBoundaryPunctuation = stripTrailingBoundaryPunctuation,
            config = defaultConfig,
        ).segments
    }

    fun splitForVoiceStreaming(text: String): List<String> {
        return drainInternal(
            normalized = text.replace("\r\n", "\n").trim(),
            forceTail = true,
            stripTrailingBoundaryPunctuation = false,
            config = voiceConfig,
        ).segments
    }

    fun drain(
        text: String,
        forceTail: Boolean,
        stripTrailingBoundaryPunctuation: Boolean = false,
    ): DrainResult {
        return drainInternal(
            normalized = text.replace("\r\n", "\n"),
            forceTail = forceTail,
            stripTrailingBoundaryPunctuation = stripTrailingBoundaryPunctuation,
            config = defaultConfig,
        )
    }

    private fun drainInternal(
        normalized: String,
        forceTail: Boolean,
        stripTrailingBoundaryPunctuation: Boolean,
        config: SegmentationConfig,
    ): DrainResult {
        if (normalized.isEmpty()) {
            return DrainResult(emptyList(), "")
        }
        val segments = mutableListOf<String>()
        var start = 0
        while (start < normalized.length) {
            val nextEnd = nextSegmentEnd(
                text = normalized,
                start = start,
                forceTail = forceTail,
                config = config,
            ) ?: break
            val segment = normalizeSegment(
                segment = normalized.substring(start, nextEnd),
                stripTrailingBoundaryPunctuation = stripTrailingBoundaryPunctuation,
            )
            if (segment.isNotBlank()) {
                segments += segment
            }
            start = nextEnd
        }

        return DrainResult(
            segments = segments,
            remainder = normalized.substring(start),
        )
    }

    private fun nextSegmentEnd(
        text: String,
        start: Int,
        forceTail: Boolean,
        config: SegmentationConfig,
    ): Int? {
        val scanEnd = minOf(text.length, start + config.hardSegmentLength)
        val boundaries = collectBoundaries(text, start, scanEnd)
        selectBoundaryEnd(boundaries, start, text.length, config)?.let { return it }

        if (scanEnd < text.length) {
            return findWhitespaceFallback(text, start, scanEnd, config) ?: scanEnd
        }

        return if (forceTail) text.length else null
    }

    private fun collectBoundaries(
        text: String,
        start: Int,
        scanEnd: Int,
    ): List<Boundary> {
        val boundaries = mutableListOf<Boundary>()
        var wrapperDepth = 0
        var index = start
        while (index < scanEnd) {
            val char = text[index]
            if (char in openingWrappers) {
                wrapperDepth += 1
            }

            val boundaryType = when {
                char == '\n' -> BoundaryType.STRONG
                wrapperDepth == 0 && char in strongBoundaries -> BoundaryType.STRONG
                wrapperDepth == 0 && char in softBoundaries -> BoundaryType.SOFT
                wrapperDepth == 0 && char.isWhitespace() -> BoundaryType.SPACE
                else -> null
            }
            if (boundaryType != null) {
                boundaries += Boundary(
                    endExclusive = expandBoundaryEnd(text, index, scanEnd),
                    type = boundaryType,
                )
            }

            if (char in closingWrappers && wrapperDepth > 0) {
                wrapperDepth -= 1
            }
            index += 1
        }
        return boundaries.distinctBy { it.endExclusive to it.type }
    }

    private fun expandBoundaryEnd(
        text: String,
        index: Int,
        scanEnd: Int,
    ): Int {
        var cursor = index + 1
        while (cursor < scanEnd && text[cursor] in boundarySuffixChars) {
            cursor += 1
        }
        while (cursor < scanEnd && text[cursor].isWhitespace()) {
            cursor += 1
        }
        return cursor
    }

    private fun selectBoundaryEnd(
        boundaries: List<Boundary>,
        start: Int,
        totalLength: Int,
        config: SegmentationConfig,
    ): Int? {
        return boundaries
            .filter { boundary ->
                val segmentLength = boundary.endExclusive - start
                if (segmentLength < minLengthFor(boundary.type, config)) {
                    return@filter false
                }
                val remainingLength = totalLength - boundary.endExclusive
                remainingLength == 0 ||
                    remainingLength >= config.minSegmentLength ||
                    segmentLength >= config.targetSegmentLength
            }
            .minWithOrNull(
                compareBy<Boundary> { boundary -> boundaryScore(boundary, start, totalLength, config) }
                    .thenByDescending { boundary -> boundary.endExclusive },
            )
            ?.endExclusive
    }

    private fun minLengthFor(type: BoundaryType, config: SegmentationConfig): Int {
        return when (type) {
            BoundaryType.STRONG -> config.minSegmentLength
            BoundaryType.SOFT -> config.softSegmentMinLength
            BoundaryType.SPACE -> config.targetSegmentLength
        }
    }

    private fun boundaryScore(
        boundary: Boundary,
        start: Int,
        totalLength: Int,
        config: SegmentationConfig,
    ): Int {
        val segmentLength = boundary.endExclusive - start
        val remainingLength = totalLength - boundary.endExclusive
        val targetLength = when (boundary.type) {
            BoundaryType.STRONG -> config.minSegmentLength
            BoundaryType.SOFT -> config.targetSegmentLength - 8
            BoundaryType.SPACE -> config.targetSegmentLength
        }
        val typePenalty = when (boundary.type) {
            BoundaryType.STRONG -> 0
            BoundaryType.SOFT -> 4
            BoundaryType.SPACE -> 12
        }
        val tinyRemainderPenalty = if (remainingLength in 1 until config.minSegmentLength) 80 else 0
        return abs(segmentLength - targetLength) + typePenalty + tinyRemainderPenalty
    }

    private fun findWhitespaceFallback(
        text: String,
        start: Int,
        scanEnd: Int,
        config: SegmentationConfig,
    ): Int? {
        val candidate = (scanEnd - 1 downTo start + config.targetSegmentLength / 2)
            .firstOrNull { text[it].isWhitespace() }
            ?: return null
        return expandBoundaryEnd(text, candidate, scanEnd)
    }

    private fun normalizeSegment(
        segment: String,
        stripTrailingBoundaryPunctuation: Boolean,
    ): String {
        var normalized = segment.trimEnd()
        if (!stripTrailingBoundaryPunctuation) {
            return normalized
        }

        while (normalized.isNotEmpty() && normalized.last() in strongBoundaries + softBoundaries) {
            normalized = normalized.dropLast(1)
        }
        return normalized.trimEnd()
    }

    private data class Boundary(
        val endExclusive: Int,
        val type: BoundaryType,
    )

    private enum class BoundaryType {
        STRONG,
        SOFT,
        SPACE,
    }
}

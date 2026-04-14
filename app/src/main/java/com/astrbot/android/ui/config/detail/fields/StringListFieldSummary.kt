package com.astrbot.android.ui.config.detail.fields

data class StringListFieldSummary(
    val count: Int,
    val previewValues: List<String>,
) {
    companion object {
        fun from(values: List<String>, previewLimit: Int = 2): StringListFieldSummary {
            val normalized = values.map { it.trim() }.filter { it.isNotBlank() }
            return StringListFieldSummary(
                count = normalized.size,
                previewValues = normalized.take(previewLimit.coerceAtLeast(0)),
            )
        }
    }
}

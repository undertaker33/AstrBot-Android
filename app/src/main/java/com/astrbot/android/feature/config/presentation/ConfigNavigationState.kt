package com.astrbot.android.ui.config

import com.astrbot.android.ui.config.detail.ConfigSection

internal fun currentSectionFor(
    visibleSectionOffsets: List<Pair<String, Int>>,
    sections: List<ConfigSection>,
): ConfigSection {
    return visibleSectionOffsets
        .mapNotNull { (key, offset) ->
            val section = sections.firstOrNull { it.name == key } ?: return@mapNotNull null
            section to offset
        }
        .minByOrNull { (_, offset) -> kotlin.math.abs(offset) }
        ?.first
        ?: sections.first()
}

internal fun toggleExpandedGroup(
    expandedGroups: Set<Int>,
    groupTitleRes: Int,
): Set<Int> {
    return expandedGroups.toMutableSet().apply {
        if (!add(groupTitleRes)) {
            remove(groupTitleRes)
        }
    }
}

package com.elymbot.android.ui.config.detail

import com.elymbot.android.core.ui.R

internal fun configDrawerGroups(): List<ConfigNavGroup> {
    return listOf(
        ConfigNavGroup(
            titleRes = R.string.config_nav_group_model,
            children = listOf(
                ConfigSection.ModelSettings,
                ConfigSection.SpeechSettings,
                ConfigSection.StreamingSettings,
                ConfigSection.RuntimeHelpers,
                ConfigSection.KnowledgeBase,
                ConfigSection.ContextStrategy,
                ConfigSection.Automation,
            ),
        ),
        ConfigNavGroup(
            titleRes = R.string.config_nav_group_platform,
            children = listOf(
                ConfigSection.Admin,
                ConfigSection.Session,
                ConfigSection.Wake,
                ConfigSection.Reply,
                ConfigSection.Whitelist,
                ConfigSection.Geofence,
                ConfigSection.IgnorePermission,
                ConfigSection.RateLimit,
                ConfigSection.Keyword,
            ),
        ),
        ConfigNavGroup(
            titleRes = R.string.config_nav_group_plugin,
            children = emptyList(),
        ),
        ConfigNavGroup(
            titleRes = R.string.config_nav_group_other,
            children = emptyList(),
        ),
    )
}


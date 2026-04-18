package com.astrbot.android.feature.chat.data

import com.astrbot.android.model.chat.ConversationSession

internal fun applySystemSessionTitle(
    session: ConversationSession,
    incomingTitle: String,
    defaultTitle: String,
): ConversationSession? {
    val cleaned = incomingTitle.trim().ifBlank { defaultTitle }
    if (session.titleCustomized || session.title == cleaned) return null
    return session.copy(title = cleaned, titleCustomized = false)
}

package com.astrbot.android.feature.chat.data

import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.defaultSessionRefFor

@Suppress("UNUSED_PARAMETER")
fun migrateLegacySessionIdentity(
    sessionId: String,
    sessionTitle: String,
): MessageSessionRef {
    return defaultSessionRefFor(sessionId)
}

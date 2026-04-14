package com.astrbot.android.data.chat

import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.defaultSessionRefFor

@Suppress("UNUSED_PARAMETER")
fun migrateLegacySessionIdentity(
    sessionId: String,
    sessionTitle: String,
): MessageSessionRef {
    return defaultSessionRefFor(sessionId)
}

package com.astrbot.android.data.chat

import com.astrbot.android.feature.chat.data.applySystemSessionTitle as featureApplySystemSessionTitle
import com.astrbot.android.feature.chat.data.migrateLegacySessionIdentity as featureMigrateLegacySessionIdentity
import com.astrbot.android.feature.chat.data.toConversationJson as featureToConversationJson
import com.astrbot.android.feature.chat.data.toConversationSession as featureToConversationSession
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageSessionRef
import org.json.JSONObject

internal fun ConversationSession.toConversationJson(): JSONObject {
    return this.featureToConversationJson()
}

internal fun JSONObject.toConversationSession(
    defaultTitle: String,
    defaultBotId: String = "qq-main",
): ConversationSession {
    return this.featureToConversationSession(defaultTitle, defaultBotId)
}

internal fun migrateLegacySessionIdentity(
    sessionId: String,
    sessionTitle: String,
): MessageSessionRef {
    return featureMigrateLegacySessionIdentity(sessionId, sessionTitle)
}

internal fun applySystemSessionTitle(
    session: ConversationSession,
    incomingTitle: String,
    defaultTitle: String,
): ConversationSession? {
    return featureApplySystemSessionTitle(session, incomingTitle, defaultTitle)
}

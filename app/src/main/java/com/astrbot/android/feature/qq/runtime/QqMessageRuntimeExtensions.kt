package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.model.chat.MessageType

internal val IncomingQqMessage.groupIdOrBlank: String
    get() = if (messageType == MessageType.GroupMessage) conversationId else ""

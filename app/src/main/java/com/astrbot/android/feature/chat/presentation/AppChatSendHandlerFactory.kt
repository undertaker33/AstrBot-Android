package com.astrbot.android.feature.chat.presentation

import com.astrbot.android.feature.chat.domain.SendAppMessageUseCase
import javax.inject.Inject

class AppChatSendHandlerFactory @Inject constructor() {
    fun create(
        sendAppMessageUseCase: SendAppMessageUseCase,
    ): AppChatSendHandler {
        return AppChatSendHandler(sendAppMessageUseCase)
    }
}

package com.astrbot.android.feature.chat.domain

import javax.inject.Inject

class SendAppMessageUseCaseFactory @Inject constructor(
    private val conversations: ConversationRepositoryPort,
) {
    fun create(runtime: AppChatRuntimePort): SendAppMessageUseCase {
        return SendAppMessageUseCase(
            conversations = conversations,
            runtime = runtime,
        )
    }
}

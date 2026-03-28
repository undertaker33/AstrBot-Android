package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.data.ConversationRepository
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

class ConversationViewModel : ViewModel() {
    val sessions: StateFlow<List<ConversationSession>> = ConversationRepository.sessions

    fun contextPreview(sessionId: String): String {
        return ConversationRepository.buildContextPreview(sessionId)
    }

    fun session(sessionId: String = ConversationRepository.DEFAULT_SESSION_ID): ConversationSession {
        return ConversationRepository.session(sessionId)
    }

    fun appendMessage(sessionId: String, role: String, content: String) {
        ConversationRepository.appendMessage(sessionId, role, content)
    }

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        ConversationRepository.replaceMessages(sessionId, messages)
    }
}

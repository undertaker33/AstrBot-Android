package com.astrbot.android.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.astrbot.android.di.ConversationViewModelDependencies
import com.astrbot.android.di.DefaultConversationViewModelDependencies
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import kotlinx.coroutines.flow.StateFlow

class ConversationViewModel(
    private val dependencies: ConversationViewModelDependencies = DefaultConversationViewModelDependencies,
) : ViewModel() {
    val sessions: StateFlow<List<ConversationSession>> = dependencies.sessions

    fun contextPreview(sessionId: String): String {
        return dependencies.contextPreview(sessionId)
    }

    fun session(sessionId: String = dependencies.defaultSessionId): ConversationSession {
        return dependencies.session(sessionId)
    }

    fun appendMessage(sessionId: String, role: String, content: String) {
        dependencies.appendMessage(sessionId, role, content)
    }

    fun replaceMessages(sessionId: String, messages: List<ConversationMessage>) {
        dependencies.replaceMessages(sessionId, messages)
    }
}

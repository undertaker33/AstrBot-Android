package com.astrbot.android.feature.chat.runtime

import com.astrbot.android.model.chat.ConversationAttachment

/**
 * Collects streaming message content and attachment updates during the LLM pipeline.
 * Used by [AppChatRuntimeService] to buffer results before emitting runtime events.
 */
internal class StreamingMessageSink {
    private var _text: String = ""
    private var _attachments: List<ConversationAttachment> = emptyList()
    private var _sealed: Boolean = false

    val text: String get() = _text
    val attachments: List<ConversationAttachment> get() = _attachments
    val isSealed: Boolean get() = _sealed

    fun updateText(content: String) {
        check(!_sealed) { "Sink is already sealed" }
        _text = content
    }

    fun updateAttachments(list: List<ConversationAttachment>) {
        check(!_sealed) { "Sink is already sealed" }
        _attachments = list
    }

    fun seal() {
        _sealed = true
    }
}

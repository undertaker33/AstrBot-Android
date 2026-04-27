
package com.astrbot.android.core.runtime.llm

import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment

object LlmMediaService {
    fun transcribeAudio(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String {
        return ChatCompletionService.transcribeAudio(provider, attachment)
    }

    fun synthesizeSpeech(
        provider: ProviderProfile,
        text: String,
        voiceId: String = "",
        readBracketedContent: Boolean = true,
    ): ConversationAttachment {
        return ChatCompletionService.synthesizeSpeech(
            provider = provider,
            text = text,
            voiceId = voiceId,
            readBracketedContent = readBracketedContent,
        )
    }
}

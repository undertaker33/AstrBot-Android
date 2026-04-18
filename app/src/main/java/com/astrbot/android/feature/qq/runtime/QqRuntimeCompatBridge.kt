package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.qq.domain.IncomingQqMessage
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.chat.ConversationAttachment
import com.astrbot.android.model.plugin.PluginV2StreamingMode
import com.astrbot.android.feature.plugin.runtime.PluginV2HostPreparedReply
import com.astrbot.android.feature.plugin.runtime.PluginV2HostSendResult

internal interface QqRuntimeCompatBridge {
    fun currentLanguageTag(): String

    fun transcribeAudio(
        provider: ProviderProfile,
        attachment: ConversationAttachment,
    ): String

    fun buildVoiceReplyAttachments(
        provider: ProviderProfile,
        response: String,
        config: ConfigProfile,
    ): List<ConversationAttachment>

    suspend fun sendPreparedReply(
        message: IncomingQqMessage,
        prepared: PluginV2HostPreparedReply,
        config: ConfigProfile,
        streamingMode: PluginV2StreamingMode,
    ): PluginV2HostSendResult

    fun resolvePluginPrivateRootPath(pluginId: String): String
}

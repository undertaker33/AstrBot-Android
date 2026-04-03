package com.astrbot.android.model.plugin

import com.astrbot.android.model.chat.MessageSessionRef

enum class PluginTriggerSource(
    val wireValue: String,
) {
    OnMessageReceived("on_message_received"),
    BeforeSendMessage("before_send_message"),
    AfterModelResponse("after_model_response"),
    OnSchedule("on_schedule"),
    OnPluginEntryClick("on_plugin_entry_click"),
    OnCommand("on_command"),
    OnConversationEnter("on_conversation_enter");

    companion object {
        @JvmStatic
        fun fromWireValue(value: String): PluginTriggerSource? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

enum class PluginHostAction(
    val wireValue: String,
) {
    CallModel("call_model"),
    NetworkRequest("network_request"),
    ReadPrivateData("read_private_data"),
    WritePrivateData("write_private_data"),
    SendMessage("send_message"),
    SendNotification("send_notification"),
    OpenHostPage("open_host_page");

    companion object {
        @JvmStatic
        fun fromWireValue(value: String): PluginHostAction? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

data class PluginMessageSummary(
    val messageId: String,
    val contentPreview: String,
    val senderId: String = "",
    val messageType: String = "",
    val attachmentCount: Int = 0,
    val timestamp: Long = 0L,
)

data class PluginBotSummary(
    val botId: String,
    val displayName: String = "",
    val platformId: String = "",
)

data class PluginConfigSummary(
    val providerId: String = "",
    val modelId: String = "",
    val personaId: String = "",
    val extras: Map<String, String> = emptyMap(),
)

data class PluginPermissionGrant(
    val permissionId: String,
    val title: String,
    val granted: Boolean,
    val required: Boolean = true,
    val riskLevel: PluginRiskLevel = PluginRiskLevel.MEDIUM,
)

data class PluginTriggerMetadata(
    val eventId: String = "",
    val command: String = "",
    val entryPoint: String = "",
    val scheduledAtEpochMillis: Long? = null,
    val extras: Map<String, String> = emptyMap(),
)

data class PluginExecutionContext(
    val trigger: PluginTriggerSource,
    val pluginId: String,
    val pluginVersion: String,
    val sessionRef: MessageSessionRef,
    val message: PluginMessageSummary,
    val bot: PluginBotSummary,
    val config: PluginConfigSummary,
    val permissionSnapshot: List<PluginPermissionGrant> = emptyList(),
    val hostActionWhitelist: List<PluginHostAction> = emptyList(),
    val triggerMetadata: PluginTriggerMetadata = PluginTriggerMetadata(),
)

enum class PluginUiStatus(
    val wireValue: String,
) {
    Info("info"),
    Success("success"),
    Warning("warning"),
    Error("error");

    companion object {
        fun fromWireValue(value: String): PluginUiStatus? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

enum class PluginUiActionStyle(
    val wireValue: String,
) {
    Default("default"),
    Primary("primary"),
    Danger("danger");

    companion object {
        fun fromWireValue(value: String): PluginUiActionStyle? {
            return entries.firstOrNull { it.wireValue == value }
        }
    }
}

data class PluginCardField(
    val label: String,
    val value: String,
)

data class PluginCardAction(
    val actionId: String,
    val label: String,
    val style: PluginUiActionStyle = PluginUiActionStyle.Default,
    val payload: Map<String, String> = emptyMap(),
)

data class PluginCardSchema(
    val title: String,
    val body: String = "",
    val status: PluginUiStatus = PluginUiStatus.Info,
    val fields: List<PluginCardField> = emptyList(),
    val actions: List<PluginCardAction> = emptyList(),
)

data class PluginSelectOption(
    val value: String,
    val label: String,
)

sealed interface PluginSettingsField {
    val fieldId: String
    val label: String
}

data class ToggleSettingField(
    override val fieldId: String,
    override val label: String,
    val defaultValue: Boolean = false,
) : PluginSettingsField

data class TextInputSettingField(
    override val fieldId: String,
    override val label: String,
    val placeholder: String = "",
    val defaultValue: String = "",
) : PluginSettingsField

data class SelectSettingField(
    override val fieldId: String,
    override val label: String,
    val defaultValue: String = "",
    val options: List<PluginSelectOption> = emptyList(),
) : PluginSettingsField

data class PluginSettingsSection(
    val sectionId: String,
    val title: String,
    val fields: List<PluginSettingsField> = emptyList(),
)

data class PluginSettingsSchema(
    val title: String,
    val sections: List<PluginSettingsSection> = emptyList(),
)

data class PluginMediaItem(
    val source: String,
    val mimeType: String = "",
    val altText: String = "",
)

sealed interface PluginExecutionResult

data class TextResult(
    val text: String,
    val markdown: Boolean = false,
    val displayTitle: String = "",
) : PluginExecutionResult

data class CardResult(
    val card: PluginCardSchema,
) : PluginExecutionResult

data class MediaResult(
    val items: List<PluginMediaItem> = emptyList(),
) : PluginExecutionResult

data class HostActionRequest(
    val action: PluginHostAction,
    val title: String = "",
    val payload: Map<String, String> = emptyMap(),
) : PluginExecutionResult

data class SettingsUiRequest(
    val schema: PluginSettingsSchema,
) : PluginExecutionResult

data class NoOp(
    val reason: String = "",
) : PluginExecutionResult

data class ErrorResult(
    val message: String,
    val code: String = "",
    val recoverable: Boolean = false,
) : PluginExecutionResult

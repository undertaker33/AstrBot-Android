package com.astrbot.android.model.plugin

import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.runtime.plugin.AllowedValue
import com.astrbot.android.runtime.plugin.JsonLikeMap
import com.astrbot.android.runtime.plugin.LlmPipelineAdmission
import com.astrbot.android.runtime.plugin.PluginLlmResponse
import com.astrbot.android.runtime.plugin.PluginLlmUsageSnapshot
import com.astrbot.android.runtime.plugin.PluginMessageEventResult
import com.astrbot.android.runtime.plugin.PluginProviderMessageDto
import com.astrbot.android.runtime.plugin.PluginProviderMessagePartDto
import com.astrbot.android.runtime.plugin.PluginProviderMessageRole
import com.astrbot.android.runtime.plugin.PluginProviderRequest
import com.astrbot.android.runtime.plugin.PluginV2AfterSentView
import com.astrbot.android.runtime.plugin.PluginV2ValueSanitizer
import org.json.JSONArray
import org.json.JSONObject

object PluginExecutionProtocolJson {
    fun encodeExecutionContext(context: PluginExecutionContext): JSONObject {
        return JSONObject().apply {
            put("trigger", context.trigger.wireValue)
            put("pluginId", context.pluginId)
            put("pluginVersion", context.pluginVersion)
            put("sessionRef", encodeSessionRef(context.sessionRef))
            put("message", encodeMessageSummary(context.message))
            put("bot", encodeBotSummary(context.bot))
            put("config", encodeConfigSummary(context.config))
            put(
                "permissionSnapshot",
                JSONArray().apply {
                    context.permissionSnapshot.forEach { permission -> put(encodePermissionGrant(permission)) }
                },
            )
            put(
                "hostActionWhitelist",
                JSONArray().apply {
                    context.hostActionWhitelist.forEach { action -> put(action.wireValue) }
                },
            )
            put("triggerMetadata", encodeTriggerMetadata(context.triggerMetadata))
        }
    }

    fun decodeExecutionContext(json: JSONObject): PluginExecutionContext {
        return PluginExecutionContext(
            trigger = decodeTriggerSource(readRequiredString(json, "trigger", "trigger")),
            pluginId = readRequiredString(json, "pluginId", "pluginId"),
            pluginVersion = readRequiredString(json, "pluginVersion", "pluginVersion"),
            sessionRef = decodeSessionRef(readRequiredObject(json, "sessionRef", "sessionRef"), "sessionRef"),
            message = decodeMessageSummary(readRequiredObject(json, "message", "message"), "message"),
            bot = decodeBotSummary(readRequiredObject(json, "bot", "bot"), "bot"),
            config = decodeConfigSummary(readRequiredObject(json, "config", "config"), "config"),
            permissionSnapshot = decodePermissionSnapshot(
                readOptionalArray(json, "permissionSnapshot"),
                "permissionSnapshot",
            ),
            hostActionWhitelist = decodeHostActionWhitelist(
                readOptionalArray(json, "hostActionWhitelist"),
                "hostActionWhitelist",
            ),
            triggerMetadata = decodeTriggerMetadata(
                readOptionalObject(json, "triggerMetadata") ?: JSONObject(),
                "triggerMetadata",
            ),
        )
    }

    fun encodeResult(result: PluginExecutionResult): JSONObject {
        return when (result) {
            is TextResult -> JSONObject().apply {
                put("resultType", "text")
                put("text", result.text)
                put("markdown", result.markdown)
                put("displayTitle", result.displayTitle)
            }

            is CardResult -> JSONObject().apply {
                put("resultType", "card")
                put("card", encodeCardSchema(result.card))
            }

            is MediaResult -> JSONObject().apply {
                put("resultType", "media")
                put(
                    "items",
                    JSONArray().apply {
                        result.items.forEach { item -> put(encodeMediaItem(item)) }
                    },
                )
            }

            is HostActionRequest -> JSONObject().apply {
                put("resultType", "host_action")
                put("action", result.action.wireValue)
                put("title", result.title)
                put("payload", result.payload.toJsonObject())
            }

            is SettingsUiRequest -> JSONObject().apply {
                put("resultType", "settings_ui")
                put("schema", encodeSettingsSchema(result.schema))
            }

            is NoOp -> JSONObject().apply {
                put("resultType", "noop")
                put("reason", result.reason)
            }

            is ErrorResult -> JSONObject().apply {
                put("resultType", "error")
                put("message", result.message)
                put("code", result.code)
                put("recoverable", result.recoverable)
            }
        }
    }

    fun decodeResult(json: JSONObject): PluginExecutionResult {
        return when (readRequiredString(json, "resultType", "resultType")) {
            "text" -> TextResult(
                text = readRequiredString(json, "text", "text"),
                markdown = json.optBoolean("markdown", false),
                displayTitle = json.optString("displayTitle"),
            )

            "card" -> CardResult(
                card = decodeCardSchema(readRequiredObject(json, "card", "card"), "card"),
            )

            "media" -> MediaResult(
                items = decodeMediaItems(readOptionalArray(json, "items"), "items"),
            )

            "host_action" -> HostActionRequest(
                action = decodeHostAction(readRequiredString(json, "action", "action"), "action"),
                title = json.optString("title"),
                payload = json.optJSONObject("payload").toStringMap(),
            )

            "settings_ui" -> SettingsUiRequest(
                schema = decodeSettingsSchema(readRequiredObject(json, "schema", "schema"), "schema"),
            )

            "noop" -> NoOp(
                reason = json.optString("reason"),
            )

            "error" -> ErrorResult(
                message = readRequiredString(json, "message", "message"),
                code = json.optString("code"),
                recoverable = json.optBoolean("recoverable", false),
            )

            else -> throw IllegalArgumentException("resultType has unsupported value")
        }
    }

    private fun encodeSessionRef(sessionRef: MessageSessionRef): JSONObject {
        return JSONObject().apply {
            put("platformId", sessionRef.platformId)
            put("messageType", sessionRef.messageType.wireValue)
            put("originSessionId", sessionRef.originSessionId)
        }
    }

    private fun decodeSessionRef(json: JSONObject, path: String): MessageSessionRef {
        return MessageSessionRef(
            platformId = readRequiredString(json, "platformId", "$path.platformId"),
            messageType = decodeMessageType(
                readRequiredString(json, "messageType", "$path.messageType"),
                "$path.messageType",
            ),
            originSessionId = readRequiredString(json, "originSessionId", "$path.originSessionId"),
        )
    }

    private fun encodeMessageSummary(message: PluginMessageSummary): JSONObject {
        return JSONObject().apply {
            put("messageId", message.messageId)
            put("contentPreview", message.contentPreview)
            put("senderId", message.senderId)
            put("messageType", message.messageType)
            put("attachmentCount", message.attachmentCount)
            put("timestamp", message.timestamp)
        }
    }

    private fun decodeMessageSummary(json: JSONObject, path: String): PluginMessageSummary {
        return PluginMessageSummary(
            messageId = readRequiredString(json, "messageId", "$path.messageId"),
            contentPreview = readRequiredString(json, "contentPreview", "$path.contentPreview"),
            senderId = json.optString("senderId"),
            messageType = json.optString("messageType"),
            attachmentCount = json.optInt("attachmentCount", 0),
            timestamp = json.optLong("timestamp", 0L),
        )
    }

    private fun encodeBotSummary(bot: PluginBotSummary): JSONObject {
        return JSONObject().apply {
            put("botId", bot.botId)
            put("displayName", bot.displayName)
            put("platformId", bot.platformId)
        }
    }

    private fun decodeBotSummary(json: JSONObject, path: String): PluginBotSummary {
        return PluginBotSummary(
            botId = readRequiredString(json, "botId", "$path.botId"),
            displayName = json.optString("displayName"),
            platformId = json.optString("platformId"),
        )
    }

    private fun encodeConfigSummary(config: PluginConfigSummary): JSONObject {
        return JSONObject().apply {
            put("providerId", config.providerId)
            put("modelId", config.modelId)
            put("personaId", config.personaId)
            put("extras", config.extras.toJsonObject())
        }
    }

    private fun decodeConfigSummary(json: JSONObject, path: String): PluginConfigSummary {
        return PluginConfigSummary(
            providerId = json.optString("providerId"),
            modelId = json.optString("modelId"),
            personaId = json.optString("personaId"),
            extras = (readOptionalObject(json, "extras") ?: JSONObject()).toStringMap("$path.extras"),
        )
    }

    private fun encodePermissionGrant(permission: PluginPermissionGrant): JSONObject {
        return JSONObject().apply {
            put("permissionId", permission.permissionId)
            put("title", permission.title)
            put("granted", permission.granted)
            put("required", permission.required)
            put("riskLevel", permission.riskLevel.name)
        }
    }

    private fun decodePermissionSnapshot(array: JSONArray?, path: String): List<PluginPermissionGrant> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val permission = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginPermissionGrant(
                        permissionId = readRequiredString(permission, "permissionId", "$path[$index].permissionId"),
                        title = readRequiredString(permission, "title", "$path[$index].title"),
                        granted = permission.optBoolean("granted", false),
                        required = permission.optBoolean("required", true),
                        riskLevel = decodeRiskLevel(
                            readRequiredString(permission, "riskLevel", "$path[$index].riskLevel"),
                            "$path[$index].riskLevel",
                        ),
                    ),
                )
            }
        }
    }

    private fun decodeHostActionWhitelist(array: JSONArray?, path: String): List<PluginHostAction> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                if (value.isBlank()) {
                    throw IllegalArgumentException("$path[$index] must be a non-blank string")
                }
                add(decodeHostAction(value, "$path[$index]"))
            }
        }
    }

    private fun encodeTriggerMetadata(metadata: PluginTriggerMetadata): JSONObject {
        return JSONObject().apply {
            put("eventId", metadata.eventId)
            put("command", metadata.command)
            put("entryPoint", metadata.entryPoint)
            if (metadata.scheduledAtEpochMillis != null) {
                put("scheduledAtEpochMillis", metadata.scheduledAtEpochMillis)
            }
            put("extras", metadata.extras.toJsonObject())
        }
    }

    private fun decodeTriggerMetadata(json: JSONObject, path: String): PluginTriggerMetadata {
        return PluginTriggerMetadata(
            eventId = json.optString("eventId"),
            command = json.optString("command"),
            entryPoint = json.optString("entryPoint"),
            scheduledAtEpochMillis = json.takeIf { it.has("scheduledAtEpochMillis") }?.optLong("scheduledAtEpochMillis"),
            extras = (readOptionalObject(json, "extras") ?: JSONObject()).toStringMap("$path.extras"),
        )
    }

    private fun encodeCardSchema(card: PluginCardSchema): JSONObject {
        return JSONObject().apply {
            put("title", card.title)
            put("body", card.body)
            put("status", card.status.wireValue)
            put(
                "fields",
                JSONArray().apply {
                    card.fields.forEach { field ->
                        put(
                            JSONObject().apply {
                                put("label", field.label)
                                put("value", field.value)
                            },
                        )
                    }
                },
            )
            put(
                "actions",
                JSONArray().apply {
                    card.actions.forEach { action ->
                        put(
                            JSONObject().apply {
                                put("actionId", action.actionId)
                                put("label", action.label)
                                put("style", action.style.wireValue)
                                put("payload", action.payload.toJsonObject())
                            },
                        )
                    }
                },
            )
        }
    }

    private fun decodeCardSchema(json: JSONObject, path: String): PluginCardSchema {
        return PluginCardSchema(
            title = readRequiredString(json, "title", "$path.title"),
            body = json.optString("body"),
            status = decodeUiStatus(json.optString("status").ifBlank { PluginUiStatus.Info.wireValue }, "$path.status"),
            fields = decodeCardFields(readOptionalArray(json, "fields"), "$path.fields"),
            actions = decodeCardActions(readOptionalArray(json, "actions"), "$path.actions"),
        )
    }

    private fun decodeCardFields(array: JSONArray?, path: String): List<PluginCardField> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginCardField(
                        label = readRequiredString(json, "label", "$path[$index].label"),
                        value = readRequiredString(json, "value", "$path[$index].value"),
                    ),
                )
            }
        }
    }

    private fun decodeCardActions(array: JSONArray?, path: String): List<PluginCardAction> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginCardAction(
                        actionId = readRequiredString(json, "actionId", "$path[$index].actionId"),
                        label = readRequiredString(json, "label", "$path[$index].label"),
                        style = decodeUiActionStyle(
                            json.optString("style").ifBlank { PluginUiActionStyle.Default.wireValue },
                            "$path[$index].style",
                        ),
                        payload = (readOptionalObject(json, "payload") ?: JSONObject()).toStringMap("$path[$index].payload"),
                    ),
                )
            }
        }
    }

    private fun encodeSettingsSchema(schema: PluginSettingsSchema): JSONObject {
        return JSONObject().apply {
            put("title", schema.title)
            put(
                "sections",
                JSONArray().apply {
                    schema.sections.forEach { section ->
                        put(
                            JSONObject().apply {
                                put("sectionId", section.sectionId)
                                put("title", section.title)
                                put(
                                    "fields",
                                    JSONArray().apply {
                                        section.fields.forEach { field -> put(encodeSettingsField(field)) }
                                    },
                                )
                            },
                        )
                    }
                },
            )
        }
    }

    private fun decodeSettingsSchema(json: JSONObject, path: String): PluginSettingsSchema {
        return PluginSettingsSchema(
            title = readRequiredString(json, "title", "$path.title"),
            sections = decodeSettingsSections(readOptionalArray(json, "sections"), "$path.sections"),
        )
    }

    private fun decodeSettingsSections(array: JSONArray?, path: String): List<PluginSettingsSection> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginSettingsSection(
                        sectionId = readRequiredString(json, "sectionId", "$path[$index].sectionId"),
                        title = readRequiredString(json, "title", "$path[$index].title"),
                        fields = decodeSettingsFields(readOptionalArray(json, "fields"), "$path[$index].fields"),
                    ),
                )
            }
        }
    }

    private fun encodeSettingsField(field: PluginSettingsField): JSONObject {
        return when (field) {
            is ToggleSettingField -> JSONObject().apply {
                put("fieldType", "toggle")
                put("fieldId", field.fieldId)
                put("label", field.label)
                put("defaultValue", field.defaultValue)
            }

            is TextInputSettingField -> JSONObject().apply {
                put("fieldType", "text_input")
                put("fieldId", field.fieldId)
                put("label", field.label)
                put("placeholder", field.placeholder)
                put("defaultValue", field.defaultValue)
            }

            is SelectSettingField -> JSONObject().apply {
                put("fieldType", "select")
                put("fieldId", field.fieldId)
                put("label", field.label)
                put("defaultValue", field.defaultValue)
                put(
                    "options",
                    JSONArray().apply {
                        field.options.forEach { option ->
                            put(
                                JSONObject().apply {
                                    put("value", option.value)
                                    put("label", option.label)
                                },
                            )
                        }
                    },
                )
            }
        }
    }

    private fun decodeSettingsFields(array: JSONArray?, path: String): List<PluginSettingsField> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                val fieldType = readRequiredString(json, "fieldType", "$path[$index].fieldType")
                val fieldId = readRequiredString(json, "fieldId", "$path[$index].fieldId")
                val label = readRequiredString(json, "label", "$path[$index].label")
                add(
                    when (fieldType) {
                        "toggle" -> ToggleSettingField(
                            fieldId = fieldId,
                            label = label,
                            defaultValue = json.optBoolean("defaultValue", false),
                        )

                        "text_input" -> TextInputSettingField(
                            fieldId = fieldId,
                            label = label,
                            placeholder = json.optString("placeholder"),
                            defaultValue = json.optString("defaultValue"),
                        )

                        "select" -> SelectSettingField(
                            fieldId = fieldId,
                            label = label,
                            defaultValue = json.optString("defaultValue"),
                            options = decodeSelectOptions(readOptionalArray(json, "options"), "$path[$index].options"),
                        )

                        else -> throw IllegalArgumentException("$path[$index].fieldType has unsupported value")
                    },
                )
            }
        }
    }

    private fun decodeSelectOptions(array: JSONArray?, path: String): List<PluginSelectOption> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginSelectOption(
                        value = readRequiredString(json, "value", "$path[$index].value"),
                        label = readRequiredString(json, "label", "$path[$index].label"),
                    ),
                )
            }
        }
    }

    private fun encodeMediaItem(item: PluginMediaItem): JSONObject {
        return JSONObject().apply {
            put("source", item.source)
            put("mimeType", item.mimeType)
            put("altText", item.altText)
        }
    }

    private fun decodeMediaItems(array: JSONArray?, path: String): List<PluginMediaItem> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginMediaItem(
                        source = readRequiredString(json, "source", "$path[$index].source"),
                        mimeType = json.optString("mimeType"),
                        altText = json.optString("altText"),
                    ),
                )
            }
        }
    }

    private fun decodeTriggerSource(value: String): PluginTriggerSource {
        return PluginTriggerSource.fromWireValue(value)
            ?: throw IllegalArgumentException("trigger has unsupported value")
    }

    private fun decodeHostAction(value: String, path: String): PluginHostAction {
        return PluginHostAction.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun decodeMessageType(value: String, path: String): MessageType {
        return MessageType.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun decodeRiskLevel(value: String, path: String): PluginRiskLevel {
        return runCatching { PluginRiskLevel.valueOf(value) }
            .getOrElse { throw IllegalArgumentException("$path has unsupported value") }
    }

    private fun decodeUiStatus(value: String, path: String): PluginUiStatus {
        return PluginUiStatus.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun decodeUiActionStyle(value: String, path: String): PluginUiActionStyle {
        return PluginUiActionStyle.fromWireValue(value)
            ?: throw IllegalArgumentException("$path has unsupported value")
    }

    private fun Map<String, String>.toJsonObject(): JSONObject {
        return JSONObject().apply {
            entries.forEach { (key, value) -> put(key, value) }
        }
    }

    private fun JSONObject?.toStringMap(path: String = ""): Map<String, String> {
        if (this == null) return emptyMap()
        val result = linkedMapOf<String, String>()
        val iterator = keys()
        while (iterator.hasNext()) {
            val key = iterator.next()
            val value = opt(key)
            if (value == null || value == JSONObject.NULL) {
                continue
            }
            if (value !is String) {
                val location = if (path.isBlank()) key else "$path.$key"
                throw IllegalArgumentException("$location must be a string")
            }
            result[key] = value
        }
        return result
    }

    private fun readRequiredString(json: JSONObject, key: String, path: String): String {
        val value = json.optString(key)
        if (value.isBlank()) {
            throw IllegalArgumentException("$path is required")
        }
        return value
    }

    private fun readRequiredObject(json: JSONObject, key: String, path: String): JSONObject {
        return json.optJSONObject(key)
            ?: throw IllegalArgumentException("$path must be an object")
    }

    private fun readOptionalObject(json: JSONObject, key: String): JSONObject? {
        return json.optJSONObject(key)
    }

    private fun readOptionalArray(json: JSONObject, key: String): JSONArray? {
        return json.optJSONArray(key)
    }

    fun encodePluginExecutionStage(stage: PluginExecutionStage): String {
        return stage.wireValue
    }

    fun decodePluginExecutionStage(value: String): PluginExecutionStage {
        return PluginExecutionStage.fromWireValue(value)
            ?: throw IllegalArgumentException("stage has unsupported value")
    }

    fun encodePluginV2LlmStage(stage: PluginV2LlmStage): String {
        return stage.wireValue
    }

    fun decodePluginV2LlmStage(value: String): PluginV2LlmStage {
        return PluginV2LlmStage.fromWireValue(value)
            ?: throw IllegalArgumentException("stage has unsupported value")
    }

    fun encodePluginV2StreamingMode(mode: PluginV2StreamingMode): String {
        return mode.wireValue
    }

    fun decodePluginV2StreamingMode(value: String): PluginV2StreamingMode {
        return PluginV2StreamingMode.fromWireValue(value)
            ?: throw IllegalArgumentException("streamingMode has unsupported value")
    }

    fun encodeAppChatLlm(target: AppChatLlm): String {
        return target.wireValue
    }

    fun decodeAppChatLlm(value: String): AppChatLlm {
        return AppChatLlm.fromWireValue(value)
            ?: throw IllegalArgumentException("routingTarget has unsupported value")
    }

    fun encodePluginProviderRequest(request: PluginProviderRequest): JSONObject {
        return JSONObject().apply {
            put("requestId", request.requestId)
            put(
                "availableProviderIds",
                JSONArray().apply {
                    request.availableProviderIds.forEach { put(it) }
                },
            )
            put(
                "availableModelIdsByProvider",
                JSONObject().apply {
                    request.availableModelIdsByProvider.forEach { (providerId, modelIds) ->
                        put(
                            providerId,
                            JSONArray().apply { modelIds.forEach { put(it) } },
                        )
                    }
                },
            )
            put("conversationId", request.conversationId)
            put(
                "messageIds",
                JSONArray().apply {
                    request.messageIds.forEach { put(it) }
                },
            )
            put("llmInputSnapshot", request.llmInputSnapshot)
            put("selectedProviderId", request.selectedProviderId)
            put("selectedModelId", request.selectedModelId)
            put("systemPrompt", request.systemPrompt?.let(::encodeJsonLikeValue) ?: JSONObject.NULL)
            put(
                "messages",
                JSONArray().apply {
                    request.messages.forEach { put(encodePluginProviderMessageDto(it)) }
                },
            )
            put("temperature", request.temperature ?: JSONObject.NULL)
            put("topP", request.topP ?: JSONObject.NULL)
            put("maxTokens", request.maxTokens ?: JSONObject.NULL)
            put("streamingEnabled", request.streamingEnabled)
            put("metadata", request.metadata?.let(::encodeJsonLikeObject) ?: JSONObject.NULL)
        }
    }

    fun decodePluginProviderRequest(json: JSONObject): PluginProviderRequest {
        return PluginProviderRequest(
            requestId = readRequiredString(json, "requestId", "requestId"),
            availableProviderIds = readStringArray(readRequiredArray(json, "availableProviderIds", "availableProviderIds"), "availableProviderIds"),
            availableModelIdsByProvider = decodeStringListMap(
                readRequiredObject(json, "availableModelIdsByProvider", "availableModelIdsByProvider"),
                "availableModelIdsByProvider",
            ),
            conversationId = readRequiredString(json, "conversationId", "conversationId"),
            messageIds = readStringArray(readRequiredArray(json, "messageIds", "messageIds"), "messageIds"),
            llmInputSnapshot = readRequiredString(json, "llmInputSnapshot", "llmInputSnapshot"),
            selectedProviderId = readOptionalString(json, "selectedProviderId").orEmpty(),
            selectedModelId = readOptionalString(json, "selectedModelId").orEmpty(),
            systemPrompt = readOptionalString(json, "systemPrompt"),
            messages = decodeProviderMessages(readOptionalArray(json, "messages"), "messages"),
            temperature = readOptionalNumber(json, "temperature")?.toDouble(),
            topP = readOptionalNumber(json, "topP")?.toDouble(),
            maxTokens = readOptionalNumber(json, "maxTokens")?.toInt(),
            streamingEnabled = json.optBoolean("streamingEnabled", false),
            metadata = decodeJsonLikeObject(readOptionalObject(json, "metadata"), "metadata"),
        )
    }

    fun encodePluginProviderMessageDto(message: PluginProviderMessageDto): JSONObject {
        return JSONObject().apply {
            put("role", message.role.wireValue)
            put("name", message.name?.let(::encodeJsonLikeValue) ?: JSONObject.NULL)
            put(
                "parts",
                JSONArray().apply {
                    message.parts.forEach { put(encodePluginProviderMessagePartDto(it)) }
                },
            )
            put("metadata", message.metadata?.let(::encodeJsonLikeObject) ?: JSONObject.NULL)
        }
    }

    fun decodePluginProviderMessageDto(json: JSONObject): PluginProviderMessageDto {
        val role = decodePluginProviderMessageRole(readRequiredString(json, "role", "role"))
        return PluginProviderMessageDto(
            role = role,
            parts = decodeProviderMessageParts(readRequiredArray(json, "parts", "parts"), "parts"),
            name = readOptionalString(json, "name"),
            metadata = decodeJsonLikeObject(readOptionalObject(json, "metadata"), "metadata"),
        )
    }

    fun encodePluginProviderMessagePartDto(part: PluginProviderMessagePartDto): JSONObject {
        return when (part) {
            is PluginProviderMessagePartDto.TextPart -> JSONObject().apply {
                put("partType", "text")
                put("text", part.text)
            }

            is PluginProviderMessagePartDto.MediaRefPart -> JSONObject().apply {
                put("partType", "media_ref")
                put("uri", part.uri)
                put("mimeType", part.mimeType)
            }
        }
    }

    fun decodePluginProviderMessagePartDto(json: JSONObject): PluginProviderMessagePartDto {
        return when (readRequiredString(json, "partType", "partType")) {
            "text" -> PluginProviderMessagePartDto.TextPart(
                text = readRequiredString(json, "text", "text"),
            )

            "media_ref" -> PluginProviderMessagePartDto.MediaRefPart(
                uri = readRequiredString(json, "uri", "uri"),
                mimeType = readRequiredString(json, "mimeType", "mimeType"),
            )

            else -> throw IllegalArgumentException("partType has unsupported value")
        }
    }

    fun encodePluginLlmUsageSnapshot(usage: PluginLlmUsageSnapshot): JSONObject {
        return JSONObject().apply {
            put("promptTokens", usage.promptTokens ?: JSONObject.NULL)
            put("completionTokens", usage.completionTokens ?: JSONObject.NULL)
            put("totalTokens", usage.totalTokens ?: JSONObject.NULL)
            put("inputCostMicros", usage.inputCostMicros ?: JSONObject.NULL)
            put("outputCostMicros", usage.outputCostMicros ?: JSONObject.NULL)
            put("currencyCode", usage.normalizedCurrencyCode?.let(::encodeJsonLikeValue) ?: JSONObject.NULL)
        }
    }

    fun decodePluginLlmUsageSnapshot(json: JSONObject): PluginLlmUsageSnapshot {
        return PluginLlmUsageSnapshot(
            promptTokens = readOptionalInt(json, "promptTokens"),
            completionTokens = readOptionalInt(json, "completionTokens"),
            totalTokens = readOptionalInt(json, "totalTokens"),
            inputCostMicros = readOptionalLong(json, "inputCostMicros"),
            outputCostMicros = readOptionalLong(json, "outputCostMicros"),
            currencyCode = readOptionalString(json, "currencyCode"),
        )
    }

    fun encodePluginLlmResponse(response: PluginLlmResponse): JSONObject {
        return JSONObject().apply {
            put("requestId", response.requestId)
            put("providerId", response.providerId)
            put("modelId", response.modelId)
            put("usage", response.usage?.let(::encodePluginLlmUsageSnapshot) ?: JSONObject.NULL)
            put("finishReason", response.finishReason?.let(::encodeJsonLikeValue) ?: JSONObject.NULL)
            put("text", response.text)
            put("markdown", response.markdown)
            put("metadata", response.metadata?.let(::encodeJsonLikeObject) ?: JSONObject.NULL)
        }
    }

    fun decodePluginLlmResponse(json: JSONObject): PluginLlmResponse {
        return PluginLlmResponse(
            requestId = readRequiredString(json, "requestId", "requestId"),
            providerId = readRequiredString(json, "providerId", "providerId"),
            modelId = readRequiredString(json, "modelId", "modelId"),
            usage = readOptionalObject(json, "usage")?.let(::decodePluginLlmUsageSnapshot),
            finishReason = readOptionalString(json, "finishReason"),
            text = readOptionalString(json, "text").orEmpty(),
            markdown = json.optBoolean("markdown", false),
            metadata = decodeJsonLikeObject(readOptionalObject(json, "metadata"), "metadata"),
        )
    }

    fun encodePluginMessageEventResult(result: PluginMessageEventResult): JSONObject {
        return JSONObject().apply {
            put("requestId", result.requestId)
            put("conversationId", result.conversationId)
            put("text", result.text)
            put("markdown", result.markdown)
            put(
                "attachments",
                JSONArray().apply {
                    result.attachments.forEach { put(encodePluginMessageEventResultAttachment(it)) }
                },
            )
            put("shouldSend", result.shouldSend)
            put("isStopped", result.isStopped)
            put("attachmentMutationIntent", result.attachmentMutationIntent.name)
        }
    }

    fun decodePluginMessageEventResult(json: JSONObject): PluginMessageEventResult {
        val attachments = decodePluginMessageEventResultAttachments(
            readOptionalArray(json, "attachments"),
            "attachments",
        )
        val result = PluginMessageEventResult(
            requestId = readRequiredString(json, "requestId", "requestId"),
            conversationId = readRequiredString(json, "conversationId", "conversationId"),
            text = readOptionalString(json, "text").orEmpty(),
            markdown = json.optBoolean("markdown", false),
            attachments = attachments,
            shouldSend = json.optBoolean("shouldSend", true),
            attachmentMutationIntent = decodePluginMessageEventResultAttachmentMutationIntent(
                readOptionalString(json, "attachmentMutationIntent"),
                attachments,
            ),
        )
        if (json.optBoolean("isStopped", false)) {
            result.stop()
        }
        return result
    }

    fun encodePluginV2AfterSentView(view: PluginV2AfterSentView): JSONObject {
        return JSONObject().apply {
            put("requestId", view.requestId)
            put("conversationId", view.conversationId)
            put("sendAttemptId", view.sendAttemptId)
            put("platformAdapterType", view.platformAdapterType)
            put("platformInstanceKey", view.platformInstanceKey)
            put("sentAtEpochMs", view.sentAtEpochMs)
            put("deliveryStatus", view.deliveryStatus.wireValue)
            put("deliveredEntryCount", view.deliveredEntryCount)
            put(
                "receiptIds",
                JSONArray().apply {
                    view.receiptIds.forEach { put(it) }
                },
            )
            put(
                "deliveredEntries",
                JSONArray().apply {
                    view.deliveredEntries.forEach { put(encodePluginV2AfterSentEntry(it)) }
                },
            )
            put("usage", view.usage?.let(::encodePluginLlmUsageSnapshot) ?: JSONObject.NULL)
        }
    }

    fun decodePluginV2AfterSentView(json: JSONObject): PluginV2AfterSentView {
        val deliveredEntries = decodePluginV2AfterSentEntries(
            readOptionalArray(json, "deliveredEntries"),
            "deliveredEntries",
        )
        return PluginV2AfterSentView(
            requestId = readRequiredString(json, "requestId", "requestId"),
            conversationId = readRequiredString(json, "conversationId", "conversationId"),
            sendAttemptId = readRequiredString(json, "sendAttemptId", "sendAttemptId"),
            platformAdapterType = readRequiredString(json, "platformAdapterType", "platformAdapterType"),
            platformInstanceKey = readRequiredString(json, "platformInstanceKey", "platformInstanceKey"),
            sentAtEpochMs = json.optLong("sentAtEpochMs"),
            deliveryStatus = decodePluginV2AfterSentDeliveryStatus(
                readRequiredString(json, "deliveryStatus", "deliveryStatus"),
            ),
            receiptIds = readStringArray(readOptionalArray(json, "receiptIds"), "receiptIds"),
            deliveredEntries = deliveredEntries,
            usage = readOptionalObject(json, "usage")?.let(::decodePluginLlmUsageSnapshot),
            deliveredEntryCount = json.optInt("deliveredEntryCount", deliveredEntries.size),
        )
    }

    private fun encodePluginV2AfterSentEntry(entry: PluginV2AfterSentView.DeliveredEntry): JSONObject {
        return JSONObject().apply {
            put("entryId", entry.entryId)
            put("entryType", entry.entryType)
            put("textPreview", entry.textPreview)
            put("attachmentCount", entry.attachmentCount)
        }
    }

    private fun decodePluginV2AfterSentEntries(array: JSONArray?, path: String): List<PluginV2AfterSentView.DeliveredEntry> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginV2AfterSentView.DeliveredEntry(
                        entryId = readRequiredString(json, "entryId", "$path[$index].entryId"),
                        entryType = readRequiredString(json, "entryType", "$path[$index].entryType"),
                        textPreview = json.optString("textPreview"),
                        attachmentCount = json.optInt("attachmentCount", 0),
                    ),
                )
            }
        }
    }

    private fun encodePluginMessageEventResultAttachment(attachment: PluginMessageEventResult.Attachment): JSONObject {
        return JSONObject().apply {
            put("uri", attachment.uri)
            put("mimeType", attachment.mimeType)
        }
    }

    private fun decodePluginMessageEventResultAttachments(array: JSONArray?, path: String): List<PluginMessageEventResult.Attachment> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(
                    PluginMessageEventResult.Attachment(
                        uri = readRequiredString(json, "uri", "$path[$index].uri"),
                        mimeType = json.optString("mimeType"),
                    ),
                )
            }
        }
    }

    private fun decodePluginMessageEventResultAttachmentMutationIntent(
        value: String?,
        attachments: List<PluginMessageEventResult.Attachment>,
    ): PluginMessageEventResult.AttachmentMutationIntent {
        if (value.isNullOrBlank()) {
            return if (attachments.isEmpty()) {
                PluginMessageEventResult.AttachmentMutationIntent.UNTOUCHED
            } else {
                PluginMessageEventResult.AttachmentMutationIntent.REPLACED
            }
        }
        return try {
            PluginMessageEventResult.AttachmentMutationIntent.valueOf(value)
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("attachmentMutationIntent has unsupported value", error)
        }
    }

    private fun encodeJsonLikeObject(map: JsonLikeMap): JSONObject {
        return JSONObject().apply {
            map.forEach { (key, value) ->
                put(key, encodeJsonLikeValue(value))
            }
        }
    }

    private fun decodeJsonLikeObject(json: JSONObject?, path: String): JsonLikeMap? {
        if (json == null) return null
        val result = linkedMapOf<String, AllowedValue>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            result[key] = decodeJsonLikeValue(json.opt(key), "$path.$key")
        }
        return PluginV2ValueSanitizer.requireAllowedMap(result)
    }

    private fun encodeJsonLikeValue(value: AllowedValue): Any? {
        return when (value) {
            null -> JSONObject.NULL
            is String,
            is Boolean,
            is Int,
            is Long,
            is Double,
            -> value

            is Float -> value.toDouble()

            is List<*> -> JSONArray().apply {
                value.forEach { put(encodeJsonLikeValue(it)) }
            }

            is Map<*, *> -> JSONObject().apply {
                value.forEach { (key, item) ->
                    require(key is String) { "JSON-like map keys must be strings." }
                    put(key, encodeJsonLikeValue(item))
                }
            }

            else -> throw IllegalArgumentException("Unsupported JSON-like value type: ${value::class.java.name}")
        }
    }

    private fun decodeJsonLikeValue(value: Any?, path: String): AllowedValue {
        return when (value) {
            null,
            JSONObject.NULL,
            -> null

            is String,
            is Boolean,
            is Int,
            is Long,
            is Double,
            -> value

            is Number -> when (value) {
                is java.lang.Integer -> value.toInt()
                is java.lang.Long -> value.toLong()
                is java.lang.Double -> value.toDouble()
                is java.lang.Float -> value.toDouble()
                else -> value.toDouble()
            }

            is JSONArray -> buildList {
                for (index in 0 until value.length()) {
                    add(decodeJsonLikeValue(value.opt(index), "$path[$index]"))
                }
            }

            is JSONObject -> decodeJsonLikeObject(value, path)
                ?: throw IllegalArgumentException("$path must be an object")

            else -> throw IllegalArgumentException("$path contains unsupported value type: ${value::class.java.name}")
        }
    }

    private fun readRequiredArray(json: JSONObject, key: String, path: String): JSONArray {
        return json.optJSONArray(key)
            ?: throw IllegalArgumentException("$path must be an array")
    }

    private fun readStringArray(array: JSONArray?, path: String): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index)
                require(value.isNotBlank()) {
                    "$path[$index] must be a non-blank string"
                }
                add(value)
            }
        }
    }

    private fun decodeStringListMap(json: JSONObject, path: String): Map<String, List<String>> {
        val result = linkedMapOf<String, List<String>>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = json.optJSONArray(key)
                ?: throw IllegalArgumentException("$path.$key must be an array")
            result[key] = readStringArray(value, "$path.$key")
        }
        return result
    }

    private fun decodeProviderMessages(array: JSONArray?, path: String): List<PluginProviderMessageDto> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(decodePluginProviderMessageDto(json))
            }
        }
    }

    private fun decodeProviderMessageParts(array: JSONArray, path: String): List<PluginProviderMessagePartDto> {
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index)
                    ?: throw IllegalArgumentException("$path[$index] must be an object")
                add(decodePluginProviderMessagePartDto(json))
            }
        }
    }

    private fun decodePluginProviderMessageRole(value: String): PluginProviderMessageRole {
        return PluginProviderMessageRole.fromWireValue(value)
            ?: throw IllegalArgumentException("role has unsupported value")
    }

    private fun decodePluginV2AfterSentDeliveryStatus(value: String): PluginV2AfterSentView.DeliveryStatus {
        return PluginV2AfterSentView.DeliveryStatus.fromWireValue(value)
            ?: throw IllegalArgumentException("deliveryStatus has unsupported value")
    }

    private fun readOptionalString(json: JSONObject, key: String): String? {
        val value = json.opt(key)
        return when (value) {
            null,
            JSONObject.NULL,
            -> null

            is String -> value.takeIf { it.isNotBlank() }
            else -> throw IllegalArgumentException("$key must be a string")
        }
    }

    private fun readOptionalInt(json: JSONObject, key: String): Int? {
        return readOptionalNumber(json, key)?.toInt()
    }

    private fun readOptionalLong(json: JSONObject, key: String): Long? {
        return readOptionalNumber(json, key)?.toLong()
    }

    private fun readOptionalNumber(json: JSONObject, key: String): Number? {
        val value = json.opt(key)
        return when (value) {
            null,
            JSONObject.NULL,
            -> null

            is Number -> value
            else -> throw IllegalArgumentException("$key must be a number")
        }
    }
}

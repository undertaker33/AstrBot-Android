package com.astrbot.android.model.plugin

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginExecutionProtocolModelsTest {
    @Test
    fun trigger_source_enum_covers_phase2_protocol_sources_with_stable_wire_values() {
        val enumClass = loadClass("com.astrbot.android.model.plugin.PluginTriggerSource")
        val enumConstants = enumClass.enumConstants ?: throw AssertionError("PluginTriggerSource must be an enum")
        val actualNames = enumConstants.map { (it as Enum<*>).name }
        val actualWireValues = enumConstants.associate { constant ->
            val wireValue = enumClass.getDeclaredField("wireValue").apply { isAccessible = true }.get(constant) as String
            (constant as Enum<*>).name to wireValue
        }

        assertEquals(
            listOf(
                "OnMessageReceived",
                "BeforeSendMessage",
                "AfterModelResponse",
                "OnSchedule",
                "OnPluginEntryClick",
                "OnCommand",
                "OnConversationEnter",
            ),
            actualNames,
        )
        assertEquals("on_message_received", actualWireValues.getValue("OnMessageReceived"))
        assertEquals("before_send_message", actualWireValues.getValue("BeforeSendMessage"))
        assertEquals("after_model_response", actualWireValues.getValue("AfterModelResponse"))
        assertEquals("on_schedule", actualWireValues.getValue("OnSchedule"))
        assertEquals("on_plugin_entry_click", actualWireValues.getValue("OnPluginEntryClick"))
        assertEquals("on_command", actualWireValues.getValue("OnCommand"))
        assertEquals("on_conversation_enter", actualWireValues.getValue("OnConversationEnter"))
        val fromWireValue = enumClass.getDeclaredMethod("fromWireValue", String::class.java)
        assertNotNull(fromWireValue.invoke(null, "on_schedule"))
    }

    @Test
    fun trigger_contracts_publish_phase3_online_and_residual_split() {
        assertEquals(
            setOf(
                PluginTriggerSource.OnPluginEntryClick,
                PluginTriggerSource.OnCommand,
                PluginTriggerSource.BeforeSendMessage,
                PluginTriggerSource.AfterModelResponse,
            ),
            PluginTriggerContracts.onlineHostTriggers,
        )
        assertEquals(
            setOf(
                PluginTriggerSource.OnMessageReceived,
                PluginTriggerSource.OnSchedule,
                PluginTriggerSource.OnConversationEnter,
            ),
            PluginTriggerContracts.residualCompatOnlyTriggers,
        )
    }

    @Test
    fun host_action_whitelist_enum_covers_phase2_capabilities_with_stable_wire_values() {
        val enumClass = loadClass("com.astrbot.android.model.plugin.PluginHostAction")
        val enumConstants = enumClass.enumConstants ?: throw AssertionError("PluginHostAction must be an enum")
        val actualNames = enumConstants.map { (it as Enum<*>).name }
        val actualWireValues = enumConstants.associate { constant ->
            val wireValue = enumClass.getDeclaredField("wireValue").apply { isAccessible = true }.get(constant) as String
            (constant as Enum<*>).name to wireValue
        }

        assertEquals(
            listOf(
                "CallModel",
                "NetworkRequest",
                "ReadPrivateData",
                "WritePrivateData",
                "SendMessage",
                "SendNotification",
                "OpenHostPage",
            ),
            actualNames,
        )
        assertEquals("call_model", actualWireValues.getValue("CallModel"))
        assertEquals("network_request", actualWireValues.getValue("NetworkRequest"))
        assertEquals("read_private_data", actualWireValues.getValue("ReadPrivateData"))
        assertEquals("write_private_data", actualWireValues.getValue("WritePrivateData"))
        assertEquals("send_message", actualWireValues.getValue("SendMessage"))
        assertEquals("send_notification", actualWireValues.getValue("SendNotification"))
        assertEquals("open_host_page", actualWireValues.getValue("OpenHostPage"))
    }

    @Test
    fun execution_context_exposes_phase2_required_fields() {
        val contextClass = loadClass("com.astrbot.android.model.plugin.PluginExecutionContext")
        val fieldNames = contextClass.declaredFields
            .map { it.name }
            .filterNot { it == "Companion" || it.startsWith("$") }

        assertEquals(
            listOf(
                "trigger",
                "pluginId",
                "pluginVersion",
                "sessionRef",
                "message",
                "bot",
                "config",
                "permissionSnapshot",
                "hostActionWhitelist",
                "triggerMetadata",
            ),
            fieldNames,
        )
    }

    @Test
    fun result_and_schema_models_exist_without_compose_type_leaks() {
        loadClass("com.astrbot.android.model.plugin.PluginExecutionResult")
        assertHasFieldNames(
            className = "com.astrbot.android.model.plugin.TextResult",
            expectedFieldNames = listOf("text", "markdown", "displayTitle"),
        )
        assertHasFieldNames(
            className = "com.astrbot.android.model.plugin.CardResult",
            expectedFieldNames = listOf("card"),
        )
        assertHasFieldNames(
            className = "com.astrbot.android.model.plugin.MediaResult",
            expectedFieldNames = listOf("items"),
        )
        assertHasFieldNames(
            className = "com.astrbot.android.model.plugin.HostActionRequest",
            expectedFieldNames = listOf("action", "title", "payload"),
        )
        assertHasFieldNames(
            className = "com.astrbot.android.model.plugin.SettingsUiRequest",
            expectedFieldNames = listOf("schema"),
        )
        assertHasFieldNames(
            className = "com.astrbot.android.model.plugin.NoOp",
            expectedFieldNames = listOf("reason"),
        )
        assertHasFieldNames(
            className = "com.astrbot.android.model.plugin.ErrorResult",
            expectedFieldNames = listOf("message", "code", "recoverable"),
        )
        assertNoComposeTypes("com.astrbot.android.model.plugin.PluginCardSchema")
        assertNoComposeTypes("com.astrbot.android.model.plugin.PluginSettingsSchema")
    }

    @Test
    fun execution_context_round_trip_preserves_nested_protocol_fields() {
        val jsonCodec = loadProtocolJsonCodec()
        val sourceJson = JSONObject(
            mapOf(
                "trigger" to "on_command",
                "pluginId" to "com.astrbot.demo",
                "pluginVersion" to "2.0.0",
                "sessionRef" to JSONObject(
                    mapOf(
                        "platformId" to "qq",
                        "messageType" to "group",
                        "originSessionId" to "group-42",
                    ),
                ),
                "message" to JSONObject(
                    mapOf(
                        "messageId" to "msg-1",
                        "contentPreview" to "/demo ping",
                        "senderId" to "user-42",
                        "messageType" to "command",
                        "attachmentCount" to 1,
                        "timestamp" to 1710000000000L,
                    ),
                ),
                "bot" to JSONObject(
                    mapOf(
                        "botId" to "bot-main",
                        "displayName" to "AstrBot",
                        "platformId" to "qq",
                    ),
                ),
                "config" to JSONObject(
                    mapOf(
                        "providerId" to "provider-main",
                        "modelId" to "gpt-4.1",
                        "personaId" to "helper",
                        "extras" to JSONObject(mapOf("temperature" to "0.2")),
                    ),
                ),
                "permissionSnapshot" to JSONArray().put(
                    JSONObject(
                        mapOf(
                            "permissionId" to "net.access",
                            "title" to "Network access",
                            "granted" to true,
                            "required" to true,
                            "riskLevel" to "HIGH",
                        ),
                    ),
                ),
                "hostActionWhitelist" to JSONArray()
                    .put("call_model")
                    .put("network_request")
                    .put("send_message"),
                "triggerMetadata" to JSONObject(
                    mapOf(
                        "eventId" to "evt-9",
                        "command" to "/demo",
                        "entryPoint" to "chat-toolbar",
                        "scheduledAtEpochMillis" to 1710000005000L,
                        "extras" to JSONObject(mapOf("traceId" to "trace-1")),
                    ),
                ),
            ),
        )

        val decoded = jsonCodec.javaClass
            .getDeclaredMethod("decodeExecutionContext", JSONObject::class.java)
            .invoke(jsonCodec, sourceJson)
        val encoded = jsonCodec.javaClass
            .getDeclaredMethod("encodeExecutionContext", loadClass("com.astrbot.android.model.plugin.PluginExecutionContext"))
            .invoke(jsonCodec, decoded) as JSONObject

        assertEquals("on_command", encoded.getString("trigger"))
        assertEquals("com.astrbot.demo", encoded.getString("pluginId"))
        assertEquals("2.0.0", encoded.getString("pluginVersion"))
        assertEquals("qq", encoded.getJSONObject("sessionRef").getString("platformId"))
        assertEquals("group", encoded.getJSONObject("sessionRef").getString("messageType"))
        assertEquals("/demo ping", encoded.getJSONObject("message").getString("contentPreview"))
        assertEquals("AstrBot", encoded.getJSONObject("bot").getString("displayName"))
        assertEquals("gpt-4.1", encoded.getJSONObject("config").getString("modelId"))
        assertEquals("temperature", encoded.getJSONObject("config").getJSONObject("extras").keys().next())
        assertEquals("net.access", encoded.getJSONArray("permissionSnapshot").getJSONObject(0).getString("permissionId"))
        assertEquals(true, encoded.getJSONArray("permissionSnapshot").getJSONObject(0).getBoolean("granted"))
        assertEquals("call_model", encoded.getJSONArray("hostActionWhitelist").getString(0))
        assertEquals("evt-9", encoded.getJSONObject("triggerMetadata").getString("eventId"))
        assertEquals("trace-1", encoded.getJSONObject("triggerMetadata").getJSONObject("extras").getString("traceId"))
    }

    @Test
    fun result_round_trip_preserves_card_and_settings_schema_without_compose_details() {
        val jsonCodec = loadProtocolJsonCodec()
        val cardResultJson = JSONObject(
            mapOf(
                "resultType" to "card",
                "card" to JSONObject(
                    mapOf(
                        "title" to "Plugin summary",
                        "body" to "Body text",
                        "status" to "warning",
                        "fields" to JSONArray().put(
                            JSONObject(
                                mapOf(
                                    "label" to "Latency",
                                    "value" to "120ms",
                                ),
                            ),
                        ),
                        "actions" to JSONArray().put(
                            JSONObject(
                                mapOf(
                                    "actionId" to "open-details",
                                    "label" to "Open details",
                                    "style" to "primary",
                                    "payload" to JSONObject(mapOf("target" to "details")),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val settingsResultJson = JSONObject(
            mapOf(
                "resultType" to "settings_ui",
                "schema" to JSONObject(
                    mapOf(
                        "title" to "Demo settings",
                        "sections" to JSONArray().put(
                            JSONObject(
                                mapOf(
                                    "sectionId" to "general",
                                    "title" to "General",
                                    "fields" to JSONArray()
                                        .put(
                                            JSONObject(
                                                mapOf(
                                                    "fieldType" to "toggle",
                                                    "fieldId" to "enabled",
                                                    "label" to "Enabled",
                                                    "defaultValue" to true,
                                                ),
                                            ),
                                        )
                                        .put(
                                            JSONObject(
                                                mapOf(
                                                    "fieldType" to "text_input",
                                                    "fieldId" to "endpoint",
                                                    "label" to "Endpoint",
                                                    "placeholder" to "https://example.com",
                                                    "defaultValue" to "",
                                                ),
                                            ),
                                        )
                                        .put(
                                            JSONObject(
                                                mapOf(
                                                    "fieldType" to "select",
                                                    "fieldId" to "mode",
                                                    "label" to "Mode",
                                                    "defaultValue" to "safe",
                                                    "options" to JSONArray()
                                                        .put(JSONObject(mapOf("value" to "safe", "label" to "Safe")))
                                                        .put(JSONObject(mapOf("value" to "fast", "label" to "Fast"))),
                                                ),
                                            ),
                                        ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val decodedCard = jsonCodec.javaClass
            .getDeclaredMethod("decodeResult", JSONObject::class.java)
            .invoke(jsonCodec, cardResultJson)
        val encodedCard = jsonCodec.javaClass
            .getDeclaredMethod("encodeResult", loadClass("com.astrbot.android.model.plugin.PluginExecutionResult"))
            .invoke(jsonCodec, decodedCard) as JSONObject
        val decodedSettings = jsonCodec.javaClass
            .getDeclaredMethod("decodeResult", JSONObject::class.java)
            .invoke(jsonCodec, settingsResultJson)
        val encodedSettings = jsonCodec.javaClass
            .getDeclaredMethod("encodeResult", loadClass("com.astrbot.android.model.plugin.PluginExecutionResult"))
            .invoke(jsonCodec, decodedSettings) as JSONObject

        assertEquals("card", encodedCard.getString("resultType"))
        assertEquals("Plugin summary", encodedCard.getJSONObject("card").getString("title"))
        assertEquals("warning", encodedCard.getJSONObject("card").getString("status"))
        assertEquals("Latency", encodedCard.getJSONObject("card").getJSONArray("fields").getJSONObject(0).getString("label"))
        assertEquals("primary", encodedCard.getJSONObject("card").getJSONArray("actions").getJSONObject(0).getString("style"))

        assertEquals("settings_ui", encodedSettings.getString("resultType"))
        assertEquals("Demo settings", encodedSettings.getJSONObject("schema").getString("title"))
        val settingsFields = encodedSettings.getJSONObject("schema")
            .getJSONArray("sections")
            .getJSONObject(0)
            .getJSONArray("fields")
        assertEquals("toggle", settingsFields.getJSONObject(0).getString("fieldType"))
        assertEquals("text_input", settingsFields.getJSONObject(1).getString("fieldType"))
        assertEquals("select", settingsFields.getJSONObject(2).getString("fieldType"))
        assertEquals("safe", settingsFields.getJSONObject(2).getString("defaultValue"))
    }

    @Test
    fun malformed_execution_context_json_points_to_specific_protocol_field() {
        val jsonCodec = loadProtocolJsonCodec()
        val invalidJson = JSONObject(
            mapOf(
                "trigger" to "on_schedule",
                "pluginId" to "com.astrbot.demo",
                "pluginVersion" to "1.0.0",
                "sessionRef" to JSONObject(
                    mapOf(
                        "platformId" to "qq",
                        "messageType" to "group",
                    ),
                ),
            ),
        )

        try {
            jsonCodec.javaClass
                .getDeclaredMethod("decodeExecutionContext", JSONObject::class.java)
                .invoke(jsonCodec, invalidJson)
        } catch (error: ReflectiveOperationException) {
            val causeMessage = error.cause?.message.orEmpty()
            assertTrue(
                "Decode error should identify the missing protocol field, actual message: $causeMessage",
                causeMessage.contains("sessionRef.originSessionId"),
            )
            return
        }

        throw AssertionError("Expected malformed protocol payload to fail decoding")
    }

    private fun assertHasFieldNames(className: String, expectedFieldNames: List<String>) {
        val clazz = loadClass(className)
        val actualFieldNames = clazz.declaredFields
            .map { it.name }
            .filterNot { it == "Companion" || it.startsWith("$") }
        assertEquals(expectedFieldNames, actualFieldNames)
    }

    private fun assertNoComposeTypes(className: String) {
        val clazz = loadClass(className)
        val composeFieldTypes = clazz.declaredFields
            .mapNotNull { field -> field.type.name.takeIf { it.startsWith("androidx.compose.") } }
        assertTrue("Expected $className to stay host-rendered and Compose-free", composeFieldTypes.isEmpty())
        assertFalse("Expected $className to avoid Compose package leaks", clazz.name.startsWith("androidx.compose."))
    }

    private fun loadProtocolJsonCodec(): Any {
        val clazz = loadClass("com.astrbot.android.model.plugin.PluginExecutionProtocolJson")
        return clazz.getDeclaredField("INSTANCE").get(null)
            ?: throw AssertionError("PluginExecutionProtocolJson object instance must be available")
    }

    private fun loadClass(name: String): Class<*> {
        return try {
            Class.forName(name)
        } catch (error: ClassNotFoundException) {
            throw AssertionError("Expected protocol class $name to exist for Task5 Phase2 Task1", error)
        }
    }
}

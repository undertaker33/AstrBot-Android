package com.astrbot.android.model.plugin

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginStaticConfigSchemaTest {
    @Test
    fun static_config_schema_round_trip_preserves_core_desktop_compatible_fields() {
        val jsonCodec = loadStaticConfigJsonCodec()
        val schemaJson = JSONObject(
            mapOf(
                "token" to JSONObject(
                    mapOf(
                        "type" to "string",
                        "description" to "Bot token",
                        "hint" to "Required for provider auth.",
                        "obvious_hint" to true,
                        "default" to "sk-demo",
                        "section" to "credentials",
                    ),
                ),
                "prompt" to JSONObject(
                    mapOf(
                        "type" to "text",
                        "description" to "System prompt",
                        "default" to "You are AstrBot.",
                    ),
                ),
                "max_tokens" to JSONObject(
                    mapOf(
                        "type" to "int",
                        "description" to "Maximum number of generated tokens.",
                        "default" to 8192,
                    ),
                ),
                "temperature" to JSONObject(
                    mapOf(
                        "type" to "float",
                        "description" to "Sampling temperature.",
                        "default" to 0.7,
                    ),
                ),
                "enabled" to JSONObject(
                    mapOf(
                        "type" to "bool",
                        "description" to "Whether the plugin is enabled.",
                        "default" to true,
                        "invisible" to true,
                    ),
                ),
                "provider" to JSONObject(
                    mapOf(
                        "type" to "string",
                        "description" to "Provider id",
                        "_special" to "select_provider",
                        "options" to JSONArray()
                            .put("openai")
                            .put(JSONObject(mapOf("value" to "gemini", "label" to "Gemini"))),
                    ),
                ),
            ),
        )

        val decoded = jsonCodec.javaClass
            .getDeclaredMethod("decodeSchema", JSONObject::class.java)
            .invoke(jsonCodec, schemaJson)
        val encoded = jsonCodec.javaClass
            .getDeclaredMethod("encodeSchema", loadClass("com.astrbot.android.model.plugin.PluginStaticConfigSchema"))
            .invoke(jsonCodec, decoded) as JSONObject

        assertEquals("string", encoded.getJSONObject("token").getString("type"))
        assertEquals("Bot token", encoded.getJSONObject("token").getString("description"))
        assertEquals(true, encoded.getJSONObject("token").getBoolean("obvious_hint"))
        assertEquals("sk-demo", encoded.getJSONObject("token").getString("default"))
        assertEquals("credentials", encoded.getJSONObject("token").getString("section"))

        assertEquals("text", encoded.getJSONObject("prompt").getString("type"))
        assertEquals("You are AstrBot.", encoded.getJSONObject("prompt").getString("default"))

        assertEquals(8192, encoded.getJSONObject("max_tokens").getInt("default"))
        assertEquals(0.7, encoded.getJSONObject("temperature").getDouble("default"), 0.0001)
        assertEquals(true, encoded.getJSONObject("enabled").getBoolean("default"))
        assertEquals(true, encoded.getJSONObject("enabled").getBoolean("invisible"))
        assertEquals("select_provider", encoded.getJSONObject("provider").getString("_special"))
        assertEquals("openai", encoded.getJSONObject("provider").getJSONArray("options").getString(0))
        assertEquals(
            "Gemini",
            encoded.getJSONObject("provider").getJSONArray("options").getJSONObject(1).getString("label"),
        )
    }

    @Test
    fun static_config_schema_decode_rejects_unsupported_field_type_with_precise_path() {
        val jsonCodec = loadStaticConfigJsonCodec()
        val schemaJson = JSONObject(
            mapOf(
                "sub_config" to JSONObject(
                    mapOf(
                        "type" to "object",
                        "description" to "Nested config",
                    ),
                ),
            ),
        )

        try {
            jsonCodec.javaClass
                .getDeclaredMethod("decodeSchema", JSONObject::class.java)
                .invoke(jsonCodec, schemaJson)
        } catch (error: ReflectiveOperationException) {
            val causeMessage = error.cause?.message.orEmpty()
            assertTrue(
                "Decode error should identify unsupported field type path, actual message: $causeMessage",
                causeMessage.contains("schema.sub_config.type"),
            )
            return
        }

        throw AssertionError("Expected unsupported field type to fail decoding")
    }

    @Test
    fun static_config_schema_decode_rejects_non_whitelisted_special_type_with_precise_path() {
        val jsonCodec = loadStaticConfigJsonCodec()
        val schemaJson = JSONObject(
            mapOf(
                "kb" to JSONObject(
                    mapOf(
                        "type" to "string",
                        "description" to "Knowledge base",
                        "_special" to "select_knowledgebase",
                    ),
                ),
            ),
        )

        try {
            jsonCodec.javaClass
                .getDeclaredMethod("decodeSchema", JSONObject::class.java)
                .invoke(jsonCodec, schemaJson)
        } catch (error: ReflectiveOperationException) {
            val causeMessage = error.cause?.message.orEmpty()
            assertTrue(
                "Decode error should identify unsupported special type path, actual message: $causeMessage",
                causeMessage.contains("schema.kb._special"),
            )
            return
        }

        throw AssertionError("Expected unsupported special type to fail decoding")
    }

    private fun loadStaticConfigJsonCodec(): Any {
        val clazz = loadClass("com.astrbot.android.model.plugin.PluginStaticConfigJson")
        return clazz.getDeclaredField("INSTANCE").get(null)
            ?: throw AssertionError("PluginStaticConfigJson object instance must be available")
    }

    private fun loadClass(name: String): Class<*> {
        return try {
            Class.forName(name)
        } catch (error: ClassNotFoundException) {
            throw AssertionError("Expected static config class $name to exist for Task5 Phase4 Task1", error)
        }
    }
}

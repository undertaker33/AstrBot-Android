package com.astrbot.android.model.plugin

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginConfigStorageBoundaryTest {

    @Test
    fun `static schema storage boundary exposes core keys and defaults`() {
        val schema = PluginStaticConfigSchema(
            fields = listOf(
                PluginStaticConfigField(
                    fieldKey = "token",
                    fieldType = PluginStaticConfigFieldType.StringField,
                    defaultValue = PluginStaticConfigValue.StringValue("sk-demo"),
                ),
                PluginStaticConfigField(
                    fieldKey = "temperature",
                    fieldType = PluginStaticConfigFieldType.FloatField,
                    defaultValue = PluginStaticConfigValue.FloatValue(0.7),
                ),
            ),
        )

        val boundary = schema.toStorageBoundary(extensionFieldKeys = setOf("session_mode"))

        assertEquals(setOf("token", "temperature"), boundary.coreFieldKeys)
        assertEquals(setOf("session_mode"), boundary.extensionFieldKeys)
        assertEquals(
            mapOf(
                "token" to PluginStaticConfigValue.StringValue("sk-demo"),
                "temperature" to PluginStaticConfigValue.FloatValue(0.7),
            ),
            boundary.coreDefaults,
        )
    }

    @Test
    fun `storage boundary rejects extension keys that collide with core schema`() {
        val schema = PluginStaticConfigSchema(
            fields = listOf(
                PluginStaticConfigField(
                    fieldKey = "token",
                    fieldType = PluginStaticConfigFieldType.StringField,
                ),
            ),
        )

        val error = runCatching {
            schema.toStorageBoundary(extensionFieldKeys = setOf("token"))
        }.exceptionOrNull()

        check(error is IllegalArgumentException)
        assertEquals("Extension config keys overlap core schema keys: token", error.message)
    }

    @Test
    fun `storage boundary rejects snapshots that write undeclared keys`() {
        val schema = PluginStaticConfigSchema(
            fields = listOf(
                PluginStaticConfigField(
                    fieldKey = "token",
                    fieldType = PluginStaticConfigFieldType.StringField,
                ),
            ),
        )
        val boundary = schema.toStorageBoundary(extensionFieldKeys = setOf("session_mode"))

        val error = runCatching {
            boundary.createSnapshot(
                coreValues = mapOf(
                    "token" to PluginStaticConfigValue.StringValue("sk-live"),
                    "unknown_core" to PluginStaticConfigValue.StringValue("x"),
                ),
                extensionValues = mapOf(
                    "session_mode" to PluginStaticConfigValue.StringValue("threaded"),
                ),
            )
        }.exceptionOrNull()

        check(error is IllegalArgumentException)
        assertEquals("Core config contains undeclared keys: unknown_core", error.message)
    }
}

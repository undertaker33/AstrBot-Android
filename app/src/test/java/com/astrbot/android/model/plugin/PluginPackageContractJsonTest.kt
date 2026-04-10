package com.astrbot.android.model.plugin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginPackageContractJsonTest {
    @Test
    fun decode_parses_valid_v2_android_plugin_json() {
        val contract = PluginPackageContractJson.decode(
            JSONObject(
                """
                {
                  "protocolVersion": 2,
                  "runtime": {
                    "kind": "js_quickjs",
                    "bootstrap": "runtime/index.js",
                    "apiVersion": 1
                  },
                  "config": {
                    "staticSchema": "schemas/static-config.schema.json",
                    "settingsSchema": "schemas/settings-ui.schema.json"
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals(2, contract.protocolVersion)
        assertEquals("js_quickjs", contract.runtime.kind)
        assertEquals("runtime/index.js", contract.runtime.bootstrap)
        assertEquals(1, contract.runtime.apiVersion)
        assertEquals("schemas/static-config.schema.json", contract.config.staticSchema)
        assertEquals("schemas/settings-ui.schema.json", contract.config.settingsSchema)
    }

    @Test
    fun decode_normalizes_config_schema_paths_to_package_relative_paths() {
        val contract = PluginPackageContractJson.decode(
            JSONObject(
                """
                {
                  "protocolVersion": 2,
                  "runtime": {
                    "kind": "js_quickjs",
                    "bootstrap": "runtime/index.js",
                    "apiVersion": 1
                  },
                  "config": {
                    "staticSchema": "  schemas//static-config.schema.json  ",
                    "settingsSchema": "schemas//settings-ui.schema.json"
                  }
                }
                """.trimIndent(),
            ),
        )

        assertEquals("schemas/static-config.schema.json", contract.config.staticSchema)
        assertEquals("schemas/settings-ui.schema.json", contract.config.settingsSchema)
    }

    @Test
    fun decode_rejects_non_v2_protocol_version() {
        val failure = runCatching {
            PluginPackageContractJson.decode(
                JSONObject(
                    """
                    {
                      "protocolVersion": 1,
                      "runtime": {
                        "kind": "js_quickjs",
                        "bootstrap": "runtime/index.js",
                        "apiVersion": 1
                      }
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("protocolVersion") == true)
    }

    @Test
    fun decode_rejects_runtime_kind_outside_js_quickjs() {
        val failure = runCatching {
            PluginPackageContractJson.decode(
                JSONObject(
                    """
                    {
                      "protocolVersion": 2,
                      "runtime": {
                        "kind": "js_v8",
                        "bootstrap": "runtime/index.js",
                        "apiVersion": 1
                      }
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("runtime.kind") == true)
    }

    @Test
    fun decode_rejects_bootstrap_outside_runtime_directory() {
        val failure = runCatching {
            PluginPackageContractJson.decode(
                JSONObject(
                    """
                    {
                      "protocolVersion": 2,
                      "runtime": {
                        "kind": "js_quickjs",
                        "bootstrap": "../index.js",
                        "apiVersion": 1
                      }
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("bootstrap") == true)
    }

    @Test
    fun decode_rejects_config_when_present_but_not_object() {
        val failure = runCatching {
            PluginPackageContractJson.decode(
                JSONObject(
                    """
                    {
                      "protocolVersion": 2,
                      "runtime": {
                        "kind": "js_quickjs",
                        "bootstrap": "runtime/index.js",
                        "apiVersion": 1
                      },
                      "config": "invalid"
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("config must be an object") == true)
    }

    @Test
    fun decode_rejects_config_static_schema_when_present_but_not_string() {
        val failure = runCatching {
            PluginPackageContractJson.decode(
                JSONObject(
                    """
                    {
                      "protocolVersion": 2,
                      "runtime": {
                        "kind": "js_quickjs",
                        "bootstrap": "runtime/index.js",
                        "apiVersion": 1
                      },
                      "config": {
                        "staticSchema": 123
                      }
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("config.staticSchema must be a string") == true)
    }

    @Test
    fun decode_rejects_config_static_schema_with_directory_traversal() {
        val failure = runCatching {
            PluginPackageContractJson.decode(
                JSONObject(
                    """
                    {
                      "protocolVersion": 2,
                      "runtime": {
                        "kind": "js_quickjs",
                        "bootstrap": "runtime/index.js",
                        "apiVersion": 1
                      },
                      "config": {
                        "staticSchema": "../schemas/static-config.schema.json"
                      }
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("config.staticSchema") == true)
    }

    @Test
    fun decode_rejects_config_settings_schema_when_present_but_not_string() {
        val failure = runCatching {
            PluginPackageContractJson.decode(
                JSONObject(
                    """
                    {
                      "protocolVersion": 2,
                      "runtime": {
                        "kind": "js_quickjs",
                        "bootstrap": "runtime/index.js",
                        "apiVersion": 1
                      },
                      "config": {
                        "settingsSchema": false
                      }
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("config.settingsSchema must be a string") == true)
    }

    @Test
    fun decode_rejects_config_settings_schema_with_absolute_path() {
        val failure = runCatching {
            PluginPackageContractJson.decode(
                JSONObject(
                    """
                    {
                      "protocolVersion": 2,
                      "runtime": {
                        "kind": "js_quickjs",
                        "bootstrap": "runtime/index.js",
                        "apiVersion": 1
                      },
                      "config": {
                        "settingsSchema": "/schemas/settings-ui.schema.json"
                      }
                    }
                    """.trimIndent(),
                ),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message?.contains("config.settingsSchema") == true)
    }
}

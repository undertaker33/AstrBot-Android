package com.astrbot.android.model.plugin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalPluginExecutionContractJsonTest {

    @Test
    fun decode_contract_accepts_js_quickjs_runtime_kind() {
        val contract = ExternalPluginExecutionContractJson.decodeContract(
            JSONObject(
                """
                {
                  "contractVersion": 1,
                  "enabled": true,
                  "entryPoint": {
                    "runtimeKind": "js_quickjs",
                    "path": "runtime/index.js",
                    "entrySymbol": "handleEvent"
                  },
                  "supportedTriggers": ["on_command", "before_send_message"]
                }
                """.trimIndent(),
            ),
        )

        assertEquals(ExternalPluginRuntimeKind.JsQuickJs, contract.entryPoint.runtimeKind)
        assertEquals("runtime/index.js", contract.entryPoint.path)
        assertEquals("handleEvent", contract.entryPoint.entrySymbol)
        assertEquals(
            setOf(
                PluginTriggerSource.OnCommand,
                PluginTriggerSource.BeforeSendMessage,
            ),
            contract.supportedTriggers,
        )
    }

    @Test
    fun decode_contract_rejects_unknown_runtime_kind() {
        val error = try {
            ExternalPluginExecutionContractJson.decodeContract(
                JSONObject(
                    """
                    {
                      "contractVersion": 1,
                      "entryPoint": {
                        "runtimeKind": "totally_unknown",
                        "path": "runtime/index.js",
                        "entrySymbol": "handleEvent"
                      }
                    }
                    """.trimIndent(),
                ),
            )
            throw AssertionError("Expected unsupported runtime kind to fail")
        } catch (expected: IllegalArgumentException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("runtimeKind"))
    }

    @Test
    fun decode_contract_rejects_legacy_python_runtime_kind() {
        val error = try {
            ExternalPluginExecutionContractJson.decodeContract(
                JSONObject(
                    """
                    {
                      "contractVersion": 1,
                      "entryPoint": {
                        "runtimeKind": "python_main",
                        "path": "runtime/entry.py",
                        "entrySymbol": "handle_event"
                      }
                    }
                    """.trimIndent(),
                ),
            )
            throw AssertionError("Expected legacy python runtime kind to fail")
        } catch (expected: IllegalArgumentException) {
            expected
        }

        assertTrue(error.message.orEmpty().contains("runtimeKind"))
    }
}

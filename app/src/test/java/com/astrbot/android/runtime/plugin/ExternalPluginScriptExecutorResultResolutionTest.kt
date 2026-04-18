package com.astrbot.android.feature.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalPluginScriptExecutorResultResolutionTest {

    @Test
    fun prefers_serialized_result_from_global_property_when_module_evaluation_value_is_not_json() {
        val resolved = resolveQuickJsSerializedResult(
            evaluationResult = "[object Object]",
            globalSerializedResult = """{"resultType":"text","text":"ok"}""",
        )

        assertEquals("""{"resultType":"text","text":"ok"}""", resolved)
    }

    @Test
    fun falls_back_to_module_evaluation_value_when_global_property_is_blank() {
        val resolved = resolveQuickJsSerializedResult(
            evaluationResult = """{"resultType":"text","text":"ok"}""",
            globalSerializedResult = "",
        )

        assertEquals("""{"resultType":"text","text":"ok"}""", resolved)
    }
}

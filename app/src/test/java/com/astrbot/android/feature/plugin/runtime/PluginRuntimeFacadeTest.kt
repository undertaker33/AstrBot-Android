package com.astrbot.android.feature.plugin.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginRuntimeFacadeTest {

    @Test
    fun facade_does_not_expose_legacy_v1_boundary_or_default_adapter_field() {
        val facadeClass = PluginRuntimeFacade::class.java

        assertFalse(
            facadeClass.declaredMethods.any { method ->
                method.name == "legacyV1Boundary"
            },
        )
        assertTrue(
            facadeClass.declaredFields.none { field ->
                field.type.name.endsWith("PluginV1LegacyAdapter")
            },
        )
    }

    @Test
    fun facade_primary_constructor_only_requires_dispatcher() {
        val constructor = PluginRuntimeFacade::class.java.declaredConstructors.single()

        assertEquals(1, constructor.parameterCount)
        assertEquals(PluginRuntimeDispatcher::class.java, constructor.parameterTypes.single())
    }
}

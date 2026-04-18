package com.astrbot.android.core.di

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class InitializationCoordinatorTest {

    private fun stubInitializer(
        key: String,
        dependencies: Set<String> = emptySet(),
    ) = object : AppInitializer {
        override val key: String = key
        override val dependencies: Set<String> = dependencies
        override fun initialize(context: android.content.Context) {}
    }

    @Test
    fun sorts_independent_initializers_deterministically() {
        val coordinator = InitializationCoordinator(
            listOf(
                stubInitializer("c"),
                stubInitializer("a"),
                stubInitializer("b"),
            ),
        )
        val sorted = coordinator.sortedInitializers().map { it.key }
        assertEquals(listOf("a", "b", "c"), sorted)
    }

    @Test
    fun sorts_dependencies_before_dependents() {
        val coordinator = InitializationCoordinator(
            listOf(
                stubInitializer("bot", setOf("config", "persona")),
                stubInitializer("persona"),
                stubInitializer("config"),
                stubInitializer("provider"),
            ),
        )
        val sorted = coordinator.sortedInitializers().map { it.key }
        val botIndex = sorted.indexOf("bot")
        val configIndex = sorted.indexOf("config")
        val personaIndex = sorted.indexOf("persona")
        assertTrue("config must come before bot", configIndex < botIndex)
        assertTrue("persona must come before bot", personaIndex < botIndex)
    }

    @Test
    fun fails_on_duplicate_keys() {
        val coordinator = InitializationCoordinator(
            listOf(
                stubInitializer("alpha"),
                stubInitializer("alpha"),
            ),
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            coordinator.sortedInitializers()
        }
        assertTrue(error.message!!.contains("Duplicate"))
        assertTrue(error.message!!.contains("alpha"))
    }

    @Test
    fun fails_on_unknown_dependency() {
        val coordinator = InitializationCoordinator(
            listOf(
                stubInitializer("a", setOf("nonexistent")),
            ),
        )
        val error = assertThrows(IllegalArgumentException::class.java) {
            coordinator.sortedInitializers()
        }
        assertTrue(error.message!!.contains("unknown"))
        assertTrue(error.message!!.contains("nonexistent"))
    }

    @Test
    fun fails_on_cycle() {
        val coordinator = InitializationCoordinator(
            listOf(
                stubInitializer("a", setOf("b")),
                stubInitializer("b", setOf("a")),
            ),
        )
        val error = assertThrows(IllegalStateException::class.java) {
            coordinator.sortedInitializers()
        }
        assertTrue(error.message!!.contains("cycle"))
    }

    @Test
    fun wraps_initializer_failure_exposes_failing_key() {
        // InitializationException wraps the cause and exposes the key.
        // Full initializeAll coverage requires an Android Context (Robolectric);
        // this test verifies the exception structure directly.
        val cause = RuntimeException("boom")
        val exception = InitializationException("bad", cause)
        assertEquals("bad", exception.initializerKey)
        assertEquals("boom", exception.cause!!.message)
        assertTrue(exception.message!!.contains("bad"))
    }

    @Test
    fun preserves_input_order_for_independent_initializers_after_key_sort() {
        val coordinator = InitializationCoordinator(
            listOf(
                stubInitializer("z"),
                stubInitializer("m"),
                stubInitializer("a"),
            ),
        )
        val sorted = coordinator.sortedInitializers().map { it.key }
        assertEquals(listOf("a", "m", "z"), sorted)
    }

    @Test
    fun complex_dependency_chain() {
        val coordinator = InitializationCoordinator(
            listOf(
                stubInitializer("d", setOf("c")),
                stubInitializer("c", setOf("b")),
                stubInitializer("b", setOf("a")),
                stubInitializer("a"),
            ),
        )
        val sorted = coordinator.sortedInitializers().map { it.key }
        assertEquals(listOf("a", "b", "c", "d"), sorted)
    }
}

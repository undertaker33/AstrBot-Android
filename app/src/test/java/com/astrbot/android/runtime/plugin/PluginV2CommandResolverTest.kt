package com.astrbot.android.runtime.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginV2CommandResolverTest {
    @Test
    fun resolve_prefers_longest_leaf_match_and_preserves_alias_matches() {
        val resolver = commandResolver(
            rawRegistries = listOf(
                rawRegistryWithCommandHandlers(
                    pluginId = "com.example.v2.alpha",
                    commandHandlers = listOf(
                        commandHandler(
                            registrationKey = "group.short",
                            command = "plugin",
                            groupPath = listOf("astrbot"),
                        ),
                        commandHandler(
                            registrationKey = "group.long",
                            command = "list",
                            aliases = listOf("astrbot plugin ls"),
                            groupPath = listOf("astrbot", "plugin"),
                        ),
                    ),
                ),
            ),
        )

        val canonicalMatch = resolver.resolve("astrbot plugin list all")!!
        assertEquals(listOf("astrbot", "plugin", "list"), canonicalMatch.commandPath)
        assertEquals("astrbot plugin list", canonicalMatch.matchedAlias)
        assertEquals("all", canonicalMatch.remainingText)
        assertEquals(listOf("astrbot", "plugin", "list"), canonicalMatch.bucket.commandPath)
        assertEquals(1, canonicalMatch.bucket.handlers.size)

        val aliasMatch = resolver.resolve("astrbot plugin ls all")!!
        assertEquals(listOf("astrbot", "plugin", "list"), aliasMatch.commandPath)
        assertEquals("astrbot plugin ls", aliasMatch.matchedAlias)
        assertEquals("all", aliasMatch.remainingText)
        assertEquals(listOf("astrbot", "plugin", "list"), aliasMatch.bucket.commandPath)
        assertEquals(listOf(listOf("astrbot", "plugin", "ls")), aliasMatch.bucket.aliasPaths)
    }

    @Test
    fun resolve_returns_null_when_input_only_matches_group_prefix() {
        val resolver = commandResolver(
            rawRegistries = listOf(
                rawRegistryWithCommandHandlers(
                    pluginId = "com.example.v2.alpha",
                    commandHandlers = listOf(
                        commandHandler(
                            registrationKey = "group.long",
                            command = "list",
                            groupPath = listOf("astrbot", "plugin"),
                        ),
                    ),
                ),
            ),
        )

        assertNull(resolver.resolve("astrbot plugin anything"))
    }

    @Test
    fun resolve_merges_shared_canonical_paths_from_multiple_plugins_into_one_bucket() {
        val resolver = commandResolver(
            rawRegistries = listOf(
                rawRegistryWithCommandHandlers(
                    pluginId = "com.example.v2.alpha",
                    commandHandlers = listOf(
                        commandHandler(
                            registrationKey = "shared.a",
                            command = "list",
                            groupPath = listOf("astrbot", "plugin"),
                        ),
                    ),
                ),
                rawRegistryWithCommandHandlers(
                    pluginId = "com.example.v2.beta",
                    commandHandlers = listOf(
                        commandHandler(
                            registrationKey = "shared.b",
                            command = "list",
                            groupPath = listOf("astrbot", "plugin"),
                        ),
                    ),
                ),
            ),
        )

        val result = resolver.resolve("astrbot plugin list")!!

        assertEquals(listOf("astrbot", "plugin", "list"), result.commandPath)
        assertEquals(2, result.bucket.handlers.size)
        assertTrue(result.bucket.handlers.map { it.pluginId }.containsAll(listOf("com.example.v2.alpha", "com.example.v2.beta")))
    }

    private fun commandResolver(
        rawRegistries: List<PluginV2RawRegistry>,
    ): PluginV2CommandResolver {
        val compiler = PluginV2RegistryCompiler()
        val compiledRegistries = rawRegistries.map { rawRegistry ->
            val compiled = compiler.compile(rawRegistry).compiledRegistry
                ?: error("Expected command registry compilation to succeed.")
            compiled.handlerRegistry
        }

        return PluginV2CommandResolver(compiledRegistries)
    }

    private fun rawRegistryWithCommandHandlers(
        pluginId: String,
        commandHandlers: List<CommandHandlerRegistrationInput> = emptyList(),
    ): PluginV2RawRegistry {
        val session = PluginV2RuntimeSession(
            installRecord = samplePluginV2InstallRecord(pluginId = pluginId),
            sessionInstanceId = "session-$pluginId",
        ).also { runtimeSession ->
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.Loading)
            runtimeSession.transitionTo(PluginV2RuntimeSessionState.BootstrapRunning)
        }
        val rawRegistry = PluginV2RawRegistry(session.pluginId)

        commandHandlers.forEach { descriptor ->
            rawRegistry.appendCommandHandler(
                callbackToken = session.allocateCallbackToken(PluginV2CallbackHandle {}),
                descriptor = descriptor,
            )
        }

        return rawRegistry
    }

    private fun commandHandler(
        registrationKey: String?,
        command: String,
        aliases: List<String> = emptyList(),
        groupPath: List<String> = emptyList(),
    ): CommandHandlerRegistrationInput {
        return CommandHandlerRegistrationInput(
            base = BaseHandlerRegistrationInput(
                registrationKey = registrationKey,
            ),
            command = command,
            aliases = aliases,
            groupPath = groupPath,
            handler = PluginV2CallbackHandle {},
        )
    }
}

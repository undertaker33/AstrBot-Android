package com.astrbot.android.architecture

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStartupChainContractTest {

    private val mainRoot: Path = listOf(
        Path.of("src/main/java/com/astrbot/android"),
        Path.of("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun startup_chain_files_exist() {
        val required = listOf(
            "di/startup/AppStartupChain.kt",
            "di/startup/AppStartupRunner.kt",
            "di/startup/BootstrapPrerequisitesStartupChain.kt",
            "di/startup/RepositoryInitializationStartupChain.kt",
            "di/startup/ReferenceGuardStartupChain.kt",
            "di/startup/PluginRuntimeObservationStartupChain.kt",
            "di/startup/RuntimeLaunchStartupChain.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing startup chain files: $missing", missing.isEmpty())
    }

    @Test
    fun app_startup_runner_calls_chains_in_fixed_order() {
        val source = startupSource("AppStartupRunner.kt")
        val runBody = functionBody(source, "run")
        val orderedCalls = listOf(
            "bootstrapPrerequisitesStartupChain.run()",
            "repositoryInitializationStartupChain.run()",
            "referenceGuardStartupChain.run()",
            "pluginRuntimeObservationStartupChain.run()",
            "runtimeLaunchStartupChain.run()",
        )

        val missing = orderedCalls.filterNot(runBody::contains)
        assertTrue("AppStartupRunner.run must call every startup chain explicitly: $missing", missing.isEmpty())

        val indexes = orderedCalls.map(runBody::indexOf)
        assertTrue(
            "AppStartupRunner.run must keep the documented startup order: $orderedCalls",
            indexes.zipWithNext().all { (left, right) -> left >= 0 && left < right },
        )
    }

    @Test
    fun startup_runner_must_not_use_unordered_multibinding_discovery() {
        val runnerSource = startupSource("AppStartupRunner.kt")
        val hiltSources = listOf(
            "di/hilt/AppDispatchersModule.kt",
            "di/hilt/DatabaseModule.kt",
            "di/hilt/DefaultChatViewModelRuntimeBindings.kt",
            "di/hilt/PluginHostCapabilityModule.kt",
            "di/hilt/PluginProvisioningModule.kt",
            "di/hilt/PluginRuntimeModule.kt",
            "di/hilt/RepositoryPortModule.kt",
            "di/hilt/RuntimeServicesModule.kt",
            "di/hilt/ViewModelDependencyModule.kt",
        ).mapNotNull { relativePath ->
            mainRoot.resolve(relativePath)
                .takeIf { it.exists() }
                ?.readText()
        }
        val forbiddenTokens = listOf(
            "Set<AppStartupChain>",
            "Set<@JvmSuppressWildcards AppStartupChain>",
            "@IntoSet",
            "@ElementsIntoSet",
        )
        val violations = forbiddenTokens.filter { token ->
            runnerSource.contains(token) || hiltSources.any { it.contains(token) }
        }

        assertTrue(
            "Startup order must stay explicit and must not switch to unordered multibinding: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun repository_initialization_chain_must_not_retain_legacy_initializer_mainline() {
        val source = startupSource("RepositoryInitializationStartupChain.kt")
        val runBody = functionBody(source, "run")
        val forbiddenTokens = listOf(
            "InitializationCoordinator(",
            "ConfigRepositoryInitializer()",
            "BotRepositoryInitializer()",
            "PersonaRepositoryInitializer()",
            ".initialize(application)",
            "providerRepositoryWarmup.warmUp()",
        )
        val violations = forbiddenTokens.filter(runBody::contains)

        assertTrue(
            "RepositoryInitializationStartupChain.run must not retain legacy repository initialization residue: $violations",
            violations.isEmpty(),
        )
    }

    @Test
    fun database_module_must_not_use_legacy_static_database_get() {
        val file = mainRoot.resolve("di/hilt/DatabaseModule.kt")
        assertTrue("DatabaseModule.kt must exist under di/hilt", file.exists())
        val source = file.readText()

        assertTrue(
            "DatabaseModule must not provide the Room database via AstrBotDatabase.get(...)",
            !source.contains("AstrBotDatabase.get("),
        )
    }

    private fun startupSource(fileName: String): String {
        val file = mainRoot.resolve("di/startup/$fileName")
        assertTrue("$fileName must exist under di/startup", file.exists())
        return file.readText()
    }

    private fun functionBody(source: String, functionName: String): String {
        val signatureIndex = source.indexOf("fun $functionName(")
        require(signatureIndex >= 0) { "Missing function: $functionName" }
        val bodyStart = source.indexOf('{', signatureIndex)
        require(bodyStart >= 0) { "Missing body for function: $functionName" }
        val bodyEnd = matchingBraceIndex(source, bodyStart)
        return source.substring(bodyStart + 1, bodyEnd)
    }

    private fun matchingBraceIndex(source: String, openIndex: Int): Int {
        var depth = 0
        for (index in openIndex until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        error("No matching closing brace found")
    }
}

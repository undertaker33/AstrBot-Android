package com.astrbot.android.architecture

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildBaselineContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val wrapperProperties: Path =
        projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties")
    private val rootBuildFile: Path = projectRoot.resolve("build.gradle.kts")
    private val appBuildFile: Path = projectRoot.resolve("app/build.gradle.kts")

    @Test
    fun gradle_wrapper_must_use_stable_8_13_distribution() {
        assertTrue(
            "Gradle wrapper properties must exist: $wrapperProperties",
            wrapperProperties.exists(),
        )

        val distributionUrl = wrapperProperties.readLines(UTF_8)
            .firstOrNull { line -> line.startsWith("distributionUrl=") }
            .orEmpty()

        assertTrue(
            "Gradle wrapper must use stable Gradle 8.13 bin distribution. Found: $distributionUrl",
            distributionUrl.contains("gradle-8.13-bin.zip"),
        )

        val forbiddenQualifiers = listOf("milestone", "rc", "alpha", "beta")
        val matchedQualifier = forbiddenQualifiers.firstOrNull { qualifier ->
            distributionUrl.contains(qualifier, ignoreCase = true)
        }
        assertTrue(
            "Gradle wrapper must not use milestone/rc/alpha/beta distributions. Found: $distributionUrl",
            matchedQualifier == null,
        )
    }

    @Test
    fun gradle_wrapper_distribution_url_validation_must_remain_enabled() {
        val text = wrapperProperties.readText(UTF_8)

        assertTrue(
            "Gradle wrapper must keep validateDistributionUrl=true",
            text.contains("validateDistributionUrl=true"),
        )
        assertTrue(
            "Gradle wrapper networkTimeout must allow stable distribution downloads.",
            Regex("""networkTimeout\s*=\s*(?:[6-9]\d{4}|[1-9]\d{5,})""").containsMatchIn(text),
        )
    }

    @Test
    fun android_gradle_plugin_must_not_be_upgraded_in_this_round() {
        val text = rootBuildFile.readText(UTF_8)

        assertTrue(
            "AGP must stay at 8.13.2 in this build-chain stabilization round.",
            text.contains("""id("com.android.application") version "8.13.2" apply false"""),
        )

        assertFalse(
            "Do not upgrade AGP to 9.x in this round; split that into a dedicated compatibility task.",
            Regex("""id\("com\.android\.application"\)\s+version\s+"9\.""").containsMatchIn(text),
        )
    }

    @Test
    fun app_compile_and_target_sdk_must_be_36() {
        val text = appBuildFile.readText(UTF_8)

        assertTrue(
            "compileSdk must be 36 once SDK 36 baseline is accepted.",
            Regex("""compileSdk\s*=\s*36""").containsMatchIn(text),
        )
        assertTrue(
            "targetSdk must be 36 once SDK 36 baseline is accepted.",
            Regex("""targetSdk\s*=\s*36""").containsMatchIn(text),
        )
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/build.gradle.kts").exists() -> cwd
            cwd.parent?.resolve("app/build.gradle.kts")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}

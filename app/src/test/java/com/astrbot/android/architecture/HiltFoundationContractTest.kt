package com.astrbot.android.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import org.junit.Assert.assertTrue
import org.junit.Test

class HiltFoundationContractTest {

    private val projectRoot: Path = detectProjectRoot()
    private val mainRoot: Path = listOf(
        projectRoot.resolve("src/main/java/com/astrbot/android"),
        projectRoot.resolve("app/src/main/java/com/astrbot/android"),
    ).first { it.exists() }

    @Test
    fun root_build_must_declare_hilt_plugin() {
        val buildFile = projectRoot.resolve("build.gradle.kts")
        assertTrue("Root build.gradle.kts must exist", buildFile.exists())
        val text = buildFile.readText()
        assertTrue(
            "Root build.gradle.kts must declare com.google.dagger.hilt.android with apply false",
            text.contains("""id("com.google.dagger.hilt.android")""") &&
                text.contains("apply false"),
        )
    }

    @Test
    fun app_build_must_apply_hilt_plugin_and_dependencies() {
        val buildFile = projectRoot.resolve("app/build.gradle.kts")
        assertTrue("app/build.gradle.kts must exist", buildFile.exists())
        val text = buildFile.readText()
        assertTrue(
            "app/build.gradle.kts must apply the Hilt plugin",
            text.contains("""id("com.google.dagger.hilt.android")"""),
        )
        assertTrue(
            "app/build.gradle.kts must include hilt-android runtime",
            text.contains("com.google.dagger:hilt-android"),
        )
        assertTrue(
            "app/build.gradle.kts must include hilt-compiler on the KSP path",
            text.contains("""ksp("com.google.dagger:hilt-compiler"""),
        )
        assertTrue(
            "app/build.gradle.kts must include AndroidX Hilt WorkManager integration",
            text.contains("androidx.hilt:hilt-work") &&
                text.contains("androidx.hilt:hilt-compiler"),
        )
    }

    @Test
    fun application_must_be_hilt_android_app() {
        val file = mainRoot.resolve("AstrBotApplication.kt")
        assertTrue("AstrBotApplication.kt must exist", file.exists())
        val text = file.readText()
        assertTrue(
            "AstrBotApplication must be annotated with @HiltAndroidApp in phase 4",
            text.contains("@HiltAndroidApp"),
        )
    }

    @Test
    fun di_hilt_foundation_files_must_exist() {
        val required = listOf(
            "di/hilt/AppDispatchersModule.kt",
            "di/hilt/RuntimeServicesModule.kt",
            "di/hilt/RepositoryPortModule.kt",
            "di/hilt/ViewModelDependencyModule.kt",
        )
        val missing = required.filterNot { mainRoot.resolve(it).exists() }
        assertTrue("Missing Hilt foundation files: $missing", missing.isEmpty())
    }

    @Test
    fun hilt_foundation_must_not_keep_legacy_container_entry_point() {
        val legacyEntryPoint = mainRoot.resolve("di/hilt/LegacyContainerEntryPoint.kt")
        assertTrue(
            "LegacyContainerEntryPoint must be removed after phase 5 Hilt exit",
            !legacyEntryPoint.exists(),
        )
        val viewModelModule = mainRoot.resolve("di/hilt/ViewModelDependencyModule.kt")
        assertTrue("ViewModelDependencyModule.kt must exist", viewModelModule.exists())
        val text = viewModelModule.readText()
        assertTrue(
            "ViewModelDependencyModule must not provide legacy container-only objects",
            !text.contains("ViewModelProvider.Factory") &&
                !text.contains("MainActivityDependencies") &&
                !text.contains("DefaultBridgeViewModelDependencies"),
        )
    }

    @Test
    fun hilt_directory_must_only_contain_kotlin_foundation_files() {
        val hiltRoot = mainRoot.resolve("di/hilt")
        assertTrue("di/hilt must exist", hiltRoot.exists())
        val unexpected = mutableListOf<String>()
        Files.walk(hiltRoot).use { stream ->
            stream
                .filter { it.isRegularFile() }
                .forEach { file ->
                    val relative = hiltRoot.relativize(file).toString()
                    if (!relative.endsWith(".kt")) {
                        unexpected += relative
                    }
                }
        }
        assertTrue("di/hilt should only contain Kotlin source files right now: $unexpected", unexpected.isEmpty())
    }

    private fun detectProjectRoot(): Path {
        val cwd = Path.of("").toAbsolutePath()
        return when {
            cwd.resolve("app/src/main/java/com/astrbot/android").exists() -> cwd
            cwd.resolve("src/main/java/com/astrbot/android").exists() -> cwd.parent
            cwd.parent?.resolve("app/src/main/java/com/astrbot/android")?.exists() == true -> cwd.parent
            else -> error("Unable to resolve project root from $cwd")
        }
    }
}

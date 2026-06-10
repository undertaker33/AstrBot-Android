import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import com.google.devtools.ksp.gradle.KspExtension
import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ELYMBOT_COMPILE_SDK = 36
val ELYMBOT_COMPILE_SDK_MINOR = 1
val ELYMBOT_MIN_SDK = 29

val architectureMainSourceRoots = listOf(
    "app/src/main/java",
    "app-integration/src/main/java",
    "core/backup/src/main/java",
    "core/common/src/main/java",
    "core/db/src/main/java",
    "core/logging/src/main/java",
    "core/network/src/main/java",
    "core/runtime/src/main/java",
    "core/runtime-audio/src/main/java",
    "core/runtime-cache/src/main/java",
    "core/runtime-container/src/main/java",
    "core/runtime-context/src/main/java",
    "core/runtime-llm/src/main/java",
    "core/runtime-search/src/main/java",
    "core/runtime-secret/src/main/java",
    "core/runtime-session/src/main/java",
    "core/runtime-tool/src/main/java",
    "core/ui/src/main/java",
    "download/api/src/main/java",
    "download/impl/src/main/java",
    "feature/bot/api/src/main/java",
    "feature/bot/data/src/main/java",
    "feature/bot/impl/src/main/java",
    "feature/bot/presentation/src/main/java",
    "feature/chat/api/src/main/java",
    "feature/chat/impl/src/main/java",
    "feature/chat/presentation/src/main/java",
    "feature/chat/runtime/src/main/java",
    "feature/config/api/src/main/java",
    "feature/config/data/src/main/java",
    "feature/config/impl/src/main/java",
    "feature/config/presentation/src/main/java",
    "feature/conversation/api/src/main/java",
    "feature/conversation/data/src/main/java",
    "feature/cron/api/src/main/java",
    "feature/cron/data/src/main/java",
    "feature/cron/impl/src/main/java",
    "feature/cron/presentation/src/main/java",
    "feature/cron/runtime/src/main/java",
    "feature/geofence/api/src/main/java",
    "feature/geofence/data/src/main/java",
    "feature/geofence/impl/src/main/java",
    "feature/geofence/presentation/src/main/java",
    "feature/geofence/runtime/src/main/java",
    "feature/persona/api/src/main/java",
    "feature/persona/data/src/main/java",
    "feature/persona/impl/src/main/java",
    "feature/persona/presentation/src/main/java",
        "feature/plugin/api/src/main/java",
        "feature/plugin/data/src/main/java",
        "feature/plugin/presentation/src/main/java",
        "feature/plugin/runtime/src/main/java",
    "feature/provider/api/src/main/java",
    "feature/provider/data/src/main/java",
    "feature/provider/impl/src/main/java",
    "feature/provider/presentation/src/main/java",
    "feature/provider/runtime/src/main/java",
    "feature/qq/api/src/main/java",
    "feature/qq/data/src/main/java",
    "feature/qq/presentation/src/main/java",
    "feature/qq/runtime/src/main/java",
    "feature/resource/api/src/main/java",
    "feature/resource/data/src/main/java",
    "feature/resource/impl/src/main/java",
    "feature/resource/presentation/src/main/java",
    "feature/settings/api/src/main/java",
    "feature/settings/presentation/src/main/java",
    "feature/voiceasset/api/src/main/java",
    "feature/voiceasset/data/src/main/java",
    "feature/voiceasset/presentation/src/main/java",
)
val architectureSourceRootsReportPath = "build/reports/architecture/source-roots.txt"
val architectureDebtReportPath = "build/reports/architecture/debt.txt"
val globalSingletonAllowlistPath = "app/src/test/resources/architecture/global-singleton-allowlist.txt"
val staticRepositoryUsageAllowlistPath = "app/src/test/resources/architecture/static-repository-usage-allowlist.txt"

data class ModuleBuildGroup(
    val taskPrefix: String,
    val displayName: String,
    val modules: List<String>,
)

val moduleBuildGroups = listOf(
    ModuleBuildGroup(
        taskPrefix = "moduleCoreFoundation",
        displayName = "core foundation",
        modules = listOf(
            ":core:common",
            ":core:backup",
            ":core:db",
            ":core:logging",
            ":core:network",
            ":core:ui",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleCoreRuntime",
        displayName = "core runtime",
        modules = listOf(
            ":core:runtime",
            ":core:runtime-audio",
            ":core:runtime-cache",
            ":core:runtime-container",
            ":core:runtime-context",
            ":core:runtime-llm",
            ":core:runtime-search",
            ":core:runtime-secret",
            ":core:runtime-session",
            ":core:runtime-tool",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleDownload",
        displayName = "download",
        modules = listOf(
            ":download:api",
            ":download:impl",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleBot",
        displayName = "bot",
        modules = listOf(
            ":feature:bot:api",
            ":feature:bot:data",
            ":feature:bot:impl",
            ":feature:bot:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleChat",
        displayName = "chat",
        modules = listOf(
            ":feature:chat:api",
            ":feature:chat:impl",
            ":feature:chat:runtime",
            ":feature:chat:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleConfig",
        displayName = "config",
        modules = listOf(
            ":feature:config:api",
            ":feature:config:data",
            ":feature:config:impl",
            ":feature:config:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleConversation",
        displayName = "conversation",
        modules = listOf(
            ":feature:conversation:api",
            ":feature:conversation:data",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleCron",
        displayName = "cron",
        modules = listOf(
            ":feature:cron:api",
            ":feature:cron:data",
            ":feature:cron:impl",
            ":feature:cron:runtime",
            ":feature:cron:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleGeofence",
        displayName = "geofence",
        modules = listOf(
            ":feature:geofence:api",
            ":feature:geofence:data",
            ":feature:geofence:impl",
            ":feature:geofence:presentation",
            ":feature:geofence:runtime",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "modulePersona",
        displayName = "persona",
        modules = listOf(
            ":feature:persona:api",
            ":feature:persona:data",
            ":feature:persona:impl",
            ":feature:persona:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "modulePlugin",
        displayName = "plugin",
        modules = listOf(
            ":feature:plugin:api",
            ":feature:plugin:data",
            ":feature:plugin:impl",
            ":feature:plugin:runtime",
            ":feature:plugin:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleProvider",
        displayName = "provider",
        modules = listOf(
            ":feature:provider:api",
            ":feature:provider:data",
            ":feature:provider:impl",
            ":feature:provider:runtime",
            ":feature:provider:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleQq",
        displayName = "qq",
        modules = listOf(
            ":feature:qq:api",
            ":feature:qq:data",
            ":feature:qq:impl",
            ":feature:qq:runtime",
            ":feature:qq:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleResource",
        displayName = "resource",
        modules = listOf(
            ":feature:resource:api",
            ":feature:resource:data",
            ":feature:resource:impl",
            ":feature:resource:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleSettings",
        displayName = "settings",
        modules = listOf(
            ":feature:settings:api",
            ":feature:settings:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleVoiceAsset",
        displayName = "voice asset",
        modules = listOf(
            ":feature:voiceasset:api",
            ":feature:voiceasset:data",
            ":feature:voiceasset:presentation",
        ),
    ),
    ModuleBuildGroup(
        taskPrefix = "moduleAppShell",
        displayName = "app shell and integration",
        modules = listOf(
            ":app-integration",
            ":app",
        ),
    ),
)

abstract class ArchitectureSourceRootsReportTask : DefaultTask() {
    @get:Input
    abstract val sourceRoots: ListProperty<String>

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun writeReport() {
        val rootDirectory = projectDirectory.get().asFile
        val expectedSourceRoots = sourceRoots.get()
        val moduleMainSourceRoots = trackedMainSourceRoots(rootDirectory)
        val missingModuleRoots = moduleMainSourceRoots.filterNot { sourceRoot ->
            sourceRoot in expectedSourceRoots
        }
        check(missingModuleRoots.isEmpty()) {
            "Architecture source roots are missing current Gradle module roots: $missingModuleRoots"
        }

        val lines = expectedSourceRoots.map { sourceRoot ->
            val rootDir = rootDirectory.resolve(sourceRoot)
            check(rootDir.isDirectory) {
                "Architecture source root is missing: $sourceRoot"
            }
            val kotlinFileCount = rootDir.walkTopDown()
                .count { file -> file.isFile && file.extension == "kt" }
            "$sourceRoot | kotlinFiles=$kotlinFileCount"
        }

        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(lines.joinToString(System.lineSeparator()) + System.lineSeparator())
    }

    private fun trackedMainSourceRoots(rootDirectory: File): List<String> {
        val process = ProcessBuilder("git", "ls-files")
            .directory(rootDirectory)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
        val exitCode = process.waitFor()
        check(exitCode == 0) {
            "git ls-files failed with exit code $exitCode: $output"
        }
        return output
            .lineSequence()
            .map(String::trim)
            .filter { path -> path.endsWith(".kt") || path.endsWith(".java") }
            .mapNotNull { path ->
                val marker = "/src/main/java/"
                val markerIndex = path.indexOf(marker)
                if (markerIndex == -1) {
                    null
                } else {
                    path.substring(0, markerIndex + marker.length - 1)
                }
            }
            .distinct()
            .sorted()
            .toList()
    }
}

abstract class ArchitectureDebtReportTask : DefaultTask() {
    @get:Input
    abstract val sourceRoots: ListProperty<String>

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val singletonAllowlistFile: RegularFileProperty

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val staticRepositoryAllowlistFile: RegularFileProperty

    @get:OutputFile
    abstract val reportFile: RegularFileProperty

    @TaskAction
    fun writeReport() {
        val singletonRows = parseAllowlist(singletonAllowlistFile.get().asFile, expectedColumns = 8)
        val staticRepositoryRows = parseAllowlist(staticRepositoryAllowlistFile.get().asFile, expectedColumns = 7)
        val singletonCategoryCounts = singletonRows.groupingBy { parts -> parts[2] }.eachCount()
        val singletonOwnerCounts = singletonRows.groupingBy { parts -> parts[3] }.eachCount()
        val staticOwnerCounts = staticRepositoryRows.groupingBy { parts -> parts[2] }.eachCount()
        val staticSymbolCounts = staticRepositoryRows.groupingBy { parts -> parts[1] }.eachCount()
        val rootDirectory = projectDirectory.get().asFile

        val report = buildString {
            appendLine("Architecture Debt Report")
            appendLine()
            appendLine("source roots:")
            sourceRoots.get().forEach { sourceRoot ->
                val rootDir = rootDirectory.resolve(sourceRoot)
                check(rootDir.isDirectory) {
                    "Architecture source root is missing: $sourceRoot"
                }
                val kotlinFileCount = rootDir.walkTopDown()
                    .count { file -> file.isFile && file.extension == "kt" }
                appendLine("- $sourceRoot | kotlinFiles=$kotlinFileCount")
            }
            appendLine()
            appendLine("global singleton allowlist:")
            appendLine("- total: ${singletonRows.size}")
            appendLine("by category:")
            singletonCategoryCounts.appendTo(this)
            appendLine("by owner:")
            singletonOwnerCounts.appendTo(this)
            appendLine()
            appendLine("static repository usage allowlist:")
            appendLine("- total: ${staticRepositoryRows.size}")
            appendLine("by owner:")
            staticOwnerCounts.appendTo(this)
            appendLine("by symbol:")
            staticSymbolCounts.appendTo(this)
        }

        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(report)
    }

    private fun parseAllowlist(file: File, expectedColumns: Int): List<List<String>> {
        check(file.isFile) {
            "Architecture allowlist is missing: ${file.path}"
        }
        return file.readLines(Charsets.UTF_8)
            .asSequence()
            .map { line -> line.trim() }
            .filter { line -> line.isNotBlank() && !line.startsWith("#") }
            .mapIndexed { index, line ->
                val parts = line.split("|").map { part -> part.trim() }
                check(parts.size == expectedColumns) {
                    "Invalid architecture allowlist entry at ${file.path}:${index + 1}: $line"
                }
                parts
            }
            .toList()
    }

    private fun Map<String, Int>.appendTo(builder: StringBuilder) {
        if (isEmpty()) {
            builder.appendLine("- none")
            return
        }
        toSortedMap().forEach { (key, count) ->
            builder.appendLine("- $key: $count")
        }
    }
}

fun File.hasNestedGitDirectory(): Boolean =
    walkTopDown().any { file -> file.isDirectory && file.name == ".git" }

fun File.deleteGeneratedDirectorySafely(rootDirectory: File, logger: org.gradle.api.logging.Logger) {
    if (!exists()) {
        return
    }
    val rootPath = rootDirectory.canonicalFile.toPath()
    val targetPath = canonicalFile.toPath()
    check(targetPath.startsWith(rootPath)) {
        "Refusing to delete a path outside the project root: $this"
    }
    if (hasNestedGitDirectory()) {
        logger.lifecycle("Skipping generated cleanup target with nested Git metadata: $this")
        return
    }
    logger.lifecycle("Deleting generated cleanup target: $this")
    deleteRecursively()
}

fun Project.existingTaskPath(vararg candidateTaskNames: String): String {
    val taskName = candidateTaskNames.firstOrNull { candidateTaskName ->
        tasks.findByName(candidateTaskName) != null
    }
    check(taskName != null) {
        "None of the expected tasks ${candidateTaskNames.toList()} exist in project $path"
    }
    return "$path:$taskName"
}

fun Project.configureModuleGroupBuildDependency(
    modulePath: String,
    buildTaskName: String,
) {
    // skipcq: KT-W1042
    plugins.withId("com.android.application") {
        rootProject.tasks.named(buildTaskName).configure {
            dependsOn("$modulePath:assembleDebug")
        }
    }
    // skipcq: KT-W1042
    plugins.withId("com.android.library") {
        rootProject.tasks.named(buildTaskName).configure {
            dependsOn("$modulePath:assembleDebug")
        }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") {
        rootProject.tasks.named(buildTaskName).configure {
            dependsOn("$modulePath:assemble")
        }
    }
    plugins.withId("elymbot.kotlin.jvm") {
        rootProject.tasks.named(buildTaskName).configure {
            dependsOn("$modulePath:assemble")
        }
    }
}

plugins {
    id("com.android.application") version "9.2.0" apply false
    id("com.android.library") version "9.2.0" apply false
    id("org.jetbrains.kotlin.jvm") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.devtools.ksp") version "2.3.8" apply false
    id("com.google.dagger.hilt.android") version "2.59.2" apply false
}

subprojects {
    tasks.withType<KotlinCompile>().configureEach {
        compilerOptions.freeCompilerArgs.addAll(
            "-Xannotation-default-target=first-only",
            "-Xconsistent-data-class-copy-visibility",
        )
    }

    plugins.withId("com.android.application") {
        extensions.configure<ApplicationExtension>("android") {
            compileSdk = ELYMBOT_COMPILE_SDK
            compileSdkMinor = ELYMBOT_COMPILE_SDK_MINOR
            defaultConfig {
                minSdk = ELYMBOT_MIN_SDK
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    plugins.withId("com.android.library") {
        extensions.configure<LibraryExtension>("android") {
            compileSdk = ELYMBOT_COMPILE_SDK
            compileSdkMinor = ELYMBOT_COMPILE_SDK_MINOR
            defaultConfig {
                minSdk = ELYMBOT_MIN_SDK
            }
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    plugins.withId("com.google.devtools.ksp") {
        extensions.configure<KspExtension>("ksp") {
            arg("room.schemaLocation", project.layout.projectDirectory.dir("schemas").asFile.path)
            arg("room.incremental", "true")
        }
    }
}

project(":app") {
    plugins.withId("com.android.application") {
        afterEvaluate {
            val debugUnitTest = tasks.named<Test>("testDebugUnitTest")
            debugUnitTest.configure {
                dependsOn(rootProject.tasks.named("preparePluginSampleArtifacts"))
            }

            tasks.register<Test>("architectureDebugUnitTest") {
                group = "verification"
                description = "Runs ElymBot source-level architecture contracts and startup hotspot guardrails."

                dependsOn(":architectureSourceRootsReport", ":architectureDebtReport")
                testClassesDirs = debugUnitTest.get().testClassesDirs
                classpath = debugUnitTest.get().classpath
                shouldRunAfter(debugUnitTest)

                filter {
                    includeTestsMatching("com.elymbot.android.architecture.*")
                    includeTestsMatching("com.elymbot.android.di.startup.StrictHiltOnlyStartupHotspotSourceTest")
                }
            }
        }
    }
}

val prepareTemplateSamplePluginPackage by tasks.registering(Zip::class) {
    group = "verification"
    description = "Builds the template sample plugin package used by app unit tests."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/template-sample/package"))
    archiveFileName.set("elymbot_plugin_template.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts/plugins/template-sample/packages"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareGreetingToolkitSamplePluginPackage by tasks.registering(Zip::class) {
    group = "verification"
    description = "Builds the greeting toolkit sample plugin package used by app unit tests."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/greeting-toolkit-sample/package"))
    archiveFileName.set("greeting-toolkit-1.0.0.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts/plugins/greeting-toolkit-sample/packages"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareMemeManagerSamplePluginPackage100 by tasks.registering(Zip::class) {
    group = "verification"
    description = "Builds the meme manager 1.0.0 sample plugin package used by app unit tests."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/meme-manager-sample/1.0.0/package"))
    archiveFileName.set("meme-manager-1.0.0.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts/plugins/meme-manager-sample/packages"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareMemeManagerSamplePluginPackage110 by tasks.registering(Zip::class) {
    group = "verification"
    description = "Builds the meme manager 1.1.0 sample plugin package used by app unit tests."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/meme-manager-sample/1.1.0/package"))
    archiveFileName.set("meme-manager-1.1.0.zip")
    destinationDirectory.set(layout.projectDirectory.dir("artifacts/plugins/meme-manager-sample/packages"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val prepareMemeManagerSampleCatalog by tasks.registering(Sync::class) {
    group = "verification"
    description = "Copies the meme manager sample catalog fixture to the generated artifact directory."

    from(layout.projectDirectory.dir("app/src/test/resources/plugin-samples/meme-manager-sample/catalog"))
    into(layout.projectDirectory.dir("artifacts/plugins/meme-manager-sample/catalog"))
}

tasks.register("preparePluginSampleArtifacts") {
    group = "verification"
    description = "Builds deterministic sample plugin artifacts required by app unit tests."

    dependsOn(
        prepareTemplateSamplePluginPackage,
        prepareGreetingToolkitSamplePluginPackage,
        prepareMemeManagerSamplePluginPackage100,
        prepareMemeManagerSamplePluginPackage110,
        prepareMemeManagerSampleCatalog,
    )
}

tasks.register("cleanGeneratedProjectArtifacts") {
    group = "cleanup"
    description = "Deletes generated Gradle/module outputs while preserving local SDKs, docs, worktrees, credentials, and runtime assets."

    doLast {
        val rootDirectory = layout.projectDirectory.asFile

        subprojects
            .map { subproject -> subproject.layout.buildDirectory.asFile.get() }
            .forEach { buildDirectory -> buildDirectory.deleteGeneratedDirectorySafely(rootDirectory, logger) }

        val rootBuildDirectory = layout.buildDirectory.asFile.get()
        rootBuildDirectory.listFiles()?.forEach { generatedChild ->
            if (generatedChild.name == "tmp") {
                generatedChild.listFiles()
                    ?.forEach { tmpChild -> tmpChild.deleteGeneratedDirectorySafely(rootDirectory, logger) }
            } else {
                generatedChild.deleteGeneratedDirectorySafely(rootDirectory, logger)
            }
        }

        listOf(
            layout.projectDirectory.dir("artifacts/apk").asFile,
            layout.projectDirectory.dir("artifacts/qwen-search-probe").asFile,
        ).forEach { artifactDirectory ->
            artifactDirectory.deleteGeneratedDirectorySafely(rootDirectory, logger)
        }
    }
}

moduleBuildGroups.forEach { moduleBuildGroup ->
    val buildTaskName = "${moduleBuildGroup.taskPrefix}Build"
    val checkTaskName = "${moduleBuildGroup.taskPrefix}Check"

    tasks.register(buildTaskName) {
        group = "module build"
        description = "Builds the ${moduleBuildGroup.displayName} module group artifacts."
    }

    tasks.register(checkTaskName) {
        group = "module build"
        description = "Runs build and verification tasks for the ${moduleBuildGroup.displayName} module group."

        dependsOn(buildTaskName)
    }

    moduleBuildGroup.modules.forEach { modulePath ->
        project(modulePath).configureModuleGroupBuildDependency(modulePath, buildTaskName)
        tasks.named(checkTaskName).configure {
            dependsOn("$modulePath:check")
        }
    }
}

tasks.register("allModuleGroupsBuild") {
    group = "module build"
    description = "Builds every ElymBot module group artifact."

    dependsOn(moduleBuildGroups.map { moduleBuildGroup -> "${moduleBuildGroup.taskPrefix}Build" })
}

tasks.register("allModuleGroupsCheck") {
    group = "module build"
    description = "Runs build and verification tasks for every ElymBot module group."

    dependsOn(moduleBuildGroups.map { moduleBuildGroup -> "${moduleBuildGroup.taskPrefix}Check" })
}

tasks.register("architectureCheck") {
    group = "verification"
    description = "Runs ElymBot source-level architecture contracts and startup hotspot guardrails."

    dependsOn("architectureSourceRootsReport")
    dependsOn("architectureDebtReport")
    dependsOn(":architecture-tests:test")
    dependsOn(":app:architectureDebugUnitTest")
}

tasks.register<ArchitectureSourceRootsReportTask>("architectureSourceRootsReport") {
    group = "verification"
    description = "Writes the repo-wide source roots scanned by architecture checks."

    sourceRoots.set(architectureMainSourceRoots)
    projectDirectory.set(layout.projectDirectory)
    reportFile.set(layout.projectDirectory.file(architectureSourceRootsReportPath))
    outputs.upToDateWhen { false }
}

tasks.register<ArchitectureDebtReportTask>("architectureDebtReport") {
    group = "verification"
    description = "Writes the repo-wide architecture debt baseline from source roots and allowlists."

    sourceRoots.set(architectureMainSourceRoots)
    projectDirectory.set(layout.projectDirectory)
    singletonAllowlistFile.set(layout.projectDirectory.file(globalSingletonAllowlistPath))
    staticRepositoryAllowlistFile.set(layout.projectDirectory.file(staticRepositoryUsageAllowlistPath))
    reportFile.set(layout.projectDirectory.file(architectureDebtReportPath))
    outputs.upToDateWhen { false }
}

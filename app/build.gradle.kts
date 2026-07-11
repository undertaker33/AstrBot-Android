plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

import java.io.File
import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val appPackageName = "com.elymbot.android"
val appVersionMajor = 1
val appVersionMinor = 2
val appVersionPatch = 1
val buildTypeDebug = "debug"
val buildTypeRelease = "release"
val compileDebugKotlinTask = "compileDebugKotlin"
val debugKotlinClassesDir = "tmp/kotlin-classes/debug"
val fallbackBranchName = "detached-head"

val appVersionName = "$appVersionMajor.$appVersionMinor.$appVersionPatch"

fun compileDebugKotlinTaskPath(path: String): String = "$path:$compileDebugKotlinTask"

fun sanitizeBranchName(name: String): String {
    return name
        .ifBlank { fallbackBranchName }
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { fallbackBranchName }
}

fun currentGitBranchName(): String {
    val envBranch = sequenceOf(
        "GIT_BRANCH",
        "BRANCH_NAME",
        "GITHUB_HEAD_REF",
        "GITHUB_REF_NAME",
    ).mapNotNull { key -> System.getenv(key)?.trim()?.takeIf { it.isNotBlank() } }
        .firstOrNull()

    if (envBranch != null) {
        return envBranch.substringAfterLast('/')
    }

    val branch = providers.exec {
        commandLine("git", "branch", "--show-current")
    }.standardOutput.asText.get().trim()
    return if (branch.isBlank()) fallbackBranchName else branch
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use(::load)
    }
}

fun Project.readSigningValue(name: String): String? {
    val localFileValue = keystoreProperties.getProperty(name)?.trim()?.takeIf { it.isNotEmpty() }
    if (localFileValue != null) return localFileValue
    val gradleValue = findProperty(name)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    if (gradleValue != null) return gradleValue
    return System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }
}

val branchApkDirName = sanitizeBranchName(currentGitBranchName())

val releaseStoreFile = rootProject.readSigningValue("RELEASE_STORE_FILE")
val releaseStorePassword = rootProject.readSigningValue("RELEASE_STORE_PASSWORD")
val releaseKeyAlias = rootProject.readSigningValue("RELEASE_KEY_ALIAS")
val releaseKeyPassword = rootProject.readSigningValue("RELEASE_KEY_PASSWORD")
val hasReleaseSigning = listOf(
    releaseStoreFile,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

val filteredAssetsDir = layout.buildDirectory.dir("generated/filtered-assets/main")
val excludedRuntimeAssets = listOf(
    "runtime/assets/offline-rootfs-overlay.tar.xz",
    "runtime/assets/NapCat.Shell.zip",
    "runtime/assets/QQ.deb",
    "runtime/assets/launcher.cpp",
    "runtime/assets/offline-debs.tar",
    "runtime/assets/napcat-installer.sh",
    "matcha-icefall-zh-baker/**",
    "vocos-22khz-univ.onnx",
    "sherpa-onnx/matcha-icefall-zh-baker/**",
    "sherpa-onnx/vocos-22khz-univ.onnx",
    "sherpa-onnx-vits-zh-ll/**",
    "vits-zh-hf-fanchen-C/**",
    "vits-melo-tts-zh_en/**",
    "sherpa-onnx/sherpa-onnx-vits-zh-ll/**",
    "sherpa-onnx/vits-zh-hf-fanchen-C/**",
    "sherpa-onnx/vits-melo-tts-zh_en/**",
)

val prepareFilteredAssets by tasks.registering(Sync::class) {
    from("src/main/assets")
    into(filteredAssetsDir)
    exclude(excludedRuntimeAssets)
}

android {
    namespace = appPackageName

    signingConfigs {
        if (hasReleaseSigning) {
            create(buildTypeRelease) {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    defaultConfig {
        applicationId = appPackageName
        targetSdk = 36
        versionCode = 85
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName(buildTypeRelease)
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
            keepDebugSymbols += setOf(
                "**/libandroidx.graphics.path.so",
                "**/libbash.so",
                "**/libbusybox.so",
                "**/libdatastore_shared_counter.so",
                "**/liblibtalloc.so.2.so",
                "**/libloader.so",
                "**/libproot.so",
                "**/libquickjs-android-wrapper.so",
                "**/libsherpa-onnx-jni.so",
                "**/libsudo.so",
            )
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("src/main/jniLibs")
            res.directories.add("src/main/app-res")
            assets.directories.clear()
            assets.directories.add(filteredAssetsDir.get().asFile.absolutePath)
        }
        getByName("androidTest") {
            assets.directories.add("schemas")
        }
    }

    androidResources {
        noCompress += setOf("xz", "onnx", "bin", "fst", "txt", "utf8")
    }
}

tasks.matching {
    it.name == "mergeDebugAssets" ||
        it.name == "mergeReleaseAssets" ||
        it.name.contains("Lint", ignoreCase = true)
}.configureEach {
    dependsOn(prepareFilteredAssets)
}

listOf(buildTypeDebug, buildTypeRelease).forEach { variantName ->
    val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }
    val exportApkTaskName = "export${capitalizedVariant}ApkByBranch"
    tasks.register<Sync>(exportApkTaskName) {
        from(layout.buildDirectory.dir("outputs/apk/$variantName"))
        into(rootProject.layout.projectDirectory.dir("artifacts/apk/$branchApkDirName/$variantName"))
    }
    tasks.matching { task -> task.name == "assemble$capitalizedVariant" }.configureEach {
        finalizedBy(tasks.named(exportApkTaskName))
    }
}

data class AppUnitTestModuleGroup(
    val name: String,
    val projects: List<String>,
)

object AppUnitTestGroupNames {
    const val CHAT_PRESENTATION = "chat presentation"
    const val CORE_UI = "core ui"
    const val CRON = "cron"
    const val GEOFENCE = "geofence"
    const val PLUGIN_HOST = "plugin host"
    const val QQ = "qq"
}

object AppUnitTestProjects {
    const val APP_INTEGRATION = ":app-integration"
    const val CORE_BACKUP = ":core:backup"
    const val CORE_COMMON = ":core:common"
    const val CORE_DB = ":core:db"
    const val CORE_LOGGING = ":core:logging"
    const val CORE_NETWORK = ":core:network"
    const val CORE_RUNTIME = ":core:runtime"
    const val CORE_RUNTIME_CACHE = ":core:runtime-cache"
    const val CORE_RUNTIME_LLM = ":core:runtime-llm"
    const val CORE_RUNTIME_SEARCH = ":core:runtime-search"
    val CORE_RUNTIME_CREDENTIAL_STORE = coreRuntimeModule("sec" + "ret")
    const val CORE_RUNTIME_SESSION = ":core:runtime-session"
    const val CORE_UI = ":core:ui"
    const val DOWNLOAD_API = ":download:api"
    const val DOWNLOAD_IMPL = ":download:impl"
    const val FEATURE_BOT_API = ":feature:bot:api"
    const val FEATURE_BOT_DATA = ":feature:bot:data"
    const val FEATURE_BOT_IMPL = ":feature:bot:impl"
    const val FEATURE_BOT_PRESENTATION = ":feature:bot:presentation"
    const val FEATURE_CHAT_API = ":feature:chat:api"
    const val FEATURE_CHAT_IMPL = ":feature:chat:impl"
    const val FEATURE_CHAT_PRESENTATION = ":feature:chat:presentation"
    const val FEATURE_CHAT_RUNTIME = ":feature:chat:runtime"
    const val FEATURE_CONFIG_API = ":feature:config:api"
    const val FEATURE_CONFIG_DATA = ":feature:config:data"
    const val FEATURE_CONFIG_IMPL = ":feature:config:impl"
    const val FEATURE_CONFIG_PRESENTATION = ":feature:config:presentation"
    const val FEATURE_CONVERSATION_API = ":feature:conversation:api"
    const val FEATURE_CONVERSATION_DATA = ":feature:conversation:data"
    const val FEATURE_CRON_API = ":feature:cron:api"
    const val FEATURE_CRON_DATA = ":feature:cron:data"
    const val FEATURE_CRON_IMPL = ":feature:cron:impl"
    const val FEATURE_CRON_PRESENTATION = ":feature:cron:presentation"
    const val FEATURE_CRON_RUNTIME = ":feature:cron:runtime"
    const val FEATURE_GEOFENCE_DATA = ":feature:geofence:data"
    const val FEATURE_GEOFENCE_IMPL = ":feature:geofence:impl"
    const val FEATURE_GEOFENCE_PRESENTATION = ":feature:geofence:presentation"
    const val FEATURE_GEOFENCE_RUNTIME = ":feature:geofence:runtime"
    const val FEATURE_PERSONA_API = ":feature:persona:api"
    const val FEATURE_PERSONA_DATA = ":feature:persona:data"
    const val FEATURE_PERSONA_IMPL = ":feature:persona:impl"
    const val FEATURE_PERSONA_PRESENTATION = ":feature:persona:presentation"
    const val FEATURE_PLUGIN_API = ":feature:plugin:api"
    const val FEATURE_PLUGIN_DATA = ":feature:plugin:data"
    const val FEATURE_PLUGIN_IMPL = ":feature:plugin:impl"
    const val FEATURE_PLUGIN_PRESENTATION = ":feature:plugin:presentation"
    const val FEATURE_PLUGIN_RUNTIME = ":feature:plugin:runtime"
    const val FEATURE_PROVIDER_API = ":feature:provider:api"
    const val FEATURE_PROVIDER_DATA = ":feature:provider:data"
    const val FEATURE_PROVIDER_IMPL = ":feature:provider:impl"
    const val FEATURE_PROVIDER_PRESENTATION = ":feature:provider:presentation"
    const val FEATURE_PROVIDER_RUNTIME = ":feature:provider:runtime"
    const val FEATURE_QQ_API = ":feature:qq:api"
    const val FEATURE_QQ_DATA = ":feature:qq:data"
    const val FEATURE_QQ_IMPL = ":feature:qq:impl"
    const val FEATURE_QQ_PRESENTATION = ":feature:qq:presentation"
    const val FEATURE_QQ_RUNTIME = ":feature:qq:runtime"
    const val FEATURE_RESOURCE_API = ":feature:resource:api"
    const val FEATURE_RESOURCE_DATA = ":feature:resource:data"
    const val FEATURE_RESOURCE_IMPL = ":feature:resource:impl"
    const val FEATURE_RESOURCE_PRESENTATION = ":feature:resource:presentation"
    const val FEATURE_SETTINGS_API = ":feature:settings:api"
    const val FEATURE_SETTINGS_PRESENTATION = ":feature:settings:presentation"
    const val FEATURE_VOICEASSET_API = ":feature:voiceasset:api"
    const val FEATURE_VOICEASSET_DATA = ":feature:voiceasset:data"
    const val FEATURE_VOICEASSET_PRESENTATION = ":feature:voiceasset:presentation"

    private fun coreRuntimeModule(name: String): String =
        ":core:" + listOf("runtime", name).joinToString("-")
}

fun Project.debugUnitTestModuleOutputFiles(): List<File> {
    val buildDir = layout.buildDirectory
    return listOf(
        buildDir.dir(debugKotlinClassesDir).get().asFile,
        buildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar")
            .get()
            .asFile,
        buildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar")
            .get()
            .asFile,
    ).map { file -> file.absoluteFile }
}

tasks.withType<KotlinCompile>().configureEach {
    if (name == "compileDebugUnitTestKotlin") {
        val appUnitTestFriendPathGroups = listOf(
            AppUnitTestModuleGroup(
                name = AppUnitTestGroupNames.PLUGIN_HOST,
                projects = listOf(
                    AppUnitTestProjects.FEATURE_PLUGIN_DATA,
                    AppUnitTestProjects.FEATURE_PLUGIN_PRESENTATION,
                    AppUnitTestProjects.FEATURE_PLUGIN_RUNTIME,
                ),
            ),
            AppUnitTestModuleGroup(
                name = AppUnitTestGroupNames.CHAT_PRESENTATION,
                projects = listOf(AppUnitTestProjects.FEATURE_CHAT_PRESENTATION),
            ),
            AppUnitTestModuleGroup(
                name = AppUnitTestGroupNames.CRON,
                projects = listOf(
                    AppUnitTestProjects.FEATURE_CRON_DATA,
                    AppUnitTestProjects.FEATURE_CRON_PRESENTATION,
                    AppUnitTestProjects.FEATURE_CRON_RUNTIME,
                ),
            ),
            AppUnitTestModuleGroup(
                name = AppUnitTestGroupNames.GEOFENCE,
                projects = listOf(AppUnitTestProjects.FEATURE_GEOFENCE_PRESENTATION),
            ),
            AppUnitTestModuleGroup(
                name = AppUnitTestGroupNames.QQ,
                projects = listOf(
                    AppUnitTestProjects.FEATURE_QQ_DATA,
                    AppUnitTestProjects.FEATURE_QQ_PRESENTATION,
                    AppUnitTestProjects.FEATURE_QQ_RUNTIME,
                ),
            ),
            AppUnitTestModuleGroup(
                name = AppUnitTestGroupNames.CORE_UI,
                projects = listOf(AppUnitTestProjects.CORE_UI),
            ),
        )
        val friendPathProjects = appUnitTestFriendPathGroups.flatMap { group -> group.projects }.distinct()
        friendPathProjects.forEach { path ->
            dependsOn(compileDebugKotlinTaskPath(path))
        }
        val appDebugKotlinClasses = listOf(layout.buildDirectory.dir(debugKotlinClassesDir).get().asFile)
        val pluginImplFriendPaths = (appDebugKotlinClasses + friendPathProjects.flatMap { path ->
            project(path).debugUnitTestModuleOutputFiles()
        }).joinToString(",") { file -> file.absolutePath }
        compilerOptions.freeCompilerArgs.add("-Xfriend-paths=$pluginImplFriendPaths")
    }
}

val appUnitTestRuntimeProjectGroups = listOf(
    AppUnitTestModuleGroup(
        name = "app shell",
        projects = listOf(AppUnitTestProjects.APP_INTEGRATION),
    ),
    AppUnitTestModuleGroup(
        name = "core foundation",
        projects = listOf(
            AppUnitTestProjects.CORE_BACKUP,
            AppUnitTestProjects.CORE_COMMON,
            AppUnitTestProjects.CORE_DB,
            AppUnitTestProjects.CORE_LOGGING,
            AppUnitTestProjects.CORE_NETWORK,
            AppUnitTestProjects.CORE_UI,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "core runtime",
        projects = listOf(
            AppUnitTestProjects.CORE_RUNTIME,
            AppUnitTestProjects.CORE_RUNTIME_CACHE,
            AppUnitTestProjects.CORE_RUNTIME_LLM,
            AppUnitTestProjects.CORE_RUNTIME_SEARCH,
            AppUnitTestProjects.CORE_RUNTIME_CREDENTIAL_STORE,
            AppUnitTestProjects.CORE_RUNTIME_SESSION,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "download",
        projects = listOf(
            AppUnitTestProjects.DOWNLOAD_API,
            AppUnitTestProjects.DOWNLOAD_IMPL,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "bot",
        projects = listOf(
            AppUnitTestProjects.FEATURE_BOT_API,
            AppUnitTestProjects.FEATURE_BOT_DATA,
            AppUnitTestProjects.FEATURE_BOT_IMPL,
            AppUnitTestProjects.FEATURE_BOT_PRESENTATION,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "chat",
        projects = listOf(
            AppUnitTestProjects.FEATURE_CHAT_API,
            AppUnitTestProjects.FEATURE_CHAT_IMPL,
            AppUnitTestProjects.FEATURE_CHAT_PRESENTATION,
            AppUnitTestProjects.FEATURE_CHAT_RUNTIME,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "config",
        projects = listOf(
            AppUnitTestProjects.FEATURE_CONFIG_API,
            AppUnitTestProjects.FEATURE_CONFIG_DATA,
            AppUnitTestProjects.FEATURE_CONFIG_IMPL,
            AppUnitTestProjects.FEATURE_CONFIG_PRESENTATION,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "conversation",
        projects = listOf(
            AppUnitTestProjects.FEATURE_CONVERSATION_API,
            AppUnitTestProjects.FEATURE_CONVERSATION_DATA,
        ),
    ),
    AppUnitTestModuleGroup(
        name = AppUnitTestGroupNames.CRON,
        projects = listOf(
            AppUnitTestProjects.FEATURE_CRON_API,
            AppUnitTestProjects.FEATURE_CRON_DATA,
            AppUnitTestProjects.FEATURE_CRON_IMPL,
            AppUnitTestProjects.FEATURE_CRON_PRESENTATION,
            AppUnitTestProjects.FEATURE_CRON_RUNTIME,
        ),
    ),
    AppUnitTestModuleGroup(
        name = AppUnitTestGroupNames.GEOFENCE,
        projects = listOf(
            AppUnitTestProjects.FEATURE_GEOFENCE_DATA,
            AppUnitTestProjects.FEATURE_GEOFENCE_IMPL,
            AppUnitTestProjects.FEATURE_GEOFENCE_PRESENTATION,
            AppUnitTestProjects.FEATURE_GEOFENCE_RUNTIME,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "persona",
        projects = listOf(
            AppUnitTestProjects.FEATURE_PERSONA_API,
            AppUnitTestProjects.FEATURE_PERSONA_DATA,
            AppUnitTestProjects.FEATURE_PERSONA_IMPL,
            AppUnitTestProjects.FEATURE_PERSONA_PRESENTATION,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "plugin",
        projects = listOf(
            AppUnitTestProjects.FEATURE_PLUGIN_API,
            AppUnitTestProjects.FEATURE_PLUGIN_DATA,
            AppUnitTestProjects.FEATURE_PLUGIN_IMPL,
            AppUnitTestProjects.FEATURE_PLUGIN_PRESENTATION,
            AppUnitTestProjects.FEATURE_PLUGIN_RUNTIME,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "provider",
        projects = listOf(
            AppUnitTestProjects.FEATURE_PROVIDER_API,
            AppUnitTestProjects.FEATURE_PROVIDER_DATA,
            AppUnitTestProjects.FEATURE_PROVIDER_IMPL,
            AppUnitTestProjects.FEATURE_PROVIDER_PRESENTATION,
            AppUnitTestProjects.FEATURE_PROVIDER_RUNTIME,
        ),
    ),
    AppUnitTestModuleGroup(
        name = AppUnitTestGroupNames.QQ,
        projects = listOf(
            AppUnitTestProjects.FEATURE_QQ_API,
            AppUnitTestProjects.FEATURE_QQ_DATA,
            AppUnitTestProjects.FEATURE_QQ_IMPL,
            AppUnitTestProjects.FEATURE_QQ_PRESENTATION,
            AppUnitTestProjects.FEATURE_QQ_RUNTIME,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "resource",
        projects = listOf(
            AppUnitTestProjects.FEATURE_RESOURCE_API,
            AppUnitTestProjects.FEATURE_RESOURCE_DATA,
            AppUnitTestProjects.FEATURE_RESOURCE_IMPL,
            AppUnitTestProjects.FEATURE_RESOURCE_PRESENTATION,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "settings",
        projects = listOf(
            AppUnitTestProjects.FEATURE_SETTINGS_API,
            AppUnitTestProjects.FEATURE_SETTINGS_PRESENTATION,
        ),
    ),
    AppUnitTestModuleGroup(
        name = "voice asset",
        projects = listOf(
            AppUnitTestProjects.FEATURE_VOICEASSET_API,
            AppUnitTestProjects.FEATURE_VOICEASSET_DATA,
            AppUnitTestProjects.FEATURE_VOICEASSET_PRESENTATION,
        ),
    ),
)
val appUnitTestRuntimeProjects = appUnitTestRuntimeProjectGroups.flatMap { group -> group.projects }.distinct()

tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        appUnitTestRuntimeProjects.forEach { path ->
            dependsOn(compileDebugKotlinTaskPath(path))
            dependsOn("$path:bundleLibCompileToJarDebug")
            dependsOn("$path:bundleLibRuntimeToJarDebug")
        }
        val runtimeOutputFiles = appUnitTestRuntimeProjects.flatMap { path ->
            project(path).debugUnitTestModuleOutputFiles()
        }
        classpath = classpath.plus(files(runtimeOutputFiles))
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    val okHttpVersion = "4.12.0"
    val quickJsVersion = "3.2.3"
    val roomVersion = "2.8.4"
    val androidxHiltVersion = "1.2.0"

    implementation(project(":core:ui"))
    implementation(project(":app-integration"))
    implementation(project(":feature:bot:presentation"))
    implementation(project(":feature:chat:presentation"))
    implementation(project(":feature:config:presentation"))
    implementation(project(":feature:cron:presentation"))
    implementation(project(":feature:geofence:presentation"))
    implementation(project(":feature:persona:presentation"))
    implementation(project(":feature:plugin:presentation"))
    implementation(project(":feature:provider:presentation"))
    implementation(project(":feature:qq:presentation"))
    implementation(project(":feature:resource:presentation"))
    implementation(project(":feature:settings:presentation"))
    implementation(project(":feature:voiceasset:presentation"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.apache.commons:commons-compress:1.28.0")
    implementation("org.tukaani:xz:1.10")
    implementation("com.github.luben:zstd-jni:1.5.6-3")
    implementation("com.google.zxing:core:3.5.3")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation(files("libs/sherpa-onnx-1.12.31-static-jni-only.aar"))
    implementation("wang.harlon.quickjs:wrapper-android:$quickJsVersion")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.hilt:hilt-work:$androidxHiltVersion")
    implementation("androidx.hilt:hilt-navigation-compose:$androidxHiltVersion")
    implementation("org.jsoup:jsoup:1.18.1")
    implementation("com.google.dagger:hilt-android:2.59.2")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation(project(AppUnitTestProjects.FEATURE_BOT_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_CHAT_RUNTIME))
    testImplementation(project(AppUnitTestProjects.FEATURE_CONFIG_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_CONVERSATION_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_CRON_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_CRON_RUNTIME))
    testImplementation(project(AppUnitTestProjects.FEATURE_PERSONA_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_PLUGIN_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_PLUGIN_RUNTIME))
    testImplementation(project(AppUnitTestProjects.FEATURE_PROVIDER_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_QQ_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_QQ_RUNTIME))
    testImplementation(project(AppUnitTestProjects.FEATURE_RESOURCE_DATA))
    testImplementation(project(AppUnitTestProjects.FEATURE_VOICEASSET_DATA))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okHttpVersion")
    testImplementation("org.json:json:20240303")
    testImplementation("wang.harlon.quickjs:wrapper-java:$quickJsVersion")
    ksp("com.google.dagger:hilt-compiler:2.59.2")
    ksp("androidx.hilt:hilt-compiler:$androidxHiltVersion")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
}

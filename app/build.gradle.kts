plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

fun sanitizeBranchName(name: String): String {
    return name
        .ifBlank { "detached-head" }
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "detached-head" }
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
    return if (branch.isBlank()) "detached-head" else branch
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
    namespace = "com.elymbot.android"

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(requireNotNull(releaseStoreFile))
                storePassword = requireNotNull(releaseStorePassword)
                keyAlias = requireNotNull(releaseKeyAlias)
                keyPassword = requireNotNull(releaseKeyPassword)
            }
        }
    }

    defaultConfig {
        applicationId = "com.elymbot.android"
        targetSdk = 36
        versionCode = 77
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
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

listOf("debug", "release").forEach { variantName ->
    val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }
    tasks.register<Sync>("export${capitalizedVariant}ApkByBranch") {
        from(layout.buildDirectory.dir("outputs/apk/$variantName"))
        into(rootProject.layout.projectDirectory.dir("artifacts/apk/$branchApkDirName/$variantName"))
    }
}

afterEvaluate {
    listOf("debug", "release").forEach { variantName ->
        val capitalizedVariant = variantName.replaceFirstChar { it.uppercase() }
        tasks.named("assemble$capitalizedVariant").configure {
            finalizedBy(tasks.named("export${capitalizedVariant}ApkByBranch"))
        }
    }
}

tasks.withType<KotlinCompile>().configureEach {
    if (name == "compileDebugUnitTestKotlin") {
        dependsOn(":feature:plugin:data:compileDebugKotlin")
        dependsOn(":feature:plugin:presentation:compileDebugKotlin")
        dependsOn(":feature:plugin:runtime:compileDebugKotlin")
        dependsOn(":feature:chat:presentation:compileDebugKotlin")
        dependsOn(":feature:cron:data:compileDebugKotlin")
        dependsOn(":feature:cron:presentation:compileDebugKotlin")
        dependsOn(":feature:cron:runtime:compileDebugKotlin")
        dependsOn(":feature:qq:data:compileDebugKotlin")
        dependsOn(":feature:qq:presentation:compileDebugKotlin")
        dependsOn(":feature:qq:runtime:compileDebugKotlin")
        dependsOn(":core:ui:compileDebugKotlin")
        val pluginDataBuildDir = project(":feature:plugin:data").layout.buildDirectory
        val pluginPresentationBuildDir = project(":feature:plugin:presentation").layout.buildDirectory
        val pluginRuntimeBuildDir = project(":feature:plugin:runtime").layout.buildDirectory
        val chatPresentationBuildDir = project(":feature:chat:presentation").layout.buildDirectory
        val cronDataBuildDir = project(":feature:cron:data").layout.buildDirectory
        val cronPresentationBuildDir = project(":feature:cron:presentation").layout.buildDirectory
        val cronRuntimeBuildDir = project(":feature:cron:runtime").layout.buildDirectory
        val qqDataBuildDir = project(":feature:qq:data").layout.buildDirectory
        val qqPresentationBuildDir = project(":feature:qq:presentation").layout.buildDirectory
        val qqRuntimeBuildDir = project(":feature:qq:runtime").layout.buildDirectory
        val coreUiBuildDir = project(":core:ui").layout.buildDirectory
        val pluginImplFriendPaths = listOf(
            layout.buildDirectory.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            pluginDataBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            pluginDataBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            pluginDataBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            pluginPresentationBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            pluginPresentationBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            pluginPresentationBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            pluginRuntimeBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            pluginRuntimeBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            pluginRuntimeBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            chatPresentationBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            chatPresentationBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            chatPresentationBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            cronDataBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            cronDataBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            cronDataBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            cronPresentationBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            cronPresentationBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            cronPresentationBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            cronRuntimeBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            cronRuntimeBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            cronRuntimeBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            qqDataBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            qqDataBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            qqDataBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            qqPresentationBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            qqPresentationBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            qqPresentationBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            qqRuntimeBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            qqRuntimeBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            qqRuntimeBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
            coreUiBuildDir.dir("tmp/kotlin-classes/debug").get().asFile.absolutePath,
            coreUiBuildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar").get().asFile.absolutePath,
            coreUiBuildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar").get().asFile.absolutePath,
        ).joinToString(",")
        compilerOptions.freeCompilerArgs.add("-Xfriend-paths=$pluginImplFriendPaths")
    }
}

val coreRuntimeModulePrefix = ":core:runtime-"
val appUnitTestRuntimeProjects = listOf(
    ":app-integration",
    ":core:backup",
    ":core:common",
    ":core:db",
    ":core:logging",
    ":core:network",
    ":core:runtime",
    coreRuntimeModulePrefix + "audio",
    ":core:runtime-cache",
    coreRuntimeModulePrefix + "container",
    ":core:runtime-llm",
    ":core:runtime-search",
    ":core:runtime-secret",
    ":core:runtime-session",
    ":core:ui",
    ":download:api",
    ":download:impl",
    ":feature:bot:api",
    ":feature:bot:data",
    ":feature:bot:impl",
    ":feature:bot:presentation",
    ":feature:chat:api",
    ":feature:chat:impl",
    ":feature:chat:presentation",
    ":feature:chat:runtime",
    ":feature:config:api",
    ":feature:config:data",
    ":feature:config:impl",
    ":feature:config:presentation",
    ":feature:conversation:api",
    ":feature:conversation:data",
    ":feature:cron:api",
    ":feature:cron:data",
    ":feature:cron:impl",
    ":feature:cron:presentation",
    ":feature:cron:runtime",
    ":feature:persona:api",
    ":feature:persona:data",
    ":feature:persona:impl",
    ":feature:persona:presentation",
    ":feature:plugin:api",
    ":feature:plugin:data",
    ":feature:plugin:impl",
    ":feature:plugin:presentation",
    ":feature:plugin:runtime",
    ":feature:provider:api",
    ":feature:provider:data",
    ":feature:provider:impl",
    ":feature:provider:presentation",
    ":feature:provider:runtime",
    ":feature:qq:api",
    ":feature:qq:data",
    ":feature:qq:impl",
    ":feature:qq:presentation",
    ":feature:qq:runtime",
    ":feature:resource:api",
    ":feature:resource:data",
    ":feature:resource:impl",
    ":feature:resource:presentation",
    ":feature:settings:api",
    ":feature:settings:presentation",
    ":feature:voiceasset:api",
    ":feature:voiceasset:data",
    ":feature:voiceasset:presentation",
)

tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest") {
        appUnitTestRuntimeProjects.forEach { path ->
            dependsOn("$path:compileDebugKotlin")
            dependsOn("$path:bundleLibCompileToJarDebug")
            dependsOn("$path:bundleLibRuntimeToJarDebug")
        }
        val runtimeOutputFiles = appUnitTestRuntimeProjects.flatMap { path ->
            val buildDir = project(path).layout.buildDirectory
            listOf(
                buildDir.dir("tmp/kotlin-classes/debug").get().asFile,
                buildDir.file("intermediates/compile_library_classes_jar/debug/bundleLibCompileToJarDebug/classes.jar")
                    .get()
                    .asFile,
                buildDir.file("intermediates/runtime_library_classes_jar/debug/bundleLibRuntimeToJarDebug/classes.jar")
                    .get()
                    .asFile,
            ).map { it.absoluteFile }
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
    testImplementation(project(":feature:bot:data"))
    testImplementation(project(":feature:chat:runtime"))
    testImplementation(project(":feature:config:data"))
    testImplementation(project(":feature:conversation:data"))
    testImplementation(project(":feature:cron:data"))
    testImplementation(project(":feature:cron:runtime"))
    testImplementation(project(":feature:persona:data"))
    testImplementation(project(":feature:plugin:data"))
    testImplementation(project(":feature:plugin:runtime"))
    testImplementation(project(":feature:provider:data"))
    testImplementation(project(":feature:qq:data"))
    testImplementation(project(":feature:qq:runtime"))
    testImplementation(project(":feature:resource:data"))
    testImplementation(project(":feature:voiceasset:data"))
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

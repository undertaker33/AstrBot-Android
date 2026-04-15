plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

import java.util.Properties

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
    namespace = "com.astrbot.android"
    compileSdk = 34

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
        applicationId = "com.astrbot.android"
        minSdk = 29
        targetSdk = 34
        versionCode = 38
        versionName = "0.6.0"

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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
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
            jniLibs.srcDirs("src/main/jniLibs")
            assets.setSrcDirs(listOf(filteredAssetsDir))
        }
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
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

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
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

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    val okHttpVersion = "4.12.0"
    val quickJsVersion = "3.2.3"
    val roomVersion = "2.6.1"

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

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("com.google.android.material:material:1.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okHttpVersion")
    testImplementation("org.json:json:20240303")
    testImplementation("wang.harlon.quickjs:wrapper-java:$quickJsVersion")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
}

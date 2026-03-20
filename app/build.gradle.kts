plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

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

val branchApkDirName = sanitizeBranchName(currentGitBranchName())

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
)

val prepareFilteredAssets by tasks.registering(Sync::class) {
    from("src/main/assets")
    into(filteredAssetsDir)
    exclude(excludedRuntimeAssets)
}

android {
    namespace = "com.astrbot.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.astrbot.android"
        minSdk = 29
        targetSdk = 34
        versionCode = 8
        versionName = "0.2.8"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    }

    androidResources {
        noCompress += setOf("xz", "onnx", "bin", "fst", "txt", "utf8")
    }
}

tasks.matching { it.name == "mergeDebugAssets" || it.name == "mergeReleaseAssets" }.configureEach {
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

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
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
    implementation(files("libs/sherpa-onnx-1.12.31-static-jni-only.aar"))

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.google.android.material:material:1.12.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

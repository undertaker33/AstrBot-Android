plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.elymbot.android.feature.config.presentation"

    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    val hiltVersion = "2.59.2"
    val androidxHiltVersion = "1.2.0"

    implementation(project(":core:common"))
    implementation(project(":core:runtime-llm"))
    implementation(project(":core:ui"))
    implementation(project(":feature:bot:api"))
    implementation(project(":feature:chat:api"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:conversation:api"))
    implementation(project(":feature:geofence:api"))
    implementation(project(":feature:provider:api"))
    implementation(project(":feature:resource:api"))
    implementation(project(":feature:voiceasset:api"))

    implementation(composeBom)
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.hilt:hilt-navigation-compose:$androidxHiltVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-compiler:$hiltVersion")
}

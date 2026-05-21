plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.plugin.runtime"
}

dependencies {
    val quickJsVersion = "3.2.3"
    val okHttpVersion = "4.12.0"

    implementation(project(":core:common"))
    implementation(project(":core:logging"))
    implementation(project(":core:network"))
    implementation(project(":core:runtime"))
    implementation(project(":core:runtime-context"))
    implementation(project(":core:runtime-search"))
    implementation(project(":core:runtime-tool"))
    implementation(project(":download:api"))
    implementation(project(":feature:config:api"))
    implementation(project(":feature:conversation:api"))
    implementation(project(":feature:cron:api"))
    implementation(project(":feature:persona:api"))
    implementation(project(":feature:plugin:api"))
    implementation(project(":feature:plugin:data"))
    implementation(project(":feature:resource:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("wang.harlon.quickjs:wrapper-android:$quickJsVersion")
    implementation("javax.inject:javax.inject:1")
    implementation("org.json:json:20240303")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.json:json:20240303")
    testImplementation("com.squareup.okhttp3:mockwebserver:$okHttpVersion")
    testImplementation("wang.harlon.quickjs:wrapper-java:$quickJsVersion")
}

plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.geofence.data"
}

dependencies {
    implementation(project(":core:db"))
    implementation(project(":feature:geofence:api"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("javax.inject:javax.inject:1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.room:room-runtime:2.8.4")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

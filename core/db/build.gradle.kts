plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.elymbot.android.core.db"
}

dependencies {
    val roomVersion = "2.8.4"

    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.room:room-testing:$roomVersion")
    androidTestImplementation("androidx.test:core:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

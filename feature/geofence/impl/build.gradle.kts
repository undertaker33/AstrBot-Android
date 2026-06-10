plugins {
    id("com.android.library")
}

android {
    namespace = "com.elymbot.android.feature.geofence.impl"
}

dependencies {
    api(project(":feature:geofence:api"))
    api(project(":feature:geofence:data"))

    testImplementation("junit:junit:4.13.2")
}

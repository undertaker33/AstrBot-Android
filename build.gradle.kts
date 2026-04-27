import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application") version "8.13.2" apply false
    id("com.android.library") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    id("com.google.dagger.hilt.android") version "2.52" apply false
}

project(":app") {
    plugins.withId("com.android.application") {
        afterEvaluate {
            val debugUnitTest = tasks.named<Test>("testDebugUnitTest")

            tasks.register<Test>("architectureDebugUnitTest") {
                group = "verification"
                description = "Runs ElymBot source-level architecture contracts and startup hotspot guardrails."

                testClassesDirs = debugUnitTest.get().testClassesDirs
                classpath = debugUnitTest.get().classpath
                shouldRunAfter(debugUnitTest)

                filter {
                    includeTestsMatching("com.astrbot.android.architecture.*")
                    includeTestsMatching("com.astrbot.android.di.startup.StrictHiltOnlyStartupHotspotSourceTest")
                }
            }
        }
    }
}

tasks.register("architectureCheck") {
    group = "verification"
    description = "Runs ElymBot source-level architecture contracts and startup hotspot guardrails."

    dependsOn(":app:architectureDebugUnitTest")
}

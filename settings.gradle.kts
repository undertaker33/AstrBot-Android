pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "AstrBotAndroidNative"
include(":app")
include(":core:common")
include(":feature:bot:api")
include(":feature:config:api")
include(":feature:cron:api")
include(":feature:persona:api")
include(":feature:plugin:api")
include(":feature:provider:api")
include(":feature:resource:api")

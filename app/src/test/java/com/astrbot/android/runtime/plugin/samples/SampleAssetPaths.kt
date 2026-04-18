package com.astrbot.android.feature.plugin.runtime.samples

import java.io.File

internal object SampleAssetPaths {
    private fun artifactRoot(name: String): File {
        return File(findRepoRoot(), "artifacts/plugins/$name")
    }

    fun templatePackageZip(): File {
        return File(artifactRoot("template-sample"), "packages/astrbot_plugin_template.zip")
    }

    private val rootDir: File by lazy {
        artifactRoot("meme-manager-sample")
    }

    val catalogFixture: File by lazy {
        File(rootDir, "catalog/meme-manager.sample.catalog.json")
    }

    fun packageZip(version: String): File {
        return File(rootDir, "packages/meme-manager-$version.zip")
    }

    fun greetingToolkitPackageZip(version: String): File {
        return File(artifactRoot("greeting-toolkit-sample"), "packages/greeting-toolkit-$version.zip")
    }
}

private fun findRepoRoot(): File {
    var dir = File(System.getProperty("user.dir") ?: ".")
    repeat(10) {
        if (File(dir, "settings.gradle.kts").exists() || File(dir, "settings.gradle").exists()) {
            return dir
        }
        dir = dir.parentFile ?: return dir
    }
    return dir
}

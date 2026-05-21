package com.elymbot.android.update

import org.json.JSONObject

private const val APK_EXTENSION = ".apk"
private const val DEBUG_ASSET_TOKEN = "debug"
private const val RELEASE_ASSET_TOKEN = "release"

internal fun parseAppUpdateRelease(json: String): AppUpdateReleaseInfo {
    val root = JSONObject(json)
    val assetsJson = root.optJSONArray("assets")
    val assets = buildList {
        if (assetsJson != null) {
            for (index in 0 until assetsJson.length()) {
                val asset = assetsJson.optJSONObject(index) ?: continue
                add(
                    AppUpdateAsset(
                        name = asset.optString("name"),
                        browserDownloadUrl = asset.optString("browser_download_url"),
                        sizeBytes = asset.optLongOrNull("size"),
                        contentType = asset.optString("content_type"),
                        state = asset.optString("state"),
                        digest = asset.optString("digest").ifBlank { null },
                    ),
                )
            }
        }
    }
    return AppUpdateReleaseInfo(
        tagName = root.optString("tag_name"),
        name = root.optString("name"),
        htmlUrl = root.optString("html_url"),
        draft = root.optBoolean("draft", false),
        prerelease = root.optBoolean("prerelease", false),
        publishedAt = root.optString("published_at"),
        assets = assets,
    )
}

internal fun selectAppUpdateAsset(
    release: AppUpdateReleaseInfo,
    track: AppUpdateBuildTrack,
): AppUpdateAsset? {
    val apkAssets = release.assets
        .filter { asset ->
            asset.state.equals("uploaded", ignoreCase = true) &&
                asset.name.endsWith(APK_EXTENSION, ignoreCase = true) &&
                asset.browserDownloadUrl.startsWith("https://", ignoreCase = true)
        }
        .sortedWith(compareBy<AppUpdateAsset> { it.name.lowercase() }.thenBy { it.browserDownloadUrl })

    return when (track) {
        AppUpdateBuildTrack.DEBUG -> apkAssets.firstOrNull { asset ->
            asset.name.contains(DEBUG_ASSET_TOKEN, ignoreCase = true)
        }

        AppUpdateBuildTrack.RELEASE -> {
            apkAssets.firstOrNull { asset ->
                asset.name.contains(RELEASE_ASSET_TOKEN, ignoreCase = true) &&
                    !asset.name.contains(DEBUG_ASSET_TOKEN, ignoreCase = true)
            }
        }
    }
}

internal fun shouldOfferAppUpdate(
    release: AppUpdateReleaseInfo,
    currentVersionName: String,
    suppression: AppUpdateSuppression,
    nowEpochMillis: Long,
): Boolean {
    if (release.tagName.isBlank()) return false
    if (release.draft || release.prerelease) return false
    if (suppression.ignoredTagName == release.tagName) return false
    if (
        suppression.snoozedTagName == release.tagName &&
        suppression.remindAfterEpochMillis > nowEpochMillis
    ) return false
    return compareVersionTags(release.tagName, currentVersionName) > 0
}

internal fun isInstalledVersionAtLeastRelease(
    installedVersionName: String,
    releaseTagName: String,
): Boolean {
    return compareVersionTags(installedVersionName, releaseTagName) >= 0
}

internal fun sanitizeUpdatePathSegment(value: String): String {
    return value
        .replace(Regex("[^A-Za-z0-9._-]+"), "_")
        .trim('_')
        .ifBlank { "release" }
}

private fun compareVersionTags(left: String, right: String): Int {
    val leftParts = numericVersionParts(left)
    val rightParts = numericVersionParts(right)
    if (leftParts.isNotEmpty() && rightParts.isNotEmpty()) {
        val maxSize = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until maxSize) {
            val leftPart = leftParts.getOrElse(index) { 0 }
            val rightPart = rightParts.getOrElse(index) { 0 }
            if (leftPart != rightPart) return leftPart.compareTo(rightPart)
        }
        return 0
    }
    return normalizeVersionLabel(left).compareTo(normalizeVersionLabel(right), ignoreCase = true)
}

private fun numericVersionParts(value: String): List<Int> {
    val cleaned = normalizeVersionLabel(value)
    return Regex("""\d+""")
        .findAll(cleaned)
        .mapNotNull { match -> match.value.toIntOrNull() }
        .toList()
}

private fun normalizeVersionLabel(value: String): String {
    return value.trim().removePrefix("v").removePrefix("V")
}

private fun JSONObject.optLongOrNull(name: String): Long? {
    if (!has(name) || isNull(name)) return null
    val value = optLong(name, -1L)
    return value.takeIf { it >= 0L }
}

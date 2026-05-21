package com.elymbot.android.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {

    @Test
    fun `release json parser reads apk assets and selects current build track`() {
        val release = parseAppUpdateRelease(sampleReleaseJson(tagName = "v1.0.1"))

        assertEquals("v1.0.1", release.tagName)
        assertEquals("https://github.com/undertaker33/ElymBot/releases/tag/v1.0.1", release.htmlUrl)
        assertEquals("app-debug.apk", selectAppUpdateAsset(release, AppUpdateBuildTrack.DEBUG)?.name)
        assertEquals("app-release.apk", selectAppUpdateAsset(release, AppUpdateBuildTrack.RELEASE)?.name)
    }

    @Test
    fun `release track ignores debug only apk assets`() {
        val release = parseAppUpdateRelease(
            sampleReleaseJson(
                tagName = "v1.0.1",
                assets = """
                    [
                      {
                        "name": "app-debug.apk",
                        "browser_download_url": "https://github.com/undertaker33/ElymBot/releases/download/v1.0.1/app-debug.apk",
                        "state": "uploaded",
                        "size": 123,
                        "content_type": "application/vnd.android.package-archive"
                      }
                    ]
                """.trimIndent(),
            ),
        )

        assertEquals("app-debug.apk", selectAppUpdateAsset(release, AppUpdateBuildTrack.DEBUG)?.name)
        assertNull(selectAppUpdateAsset(release, AppUpdateBuildTrack.RELEASE))
    }

    @Test
    fun `release track does not fallback to non debug apk without release token`() {
        val release = parseAppUpdateRelease(
            sampleReleaseJson(
                tagName = "v1.0.1",
                assets = """
                    [
                      {
                        "name": "app-universal.apk",
                        "browser_download_url": "https://github.com/undertaker33/ElymBot/releases/download/v1.0.1/app-universal.apk",
                        "state": "uploaded",
                        "size": 123,
                        "content_type": "application/vnd.android.package-archive"
                      }
                    ]
                """.trimIndent(),
            ),
        )

        assertNull(selectAppUpdateAsset(release, AppUpdateBuildTrack.RELEASE))
    }

    @Test
    fun `prompt policy respects version tag scoped snooze and ignored tag`() {
        val release = parseAppUpdateRelease(sampleReleaseJson(tagName = "v1.0.1"))
        val now = 1_000L

        assertTrue(
            shouldOfferAppUpdate(
                release = release,
                currentVersionName = "1.0.0",
                suppression = AppUpdateSuppression(
                    ignoredTagName = null,
                    snoozedTagName = null,
                    remindAfterEpochMillis = 0L,
                ),
                nowEpochMillis = now,
            ),
        )
        assertFalse(
            shouldOfferAppUpdate(
                release = release,
                currentVersionName = "1.0.0",
                suppression = AppUpdateSuppression(
                    ignoredTagName = null,
                    snoozedTagName = "v1.0.1",
                    remindAfterEpochMillis = now + 1L,
                ),
                nowEpochMillis = now,
            ),
        )
        assertTrue(
            "A new latest tag must not be suppressed by the previous tag snooze",
            shouldOfferAppUpdate(
                release = release.copy(tagName = "v1.0.2"),
                currentVersionName = "1.0.0",
                suppression = AppUpdateSuppression(
                    ignoredTagName = null,
                    snoozedTagName = "v1.0.1",
                    remindAfterEpochMillis = now + 1L,
                ),
                nowEpochMillis = now,
            ),
        )
        assertFalse(
            shouldOfferAppUpdate(
                release = release,
                currentVersionName = "1.0.0",
                suppression = AppUpdateSuppression(
                    ignoredTagName = "v1.0.1",
                    snoozedTagName = null,
                    remindAfterEpochMillis = 0L,
                ),
                nowEpochMillis = now,
            ),
        )
        assertTrue(
            "A new latest tag must not be ignored by the previous tag ignore",
            shouldOfferAppUpdate(
                release = release.copy(tagName = "v1.0.2"),
                currentVersionName = "1.0.0",
                suppression = AppUpdateSuppression(
                    ignoredTagName = "v1.0.1",
                    snoozedTagName = null,
                    remindAfterEpochMillis = 0L,
                ),
                nowEpochMillis = now,
            ),
        )
        assertFalse(
            shouldOfferAppUpdate(
                release = release.copy(tagName = "v1.0.0"),
                currentVersionName = "1.0.0",
                suppression = AppUpdateSuppression(
                    ignoredTagName = null,
                    snoozedTagName = null,
                    remindAfterEpochMillis = 0L,
                ),
                nowEpochMillis = now,
            ),
        )
        assertFalse(
            shouldOfferAppUpdate(
                release = release.copy(tagName = "v0.9.9"),
                currentVersionName = "1.0.0",
                suppression = AppUpdateSuppression(
                    ignoredTagName = null,
                    snoozedTagName = null,
                    remindAfterEpochMillis = 0L,
                ),
                nowEpochMillis = now,
            ),
        )
    }

    @Test
    fun `installed cleanup version comparison accepts normalized release tags`() {
        assertTrue(isInstalledVersionAtLeastRelease("1.0.1", "v1.0.1"))
        assertTrue(isInstalledVersionAtLeastRelease("1.0.2", "v1.0.1"))
        assertFalse(isInstalledVersionAtLeastRelease("1.0.0", "v1.0.1"))
    }

    private fun sampleReleaseJson(
        tagName: String,
        assets: String = """
            [
              {
                "name": "app-debug.apk",
                "browser_download_url": "https://github.com/undertaker33/ElymBot/releases/download/$tagName/app-debug.apk",
                "state": "uploaded",
                "size": 189886609,
                "content_type": "application/vnd.android.package-archive",
                "digest": "sha256:debug"
              },
              {
                "name": "app-release.apk",
                "browser_download_url": "https://github.com/undertaker33/ElymBot/releases/download/$tagName/app-release.apk",
                "state": "uploaded",
                "size": 111035163,
                "content_type": "application/vnd.android.package-archive",
                "digest": "sha256:release"
              }
            ]
        """.trimIndent(),
    ): String {
        return """
            {
              "tag_name": "$tagName",
              "name": "$tagName",
              "html_url": "https://github.com/undertaker33/ElymBot/releases/tag/$tagName",
              "draft": false,
              "prerelease": false,
              "published_at": "2026-05-20T10:25:41Z",
              "assets": $assets
            }
        """.trimIndent()
    }
}

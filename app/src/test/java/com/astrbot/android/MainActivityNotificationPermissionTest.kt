package com.astrbot.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityNotificationPermissionTest {
    @Test
    fun `notification permission is requested on android 13 and above when not granted`() {
        assertTrue(
            shouldRequestNotificationPermissionForTests(
                sdkInt = 33,
                permissionGranted = false,
            ),
        )
        assertTrue(
            shouldRequestNotificationPermissionForTests(
                sdkInt = 34,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `notification permission is not requested below android 13`() {
        assertFalse(
            shouldRequestNotificationPermissionForTests(
                sdkInt = 32,
                permissionGranted = false,
            ),
        )
    }

    @Test
    fun `notification permission is not requested when already granted`() {
        assertFalse(
            shouldRequestNotificationPermissionForTests(
                sdkInt = 34,
                permissionGranted = true,
            ),
        )
    }
}

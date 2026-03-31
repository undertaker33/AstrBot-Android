package com.astrbot.android.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileDeletionGuardTest {

    @Test
    fun `cannot delete the last remaining profile`() {
        assertFalse(ProfileDeletionGuard.canDelete(remainingCount = 1))
        assertTrue(ProfileDeletionGuard.canDelete(remainingCount = 2))
    }
}

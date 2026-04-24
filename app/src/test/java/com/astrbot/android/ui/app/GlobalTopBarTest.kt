package com.astrbot.android.ui.app

import com.astrbot.android.ui.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalTopBarTest {

    @Test
    fun `cron jobs route has secondary top bar fallback`() {
        val spec = fallbackSecondaryTopBarSpecForRoute(
            route = AppDestination.CronJobs.route,
            strings = SecondaryTopBarStrings(
                config = "Config",
                logs = "Logs",
                qqAccount = "QQ",
                qqLogin = "Login",
                settings = "Settings",
                assetManagement = "Assets",
                pluginDetail = "Plugin",
                pluginWorkspace = "Workspace",
                pluginConfig = "Plugin Config",
                models = "Models",
                runtime = "Runtime",
                dataBackup = "Backup",
                cronJobs = "Scheduled Tasks",
                configDetailDefaultSection = "Basic",
            ),
        )

        assertTrue(spec is SecondaryTopBarSpec.SubPage)
        assertEquals("Scheduled Tasks", (spec as SecondaryTopBarSpec.SubPage).title)
    }
}

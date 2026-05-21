package com.elymbot.android.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

internal class AppUpdateCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        AppUpdateLocalStore(context).cleanupInstalledUpdate(context.currentAppVersionName())
    }
}

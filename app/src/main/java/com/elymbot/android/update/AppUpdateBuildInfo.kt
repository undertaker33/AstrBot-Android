package com.elymbot.android.update

import android.content.Context
import android.content.pm.ApplicationInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class AppUpdateBuildInfo @Inject constructor(
    @ApplicationContext private val appContext: Context,
) {
    val versionName: String
        get() = appContext.currentAppVersionName()

    val versionCode: Int
        get() = appContext.currentAppVersionCode()

    val track: AppUpdateBuildTrack
        get() = if (appContext.isAppDebuggable()) {
            AppUpdateBuildTrack.DEBUG
        } else {
            AppUpdateBuildTrack.RELEASE
        }
}

internal fun Context.currentAppVersionName(): String {
    return packageInfo().versionName.orEmpty()
}

private fun Context.currentAppVersionCode(): Int {
    return AppUpdatePackageInfoCompat.versionCode(packageInfo())
}

private fun Context.isAppDebuggable(): Boolean {
    return applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
}

private fun Context.packageInfo() = AppUpdatePackageInfoCompat.packageInfo(this)

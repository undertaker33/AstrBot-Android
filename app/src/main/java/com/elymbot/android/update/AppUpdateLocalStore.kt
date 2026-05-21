package com.elymbot.android.update

import android.content.Context
import java.io.File

private const val APP_UPDATE_PREFS = "elymbot_app_updates"
private const val KEY_IGNORED_TAG = "ignored_tag"
private const val KEY_SNOOZED_TAG = "snoozed_tag"
private const val KEY_REMIND_AFTER = "remind_after"
private const val KEY_PENDING_CLEANUP_TAG = "pending_cleanup_tag"
private const val KEY_PENDING_CLEANUP_FILE = "pending_cleanup_file"
private const val DAY_IN_MILLIS = 24L * 60L * 60L * 1000L

internal class AppUpdateLocalStore(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(APP_UPDATE_PREFS, Context.MODE_PRIVATE)

    fun suppression(): AppUpdateSuppression {
        return AppUpdateSuppression(
            ignoredTagName = prefs.getString(KEY_IGNORED_TAG, null),
            snoozedTagName = prefs.getString(KEY_SNOOZED_TAG, null),
            remindAfterEpochMillis = prefs.getLong(KEY_REMIND_AFTER, 0L),
        )
    }

    fun snooze(candidate: AppUpdateCandidate, nowEpochMillis: Long) {
        prefs.edit()
            .putString(KEY_SNOOZED_TAG, candidate.release.tagName)
            .putLong(KEY_REMIND_AFTER, nowEpochMillis + DAY_IN_MILLIS)
            .apply()
    }

    fun ignore(candidate: AppUpdateCandidate) {
        prefs.edit()
            .putString(KEY_IGNORED_TAG, candidate.release.tagName)
            .remove(KEY_SNOOZED_TAG)
            .remove(KEY_REMIND_AFTER)
            .apply()
    }

    fun markPendingCleanup(candidate: AppUpdateCandidate, file: File) {
        prefs.edit()
            .putString(KEY_PENDING_CLEANUP_TAG, candidate.release.tagName)
            .putString(KEY_PENDING_CLEANUP_FILE, file.absolutePath)
            .apply()
    }

    fun cleanupInstalledUpdate(currentVersionName: String) {
        val pendingTag = prefs.getString(KEY_PENDING_CLEANUP_TAG, null)?.takeIf { it.isNotBlank() }
        val pendingFile = prefs.getString(KEY_PENDING_CLEANUP_FILE, null)?.takeIf { it.isNotBlank() }
        if (pendingTag == null || pendingFile == null) return
        if (!isInstalledVersionAtLeastRelease(currentVersionName, pendingTag)) return

        File(pendingFile).delete()
        File(pendingFile).parentFile?.deleteIfEmpty()
        prefs.edit()
            .remove(KEY_PENDING_CLEANUP_TAG)
            .remove(KEY_PENDING_CLEANUP_FILE)
            .apply()
    }

    private fun File.deleteIfEmpty() {
        if (isDirectory && listFiles()?.isEmpty() == true) {
            delete()
        }
    }
}

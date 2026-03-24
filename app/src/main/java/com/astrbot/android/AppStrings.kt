package com.astrbot.android

import android.content.Context
import androidx.annotation.StringRes

object AppStrings {
    @Volatile
    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    fun get(@StringRes resId: Int, vararg formatArgs: Any): String {
        val context = appContext ?: return ""
        return if (formatArgs.isEmpty()) {
            context.getString(resId)
        } else {
            context.getString(resId, *formatArgs)
        }
    }
}

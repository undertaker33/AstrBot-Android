package com.astrbot.android

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.StringRes
import java.util.Locale

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

    fun getForLanguageTag(
        languageTag: String,
        @StringRes resId: Int,
        vararg formatArgs: Any,
    ): String {
        val context = appContext ?: return ""
        val locale = Locale.forLanguageTag(languageTag.ifBlank { Locale.getDefault().toLanguageTag() })
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(locale)
        }
        val localizedContext = context.createConfigurationContext(configuration)
        return if (formatArgs.isEmpty()) {
            localizedContext.getString(resId)
        } else {
            localizedContext.getString(resId, *formatArgs)
        }
    }
}

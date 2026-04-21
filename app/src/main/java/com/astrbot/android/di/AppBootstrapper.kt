package com.astrbot.android.di

import android.util.Log
import com.astrbot.android.di.startup.AppStartupRunner
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

internal class AppBootstrapper @Inject constructor(
    private val appStartupRunner: AppStartupRunner,
) {
    private val bootstrapped = AtomicBoolean(false)

    fun bootstrap() {
        if (!bootstrapped.compareAndSet(false, true)) return
        Log.i("AstrBotRuntime", "AppBootstrapper.bootstrap entered")
        appStartupRunner.run()
        Log.i("AstrBotRuntime", "AppBootstrapper.bootstrap completed")
    }
}

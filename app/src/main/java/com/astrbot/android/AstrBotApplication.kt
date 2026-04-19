package com.astrbot.android

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.astrbot.android.di.AppBootstrapper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AstrBotApplication : Application(), Configuration.Provider {
    @Inject
    internal lateinit var appBootstrapper: AppBootstrapper

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i("AstrBotRuntime", "AstrBotApplication.onCreate entered")
        appBootstrapper.bootstrap()
        Log.i("AstrBotRuntime", "AstrBotApplication.onCreate completed")
    }
}

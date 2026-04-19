package com.astrbot.android

import android.app.Application
import android.util.Log
import com.astrbot.android.di.ElymBotAppContainer

class AstrBotApplication : Application() {
    lateinit var appContainer: ElymBotAppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        Log.i("AstrBotRuntime", "AstrBotApplication.onCreate entered")
        appContainer = ElymBotAppContainer(this)
        appContainer.bootstrap()
        Log.i("AstrBotRuntime", "AstrBotApplication.onCreate completed")
    }
}

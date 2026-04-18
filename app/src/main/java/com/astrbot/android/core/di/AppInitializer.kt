package com.astrbot.android.core.di

import android.content.Context

interface AppInitializer {
    val key: String
    val dependencies: Set<String>
    fun initialize(context: Context)
}

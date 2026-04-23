package com.astrbot.android.feature.bot.data

import android.content.Context
import com.astrbot.android.core.di.AppInitializer
import javax.inject.Inject

class BotRepositoryInitializer @Inject constructor(
    @Suppress("unused") private val repository: FeatureBotRepositoryStore,
) : AppInitializer {
    override val key: String = "bot"
    override val dependencies: Set<String> = setOf("config")

    override fun initialize(context: Context) = Unit
}




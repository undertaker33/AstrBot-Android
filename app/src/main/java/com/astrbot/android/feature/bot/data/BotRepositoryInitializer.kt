package com.astrbot.android.feature.bot.data

import android.content.Context
import com.astrbot.android.core.di.AppInitializer
import com.astrbot.android.feature.bot.data.FeatureBotRepository

class BotRepositoryInitializer : AppInitializer {
    override val key: String = "bot"
    override val dependencies: Set<String> = setOf("config")

    override fun initialize(context: Context) {
        FeatureBotRepository.initialize(context)
    }
}




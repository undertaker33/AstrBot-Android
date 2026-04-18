package com.astrbot.android.core.runtime.secret

import android.content.Context

object AppSecretStore {
    fun initialize(context: Context) {
        RuntimeSecretRepository.initialize(context)
    }

    fun getOrCreateWebUiToken(): String {
        return RuntimeSecretRepository.getOrCreateWebUiToken()
    }
}

@file:Suppress("DEPRECATION")

package com.astrbot.android.core.runtime.secret

import android.content.Context

object AppSecretStore {
    fun initialize(context: Context) {
        RuntimeSecretRepository.ensureSecrets(context.filesDir)
    }

    fun getOrCreateWebUiToken(): String {
        return RuntimeSecretRepository.getOrCreateWebUiToken()
    }
}

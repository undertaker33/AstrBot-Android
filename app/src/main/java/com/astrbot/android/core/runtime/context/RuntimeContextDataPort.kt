package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ResourceCenterCompatibilitySnapshot
import com.astrbot.android.model.chat.ConversationSession

interface RuntimeContextDataPort {
    fun resolveConfig(configProfileId: String): ConfigProfile
    fun listProviders(): List<ProviderProfile>
    fun findEnabledPersona(personaId: String): PersonaProfile?
    fun session(sessionId: String): ConversationSession
    fun compatibilitySnapshotForConfig(config: ConfigProfile): ResourceCenterCompatibilitySnapshot
}

object RuntimeContextDataRegistry {
    @Volatile
    var port: RuntimeContextDataPort = MissingRuntimeContextDataPort
}

private object MissingRuntimeContextDataPort : RuntimeContextDataPort {
    override fun resolveConfig(configProfileId: String): ConfigProfile = error("RuntimeContextDataPort is not configured")

    override fun listProviders(): List<ProviderProfile> = error("RuntimeContextDataPort is not configured")

    override fun findEnabledPersona(personaId: String): PersonaProfile? = error("RuntimeContextDataPort is not configured")

    override fun session(sessionId: String): ConversationSession = error("RuntimeContextDataPort is not configured")

    override fun compatibilitySnapshotForConfig(config: ConfigProfile): ResourceCenterCompatibilitySnapshot {
        error("RuntimeContextDataPort is not configured")
    }
}

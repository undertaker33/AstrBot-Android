package com.astrbot.android.data.db.provider

import com.astrbot.android.data.db.ProviderAggregate
import com.astrbot.android.data.db.ProviderCapabilityEntity
import com.astrbot.android.data.db.ProviderEntity
import com.astrbot.android.data.db.ProviderTtsVoiceOptionEntity
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderMappersTest {
    @Test
    fun aggregate_toProfile_restoresCapabilitiesAndVoiceOptions() {
        val profile = ProviderAggregate(
            provider = ProviderEntity("p1", "P", "url", "model", ProviderType.OPENAI_COMPATIBLE.name, "", true, "SUPPORTED", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", "UNKNOWN", 0, 1L),
            capabilities = listOf(ProviderCapabilityEntity("p1", ProviderCapability.CHAT.name)),
            ttsVoiceOptions = listOf(ProviderTtsVoiceOptionEntity("p1", "voice-a", 0)),
        ).toProfile()

        assertEquals(setOf(ProviderCapability.CHAT), profile.capabilities)
        assertEquals(listOf("voice-a"), profile.ttsVoiceOptions)
    }

    @Test
    fun profile_toWriteModel_flattensCollections() {
        val writeModel = ProviderProfile(
            id = "p1",
            name = "P",
            baseUrl = "url",
            model = "model",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.UNKNOWN,
        ).copy(ttsVoiceOptions = listOf("voice-a")).toWriteModel(sortIndex = 0)

        assertEquals(1, writeModel.capabilities.size)
        assertEquals(1, writeModel.ttsVoiceOptions.size)
    }
}

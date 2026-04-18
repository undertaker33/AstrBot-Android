package com.astrbot.android.feature.provider.data

import kotlinx.coroutines.flow.collect

import android.content.Context
import android.content.SharedPreferences
import com.astrbot.android.data.parseLegacyProviderProfiles
import com.astrbot.android.data.db.AstrBotDatabase
import com.astrbot.android.data.db.ProviderAggregate
import com.astrbot.android.data.db.ProviderAggregateDao
import com.astrbot.android.data.db.ProviderEntity
import com.astrbot.android.data.db.ProviderWriteModel
import com.astrbot.android.data.db.toProfile
import com.astrbot.android.data.db.toWriteModel
import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.defaultCapability
import com.astrbot.android.model.inferMultimodalRuleSupport
import com.astrbot.android.model.inferNativeStreamingRuleSupport
import com.astrbot.android.core.common.logging.AppLogger
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Deprecated("Use ProviderRepositoryPort from feature/provider/domain. Direct access will be removed.")
object FeatureProviderRepository {
    private const val PREFS_NAME = "provider_profiles"
    private const val KEY_PROVIDERS_JSON = "providers_json"

    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = AtomicBoolean(false)

    private var preferences: SharedPreferences? = null
    private var providerDao: ProviderAggregateDao = ProviderDaoPlaceholder.instance
    private val _providers = MutableStateFlow(defaultProviders())

    val providers: StateFlow<List<ProviderProfile>> = _providers.asStateFlow()

    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        preferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        providerDao = AstrBotDatabase.get(context).providerAggregateDao()

        runBlocking(Dispatchers.IO) {
            seedStorageIfNeeded()
        }
        repositoryScope.launch {
            providerDao.observeProviderAggregates().collect { aggregates ->
                val loaded = aggregates.map { aggregate -> normalizeProvider(aggregate.toProfile()) }.ifEmpty { defaultProviders() }
                _providers.value = loaded
                AppLogger.append("Provider catalog loaded: count=${loaded.size}")
            }
        }
    }

    fun save(
        id: String?,
        name: String,
        baseUrl: String,
        model: String,
        providerType: ProviderType,
        apiKey: String,
        capabilities: Set<ProviderCapability>,
        enabled: Boolean,
        multimodalRuleSupport: FeatureSupportState,
        multimodalProbeSupport: FeatureSupportState,
        nativeStreamingRuleSupport: FeatureSupportState,
        nativeStreamingProbeSupport: FeatureSupportState,
        sttProbeSupport: FeatureSupportState,
        ttsProbeSupport: FeatureSupportState,
        ttsVoiceOptions: List<String>,
    ): ProviderProfile {
        val resolvedId = id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val profile = normalizeProvider(
            ProviderProfile(
                id = resolvedId,
                name = name.trim(),
                baseUrl = baseUrl.trim(),
                model = model.trim(),
                providerType = providerType,
                apiKey = apiKey.trim(),
                capabilities = capabilities,
                enabled = enabled,
                multimodalRuleSupport = multimodalRuleSupport,
                multimodalProbeSupport = multimodalProbeSupport,
                nativeStreamingRuleSupport = nativeStreamingRuleSupport,
                nativeStreamingProbeSupport = nativeStreamingProbeSupport,
                sttProbeSupport = sttProbeSupport,
                ttsProbeSupport = ttsProbeSupport,
                ttsVoiceOptions = ttsVoiceOptions.map(String::trim).filter(String::isNotBlank).distinct(),
            ),
        )
        val exists = _providers.value.any { it.id == resolvedId }
        val updated = if (exists) {
            _providers.value.map { item -> if (item.id == resolvedId) profile else item }
        } else {
            _providers.value + profile
        }
        _providers.value = updated
        persistProviders(updated)
        AppLogger.append(
            if (exists) {
                "Provider updated: ${profile.name} (${profile.providerType.name}, key=${maskState(profile.apiKey)})"
            } else {
                "Provider added: ${profile.name} (${profile.providerType.name}, key=${maskState(profile.apiKey)})"
            },
        )
        return profile
    }

    fun toggleEnabled(id: String) {
        val updated = _providers.value.map { item ->
            if (item.id == id) item.copy(enabled = !item.enabled) else item
        }
        _providers.value = updated
        persistProviders(updated)
        updated.firstOrNull { it.id == id }?.let { provider ->
            AppLogger.append("Provider toggled: ${provider.name} enabled=${provider.enabled}")
        }
    }

    fun delete(id: String) {
        val removed = _providers.value.firstOrNull { it.id == id }
        val updated = _providers.value.filterNot { it.id == id }
        _providers.value = updated
        persistProviders(updated)
        if (removed != null) {
            AppLogger.append("Provider deleted: ${removed.name}")
        }
    }

    fun snapshotProfiles(): List<ProviderProfile> {
        return _providers.value.map { provider ->
            provider.copy(
                capabilities = provider.capabilities.toSet(),
                ttsVoiceOptions = provider.ttsVoiceOptions.toList(),
            )
        }
    }

    fun restoreProfiles(profiles: List<ProviderProfile>) {
        val normalized = profiles
            .map(::normalizeProvider)
            .distinctBy { it.id }
            .ifEmpty { defaultProviders() }
        _providers.value = normalized
        persistProviders(normalized)
        AppLogger.append("Provider profiles restored: count=${normalized.size}")
    }

    fun updateMultimodalProbeSupport(id: String, probeSupport: FeatureSupportState) {
        updateProbeSupport(id, probeSupport) { item, state -> item.copy(multimodalProbeSupport = state) }
    }

    fun updateNativeStreamingProbeSupport(id: String, probeSupport: FeatureSupportState) {
        updateProbeSupport(id, probeSupport) { item, state -> item.copy(nativeStreamingProbeSupport = state) }
    }

    fun updateSttProbeSupport(id: String, probeSupport: FeatureSupportState) {
        updateProbeSupport(id, probeSupport) { item, state -> item.copy(sttProbeSupport = state) }
    }

    fun updateTtsProbeSupport(id: String, probeSupport: FeatureSupportState) {
        updateProbeSupport(id, probeSupport) { item, state -> item.copy(ttsProbeSupport = state) }
    }

    private fun updateProbeSupport(
        id: String,
        probeSupport: FeatureSupportState,
        transform: (ProviderProfile, FeatureSupportState) -> ProviderProfile,
    ) {
        val updated = _providers.value.map { item ->
            if (item.id == id) transform(item, probeSupport) else item
        }
        _providers.value = updated
        persistProviders(updated)
        updated.firstOrNull { it.id == id }?.let { provider ->
            AppLogger.append("Provider probe support updated: ${provider.name} -> ${probeSupport.name}")
        }
    }

    private fun persistProviders(profiles: List<ProviderProfile>) {
        runBlocking(Dispatchers.IO) {
            if (profiles.isEmpty()) {
                providerDao.replaceAll(emptyList())
            } else {
                providerDao.replaceAll(
                    profiles.mapIndexed { index, profile -> profile.toWriteModel(sortIndex = index) },
                )
            }
        }
    }

    private suspend fun seedStorageIfNeeded() {
        if (providerDao.count() > 0) return
        val imported = runCatching {
            parseLegacyProviderProfiles(preferences?.getString(KEY_PROVIDERS_JSON, null))
        }.onFailure { error ->
            AppLogger.append("Provider catalog legacy import failed: ${error.message ?: error.javaClass.simpleName}")
        }.getOrDefault(emptyList())
        val seeded = imported.map(::normalizeProvider).ifEmpty { defaultProviders() }
        providerDao.replaceAll(
            seeded.mapIndexed { index, profile -> profile.toWriteModel(sortIndex = index) },
        )
        AppLogger.append(
            if (imported.isNotEmpty()) {
                "Provider catalog migrated from SharedPreferences: count=${seeded.size}"
            } else {
                "Provider catalog seeded with defaults: count=${seeded.size}"
            },
        )
    }

    /**
     * Legacy mojibake patterns that appear in corrupted OpenAI provider names.
     * These originate from the default Chinese name "OpenAI 鑱婂ぉ" being stored via
     * a Latin-1/GBK encoding mismatch in early database versions. We detect them
     * here so that corrupted records are silently repaired to "OpenAI Chat".
     */
    private val LEGACY_MOJIBAKE_OPENAI_PATTERNS = listOf(
        "\u9390\u7535\u9426\uff1f",   // 閻庣數閻?  鈥?mojibake of partial "鑱婂ぉ"
        "\u95fb\u5ea3\u6578\u9862\u5a47\u60c1", // 闁诲海鏁搁、濠囨儊 鈥?mojibake of "OpenAI 鑱婂ぉ" prefix
    )

    private fun normalizeProvider(provider: ProviderProfile): ProviderProfile {
        val normalizedName = when {
            provider.id == "openai-chat" && provider.name.isBlank() -> "OpenAI Chat"
            provider.id == "openai-chat" && LEGACY_MOJIBAKE_OPENAI_PATTERNS.any { provider.name.contains(it) } ->
                "OpenAI Chat"
            else -> provider.name
        }
        val normalizedModel = provider.model.trim()
        return provider.copy(
            name = normalizedName,
            model = normalizedModel,
            capabilities = provider.capabilities.ifEmpty { setOf(provider.providerType.defaultCapability()) },
            multimodalRuleSupport = inferMultimodalRuleSupport(provider.providerType, normalizedModel),
            nativeStreamingRuleSupport = inferNativeStreamingRuleSupport(provider.providerType, normalizedModel),
            ttsVoiceOptions = provider.ttsVoiceOptions.map(String::trim).filter(String::isNotBlank).distinct(),
        )
    }

    private fun maskState(apiKey: String): String {
        return if (apiKey.isBlank()) "empty" else "configured"
    }

    private fun defaultProviders() = listOf(
        ProviderProfile(
            id = "openai-chat",
            name = "OpenAI Chat",
            baseUrl = "https://api.openai.com/v1",
            model = "gpt-4.1-mini",
            providerType = ProviderType.OPENAI_COMPATIBLE,
            apiKey = "",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.SUPPORTED,
        ),
        ProviderProfile(
            id = "deepseek-chat",
            name = "DeepSeek",
            baseUrl = "https://api.deepseek.com/v1",
            model = "deepseek-chat",
            providerType = ProviderType.DEEPSEEK,
            apiKey = "",
            capabilities = setOf(ProviderCapability.CHAT),
            multimodalRuleSupport = FeatureSupportState.UNSUPPORTED,
        ),
    )
}

private object ProviderDaoPlaceholder {
    val instance = object : ProviderAggregateDao() {
        override fun observeProviderAggregates() = flowOf(emptyList<ProviderAggregate>())
        override suspend fun listProviderAggregates(): List<ProviderAggregate> = emptyList()
        override suspend fun upsertProviders(entities: List<ProviderEntity>) = Unit
        override suspend fun upsertCapabilities(entities: List<com.astrbot.android.data.db.ProviderCapabilityEntity>) = Unit
        override suspend fun upsertVoiceOptions(entities: List<com.astrbot.android.data.db.ProviderTtsVoiceOptionEntity>) = Unit
        override suspend fun deleteMissingProviders(ids: List<String>) = Unit
        override suspend fun clearProviders() = Unit
        override suspend fun deleteCapabilities(providerIds: List<String>) = Unit
        override suspend fun deleteVoiceOptions(providerIds: List<String>) = Unit
        override suspend fun count(): Int = 0
    }
}


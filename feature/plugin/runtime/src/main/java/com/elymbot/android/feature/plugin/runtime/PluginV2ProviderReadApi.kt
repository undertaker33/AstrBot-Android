package com.elymbot.android.feature.plugin.runtime

import java.util.Locale

data class PluginV2ProviderReadProvider(
    val providerId: String,
    val displayName: String,
    val enabled: Boolean,
    val capabilities: Set<String>,
    val defaultModelId: String,
    val models: List<PluginV2ProviderReadModel>,
)

data class PluginV2ProviderReadModel(
    val modelId: String,
    val displayName: String,
    val capabilities: Set<String>,
    val contextWindow: Int?,
    val supportsToolCalling: Boolean,
    val supportsStreaming: Boolean,
)

interface PluginV2ProviderReadPort {
    suspend fun providers(): List<PluginV2ProviderReadProvider>
}

data class PluginV2ProviderSummary(
    val providerId: String,
    val displayName: String,
    val enabled: Boolean,
    val capabilities: Set<String>,
    val defaultModelId: String,
    val modelCount: Int,
)

data class PluginV2ProviderModelSummary(
    val modelId: String,
    val displayName: String,
    val capabilities: Set<String>,
    val contextWindow: Int?,
    val supportsToolCalling: Boolean,
    val supportsStreaming: Boolean,
)

data class PluginV2ProviderModelsRequest(
    val providerId: String,
)

class PluginV2ProviderReadApi(
    private val facade: PluginV2HostApiFacade,
    private val providerReader: PluginV2ProviderReadPort,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) {
    suspend fun list(context: PluginV2HostApiRequestContext): PluginV2HostApiResult {
        return facade.call(
            context = context,
            api = HOST_API_PROVIDERS_LIST,
            permissionId = PluginV2HostApiPermissions.PROVIDER_READ,
            timeoutMs = timeoutMs,
        ) {
            providerReader.providers().map { provider -> provider.toSummary() }
        }
    }

    suspend fun models(
        context: PluginV2HostApiRequestContext,
        request: PluginV2ProviderModelsRequest,
    ): PluginV2HostApiResult {
        return facade.call(
            context = context,
            api = HOST_API_PROVIDERS_MODELS,
            permissionId = PluginV2HostApiPermissions.PROVIDER_READ,
            timeoutMs = timeoutMs,
        ) {
            val providerId = sanitizeIdentifier(request.providerId)
            if (providerId.isBlank()) {
                throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = PluginV2HostApiErrorCodes.INVALID_PAYLOAD,
                        message = "Provider id is required.",
                    ),
                )
            }
            val provider = providerReader.providers()
                .firstOrNull { sanitizeIdentifier(it.providerId) == providerId }
                ?: throw PluginV2HostApiException(
                    PluginV2HostApiError(
                        code = PROVIDER_NOT_FOUND,
                        message = "Provider was not found.",
                        details = mapOf("providerId" to providerId),
                    ),
                )
            provider.models.map { model -> model.toSummary() }
        }
    }

    private fun PluginV2ProviderReadProvider.toSummary(): PluginV2ProviderSummary {
        return PluginV2ProviderSummary(
            providerId = sanitizeIdentifier(providerId),
            displayName = sanitizeText(displayName),
            enabled = enabled,
            capabilities = sanitizeCapabilities(capabilities),
            defaultModelId = sanitizeIdentifier(defaultModelId),
            modelCount = models.size,
        )
    }

    private fun PluginV2ProviderReadModel.toSummary(): PluginV2ProviderModelSummary {
        return PluginV2ProviderModelSummary(
            modelId = sanitizeIdentifier(modelId),
            displayName = sanitizeText(displayName),
            capabilities = sanitizeCapabilities(capabilities),
            contextWindow = contextWindow?.takeIf { it > 0 },
            supportsToolCalling = supportsToolCalling,
            supportsStreaming = supportsStreaming,
        )
    }

    companion object {
        const val HOST_API_PROVIDERS_LIST = "hostApi.providers.list"
        const val HOST_API_PROVIDERS_MODELS = "hostApi.providers.models"
        const val PROVIDER_NOT_FOUND = "provider_not_found"
        const val DEFAULT_TIMEOUT_MS = 5_000L

        private val SENSITIVE_TOKENS = listOf(
            "apikey",
            "baseurl",
            "headers",
            "credential",
            "providerdao",
        )

        private fun sanitizeCapabilities(capabilities: Set<String>): Set<String> {
            return capabilities
                .map(::sanitizeIdentifier)
                .filterTo(linkedSetOf()) { it.isNotBlank() }
        }

        private fun sanitizeIdentifier(value: String): String {
            return sanitizeText(value).trim()
        }

        private fun sanitizeText(value: String): String {
            val trimmed = value.trim()
            if (trimmed.isBlank()) return ""
            val normalized = trimmed.lowercase(Locale.US).filter { it.isLetterOrDigit() }
            if (SENSITIVE_TOKENS.any { token -> normalized.contains(token) }) {
                return ""
            }
            return trimmed
        }
    }
}

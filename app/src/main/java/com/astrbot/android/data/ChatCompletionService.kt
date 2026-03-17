package com.astrbot.android.data

import com.astrbot.android.model.FeatureSupportState
import com.astrbot.android.model.ProviderCapability
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.ProviderType
import com.astrbot.android.model.inferMultimodalRuleSupport
import com.astrbot.android.model.supportsChatCompletions
import com.astrbot.android.model.usesOpenAiStyleChatApi
import com.astrbot.android.runtime.RuntimeLogRepository
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets

object ChatCompletionService {
    private const val MULTIMODAL_PROMPT = "Describe the main subject of this image in one short sentence."
    private const val TEST_IMAGE_BASE64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAusB9VE3d2QAAAAASUVORK5CYII="

    fun fetchModels(baseUrl: String, apiKey: String, providerType: ProviderType): List<String> {
        require(baseUrl.isNotBlank()) { "Base URL cannot be empty." }
        return when {
            providerType.usesOpenAiStyleChatApi() -> fetchOpenAiStyleModels(baseUrl, apiKey)
            providerType == ProviderType.OLLAMA -> fetchOllamaModels(baseUrl)
            providerType == ProviderType.GEMINI -> fetchGeminiModels(baseUrl, apiKey)
            else -> throw IllegalStateException("Pull models is not supported for ${providerType.name}.")
        }
    }

    fun detectMultimodalRule(provider: ProviderProfile): FeatureSupportState {
        return inferMultimodalRuleSupport(provider.providerType, provider.model)
    }

    fun probeMultimodalSupport(provider: ProviderProfile): FeatureSupportState {
        require(ProviderCapability.CHAT in provider.capabilities) { "Multimodal checks are only available for chat models." }
        require(provider.providerType.supportsChatCompletions()) { "This provider does not support chat completions." }

        val result = when {
            provider.providerType.usesOpenAiStyleChatApi() -> probeOpenAiStyleMultimodal(provider)
            provider.providerType == ProviderType.OLLAMA -> probeOllamaMultimodal(provider)
            provider.providerType == ProviderType.GEMINI -> probeGeminiMultimodal(provider)
            else -> FeatureSupportState.UNKNOWN
        }
        RuntimeLogRepository.append("Multimodal probe: provider=${provider.name} result=${result.name}")
        return result
    }

    fun sendChat(
        provider: ProviderProfile,
        messages: List<com.astrbot.android.model.ConversationMessage>,
        systemPrompt: String? = null,
    ): String {
        require(provider.providerType.supportsChatCompletions()) { "This provider type does not support chat requests." }
        require(provider.capabilities.contains(ProviderCapability.CHAT)) { "This provider is not configured as a chat model." }

        return when {
            provider.providerType.usesOpenAiStyleChatApi() -> sendOpenAiStyleChat(provider, messages, systemPrompt)
            provider.providerType == ProviderType.OLLAMA -> sendOllamaChat(provider, messages, systemPrompt)
            provider.providerType == ProviderType.GEMINI -> sendGeminiChat(provider, messages, systemPrompt)
            else -> throw IllegalStateException("Chat routing is not implemented for ${provider.providerType.name}.")
        }
    }

    private fun fetchOpenAiStyleModels(baseUrl: String, apiKey: String): List<String> {
        require(apiKey.isNotBlank()) { "API key cannot be empty." }
        val connection = openConnection(baseUrl.trimEnd('/') + "/models", "GET").apply {
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return readModelList(connection) { body -> JSONObject(body).optJSONArray("data") ?: JSONArray() }
    }

    private fun fetchOllamaModels(baseUrl: String): List<String> {
        val connection = openConnection(baseUrl.trimEnd('/') + "/api/tags", "GET")
        return readModelList(connection) { body -> JSONObject(body).optJSONArray("models") ?: JSONArray() }
    }

    private fun fetchGeminiModels(baseUrl: String, apiKey: String): List<String> {
        require(apiKey.isNotBlank()) { "API key cannot be empty." }
        val endpoint = baseUrl.trimEnd('/') + "/models?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8.name())
        val connection = openConnection(endpoint, "GET")
        return readModelList(connection) { body -> JSONObject(body).optJSONArray("models") ?: JSONArray() }
            .map { it.removePrefix("models/") }
    }

    private fun readModelList(connection: HttpURLConnection, arrayProvider: (String) -> JSONArray): List<String> {
        return try {
            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode: $body")
            }
            val array = arrayProvider(body)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id").ifBlank { item.optString("name") }
                    if (id.isNotBlank()) add(id)
                }
            }.distinct().sorted()
        } finally {
            connection.disconnect()
        }
    }

    private fun probeOpenAiStyleMultimodal(provider: ProviderProfile): FeatureSupportState {
        require(provider.apiKey.isNotBlank()) { "API key cannot be empty." }
        val payload = JSONObject().apply {
            put("model", provider.model)
            put(
                "messages",
                JSONArray().put(
                    JSONObject().put("role", "user").put(
                        "content",
                        JSONArray()
                            .put(JSONObject().put("type", "text").put("text", MULTIMODAL_PROMPT))
                            .put(
                                JSONObject().put("type", "image_url").put(
                                    "image_url",
                                    JSONObject().put("url", "data:image/png;base64,$TEST_IMAGE_BASE64"),
                                ),
                            ),
                    ),
                ),
            )
        }
        return executeProbeRequest(
            endpoint = provider.baseUrl.trimEnd('/') + "/chat/completions",
            payload = payload,
            configure = { connection -> connection.setRequestProperty("Authorization", "Bearer ${provider.apiKey}") },
            parser = { body ->
                val content = JSONObject(body)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    .orEmpty()
                if (content.isNotBlank()) FeatureSupportState.SUPPORTED else FeatureSupportState.UNSUPPORTED
            },
        )
    }

    private fun probeOllamaMultimodal(provider: ProviderProfile): FeatureSupportState {
        val payload = JSONObject().apply {
            put("model", provider.model)
            put("stream", false)
            put(
                "messages",
                JSONArray().put(
                    JSONObject()
                        .put("role", "user")
                        .put("content", MULTIMODAL_PROMPT)
                        .put("images", JSONArray().put(TEST_IMAGE_BASE64)),
                ),
            )
        }
        return executeProbeRequest(
            endpoint = provider.baseUrl.trimEnd('/') + "/api/chat",
            payload = payload,
            configure = {},
            parser = { body ->
                val content = JSONObject(body).optJSONObject("message")?.optString("content").orEmpty()
                if (content.isNotBlank()) FeatureSupportState.SUPPORTED else FeatureSupportState.UNSUPPORTED
            },
        )
    }

    private fun probeGeminiMultimodal(provider: ProviderProfile): FeatureSupportState {
        require(provider.apiKey.isNotBlank()) { "API key cannot be empty." }
        val modelName = if (provider.model.startsWith("models/")) provider.model else "models/${provider.model}"
        val endpoint = provider.baseUrl.trimEnd('/') + "/$modelName:generateContent?key=" +
            URLEncoder.encode(provider.apiKey, StandardCharsets.UTF_8.name())
        val payload = JSONObject().apply {
            put(
                "contents",
                JSONArray().put(
                    JSONObject().put(
                        "parts",
                        JSONArray()
                            .put(JSONObject().put("text", MULTIMODAL_PROMPT))
                            .put(
                                JSONObject().put(
                                    "inlineData",
                                    JSONObject().put("mimeType", "image/png").put("data", TEST_IMAGE_BASE64),
                                ),
                            ),
                    ),
                ),
            )
        }
        return executeProbeRequest(
            endpoint = endpoint,
            payload = payload,
            configure = {},
            parser = { body ->
                val text = JSONObject(body)
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    .orEmpty()
                if (text.isNotBlank()) FeatureSupportState.SUPPORTED else FeatureSupportState.UNSUPPORTED
            },
        )
    }

    private fun sendOpenAiStyleChat(
        provider: ProviderProfile,
        messages: List<com.astrbot.android.model.ConversationMessage>,
        systemPrompt: String?,
    ): String {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val payload = JSONObject().apply {
            put("model", provider.model)
            put(
                "messages",
                JSONArray().apply {
                    if (!systemPrompt.isNullOrBlank()) {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                    }
                    messages.forEach { message ->
                        put(JSONObject().put("role", message.role).put("content", message.content))
                    }
                },
            )
        }
        val connection = openConnection(provider.baseUrl.trimEnd('/') + "/chat/completions", "POST").apply {
            setRequestProperty("Authorization", "Bearer ${provider.apiKey}")
        }
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Model response is empty.")
        }
    }

    private fun sendOllamaChat(
        provider: ProviderProfile,
        messages: List<com.astrbot.android.model.ConversationMessage>,
        systemPrompt: String?,
    ): String {
        val payload = JSONObject().apply {
            put("model", provider.model)
            put("stream", false)
            put(
                "messages",
                JSONArray().apply {
                    if (!systemPrompt.isNullOrBlank()) {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                    }
                    messages.forEach { message ->
                        put(JSONObject().put("role", message.role).put("content", message.content))
                    }
                },
            )
        }
        val connection = openConnection(provider.baseUrl.trimEnd('/') + "/api/chat", "POST")
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body).optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Model response is empty.")
        }
    }

    private fun sendGeminiChat(
        provider: ProviderProfile,
        messages: List<com.astrbot.android.model.ConversationMessage>,
        systemPrompt: String?,
    ): String {
        require(provider.apiKey.isNotBlank()) { "Provider API key is empty." }
        val modelName = if (provider.model.startsWith("models/")) provider.model else "models/${provider.model}"
        val endpoint = provider.baseUrl.trimEnd('/') + "/$modelName:generateContent?key=" +
            URLEncoder.encode(provider.apiKey, StandardCharsets.UTF_8.name())
        val payload = JSONObject().apply {
            if (!systemPrompt.isNullOrBlank()) {
                put(
                    "system_instruction",
                    JSONObject().put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))),
                )
            }
            put(
                "contents",
                JSONArray().apply {
                    messages.forEach { message ->
                        put(
                            JSONObject()
                                .put("role", if (message.role == "assistant") "model" else "user")
                                .put("parts", JSONArray().put(JSONObject().put("text", message.content))),
                        )
                    }
                },
            )
        }
        val connection = openConnection(endpoint, "POST")
        return executeJsonRequest(connection, payload) { body ->
            JSONObject(body)
                .optJSONArray("candidates")
                ?.optJSONObject(0)
                ?.optJSONObject("content")
                ?.optJSONArray("parts")
                ?.optJSONObject(0)
                ?.optString("text")
                ?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Model response is empty.")
        }
    }

    private fun executeProbeRequest(
        endpoint: String,
        payload: JSONObject,
        configure: (HttpURLConnection) -> Unit,
        parser: (String) -> FeatureSupportState,
    ): FeatureSupportState {
        val connection = openConnection(endpoint, "POST")
        configure(connection)
        return runCatching { executeJsonRequest(connection, payload, parser) }
            .getOrElse { error ->
                RuntimeLogRepository.append("Multimodal probe error: ${error.message ?: error.javaClass.simpleName}")
                FeatureSupportState.UNSUPPORTED
            }
    }

    private fun <T> executeJsonRequest(connection: HttpURLConnection, payload: JSONObject, parser: (String) -> T): T {
        return try {
            connection.doOutput = true
            connection.outputStream.use { output ->
                output.write(payload.toString().toByteArray(StandardCharsets.UTF_8))
            }
            val responseCode = connection.responseCode
            val body = readBody(connection, responseCode)
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode: $body")
            }
            parser(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(endpoint: String, method: String): HttpURLConnection {
        RuntimeLogRepository.append("HTTP request: method=$method endpoint=$endpoint")
        return (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 30_000
            readTimeout = 60_000
            setRequestProperty("Content-Type", "application/json")
        }
    }

    private fun readBody(connection: HttpURLConnection, responseCode: Int): String {
        val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        if (stream == null) return ""
        return BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8)).use { reader -> reader.readText() }
    }
}

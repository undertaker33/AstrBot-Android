package com.astrbot.android.feature.qq.runtime

import com.astrbot.android.feature.qq.domain.QqRuntimePort
import com.astrbot.android.feature.qq.domain.QqRuntimeResult
import kotlinx.coroutines.CancellationException

internal class OneBotServerAdapter(
    private val parser: OneBotPayloadParser,
    private val runtime: QqRuntimePort,
    private val log: (String) -> Unit = {},
) {
    suspend fun handlePayload(payload: String): OneBotServerAdapterResult {
        val parsed = try {
            parser.parse(payload)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            log("OneBotServerAdapter parse exception: ${e.message}")
            return OneBotServerAdapterResult.Invalid("Parser exception: ${e.message}")
        }

        return when (parsed) {
            is OneBotPayloadParseResult.Message -> {
                val result = try {
                    runtime.handleIncomingMessage(parsed.message)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    log("OneBotServerAdapter runtime exception: ${e.message}")
                    QqRuntimeResult.Failed("Runtime exception: ${e.message}", e)
                }
                OneBotServerAdapterResult.Handled(result.toString())
            }

            is OneBotPayloadParseResult.Ignored -> {
                OneBotServerAdapterResult.Ignored(parsed.reason)
            }

            is OneBotPayloadParseResult.Invalid -> {
                OneBotServerAdapterResult.Invalid(parsed.reason)
            }
        }
    }
}

sealed interface OneBotServerAdapterResult {
    data class Handled(val summary: String) : OneBotServerAdapterResult
    data class Ignored(val reason: String) : OneBotServerAdapterResult
    data class Invalid(val reason: String) : OneBotServerAdapterResult
}

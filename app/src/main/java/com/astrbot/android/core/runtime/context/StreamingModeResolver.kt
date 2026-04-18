package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.hasNativeStreamingSupport
import com.astrbot.android.model.plugin.PluginV2StreamingMode

/**
 * Resolves the streaming mode for an LLM request based on config and provider
 * capabilities. Shared between App and QQ paths.
 */
object StreamingModeResolver {

    fun resolve(ctx: ResolvedRuntimeContext): PluginV2StreamingMode {
        return resolve(ctx.deliveryPolicy.streamingEnabled, ctx.provider)
    }

    fun resolve(streamingEnabled: Boolean, provider: ProviderProfile): PluginV2StreamingMode {
        return when {
            !streamingEnabled -> PluginV2StreamingMode.NON_STREAM
            provider.hasNativeStreamingSupport() -> PluginV2StreamingMode.NATIVE_STREAM
            else -> PluginV2StreamingMode.PSEUDO_STREAM
        }
    }
}

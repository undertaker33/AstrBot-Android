package com.astrbot.android.core.runtime.context

/**
 * Platform-specific callbacks needed by the runtime orchestrator to deliver LLM
 * results. Each platform (App chat, QQ OneBot) provides an implementation.
 *
 * Defined here in runtime.context so it can reference [ResolvedRuntimeContext]
 * but actual implementations live in their respective platform modules.
 */
interface PlatformRuntimeAdapter {
    val platform: RuntimePlatform
    val platformAdapterType: String
    val platformInstanceKey: String
}

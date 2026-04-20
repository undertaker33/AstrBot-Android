package com.astrbot.android.feature.plugin.runtime

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Factory that creates [PluginHostCapabilityGateway] instances with an injected
 * [PluginExecutionHostResolver]. Callers receive a contextual gateway by providing
 * platform-specific [ExternalPluginHostActionExecutor] and [PluginExecutionHostToolHandlers].
 *
 * Hilt provides this as a [Singleton]; the factory itself is stateless.
 */
@Singleton
class PluginHostCapabilityGatewayFactory @Inject constructor(
    private val resolver: PluginExecutionHostResolver,
) {
    fun create(
        sendMessageHandler: (String) -> Unit = {},
        sendNotificationHandler: (String, String) -> Unit = { _, _ -> },
        openHostPageHandler: (String) -> Unit = {},
        hostToolHandlers: PluginExecutionHostToolHandlers = PluginExecutionHostToolHandlers(),
    ): PluginHostCapabilityGateway = DefaultPluginHostCapabilityGateway(
        resolver = resolver,
        hostActionExecutor = ExternalPluginHostActionExecutor(
            sendMessageHandler = sendMessageHandler,
            sendNotificationHandler = sendNotificationHandler,
            openHostPageHandler = openHostPageHandler,
        ),
        hostToolHandlers = hostToolHandlers,
    )
}

internal fun createCompatPluginHostCapabilityGatewayFactory(): PluginHostCapabilityGatewayFactory {
    return PluginHostCapabilityGatewayFactory(
        resolver = DefaultPluginExecutionHostResolver(),
    )
}

internal fun createCompatPluginHostCapabilityGateway(
    sendMessageHandler: (String) -> Unit = {},
    sendNotificationHandler: (String, String) -> Unit = { _, _ -> },
    openHostPageHandler: (String) -> Unit = {},
    hostToolHandlers: PluginExecutionHostToolHandlers = PluginExecutionHostToolHandlers(),
): PluginHostCapabilityGateway {
    return createCompatPluginHostCapabilityGatewayFactory().create(
        sendMessageHandler = sendMessageHandler,
        sendNotificationHandler = sendNotificationHandler,
        openHostPageHandler = openHostPageHandler,
        hostToolHandlers = hostToolHandlers,
    )
}

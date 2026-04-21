package com.astrbot.android.feature.plugin.runtime

/**
 * Factory that creates [PluginHostCapabilityGateway] instances with an injected
 * [PluginExecutionHostResolver]. Callers receive a contextual gateway by providing
 * platform-specific [ExternalPluginHostActionExecutor] and [PluginExecutionHostToolHandlers].
 *
 * Production code should receive this from Hilt. The compat helpers below are
 * legacy-only and remain as non-default seams for tests and older entry points.
 */
class PluginHostCapabilityGatewayFactory(
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

@Deprecated(
    "Compat-only. Production code should use the Hilt-owned PluginHostCapabilityGatewayFactory.",
    level = DeprecationLevel.WARNING,
)
internal fun createCompatPluginHostCapabilityGatewayFactory(): PluginHostCapabilityGatewayFactory {
    return PluginHostCapabilityGatewayFactory(
        resolver = DefaultPluginExecutionHostResolver(DefaultPluginExecutionHostOperations()),
    )
}

@Deprecated(
    "Compat-only. Production code should use the Hilt-owned PluginHostCapabilityGatewayFactory.",
    level = DeprecationLevel.WARNING,
)
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

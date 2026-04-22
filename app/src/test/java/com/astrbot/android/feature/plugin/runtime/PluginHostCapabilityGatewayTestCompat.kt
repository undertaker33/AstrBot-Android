package com.astrbot.android.feature.plugin.runtime

internal fun createCompatPluginHostCapabilityGatewayFactory(): PluginHostCapabilityGatewayFactory {
    return PluginHostCapabilityGatewayFactory(
        resolver = DefaultPluginExecutionHostResolver(DefaultPluginExecutionHostOperations()),
        hostActionExecutor = ExternalPluginHostActionExecutor(),
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

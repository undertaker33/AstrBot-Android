package com.astrbot.android.feature.qq.runtime

internal class QqOneBotRuntimeGraph(
    private val outboundGateway: QqOneBotOutboundGateway,
    private val pluginDispatchService: QqPluginDispatchService,
    private val runtimeService: QqMessageRuntimeService,
    private val replySender: QqReplySender,
    private val log: (String) -> Unit,
) {
    fun adapter(): OneBotServerAdapter {
        return OneBotServerAdapter(
            parser = OneBotPayloadParser(),
            runtime = runtimeService,
            log = log,
        )
    }

    fun outboundGateway(): QqOneBotOutboundGateway = outboundGateway

    fun pluginDispatchService(): QqPluginDispatchService = pluginDispatchService

    fun runtimeService(): QqMessageRuntimeService = runtimeService

    internal fun pluginDispatchServiceForTests(): QqPluginDispatchService = pluginDispatchService

    internal fun replySenderForTests(): QqReplySender = replySender
}

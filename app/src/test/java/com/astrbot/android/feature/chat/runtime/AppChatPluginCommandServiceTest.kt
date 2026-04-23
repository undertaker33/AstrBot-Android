package com.astrbot.android.feature.chat.runtime

import com.astrbot.android.feature.plugin.runtime.AppChatPluginRuntime
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutionResult
import com.astrbot.android.feature.plugin.runtime.ExternalPluginHostActionExecutor
import com.astrbot.android.feature.plugin.runtime.PluginExecutionBatchResult
import com.astrbot.android.feature.plugin.runtime.PluginExecutionHostSnapshot
import com.astrbot.android.feature.plugin.runtime.PluginHostCapabilityGateway
import com.astrbot.android.feature.plugin.runtime.PluginMessageEvent
import com.astrbot.android.feature.plugin.runtime.PluginToolArgs
import com.astrbot.android.feature.plugin.runtime.PluginToolDescriptor
import com.astrbot.android.feature.plugin.runtime.PluginToolResult
import com.astrbot.android.feature.plugin.runtime.PluginToolSourceKind
import com.astrbot.android.feature.plugin.runtime.PluginV2ActiveRuntimeSnapshot
import com.astrbot.android.feature.plugin.runtime.PluginV2DispatchEngine
import com.astrbot.android.feature.plugin.runtime.PluginV2ToolRegistryEntry
import com.astrbot.android.model.chat.ConversationMessage
import com.astrbot.android.model.chat.ConversationSession
import com.astrbot.android.model.chat.MessageSessionRef
import com.astrbot.android.model.chat.MessageType
import com.astrbot.android.model.plugin.HostActionRequest
import com.astrbot.android.model.plugin.PluginExecutionContext
import com.astrbot.android.model.plugin.PluginTriggerSource
import com.astrbot.android.ui.viewmodel.ChatViewModelRuntimeBindings
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AppChatPluginCommandServiceTest {
    @Test
    fun build_app_chat_plugin_message_event_injects_session_unified_origin_without_fallback_to_db_session_id() {
        val service = AppChatPluginCommandService(
            dependencies = unsupportedBindingsProxy(),
            appChatPluginRuntime = object : AppChatPluginRuntime {
                override fun execute(
                    trigger: PluginTriggerSource,
                    contextFactory: (com.astrbot.android.feature.plugin.runtime.PluginRuntimePlugin) -> PluginExecutionContext,
                ): PluginExecutionBatchResult {
                    return PluginExecutionBatchResult(
                        trigger = trigger,
                        outcomes = emptyList(),
                        skipped = emptyList(),
                    )
                }
            },
            hostCapabilityGateway = NoOpPluginHostCapabilityGateway,
            hostActionExecutor = ExternalPluginHostActionExecutor(),
            dispatchEngine = PluginV2DispatchEngine(),
        )
        val session = ConversationSession(
            id = "db-session-42",
            title = "Main Chat",
            botId = "bot-1",
            personaId = "",
            providerId = "",
            platformId = "app",
            messageType = MessageType.OtherMessage,
            originSessionId = "",
            maxContextMessages = 12,
            messages = emptyList(),
        )
        val message = ConversationMessage(
            id = "msg-1",
            role = "user",
            content = "hello",
            timestamp = 123L,
        )

        val event = buildPluginMessageEvent(
            service = service,
            session = session,
            message = message,
        )

        assertEquals(
            MessageSessionRef(
                platformId = session.platformId,
                messageType = session.messageType,
                originSessionId = session.originSessionId,
            ).unifiedOrigin,
            event.extras["sessionUnifiedOrigin"],
        )
        assertFalse(event.extras["sessionUnifiedOrigin"] == session.id)
    }

    private fun buildPluginMessageEvent(
        service: AppChatPluginCommandService,
        session: ConversationSession,
        message: ConversationMessage,
    ): PluginMessageEvent {
        val method = AppChatPluginCommandService::class.java.getDeclaredMethod(
            "buildAppChatPluginMessageEvent",
            PluginTriggerSource::class.java,
            ConversationSession::class.java,
            ConversationMessage::class.java,
            com.astrbot.android.model.ProviderProfile::class.java,
            com.astrbot.android.model.BotProfile::class.java,
            String::class.java,
            com.astrbot.android.model.ConfigProfile::class.java,
            Boolean::class.javaPrimitiveType,
        )
        method.isAccessible = true
        return method.invoke(
            service,
            PluginTriggerSource.BeforeSendMessage,
            session,
            message,
            null,
            null,
            "",
            null,
            false,
        ) as PluginMessageEvent
    }

    private fun unsupportedBindingsProxy(): ChatViewModelRuntimeBindings {
        return Proxy.newProxyInstance(
            ChatViewModelRuntimeBindings::class.java.classLoader,
            arrayOf(ChatViewModelRuntimeBindings::class.java),
        ) { _, method, _ ->
            throw UnsupportedOperationException("Unexpected ChatViewModelRuntimeBindings call: ${method.name}")
        } as ChatViewModelRuntimeBindings
    }
}

private object NoOpPluginHostCapabilityGateway : PluginHostCapabilityGateway {
    override fun executeHostAction(
        pluginId: String,
        request: HostActionRequest,
        context: PluginExecutionContext,
    ): ExternalPluginHostActionExecutionResult {
        throw UnsupportedOperationException("executeHostAction should not be called in this test")
    }

    override fun injectContext(context: PluginExecutionContext): PluginExecutionContext = context

    override fun injectContext(
        context: PluginExecutionContext,
        hostSnapshot: PluginExecutionHostSnapshot,
    ): PluginExecutionContext = context

    override fun registerHostBuiltinTools(
        snapshot: PluginV2ActiveRuntimeSnapshot,
        personaSnapshot: com.astrbot.android.model.PersonaToolEnablementSnapshot?,
        futureSourceDescriptors: Collection<PluginToolDescriptor>,
        activeFutureSourceKinds: Set<PluginToolSourceKind>,
    ): PluginV2ActiveRuntimeSnapshot = snapshot

    override fun executeHostBuiltinTool(
        args: PluginToolArgs,
    ): PluginToolResult? = null

    override fun isToolAllowed(entry: PluginV2ToolRegistryEntry): Boolean = true
}

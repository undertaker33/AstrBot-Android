package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.BotProfile
import com.astrbot.android.model.ConfigProfile
import com.astrbot.android.model.McpServerEntry
import com.astrbot.android.model.PersonaProfile
import com.astrbot.android.model.PersonaToolEnablementSnapshot
import com.astrbot.android.model.ProviderProfile
import com.astrbot.android.model.SkillEntry
import com.astrbot.android.model.chat.ConversationMessage
import java.util.UUID

/**
 * Complete runtime context snapshot for a single request. Every field needed by
 * the LLM pipeline, tool registry, prompt assembler, and delivery adapter is
 * available here. Internal-only — never serialise the whole object to LLM or
 * external systems; use view projections instead.
 */
data class ResolvedRuntimeContext(
    val requestId: String = UUID.randomUUID().toString(),
    val ingressEvent: RuntimeIngressEvent,

    // ── Identity ──
    val bot: BotProfile,
    val config: ConfigProfile,
    val persona: PersonaProfile?,
    val provider: ProviderProfile,
    val availableProviders: List<ProviderProfile>,

    // ── Conversation ──
    val conversationId: String,
    val messageWindow: List<ConversationMessage>,

    // ── Context strategy ──
    val contextPolicy: ContextPolicy,

    // ── Persona tool policy ──
    val personaToolSnapshot: PersonaToolEnablementSnapshot?,

    // ── Provider capabilities ──
    val providerCapabilities: ProviderCapabilitySnapshot,

    // ── Future capabilities (snapshots from config) ──
    val webSearchEnabled: Boolean,
    val proactiveEnabled: Boolean,
    val mcpServers: List<McpServerEntry>,
    val skills: List<SkillEntry>,
    val promptSkills: List<PromptSkillProjection>,
    val toolSkills: List<ToolSkillProjection>,
    val toolSourceContext: ToolSourceContext,

    // ── Delivery ──
    val deliveryPolicy: DeliveryPolicy,

    // ── Misc ──
    val realWorldTimeAwarenessEnabled: Boolean,
)

data class ContextPolicy(
    val strategy: String,
    val maxTurns: Int,
    val dequeueTurns: Int,
    val compressInstruction: String,
    val compressKeepRecent: Int,
    val compressProviderId: String,
)

data class ProviderCapabilitySnapshot(
    val supportsToolCalling: Boolean,
    val supportsStreaming: Boolean,
    val supportsMultimodal: Boolean,
)

data class DeliveryPolicy(
    val platform: RuntimePlatform,
    val streamingEnabled: Boolean,
    val quoteSenderMessage: Boolean,
    val mentionSender: Boolean,
    val replyTextPrefix: String,
    val ttsEnabled: Boolean,
    val alwaysTts: Boolean,
)

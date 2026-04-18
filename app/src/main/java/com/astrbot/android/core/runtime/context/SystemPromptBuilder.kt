package com.astrbot.android.core.runtime.context

import com.astrbot.android.model.SkillEntry

/**
 * Thin facade that delegates to [PromptAssembler]. Retained for source
 * compatibility during the Phase 1→2 transition; callers should migrate to
 * [PromptAssembler] directly.
 */
object SystemPromptBuilder {

    /** @see PromptAssembler.assemble */
    fun build(ctx: ResolvedRuntimeContext): String? = PromptAssembler.assemble(ctx)

    /** @see PromptAssembler.assembleForAppChat */
    fun buildForAppChat(
        personaPrompt: String?,
        realWorldTimeAwarenessEnabled: Boolean,
        skills: List<SkillEntry> = emptyList(),
    ): String? = PromptAssembler.assembleForAppChat(personaPrompt, realWorldTimeAwarenessEnabled, skills)
}

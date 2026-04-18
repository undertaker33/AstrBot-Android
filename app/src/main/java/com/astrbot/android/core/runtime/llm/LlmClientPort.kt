package com.astrbot.android.core.runtime.llm

import kotlinx.coroutines.flow.Flow

interface LlmClientPort {
    suspend fun sendWithTools(request: LlmInvocationRequest): LlmInvocationResult
    fun streamWithTools(request: LlmInvocationRequest): Flow<LlmStreamEvent>
}

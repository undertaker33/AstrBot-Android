# P260521-1353 T05 worker prompt: current-session conversation history API

## Context

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26052102-插件API对齐AstrBot能力补齐`
- Design: `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
- Todo: `docs/work/D26052102-插件API对齐AstrBot能力补齐/50-D26052102-T05-todo-当前会话历史只读查询.md`
- Phase: B / T05
- Worker role: implementation worker
- Git writes: forbidden

You are not alone in the codebase. Do not revert or overwrite unrelated edits. T01-T04 are already in the workspace and are the base for this task.

## Current base facts

- `PluginV2HostApiFoundation.kt` owns `PluginV2HostApiFacade`, permission policy, async bridge, structured errors, audit logging, and well-known permissions.
- `PluginV2HostNetworkApi.kt`, `PluginV2ProviderReadApi.kt`, and `PluginV2MessageSendApi.kt` are current capability-class patterns.
- `ConversationRepositoryPort` already has `session(sessionId): ConversationSession`, and `ConversationSession.messages` contains `ConversationMessage`, but this worker must not wire production repositories.
- T05 must stay read-only and must not expose Room entity/DAO fields.

## Goal

Implement the current-session conversation history capability behind the T01 host API facade:

- Canonical JS target for later controller integration: `hostApi.conversation.history({ limit, beforeMessageId, includeAttachments, conversationId })`.
- Use `conversation_read` permission through `PluginV2HostApiFacade`.
- Read only the current event conversation.
- Provide a platform-neutral read port that can be backed by existing conversation/chat API later.

## Strict write scope for this worker

You may modify or add only these files:

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2ConversationHistoryApi.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2ConversationHistoryApiTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2ConversationHistoryApiTest.kt` only if a feature-runtime test cannot cover the needed behavior.
- `app/src/test/java/com/elymbot/android/architecture/PluginV2HostApiArchitectureContractTest.kt` only for a narrow source contract directly related to history DB/data bypass.

Do not edit shared integration files. The controller will wire them after workers finish:

- Do not edit `ExternalPluginScriptExecutor.kt`.
- Do not edit `PluginV2BootstrapHostApi.kt`.
- Do not edit `PluginV2RuntimeLoader.kt`.
- Do not edit `PluginHostCapabilityModule.kt`.
- Do not edit `PluginRuntimeModule.kt`.
- Do not edit `PluginDataWiringFactory.kt`.
- Do not edit `ConversationRepositoryPort.kt`.
- Do not edit `feature/conversation/data/**` or any Room DAO/entity.

If you need any other file, return `NEEDS_CONTEXT` with the exact file and reason.

## Required behavior

1. Define request/result DTOs and a platform-neutral history read port in `PluginV2ConversationHistoryApi.kt`.
2. `history(context, request)` must call `PluginV2HostApiFacade.call(...)` with API name `hostApi.conversation.history` and permission `conversation_read`.
3. Require a current conversation scope:
   - if `context.conversationId` is blank, return structured `missing_session_scope`.
   - if `request.conversationId` is blank, read `context.conversationId`.
   - if `request.conversationId` is nonblank and differs from `context.conversationId`, return structured `conversation_scope_violation`.
4. Limit behavior:
   - default `limit = 20`.
   - hard max `100`.
   - if incoming limit is <= 0, use default 20.
   - if incoming limit is > 100, clamp to 100.
5. `beforeMessageId` behavior:
   - blank means latest messages.
   - nonblank means only messages older than the matched message id.
   - if the id is not found, return an empty list.
6. Return DTO fields:
   - `messageId`, `role`, `senderId`, `messageType`, `text`, `timestampEpochMillis`, `attachmentRefs`.
   - attachment refs include only host-safe ref/uri, mime type, and type.
7. Do not expose Room entity names, DAO names, raw SQL fields, database ids beyond the public message id, provider secrets, or bot private config.
8. Do not add AstrBot-style aliases.

## Required tests

Add focused tests for:

- current conversation returns the latest default 20 messages.
- limit over 100 is clamped to 100.
- `beforeMessageId` returns only earlier messages.
- missing current conversation scope returns `missing_session_scope`.
- non-current conversation request returns `conversation_scope_violation`.
- missing permission returns `permission_denied`.
- attachment refs are omitted when `includeAttachments=false` and sanitized when true.
- returned results do not contain `Room`, `Dao`, `Entity`, `sql`, `apiKey`, `baseUrl`, or `credential`.

Use fake in-memory history data. Do not touch real DB or repositories.

## Verification

Run at minimum:

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*ConversationHistory*" --console=plain --no-daemon --stacktrace --max-workers=1
```

If you update the architecture contract, also run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*PluginV2HostApiArchitectureContractTest*" --console=plain --no-daemon --stacktrace --max-workers=1
```

## Return format

Return one of:

- `DONE`
- `DONE_WITH_CONCERNS`
- `NEEDS_CONTEXT`
- `BLOCKED`

Then include:

- modified files
- tests added/updated
- verification commands and results
- remaining risks

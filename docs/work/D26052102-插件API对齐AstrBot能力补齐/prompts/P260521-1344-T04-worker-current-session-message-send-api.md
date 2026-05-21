# P260521-1344 T04 worker prompt: current-session message send API

## Context

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26052102-插件API对齐AstrBot能力补齐`
- Design: `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
- Todo: `docs/work/D26052102-插件API对齐AstrBot能力补齐/40-D26052102-T04-todo-当前会话消息发送.md`
- Phase: B / T04
- Worker role: implementation worker
- Git writes: forbidden

You are not alone in the codebase. Do not revert or overwrite unrelated edits. T01, T02, and T03 are already in the workspace and are the base for this task.

## Current base facts

- `PluginV2HostApiFoundation.kt` owns `PluginV2HostApiFacade`, permission policy, async bridge, structured errors, audit logging, and well-known permissions.
- `PluginV2HostNetworkApi.kt` and `PluginV2ProviderReadApi.kt` are current capability-class patterns.
- `PluginV2HostApiRequestContext` currently carries `conversationId`, `platformAdapterType`, `triggerMetadata`, and permission data.
- Existing plugin runtime contracts expose `PluginV2FollowupSender`, `PluginV2HostSendResult`, and `ConversationAttachment`, but this worker must not wire production platform services.

## Goal

Implement the current-session message send capability behind the T01 host API facade:

- Canonical JS target for later controller integration: `hostApi.message.send({ text, markdown, attachments, conversationId })`.
- Use `send_message` permission through `PluginV2HostApiFacade`.
- Enforce current conversation scope.
- Provide a platform-neutral port that can be backed by App Chat or QQ follow-up senders.

## Strict write scope for this worker

You may modify or add only these files:

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2MessageSendApi.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2MessageSendApiTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2MessageSendApiTest.kt` only if a feature-runtime test cannot cover the needed behavior.
- `app/src/test/java/com/elymbot/android/architecture/PluginV2HostApiArchitectureContractTest.kt` only for a narrow source contract directly related to message-send platform bypass.

Do not edit shared integration files. The controller will wire them after workers finish:

- Do not edit `ExternalPluginScriptExecutor.kt`.
- Do not edit `PluginV2BootstrapHostApi.kt`.
- Do not edit `PluginV2RuntimeLoader.kt`.
- Do not edit `PluginHostCapabilityModule.kt`.
- Do not edit `PluginRuntimeModule.kt`.
- Do not edit `PluginDataWiringFactory.kt`.
- Do not edit `feature/chat/runtime/**` or `feature/qq/runtime/**`.

If you need any other file, return `NEEDS_CONTEXT` with the exact file and reason.

## Required behavior

1. Define request/result DTOs and a platform-neutral send port in `PluginV2MessageSendApi.kt`.
2. `send(context, request)` must call `PluginV2HostApiFacade.call(...)` with API name `hostApi.message.send` and permission `send_message`.
3. Reject blank text when there are no attachments.
4. Require a current conversation scope:
   - if `context.conversationId` is blank, return structured `missing_session_scope`.
   - if `request.conversationId` is blank, send to `context.conversationId`.
   - if `request.conversationId` is nonblank and differs from `context.conversationId`, return structured `conversation_scope_violation`.
5. Preserve platform boundary:
   - pass `context.platformAdapterType` to the port.
   - support fake `app_chat` and `onebot` in tests through the same port contract.
   - do not directly touch QQ socket, OneBot gateway, App Chat repository, or conversation DAO.
6. Attachments are minimal for this Todo:
   - accept simple attachment refs with `uri` and `mimeType`.
   - only allow `plugin://package` or `plugin://workspace` URIs.
   - unsupported attachment URI returns structured `unsupported_attachment`.
7. Return a sanitized result with `conversationId`, `platformAdapterType`, `receiptIds`, and `messageLength`.
8. Do not add AstrBot-style aliases.

## Required tests

Add focused tests for:

- current App Chat conversation sends successfully through a fake port.
- current QQ/OneBot conversation sends successfully through the same fake port.
- missing current conversation scope returns `missing_session_scope`.
- missing permission returns `permission_denied`.
- explicit non-current conversation returns `conversation_scope_violation`.
- schedule-like context with a bound conversation can send to that bound conversation and rejects another target.
- unsupported attachment URI is rejected.
- port failure maps to structured error without leaking platform stack traces.

## Verification

Run at minimum:

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*MessageSend*" --console=plain --no-daemon --stacktrace --max-workers=1
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

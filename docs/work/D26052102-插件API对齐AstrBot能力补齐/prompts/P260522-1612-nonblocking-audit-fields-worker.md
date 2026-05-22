# P260522-1612 worker：T06/T09 非阻塞审计字段收口

## Scene

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- User request: 非阻塞问题中，除验证类残留外，派一个子代理收口。
- Worker role: implementation worker only.
- Git writes: forbidden.

## Scope

Close the non-verification residual recorded during D26052102 acceptance:

- T06 `hostApi.callLlm` / `hostApi.llm.generate` audit details should include provider/model and token usage when available.
- T09 `hostApi.message.openStream` / `append` / `replace` / `close` audit details should include stream id, chunk or operation counters, bytes, duration/platform, and failure code when available.

This is an audit granularity enhancement only. It is not an AstrBot parity gap, and it must not expose AstrBot-style aliases.

## Excluded

Do not implement or validate:

- real-device validation
- real-network validation
- real-provider validation
- real plugin package validation
- Web API registration
- platform adapter registration
- DB direct access
- text-to-image
- HTML rendering

Do not add a new logging system. Reuse the existing `PluginV2HostApiAuditLogger`, host API facade, runtime log bus, permission policy, and structured error path.

## Starting Context

Relevant docs:

- `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
- `docs/work/D26052102-插件API对齐AstrBot能力补齐/60-D26052102-T06-todo-直接LLM调用.md`
- `docs/work/D26052102-插件API对齐AstrBot能力补齐/90-D26052102-T09-todo-插件流式输出.md`
- `docs/work/D26052102-插件API对齐AstrBot能力补齐/130-D26052102-剩余插件API能力索引.md`

Likely code/test entry points:

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostApiFoundation.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostLlmApi.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2MessageStreamApi.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostLlmApiTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2MessageStreamApiTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostApiAuditLoggerTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2HostApiAuditLoggerTest.kt`

## Requirements

1. Follow existing architecture: plugin runtime, host capability, and LLM orchestration must stay on port + Hilt wiring; no static API, no service locator, no production subgraph.
2. Add focused tests before production changes where practical. The expected behavior is that audit log payloads contain the new detail fields without changing public plugin API shape.
3. Keep public API compatibility. Do not rename current JS APIs or add AstrBot-style aliases.
4. Keep failure audit behavior: denied, failed, timeout, and successful calls should still be audited through the common path.
5. Avoid broad refactors. Touch only files needed for the audit detail enhancement.

## Suggested Implementation Direction

- If the common facade currently records only generic audit fields, extend it with a small optional details map or metadata object.
- Populate T06 details from the LLM request/response path: requested/selected provider id, model id, prompt/message counts if already available, token usage if the response model exposes it.
- Populate T09 details from stream lifecycle state: `streamId`, operation kind, chunk count, byte count, platform adapter type, and elapsed duration if already tracked.
- Keep detail values sanitized and string/number/boolean friendly for runtime logs.

## Required Final Report

Return:

- status: `DONE`, `DONE_WITH_CONCERNS`, `NEEDS_CONTEXT`, or `BLOCKED`
- changed files
- tests added/changed
- commands run and their result
- any remaining non-verification residuals you found

Do not claim full project acceptance; the controller will verify and update docs.

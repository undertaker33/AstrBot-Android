# P260521-1854 Phase D worker：T08/T09/T10 schedule、stream、rich message

## 场景

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- Phase: Phase D
- Todos: `T08 插件定时任务回调`、`T09 插件流式输出`、`T10 富消息链`
- Worker role: implementation worker
- Git writes: forbidden. Do not commit, push, tag, merge, rebase, checkout, reset, or create/delete worktrees.

You are not alone in the codebase. The current coordinator owns integration and final Feedback/current-state. Do not revert or overwrite unrelated edits. If you see Phase C changes, build on them; do not roll them back.

## Required Reads

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/current-state.md`
4. `docs/context/07-聊天与会话.md`
5. `docs/context/08-QQ_NapCat_OneBot.md`
6. `docs/context/09-插件平台.md`
7. `docs/context/10-Cron运行时.md`
8. `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
9. `docs/work/D26052102-插件API对齐AstrBot能力补齐/80-D26052102-T08-todo-插件定时任务回调.md`
10. `docs/work/D26052102-插件API对齐AstrBot能力补齐/90-D26052102-T09-todo-插件流式输出.md`
11. `docs/work/D26052102-插件API对齐AstrBot能力补齐/100-D26052102-T10-todo-富消息链.md`
12. Phase C current implementation files for `PluginV2HostLlmApi` and `PluginV2ContextCompressApi`, because Phase D must not break them.

## Allowed Write Scope

- `feature/plugin/api/src/main/java/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/**`
- `feature/cron/api/src/main/java/**`
- `feature/cron/runtime/src/main/java/**`
- `feature/chat/runtime/src/main/java/**`
- `feature/qq/runtime/src/main/java/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `feature/plugin/runtime/src/test/java/**`
- `feature/cron/runtime/src/test/java/**`
- `app-integration/src/test/java/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`
- `app/src/test/java/com/elymbot/android/feature/cron/**`
- `app/src/test/java/com/elymbot/android/feature/chat/**`
- `app/src/test/java/com/elymbot/android/feature/qq/**`
- `app/src/test/java/com/elymbot/android/architecture/**`

## Forbidden Scope

- Do not edit `docs/context/**`.
- Do not add AstrBot-style aliases; expose only canonical `hostApi.*` APIs.
- Do not implement Agent or filter AST; those belong to Phase E.
- Do not restore static APIs, service locators, handwritten runtime subgraphs, static repository facades, or production global registries.
- Do not let plugin runtime import QQ runtime implementation, Room DAO, provider secret store, App UI, or any platform implementation directly.
- Do not add a new dependency without coordinator approval.
- Do not create a long-running background JS runtime.

## Execution Order

Use TDD. Add failing tests first where feasible, then production code, then rerun focused tests.

Work serially in this order:

1. T08 scheduled handler registration and Cron wakeup.
2. T10 rich message chain model and send mapping.
3. T09 message stream API, reusing the T10/T04 message abstraction.

Do not split this into parallel edits; T08/T09/T10 all touch bootstrap/QuickJS/Hilt surfaces.

## T08 Requirements

Implement canonical JS API:

- `hostApi.registerScheduledHandler({ key, cron, runAt, conversationId, handler })`

Required behavior:

- `schedule_manage` permission is required at registration.
- Handler key is unique within the same plugin; duplicate semantics must be fixed by tests.
- `cron` and `runAt` are mutually exclusive; one is required.
- Bootstrap registry compiles scheduled handler descriptors into the V2 snapshot.
- Plugin enable/reload reconciles schedules; disable/suspend pauses schedules; uninstall deletes schedules; upgrade reconciles by handler key.
- Cron wakeup enters Plugin V2 scheduled handler dispatch with event payload containing at least `scheduledAt`, `jobId`, `conversationId`, and trigger source.
- Scheduled handlers can use T04 current-conversation message send. Sending still requires `send_message`.
- Handler failure is recorded in Cron execution record/runtime log path.
- Do not use legacy V1 `on_schedule` as the new production path.

Preferred minimum implementation:

- Use `CronJob.jobType = "plugin_v2_schedule"` and structured `payloadJson` for plugin schedule metadata unless a model/schema extension is strictly necessary.
- Keep persistence and scheduler access behind `CronJobRepositoryPort` and `CronSchedulerPort`.
- Add a small port/coordinator between plugin runtime and cron runtime if needed; wire via Hilt.

## T10 Requirements

Implement rich message chain:

- Define `PluginMessageSegment` or equivalent stable DTO.
- Support `text`, `image`, `file`, `mention`, `reply`, and `card`.
- Extend `hostApi.message.send({ chain })` while keeping simple `{ text }` from T04 compatible.
- Use `rich_message_send` permission for chain sends. Plain text sends continue to use `send_message`.
- Preserve current conversation constraint.
- Unsupported segment types return structured error.
- Media references must be constrained to safe refs: `plugin://package`, `plugin://workspace`, host-allowed `content://`, or HTTPS remote URL. Reject path escape and arbitrary absolute file paths.
- App Chat mapping: merge text into visible body, map image/file to `ConversationAttachment`, fallback unsupported mention/reply/card to visible markdown/text.
- QQ mapping: map text/mention/image/reply where possible or produce visible fallback. Unsupported items must produce structured warning or recorded fallback, not silent drop.

## T09 Requirements

Implement stream API:

- `hostApi.message.openStream({ markdown })`
- Stream handle methods: `append(text)`, `replace(text)`, `close()`, `fail(message)`
- Use `message_stream` permission.
- Stream is current-conversation only.
- Enforce max duration, max chunks, and max bytes.
- Unclosed stream must auto-close or fail when the handler ends; cancellation/plugin unload must close streams.
- App Chat should create/update a pending assistant message through `ConversationRepositoryPort`.
- QQ must have a fixed fallback contract: segment sends or final-on-close send. Do not assume QQ can edit messages.
- Append/replace after close and limit overflow return structured errors.
- Audit records stream id, chunk count, bytes, duration, platform, failure code.

## Tests Required

At minimum add or update:

- `PluginV2ScheduledHandlerRegistryTest`
- `PluginV2ScheduledHandlerLifecycleTest`
- `PluginV2ScheduledDispatchTest`
- `PluginMessageSegmentTest`
- `PluginV2RichMessageSendApiTest`
- `PluginV2MessageStreamApiTest`
- Platform mapping tests for App Chat and QQ fallback behavior.

Existing focused tests to protect:

- `PluginV2HostApiQuickJsCapabilitiesTest`
- `PluginV2MessageSendApiTest`
- Cron runtime tests under `feature/cron/runtime/src/test/java/**`
- App integration host capability tests under `app-integration/src/test/java/**`
- Architecture/source contract tests that guard plugin/runtime/QQ/Cron boundaries.

## Suggested Verification

Run focused tests first:

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*Scheduled*" --tests "*Stream*" --tests "*MessageSegment*" --tests "*RichMessage*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:cron:runtime:testDebugUnitTest --tests "*Cron*" --tests "*Scheduled*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app-integration:testDebugUnitTest --tests "*PluginHostCapability*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Plugin*" --tests "*Cron*" --tests "*Qq*" --tests "*AppChat*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

If time is constrained, run the focused new tests and clearly report unverified commands. Do not claim a command passed unless you ran it.

## Return Format

Reply with:

- Status: `DONE` / `DONE_WITH_CONCERNS` / `NEEDS_CONTEXT` / `BLOCKED`
- Changed files: list every changed file
- Implementation summary
- Tests run: exact command + result
- TDD evidence: red/green notes; if not achieved, say so
- Risks / remaining work
- Needs uth-docs scoped-sync: `yes/no`

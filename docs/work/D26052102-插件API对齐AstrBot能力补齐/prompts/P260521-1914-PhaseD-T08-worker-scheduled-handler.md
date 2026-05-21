# P260521-1914 Phase D T08 worker：插件定时任务回调

## 场景

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- Phase: Phase D
- Todo: `T08 插件定时任务回调`
- Worker role: implementation worker
- Git writes: forbidden. Do not commit, push, tag, merge, rebase, checkout, reset, or create/delete worktrees.

You are not alone in the codebase. Do not revert or delete existing Phase C changes. The previous broad Phase D worker left red baseline tests for T08/T09/T10; keep them and implement only the T08 slice in this worker.

## Required Reads

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/current-state.md`
4. `docs/context/09-插件平台.md`
5. `docs/context/10-Cron运行时.md`
6. `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
7. `docs/work/D26052102-插件API对齐AstrBot能力补齐/80-D26052102-T08-todo-插件定时任务回调.md`
8. Existing red baseline tests:
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2ScheduledHandlerRegistryTest.kt`
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2ScheduledHandlerLifecycleTest.kt`
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2ScheduledDispatchTest.kt`
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2ScheduledHandlerRegistryTestSupport.kt`

## Allowed Write Scope

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/**`
- `feature/cron/api/src/main/java/**`
- `feature/cron/runtime/src/main/java/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2Scheduled*.kt`
- `feature/cron/runtime/src/test/java/**`
- `app/src/test/java/com/elymbot/android/feature/cron/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`
- `app/src/test/java/com/elymbot/android/architecture/**`

## Forbidden Scope

- Do not implement T09 stream API.
- Do not implement T10 rich message chain.
- Do not delete the existing T09/T10 red baseline tests; leave them for later workers even if they do not compile under broad test globs.
- Do not edit `docs/context/**`.
- Do not add AstrBot-style aliases.
- Do not restore static APIs, service locators, handwritten runtime subgraphs, static repository facades, or production global registries.
- Do not let plugin runtime import QQ runtime implementation, Room DAO, provider secret store, or App UI.

## T08 Requirements

Implement canonical JS/Kotlin registration:

- `hostApi.registerScheduledHandler({ key, cron, runAt, conversationId, handler })`
- Kotlin input type equivalent to `ScheduledHandlerRegistrationInput`.

Required behavior:

- Registration requires `schedule_manage` at bootstrap.
- Handler key is unique within the same plugin; duplicate keys fail compile with `duplicate_schedule_handler_key`.
- `cron` and `runAt` are mutually exclusive; exactly one is required.
- Bootstrap raw registry and compiled snapshot include scheduled handler descriptors.
- Add lifecycle/reconcile component, equivalent to `PluginV2ScheduledHandlerLifecycle`.
- Use `CronJob.jobType = "plugin_v2_schedule"` and structured `payloadJson` unless a model/schema change is truly necessary.
- Reconcile creates/updates schedules by `pluginId + handlerKey + pluginVersion`.
- Plugin pause disables and cancels plugin schedule jobs.
- Plugin delete removes plugin schedule jobs.
- Add dispatch component, equivalent to `PluginV2ScheduledDispatchEngine`, that invokes only the matching V2 scheduled handler.
- Add event payload type, equivalent to `PluginV2ScheduledHandlerEvent`, containing at least `pluginId`, `handlerKey`, `jobId`, `conversationId`, `scheduledAtEpochMillis`, and `triggerSource`.
- Scheduled handler event should implement the existing plugin event payload interface used by event-aware callbacks.
- Do not use legacy V1 `on_schedule` as the production path.

## Expected Red/Green Path

First run:

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*Scheduled*" --console=plain --no-daemon --stacktrace
```

It is acceptable if this initially fails on missing T08 production symbols. Implement until the T08 scheduled tests pass.

Then run:

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*ScheduledHandler*" --tests "*ScheduledDispatch*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

If broad `*Scheduled*` also picks up unrelated T09/T10 red tests or old cron tests, report that precisely and run the narrower T08 commands above.

## Return Format

Reply with:

- Status: `DONE` / `DONE_WITH_CONCERNS` / `NEEDS_CONTEXT` / `BLOCKED`
- Changed files: list every changed file
- Implementation summary
- Tests run: exact command + result
- TDD evidence: red/green notes; if not achieved, say so
- Risks / remaining work
- Needs uth-docs scoped-sync: `yes/no`

# P260521-1200 T01b worker prompt: QuickJS callback Promise settle

## Context

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26052102-PluginAPI-align-AstrBot-capability-fill`
- Design: `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
- Todo: `docs/work/D26052102-插件API对齐AstrBot能力补齐/10-D26052102-T01-todo-宿主API异步桥与权限底座.md`
- Phase: A / T01b
- Worker role: implementation worker
- Git writes: forbidden

You are not alone in the codebase. Do not revert, overwrite, or reformat unrelated edits. There are already T01a changes in the workspace from another worker.

## Why this slice exists

The Phase A evaluator failed T01a because the Kotlin host API foundation exists, but the real QuickJS callback path still returns synchronously:

- `QuickJsPluginV2CallbackHandle.callFunction(...)` calls the JS callback and returns without waiting for a returned Promise.
- Existing tests only prove `PluginV2HostApiAsyncBridge.await(...)`; they do not prove a real QuickJS handler can `await hostApi.*` or any Promise before dispatch completes.

Your task is to close this evaluator-blocking gap without implementing T02+ business APIs.

## Narrow goal

Make QuickJS Plugin V2 callback invocation settle returned JS Promises before Kotlin dispatch treats the handler as complete.

Minimum acceptable behavior:

- If a registered QuickJS command/message callback returns a Promise, the callback invocation waits until it fulfills before returning to the dispatcher.
- If that Promise rejects, the existing callback failure path observes a failure instead of silently treating it as success.
- Add an integration test proving an `async` QuickJS handler performs a side effect after `await Promise.resolve(...)` before `PluginV2DispatchEngine.dispatchMessage(...)` returns.

Prefer reusing the existing bootstrap async pattern in `ExternalPluginScriptExecutor.kt`:

- `buildQuickJsBootstrapExecutionSource()`
- `awaitQuickJsBootstrapCompletion(...)`
- `buildQuickJsBootstrapCompletionStatePollSource()`

Context7 check for QuickJS confirmed the general native rule: Promise jobs/microtasks must be drained/executed before Promise settlement is observable. Use the repo wrapper's existing local pattern first.

## Allowed write scope

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/ExternalPluginScriptExecutor.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostApiFoundation.kt` only if needed for a narrow adapter seam
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`
- `app/src/test/resources/plugin-v2-message/**` or a similarly local Plugin V2 QuickJS fixture

If you need any file outside this list, return `NEEDS_CONTEXT` with the exact reason.

## Forbidden

- No Git writes.
- No Hilt edits.
- No dependency changes.
- No concrete business host APIs: `fetch`, provider/model read, send message, history, LLM, cron, streaming, rich message, agent, or filters.
- No static service locator, new global singleton allowlist entry, handwritten production DI graph, or production `PluginExecutionHostApi` hot-path.
- Do not rename or move broad runtime classes.

## Suggested test path

Create or extend a gated QuickJS integration test under `app/src/test/java/com/elymbot/android/runtime/plugin/`.

Recommended fixture shape:

```js
export default async function bootstrap(hostApi) {
  hostApi.registerCommandHandler({
    command: "async-promise",
    handler: async (event) => {
      await Promise.resolve("settled");
      event.reply("settled-after-await");
    }
  });
}
```

Then dispatch `/async-promise` and assert `result.commandResponse.text == "settled-after-await"`.

Also add a rejection case if feasible:

```js
handler: async () => {
  await Promise.resolve();
  throw new Error("rejected-after-await");
}
```

Assert the dispatch/log path observes failure according to existing Plugin V2 dispatch semantics. If the current dispatcher has no clear failure return, assert the existing runtime log contains the callback error category/message.

## Verification

Run the narrow tests first:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*QuickJs*Callback*" --console=plain --no-daemon --stacktrace
```

Then run:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*PluginV2HostApi*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

If QuickJS is unavailable on this machine and tests are gated/skipped by `PluginV2QuickJsTestGate`, still compile and report the gate result explicitly.

## Return format

Return one of:

- `DONE`
- `DONE_WITH_CONCERNS`
- `NEEDS_CONTEXT`
- `BLOCKED`

Then include:

- modified files
- tests added/updated
- red-green evidence
- verification commands and results
- remaining risks

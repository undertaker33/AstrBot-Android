# P260521-1232 T02 worker prompt: host network API

## Context

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26052102-插件API对齐AstrBot能力补齐`
- Design: `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
- Todo: `docs/work/D26052102-插件API对齐AstrBot能力补齐/20-D26052102-T02-todo-宿主网络请求代理.md`
- Phase: B / T02
- Worker role: implementation worker
- Git writes: forbidden

You are not alone in the codebase. Do not revert or overwrite unrelated edits. T01 is already in the workspace and passed Phase A evaluator with non-blocking risk. Treat those files as the base you must build on.

## Current known T01 facts

- `PluginV2HostApiFoundation.kt` provides `PluginV2HostApiFacade`, permission policy, async bridge, structured errors, and audit logger.
- `ExternalPluginScriptExecutor.kt` now waits returned QuickJS callback Promises.
- `PluginHostCapabilityModule.kt` already provides the T01 facade stack.
- Verification already passed for T01:
  - `:app:testDebugUnitTest --tests "*QuickJs*Callback*"`
  - `:app:testDebugUnitTest --tests "*PluginV2HostApi*"`
  - `:feature:plugin:runtime:testDebugUnitTest --tests "*PluginV2HostApi*"`
  - `architectureCheck`

## Goal

Implement the T02 active host network API:

- JS `hostApi.fetch(request)`
- JS canonical synonym `hostApi.network.request(request)`
- All requests go through host-owned `RuntimeNetworkTransport`; plugin runtime must not instantiate OkHttp or expose a raw network client.
- Use `network_request` permission through the T01 facade / policy.

## Allowed write scope

- `feature/plugin/api/src/main/java/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/**`
- `core/network/src/main/java/**` only for a needed runtime capability enum / timeout profile / model extension.
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `feature/plugin/runtime/src/test/java/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`
- `app/src/test/java/com/elymbot/android/runtime/network/**`
- `app/src/test/java/com/elymbot/android/architecture/**` only for source contracts directly required by T02.

If you need any file outside this list, return `NEEDS_CONTEXT` with the exact file and reason.

## Forbidden

- No Git writes.
- No Provider/model read API, message send API, conversation history API, LLM API, Cron, streaming, rich message, Agent, or filter AST.
- No Web API registration, local server, HTTP endpoint listener, Unix socket, Android content provider access, or raw plugin-owned network client.
- No static service locator, handwritten production DI graph, global singleton allowlist, or `PluginExecutionHostApi` hot-path revival.
- Do not add new dependencies.

## Required behavior

1. JS API
   - `await hostApi.fetch(request)` returns a JS object.
   - `await hostApi.network.request(request)` returns the same shape.
   - Do not add AstrBot-style aliases.

2. Request model
   - Input supports `url`, `method`, `headers`, `bodyText`, `bodyBase64`, `timeoutMs`.
   - Default method: `GET`.
   - Allowed methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`.
   - Header names and values must be validated for max length and illegal control characters.
   - `bodyText` and `bodyBase64` are mutually exclusive.

3. Safety and governance
   - Only `http` and `https` schemes.
   - Block `localhost`, loopback, link-local, private IPv4/IPv6, and obvious local hostnames.
   - Support manifest domain allowlist. If no current manifest field exists, add the smallest typed model / parsing path needed and test it; do not fake it via permission description text.
   - Limit per-plugin concurrent network requests.
   - Clamp per-request timeout to a fixed upper bound.

4. Host execution
   - Execute through injected `RuntimeNetworkTransport`.
   - Prefer adding a `RuntimeNetworkCapability.PLUGIN_HOST_API` and timeout profile if that is the cleanest local fit.
   - Response body limit defaults to 1 MB.

5. Response/error model
   - Success returns `status`, `headers`, `bodyText`, `bodyBase64`, `contentType`, `elapsedMs`.
   - Body over 1 MB returns structured error code `network_response_too_large`.
   - Network failures return structured errors without exposing OkHttp stack traces or internal class names.
   - Permission denial must come from the T01 permission path and use `network_request`.
   - Failure writes audit / plugin runtime log.

## Required tests

Use TDD where feasible. Add focused JVM tests:

- `PluginV2HostNetworkApiTest`
  - successful GET returns status/header/body.
  - POST body is passed to host transport.
  - non-http/https scheme is rejected.
  - localhost / private network is rejected.
  - domain outside manifest allowlist is rejected.
  - response body over 1 MB returns `network_response_too_large`.
  - timeout returns structured error.
  - QuickJS can `await hostApi.fetch(...)` and continue the handler before dispatch returns.

- `RuntimeNetworkModelsTest` if you add a new runtime capability or timeout profile.

- architecture/source contract:
  - QuickJS bridge / plugin runtime does not instantiate OkHttp.
  - plugin runtime does not add Android network permission or a bypass path.

## Suggested implementation notes

- Build on `PluginV2HostApiFacade.call(...)`; do not bypass it.
- The JS bridge currently binds bootstrap host API methods in `ExternalPluginScriptExecutor.kt`.
- `PluginV2RuntimeLoader` creates `PluginV2BootstrapHostApi`; if you need production wiring, thread injected host network API dependencies through loader/factory/Hilt instead of static access.
- For tests, a fake `RuntimeNetworkTransport` is preferred over real network.
- Keep public API names canonical and narrow.

## Verification

Run at minimum:

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*Network*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*RuntimeNetworkModelsTest*" --tests "*Plugin*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

If a command is too broad or times out, run the narrower failing/passing class and explain exactly what remains unverified.

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

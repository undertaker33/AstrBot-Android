# P260521-1336 T03 worker prompt: provider read API

## Context

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/work/D26052102-插件API对齐AstrBot能力补齐`
- Design: `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
- Todo: `docs/work/D26052102-插件API对齐AstrBot能力补齐/30-D26052102-T03-todo-Provider模型只读查询.md`
- Phase: B / T03
- Worker role: implementation worker
- Git writes: forbidden

You are not alone in the codebase. Do not revert or overwrite unrelated edits. T01 and T02 are already in the workspace and are the base for this task.

## Current base facts

- `PluginV2HostApiFoundation.kt` owns `PluginV2HostApiFacade`, permission policy, async bridge, structured errors, audit logging, and well-known permissions.
- `PluginV2HostNetworkApi.kt` is the current pattern for a host API capability class.
- `ProviderRuntimePort` exists at `feature/provider/api/src/main/java/com/elymbot/android/feature/provider/api/runtime/ProviderRuntimePort.kt`.
- `ProviderProfile` contains sensitive fields such as `baseUrl` and `apiKey`; T03 must never expose them to plugin JS or logs.

## Goal

Implement the provider/model read capability behind the T01 host API facade:

- Canonical JS target for later controller integration: `hostApi.providers.list()` and `hostApi.providers.models({ providerId })`.
- Use `provider_read` permission through `PluginV2HostApiFacade`.
- Return only sanitized provider/model DTOs.

## Strict write scope for this worker

You may modify or add only these files:

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2ProviderReadApi.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2ProviderReadApiTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2ProviderReadApiTest.kt` only if a feature-runtime test cannot cover the needed behavior.
- `app/src/test/java/com/elymbot/android/architecture/PluginV2HostApiArchitectureContractTest.kt` only for a narrow source contract directly related to provider-read secret leakage or data-layer bypass.

Do not edit shared integration files. The controller will wire them after workers finish:

- Do not edit `ExternalPluginScriptExecutor.kt`.
- Do not edit `PluginV2BootstrapHostApi.kt`.
- Do not edit `PluginV2RuntimeLoader.kt`.
- Do not edit `PluginHostCapabilityModule.kt`.
- Do not edit `PluginRuntimeModule.kt`.
- Do not edit `PluginDataWiringFactory.kt`.
- Do not edit provider data/runtime implementations.

If you need any other file, return `NEEDS_CONTEXT` with the exact file and reason.

## Required behavior

1. Define a small host-facing provider read port or function type in `PluginV2ProviderReadApi.kt` if needed. Keep it inside plugin runtime for this slice; the controller may later adapt it to Hilt.
2. Define sanitized DTOs:
   - provider summary: `providerId`, `displayName`, `enabled`, `capabilities`, `defaultModelId`, `modelCount`
   - model summary: `modelId`, `displayName`, `capabilities`, `contextWindow`, `supportsToolCalling`, `supportsStreaming`
3. `list(context)` must call `PluginV2HostApiFacade.call(...)` with API name `hostApi.providers.list` and permission `provider_read`.
4. `models(context, request)` must call `PluginV2HostApiFacade.call(...)` with API name `hostApi.providers.models` and permission `provider_read`.
5. Missing provider semantics must be fixed and tested: either structured `provider_not_found` or empty models. Prefer structured `provider_not_found` if it matches the local error style.
6. Do not leak `apiKey`, `baseUrl`, raw headers, credentials, Room entity names, DAO names, or stack traces in returned DTOs or structured errors.

## Required tests

Add focused tests for:

- list returns sanitized provider summaries.
- models returns sanitized model summaries for the selected provider.
- permission denial returns structured `permission_denied`.
- missing provider returns the chosen fixed structured semantics.
- returned objects and error details do not contain `apiKey`, `baseUrl`, `headers`, `credential`, `ProviderDao`, or raw internal class names.

Use fake in-memory providers/models. Do not call real network or real provider probes.

## Verification

Run at minimum:

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*ProviderRead*" --console=plain --no-daemon --stacktrace --max-workers=1
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

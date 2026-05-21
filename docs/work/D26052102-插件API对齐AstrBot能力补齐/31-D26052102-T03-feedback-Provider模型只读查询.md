# D26052102-T03 Feedback：Provider 模型只读查询

更新时间：2026-05-21 16:22 +08:00

## 场景

- Scene：`uth-dev`
- Mode：`formal-dev`
- Todo：`30-D26052102-T03-todo-Provider模型只读查询.md`
- Worker prompt：`prompts/P260521-1336-T03-worker-provider-read-api.md`
- Evaluator：`019e4981-64d9-7be2-badc-c3d4d0539a40`
- Git 写入：未执行

## 实现摘要

- 新增 `PluginV2ProviderReadApi`，暴露 `hostApi.providers.list()` 和 `hostApi.providers.models({ providerId })`。
- production wiring 通过 `ProviderRuntimePort` 生成 provider / model 只读摘要。
- 返回内容限定为 provider id、display name、enabled、capabilities、default model、model capability 等非 secret 字段，不暴露 API key、base URL secret 或内部 credential。
- QuickJS bridge 已能把 provider / model 查询结果转成 JS object / array 返回给插件。

## 变更文件

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2ProviderReadApi.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/ExternalPluginScriptExecutor.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/PluginHostCapabilityModule.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/PluginDataWiringFactory.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/PluginRuntimeModule.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2ProviderReadApiTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostApiQuickJsCapabilitiesTest.kt`

## 验证

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*HostNetwork*" --tests "*ProviderRead*" --tests "*MessageSend*" --tests "*ConversationHistory*" --tests "*PluginV2HostApiQuickJsCapabilitiesTest*" --console=plain --no-daemon --stacktrace` | pass | Phase B runtime 能力集合通过 |
| `.\gradlew.bat :app:testDebugUnitTest --tests "*RuntimeNetworkModelsTest*" --tests "*PluginV2HostApiArchitectureContractTest*" --tests "*PluginPackageContractJsonTest*" --tests "*PluginV2BootstrapHostApiTest*" --tests "*PluginV2QuickJsCallbackLifecycleTest*" --tests "*PluginV2HostApiAsyncBridgeTest*" --tests "*PluginV2HostApiAuditLoggerTest*" --tests "*PluginV2HostApiPermissionPolicyTest*" --console=plain --no-daemon --stacktrace` | pass | package contract、bootstrap host API、权限/审计/异步桥合同通过 |
| `.\gradlew.bat :app-integration:compileDebugKotlin :app:compileDebugKotlin architectureCheck --console=plain --no-daemon --stacktrace` | pass | Hilt wiring、app compile、架构合同通过 |
| `.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace` | pass | 日志：`build/reports/D26052102-phase-b-clean-assembleDebug.log`；warning / deprecated / exception 扫描为 0 |
| `.\gradlew.bat modulePluginCheck --console=plain --no-daemon --stacktrace` | pass | 2026-05-21 16:39 收口既有 MissingTranslation 后通过；日志：`build/reports/D26052102-modulePluginCheck-translation-closeout.log` |

## Evaluator 结论

复核结论：`PASS`。T03 未发现 Phase B 阻塞项。

## 未验证项

- provider 可用性来自当前 `ProviderRuntimePort` snapshot；未做真实 provider 网络调用。
- `modulePluginCheck` 已在翻译资源收口后通过。

## 风险与回滚

- 当前只读摘要只覆盖已知 provider runtime 字段；未来若 provider 支持多模型列表，需要扩展 production adapter。
- 回滚 `PluginV2ProviderReadApi` wiring 会让插件失去 provider/model 查询能力，但不影响 provider runtime 主链路。

## 后续

- `T06` 直接 LLM 调用可基于本 Todo 的 provider/model 非 secret 摘要继续实施。
- `Needs uth-docs scoped-sync`：插件平台上下文需要在本任务整体验收后同步 Phase B 当前事实。

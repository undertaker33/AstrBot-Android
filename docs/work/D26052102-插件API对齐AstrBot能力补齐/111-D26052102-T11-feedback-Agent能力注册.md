# D26052102-T11 Feedback：Agent 能力注册

更新时间：2026-05-22 14:07 +08:00

## 场景

- Scene：`uth-dev`
- Mode：`formal-dev`
- Todo：`110-D26052102-T11-todo-Agent能力注册.md`
- Worker prompt：
  - `prompts/P260522-0812-T11-worker-Agent能力注册.md`
  - `prompts/P260522-0924-T11-worker2-Agent能力注册-rework.md`
- Worker：
  - 原 worker：`019e4d08-07c8-72e1-8dca-252abf96af57`
  - 替代 worker：`019e4d49-9630-7e93-824b-c658d1c4a579`
- Evaluator：`019e4d22-d799-7421-a4ad-a9f072a4b860`
- Git 写入：未执行

## 实现摘要

- 新增 `hostApi.registerAgent({ key, systemPrompt, tools, model, handler })` 注册面，Agent key 在同一插件内唯一，并编译进 V2 registry snapshot。
- 新增 `hostApi.agent.run({ key, input })` 与 handler 内 `event.agent.run(input)` 生产可达路径，注册 handler 会参与执行。
- 新增 `PluginV2AgentRunner` / `PluginV2AgentInvoker`，通过 `PluginV2RuntimeLoader` 接入 active runtime snapshot、host LLM port、tool executor、ToolSource availability、权限 context 和 runtime log bus。
- Agent run 默认 `bypassPluginLlmHooks = true`，避免同插件 LLM hook 递归。
- Agent tool policy 限制插件自身 tools、host builtin tools 和当前 availability 允许的 future ToolSource；保留 max tool calls、max depth、timeout、token/cost guard 与审计字段。
- 初始 evaluator 发现 registry/runner 不具备生产可达性；Rework 1 已补 production invocation path 并由原 evaluator 复查通过。

## 变更文件

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentRuntime.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2BootstrapHostApi.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/ExternalPluginScriptExecutor.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RawRegistry.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RegistryCompiler.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HandlerRegistry.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RuntimeLoader.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/PluginRuntimeModule.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/PluginDataWiringFactory.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentRegistryTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentRunTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentToolPolicyTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentInvocationTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentTestSupport.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostApiQuickJsCapabilitiesTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2RuntimeLoaderTest.kt`

## 验证

| 命令 | 结果 | 说明 |
| --- | --- | --- |
| `.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Agent*' --tests '*Filter*' --tests '*PluginV2HostApiQuickJsCapabilitiesTest*' --console=plain --no-daemon --stacktrace` | pass | 主控合并验证；日志 `build/reports/D26052102-T11-T12-feature-plugin-runtime-tests.log` |
| `.\gradlew.bat :app:testDebugUnitTest --tests '*PluginV2*' --tests '*Plugin*' --tests '*ToolSource*' --console=plain --no-daemon --stacktrace` | pass | 主控 app 侧 Plugin / ToolSource 回归；日志 `build/reports/D26052102-T11-T12-app-tests.log` |
| `.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace` | pass | 主控结构门禁；日志 `build/reports/D26052102-T11-T12-architectureCheck.log` |
| `.\gradlew.bat modulePluginCheck --console=plain --no-daemon --stacktrace` | pass | 主控 plugin 模块组门禁；日志 `build/reports/D26052102-T11-T12-modulePluginCheck.log` |
| `.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace` | pass | 主控全量构建门禁；日志 `build/reports/D26052102-T11-T12-clean-assembleDebug.log` |

上述 5 个主控日志的 `warning` / `deprecated` / `exception` / `failed` 扫描计数均为 0。

## Evaluator 结论

复核结论：`PASS`。

已关闭初始三个 blocker：

- QuickJS handler 中 `agent.run(input)` 已有生产可达路径。
- `PluginV2AgentRunner` 已通过 `PluginV2RuntimeLoader` / Hilt-owned runtime 边界接入。
- compiled `agentHandlers` 已由 `PluginV2AgentInvoker` 消费，不再只是 registry snapshot 数据。

## 未验证项

- 未做真实 provider / 真实外部 ToolSource 设备级联调；本 Todo 以 fake LLM / fake tool / host runtime path 单测和构建门禁为验收证据。
- 未执行 Git 写入。

## 风险与回滚

- Agent 能力会放大 LLM 成本和 tool 调用风险；当前通过 max tool calls、max depth、timeout、token/cost guard 和 ToolSource availability 收敛。
- 回滚本 Todo 会移除插件 Agent 注册与 `agent.run` 能力，但不应影响 T01-T10 已完成的宿主 API。

## 后续

- `Needs uth-docs scoped-sync`：插件平台上下文需要在 D26052102 整体验收后同步 T11 当前代码事实。

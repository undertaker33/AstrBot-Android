# D26052102 T11 Worker 2 Prompt - Agent 能力注册 Rework

创建时间：2026-05-22 09:24 +08:00

## 替换例外

原 T11 worker：`019e4d08-07c8-72e1-8dca-252abf96af57`

原 T11 evaluator：`019e4d22-d799-7421-a4ad-a9f072a4b860`

替换原因：原 worker 在接收 Rework 1 后两次因平台 compact 连接错误失败，无法恢复继续同一 rework。主控已暂停并询问用户，用户明确确认“新派一个”。本 prompt 是经用户确认后的替代 worker prompt。

不得执行 Git 写入，包括 commit、push、merge、rebase、tag、branch/worktree 创建或删除。

你不是独占代码库。当前工作区已有原 worker 的 T11 半成品改动；不要 revert 这些改动，先把它们当作当前基线理解并在其上修复。

## 必读上下文

先按顺序读取：

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/current-state.md`
4. `docs/context/09-插件平台.md`
5. `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
6. `docs/work/D26052102-插件API对齐AstrBot能力补齐/110-D26052102-T11-todo-Agent能力注册.md`
7. 原 worker prompt：`docs/work/D26052102-插件API对齐AstrBot能力补齐/prompts/P260522-0812-T11-worker-Agent能力注册.md`
8. 当前 T11 改动文件：
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RawRegistry.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HandlerRegistry.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RegistryCompiler.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2BootstrapHostApi.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/ExternalPluginScriptExecutor.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentRuntime.kt`
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentRegistryTest.kt`
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentRunTest.kt`
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentToolPolicyTest.kt`
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2AgentTestSupport.kt`

## 当前 evaluator blocker

只读 evaluator 判定 T11 `FAIL`，必须关闭下面三个 blocker：

1. `ExternalPluginScriptExecutor.kt` 只绑定了 `hostApi.registerAgent(...)`，没有暴露 Todo 要求的 handler 内 `agent.run(input)` 运行入口；当前 JS 插件无法从注册 handler 触发 `PluginV2AgentRunner`。
2. `PluginV2AgentRunner` 只有生产定义和测试直连调用；生产引用扫描只命中定义本身，没有 Hilt wiring、host API bridge、dispatcher 或 handler invocation caller，因此 registry 中的 Agent handler 实际不可运行。
3. `agentHandlers` 被编译进 snapshot，但现有 dispatch/lifecycle/runtime loader 路径没有消费该列表；注册结果停留在 registry snapshot，不满足“插件可注册并运行受控 Agent”。

## Rework 目标

在原 worker 已完成的 registry / runner / policy 基础上，补齐生产可达路径：

- 插件注册 `hostApi.registerAgent({ key, systemPrompt, tools, model, handler })` 后，compiled `agentHandlers` 必须被一个明确的 production invoker/dispatcher 消费。
- handler 执行时必须获得受控 agent context，使 handler 可调用 `agent.run(input)`。
- `agent.run(input)` 必须进入 `PluginV2AgentRunner`，并传入当前 `PluginV2ActiveRuntimeSnapshot`、host LLM port、tool executor、权限 context 和审计 log bus。
- 必须保留 T11 设计边界：Agent 是插件声明的 LLM orchestration preset，不是新平台 adapter。

如果现有架构没有独立 Agent event surface，优先实现最小明确的 host API invoker，例如 `hostApi.agent.run({ key, input })` 或等价 runtime entrypoint；但注册 handler 必须参与执行，不能只直连 runner。

## 非目标

- 不实现 T12 filter AST。
- 不新增 Web API 注册。
- 不新增平台 adapter 注册。
- 不开放 DB 直连、DAO、SQL、provider secret。
- 不暴露 AstrBot 风格别名。
- 不绕过 `FutureToolSourceRegistry` / `PluginV2ToolAvailabilitySnapshot`。
- 不扩大到 UI、QQ、Cron、Backup、版本发布或 Git closure。

## 允许修改范围

- `feature/plugin/api/src/main/java/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/**`
- `feature/provider/api/src/main/java/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `feature/plugin/runtime/src/test/java/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/toolsource/**`

## TDD 要求

使用 `uth-sp-test-driven-development`：

1. 先补 RED 测试，测试必须能在当前半成品基线上失败，并且失败原因是 evaluator 指出的生产可达性缺口。
2. 推荐新增或扩展 QuickJS / production-path 测试，覆盖：
   - `hostApi.registerAgent(... handler: async ({ agent }) => agent.run(input))`
   - 真实触发 compiled agent handler。
   - `agent.run` 进入 `PluginV2AgentRunner`。
   - 默认绕过 LLM hooks。
   - 授权 tool loop 成功，未授权 ToolSource 被拒绝。
3. 修复后保持原有 Agent registry/run/tool policy 测试通过。

## 推荐验证命令

至少运行：

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Agent*' --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Llm*' --tests '*PluginV2HostApiQuickJsCapabilitiesTest*' --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests '*PluginV2Tool*' --tests '*PluginV2HostApi*' --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

PowerShell 若通配符展开异常，可以用 `--tests=*Agent*` 或精确类名，但必须说明等价覆盖关系。

## 回传格式

完成后按以下格式回复主控：

```text
STATUS: DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED
WORKER_ID: <你的 agent id 或稳定标识>
SUBSTITUTION: replacement for 019e4d08-07c8-72e1-8dca-252abf96af57, approved by user

Changed files:
- ...

Implementation summary:
- ...

Rework blocker closure:
- Blocker 1:
- Blocker 2:
- Blocker 3:

TDD evidence:
- RED command:
- Expected failure:
- GREEN command:
- Result:

Verification:
- command:
- result:
- warning/deprecated/exception/failed scan if available:

Open concerns:
- ...

Handoff notes for evaluator:
- ...
```

如果需求、边界或生产接入点不清楚，先返回 `NEEDS_CONTEXT`；不要自行扩大范围。

# D26052102 T11 Worker Prompt - Agent 能力注册

创建时间：2026-05-22 08:12 +08:00

## 角色

你是 `D26052102` 的 T11 实现 worker。当前窗口是总控；你只负责本 prompt 声明的 T11 写入范围。

不得执行 Git 写入，包括 commit、push、merge、rebase、tag、branch/worktree 创建或删除。

你不是独占代码库。工作区可能有主控或其他 worker 的改动；不要 revert 你没有产生的改动，遇到冲突时先适配现状。

## 必读上下文

先按顺序读取：

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/current-state.md`
4. `docs/context/09-插件平台.md`
5. `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
6. `docs/work/D26052102-插件API对齐AstrBot能力补齐/110-D26052102-T11-todo-Agent能力注册.md`
7. 与 T11 直接相关的源码与测试：
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2BootstrapHostApi.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/ExternalPluginScriptExecutor.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RawRegistry.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RegistryCompiler.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HandlerRegistry.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2ToolRegistry.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2ToolLoopCoordinator.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostLlmApi.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/RuntimeLlmOrchestratorPort.kt`
   - `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostLlmApiTest.kt`
   - `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2ToolLoopCoordinatorTest.kt`
   - `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2ToolRegistryTest.kt`
   - `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2ToolAvailabilityTest.kt`

## 任务边界

目标：实现插件声明式 Agent 能力注册，让插件可以注册 `hostApi.registerAgent({ key, systemPrompt, tools, model, handler })`，并在 handler 中通过受控 `agent.run(input)` 运行宿主 LLM + 已授权 tool loop。

前置事实：

- T01、T03、T06 已完成；可复用 Host API facade、provider/model read、`hostApi.callLlm` 相关实现。
- T11 依赖现有 tool registry / ToolSource availability，不得绕过它。
- JS API 只使用 ElymBot canonical host API 命名，不提供 AstrBot 风格别名。

非目标：

- 不做 Web API 注册。
- 不做平台 adapter 注册。
- 不开放 DB 直连、provider secret、raw Room/DAO。
- 不实现长期自治、多 Agent 后台常驻、无限递归。
- 不实现 T12 filter AST；如发现 filter 相关缺口，只记录给主控，不要顺手改。

## 允许修改范围

- `feature/plugin/api/src/main/java/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/**`
- `feature/provider/api/src/main/java/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `feature/plugin/runtime/src/test/java/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/toolsource/**`

禁止修改：

- `docs/context/**`
- 其他任务包
- `docs/archive/**`
- Git 元数据
- 与 T11 无关的 UI、QQ、Cron、Backup、release/version 文件

## 必须实现

1. JS API
   - 暴露 `hostApi.registerAgent({ key, systemPrompt, tools, model, handler })`。
   - handler 中可调用 `agent.run(input)`。
   - 不暴露 AstrBot 风格别名。

2. Agent registry
   - Agent key 在同一 plugin 内唯一。
   - 编译进 V2 registry snapshot。
   - plugin unload 时 runtime agent state 可清理，不能留下全局状态。

3. Tool allow policy
   - Agent 只能调用插件自身工具、host builtin tools、当前 config/persona 允许的 ToolSource。
   - MCP / Skill / Web Search / Active Capability 需通过 `FutureToolSourceRegistry` availability 或现有 `PluginV2ToolAvailabilitySnapshot`。
   - 不允许通过 tool name 字符串绕过 reserved source kind。

4. LLM orchestration
   - 复用 T06 的 host LLM port / `RuntimeLlmOrchestratorPort` 现有边界。
   - Agent run 默认绕过插件 LLM hooks，避免同插件 hook 递归。
   - 支持 tool call loop，并实现 max tool calls、max depth、timeout、max tokens/cost guard 中本地可验证的最小闭环。

5. 审计与失败
   - 记录 agent key、tool call count、provider/model、usage、duration、failureCode。
   - tool failure 语义固定并有测试：要么可注入下一轮 LLM，要么中止；不要含糊。

## TDD 要求

使用 `uth-sp-test-driven-development`：

1. 先补失败测试，再改生产代码。
2. 至少覆盖 Todo 要求中的三类测试：
   - `PluginV2AgentRegistryTest`
   - `PluginV2AgentRunTest`
   - `PluginV2AgentToolPolicyTest`
3. 记录 red 命令和失败原因；如果测试一开始就通过，修正测试后重跑直到能证明缺口存在。

## 推荐验证命令

优先按从窄到宽运行：

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*Agent*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*PluginV2Agent*" --tests "*ToolSource*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

如果你触及 tool registry 或 host LLM API，请加跑相关现有测试：

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*Llm*" --tests "*PluginV2HostApiQuickJsCapabilitiesTest*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*PluginV2Tool*" --tests "*PluginV2HostApi*" --console=plain --no-daemon --stacktrace
```

## 回传格式

完成后按以下格式回复主控：

```text
STATUS: DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED
WORKER_ID: <你的 agent id 或稳定标识>

Changed files:
- ...

Implementation summary:
- ...

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

如果遇到需求、边界或前置实现不清楚，先返回 `NEEDS_CONTEXT`，不要扩大范围自行设计。

## Rework 1 - 2026-05-22 08:49

Reason:

T11 evaluator `D26052102-T11-readonly-evaluator-codex-20260522` 结论为 `FAIL`。当前实现完成了 `hostApi.registerAgent(...)`、registry snapshot、runner 与局部测试，但缺少生产可达的 Agent handler invocation / `agent.run(input)` wiring，不满足 Todo 中“handler 可调用 `agent.run(input)`”和“插件可注册并运行受控 Agent”的完成判定。

Additional instructions:

1. 不要换 worker，不要执行 Git 写入。
2. 先补 RED 测试，测试必须证明当前缺口：
   - JS/QuickJS 或等价 production path 中，插件注册 `hostApi.registerAgent({ key, systemPrompt, tools, model, handler })` 后，handler 内可调用受控 `agent.run(input)`。
   - compiled `agentHandlers` 不只是进入 snapshot，而是有生产可达 invoker/dispatcher 消费。
3. 增加生产 wiring：
   - 从 compiled `agentHandlers` 找到目标 Agent handler callback。
   - 调用 handler 时注入受控 agent context，使 `agent.run(input)` 进入 `PluginV2AgentRunner`。
   - `PluginV2AgentRunner` 必须获得当前 `PluginV2ActiveRuntimeSnapshot`、host LLM port、tool executor、权限 context 与审计 log bus。
4. 保持 T11 边界：
   - 不实现 T12 filter AST。
   - 不新增 Web API 注册、平台 adapter 注册、DB 直连、provider secret 读取或 AstrBot 风格别名。
   - 不绕过 `FutureToolSourceRegistry` / `PluginV2ToolAvailabilitySnapshot`。
5. 如果现有架构没有“独立 Agent event surface”，优先实现最小明确的 host API invoker，例如通过 `hostApi.agent.run({ key, input })` 或现有 dispatch envelope 的明确 entrypoint 触发已注册 handler；但必须保证注册 handler 参与执行，而不是只直连 runner。

Validation:

至少重新运行：

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Agent*' --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Llm*' --tests '*PluginV2HostApiQuickJsCapabilitiesTest*' --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests '*PluginV2Tool*' --tests '*PluginV2HostApi*' --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

PowerShell 若通配符展开异常，可以使用 `--tests=*Agent*` 或精确类名，但回传必须说明等价覆盖关系。

Return requirements:

按原回传格式回复，并额外包含：

- Rework RED 测试命令与失败原因。
- 新增 production wiring 的入口路径。
- evaluator 三个 blocker 分别如何被关闭。
- 仍未覆盖的风险；如果没有，写 `none`。

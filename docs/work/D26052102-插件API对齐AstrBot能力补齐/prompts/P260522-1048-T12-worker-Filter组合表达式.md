# D26052102 T12 Worker Prompt - Filter 组合表达式

创建时间：2026-05-22 10:48 +08:00

## 角色

你是 `D26052102` 的 T12 实现 worker。当前窗口是总控；你只负责本 prompt 声明的 T12 写入范围。

不得执行 Git 写入，包括 commit、push、merge、rebase、tag、branch/worktree 创建或删除。

你不是独占代码库。当前工作区已有 T11 Agent 能力注册的未提交改动，并已通过 evaluator 复查。不要 revert、覆盖或重写 T11 改动；如果同一文件需要继续编辑，先适配当前内容。

## 必读上下文

先按顺序读取：

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/current-state.md`
4. `docs/context/09-插件平台.md`
5. `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
6. `docs/work/D26052102-插件API对齐AstrBot能力补齐/120-D26052102-T12-todo-Filter组合表达式.md`
7. 当前相关源码和测试：
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RawRegistry.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HandlerRegistry.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RegistryCompiler.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2FilterEvaluator.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2BootstrapHostApi.kt`
   - `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/ExternalPluginScriptExecutor.kt`
   - `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2FilterEvaluatorTest.kt`
   - `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2RegistryCompilerTest.kt`
   - `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2BootstrapHostApiTest.kt`

## 任务目标

在保持旧 `declaredFilters` 线性 AND 行为兼容的前提下，为 Plugin V2 handler 增加 filter AST：

- `allOf`
- `anyOf`
- `not`
- builtin filter：`eventMessageType`、`platformAdapterType`、`permissionType`
- custom filter，沿用现有 callback、timeout 和 failure-stop 语义

## 非目标

- 不改变旧插件 `declaredFilters: []` 行为。
- 不改变 custom filter timeout 和 failure-stop 语义。
- 不实现或修改 T11 Agent 能力语义。
- 不新增平台 adapter。
- 不新增权限体系，只复用现有 permission filter。
- 不执行 Git 写入。

## 允许修改范围

- `feature/plugin/api/src/main/java/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2BootstrapHostApi.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/ExternalPluginScriptExecutor.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HandlerRegistry.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RawRegistry.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RegistryCompiler.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2FilterEvaluator.kt`
- `feature/plugin/runtime/src/test/java/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`

禁止修改：

- `docs/context/**`
- 其他任务包
- `docs/archive/**`
- Git 元数据
- 与 T12 无关的 Agent runner、LLM provider、ToolSource execution、UI、QQ、Cron、Backup、release/version 文件

## 必须实现

1. Filter AST DTO
   - 支持 `allOf`。
   - 支持 `anyOf`。
   - 支持 `not`。
   - 支持 builtin filter：`eventMessageType`、`platformAdapterType`、`permissionType`。
   - 支持 custom filter，并沿用 handler callback。

2. 兼容编译
   - 旧 `declaredFilters` 编译为 `allOf`。
   - 新 `filters` AST 与旧字段不能产生歧义；如果同时存在，必须固定语义。推荐拒绝同时声明并给 diagnostic / immediate registration error，除非现有 API 结构更适合明确定义优先级。
   - handler registry snapshot 保持可冻结、可测试。

3. Evaluator
   - `allOf` 全部通过才通过。
   - `anyOf` 至少一个通过。
   - `not` 反转结果。
   - custom filter timeout 仍为现有 `CUSTOM_FILTER_TIMEOUT_MS`。
   - custom filter exception 仍返回 user visible failure。
   - custom filter false 在 `anyOf` 中不影响其他分支继续求值。

4. 日志
   - filter reject 记录 reasonCode 和 AST path。
   - custom filter failure 保留现有 error log。

## TDD 要求

使用 `uth-sp-test-driven-development`：

1. 先补 RED 测试，再改生产代码。
2. 至少覆盖 Todo 要求中的三类测试：
   - `PluginV2FilterAstCompilerTest`
   - `PluginV2FilterEvaluatorAstTest`
   - compatibility test：现有 message / command / regex filter 测试保持通过
3. 记录 RED 命令和失败原因；如果测试一开始就通过，修正测试后重跑直到能证明缺口存在。

## 推荐验证命令

优先按从窄到宽运行：

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Filter*' --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests '*PluginV2*' --tests '*Plugin*' --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

如果你触及 bootstrap/parser 或 shared registry，请加跑：

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Agent*' --tests '*PluginV2HostApiQuickJsCapabilitiesTest*' --console=plain --no-daemon --stacktrace
```

PowerShell 若通配符展开异常，可以使用 `--tests=*Filter*` 或精确类名，但回传必须说明等价覆盖关系。

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

Compatibility evidence:
- old declaredFilters:
- message / command / regex filter:

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

## Rework 1 - 2026-05-22 11:23

Reason:

T12 evaluator `codex-readonly-D26052102-T12-20260522` 结论为 `FAIL`。当前 AST 实现让旧 `declaredFilters` 按声明顺序进入 `AllOf` 并按 AST 子节点顺序执行，改变了旧 evaluator 的“builtin 先于 custom”兼容语义。旧测试 `PluginV2FilterEvaluatorTest.evaluate_rejects_with_fixed_builtin_order_before_custom_filter` 仍把该语义作为固定行为。

Additional instructions:

1. 不要换 worker，不要执行 Git 写入。
2. 先补 RED 回归测试：
   - `declaredFilters = [custom_filter, failing builtin]` 时 custom 不应被调用。
   - 结果应为 builtin reject，而不是 custom timeout / exception / false。
3. 修复旧 `declaredFilters` 兼容：
   - 推荐在旧字段编译到 AST 时，把 builtin 与 custom 按旧 evaluator 顺序组织。
   - 新 `filters` AST 保持显式 AST 顺序语义，不要为了旧字段兼容破坏新 AST。
4. 补 QuickJS/parser 层测试：
   - `filters: { anyOf: [...] }` 可从 JS 注册进入 AST。
   - 旧字段和新 AST 同时声明时，正常入口拒绝，语义与 `ambiguous_filter_sources` 一致。
5. 清理无调用的旧 evaluator 私有代码（如仍确认为 dead code），但只限 T12 filter evaluator 内部清理。
6. 不要修改 T11 Agent 语义。

Validation:

至少重新运行：

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Filter*' --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests '*PluginV2*' --tests '*Plugin*' --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests '*Agent*' --tests '*PluginV2HostApiQuickJsCapabilitiesTest*' --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

Return requirements:

按原回传格式回复，并额外包含：

- Rework RED 测试命令与失败原因。
- 旧 `declaredFilters` builtin-before-custom 兼容如何恢复。
- QuickJS/parser 层新增覆盖点。
- evaluator finding 是否全部关闭。

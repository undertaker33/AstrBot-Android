# D26052102-T12 Feedback：Filter 组合表达式

更新时间：2026-05-22 14:07 +08:00

## 场景

- Scene：`uth-dev`
- Mode：`formal-dev`
- Todo：`120-D26052102-T12-todo-Filter组合表达式.md`
- Worker prompt：`prompts/P260522-1048-T12-worker-Filter组合表达式.md`
- Worker：`019e4d96-8cbd-7b73-a5a0-510be02b6b50`
- Evaluator：`019e4dae-bd3b-79f3-9763-80fd6eeef3a2`
- Git 写入：未执行

## 实现摘要

- 新增 `PluginV2FilterExpression` / `PluginV2CompiledFilterExpression`，支持 `allOf`、`anyOf`、`not`、builtin filter 和 custom filter。
- QuickJS bootstrap parser 支持 `filters` AST，并保留旧 `declaredFilters` 线性列表入口。
- 旧 `declaredFilters` 编译为 builtin-before-custom 的 `AllOf`，保持旧 evaluator 兼容语义。
- 显式新 `filters` AST 保留声明顺序和短路语义；同时声明旧字段和新 AST 时拒绝为 `ambiguous_filter_sources`。
- `PluginV2FilterEvaluator` 递归执行 AST，custom filter timeout / exception / failure-stop 语义保持不变；filter reject 日志包含 `reasonCode` 和 `filterAstPath`。
- 初始 evaluator 发现旧 `declaredFilters` 被按声明顺序执行会破坏 builtin-before-custom 兼容；Rework 1 已修复并由同一 evaluator 复查通过。

## 变更文件

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2FilterExpression.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RawRegistry.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2HandlerRegistry.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2RegistryCompiler.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2FilterEvaluator.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/PluginV2BootstrapHostApi.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/ExternalPluginScriptExecutor.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2FilterAstCompilerTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2FilterEvaluatorAstTest.kt`
- `feature/plugin/runtime/src/test/java/com/elymbot/android/feature/plugin/runtime/PluginV2HostApiQuickJsCapabilitiesTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2BootstrapHostApiTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/PluginV2FilterEvaluatorTest.kt`

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

已关闭初始 compatibility finding：

- 旧 `declaredFilters` 恢复 builtin-before-custom 执行顺序。
- 新增回归覆盖 `declaredFilters = [custom_filter, failing builtin]` 时 custom 不被调用。
- QuickJS/parser 覆盖 `filters.anyOf` 与 `declaredFilters + filters` 正常入口拒绝。

## 未验证项

- 未做真实第三方插件包的设备级加载验证；本 Todo 以 QuickJS parser、registry compiler、filter evaluator 单测和构建门禁为验收证据。
- 未执行 Git 写入。

## 风险与回滚

- 新 AST 增加 filter 表达力，但显式 AST 顺序与旧字段兼容顺序不同；当前已通过测试把两种语义固定。
- 回滚本 Todo 会移除 `allOf` / `anyOf` / `not` AST 能力，但应保留旧 `declaredFilters` 线性 AND。

## 后续

- `Needs uth-docs scoped-sync`：插件平台上下文需要在 D26052102 整体验收后同步 T12 当前代码事实。

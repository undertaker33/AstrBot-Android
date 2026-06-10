# D26060301 Design-level 返工后验收记录

更新时间：2026-06-04 00:02 +08:00

## 场景

- Scene：`uth-review`
- Mode：`design-level-acceptance-after-fix`
- Review target：D26060301 Design、T01 / T02 Todo、T01 / T02 Feedback、T02 返工修复、当前工作区 diff。
- Acceptance basis：`00-D26060301-design.md` 的验证设计、T01 / T02 完成判定、`30-D26060301-design-level-review.md` 的阻塞发现、仓库 `AGENTS.md` 对稳定节点 `clean assembleDebug` 和 architecture contract 的要求。
- Subagent：无上下文 evaluator `019e8e2b-7124-7fa3-bf38-6d08b85ed508` / Dalton。
- Git 写入：未执行。

## 结论

Design-level 验收通过。

上一轮阻塞项已修复：`app/build.gradle.kts` 不再包含 `:core:runtime-audio` 或 `:core:runtime-container` 字符串；这两个模块仍由 `app-integration` 持有 runtime wiring，并保留在 `settings.gradle.kts` 与根 `build.gradle.kts` 的 architecture source roots / module group 中。

## Findings

### 阻塞项

无。

### 非阻塞项

- 根 `build.gradle.kts` 仍保留一个用于 `architectureDebugUnitTest` 注册的 `afterEvaluate`。该项不属于 T02 的 APK export finalizer 范围，且 `architectureCheck --configuration-cache` 已通过；因此不阻塞本 Design-level 验收。
- `:app:testDebugUnitTest --dry-run` 任务线仍为 1248 行。T02 明确第一轮不删除 runtime project，目标是把列表从全仓默认变成按测试域显式分组；实际任务图下降需要后续迁移或拆分 app 测试入口。

## 本轮验证

| Command | Result | Notes |
| --- | --- | --- |
| `git diff --check` | pass | 无空白错误；仅输出仓库当前 LF/CRLF 提示。 |
| `rg -n "gradle\.projectsEvaluated\|org\.gradle\.configuration-cache\.problems\|kotlin\.incremental=false" build.gradle.kts app\build.gradle.kts gradle.properties build-logic\gradle.properties` | pass | exit 1，无匹配项；未发现 `gradle.projectsEvaluated`、根级 `configuration-cache.problems` 或 `kotlin.incremental=false`。 |
| `rg -n 'core:runtime-audio\|core:runtime-container' app\build.gradle.kts app-integration\build.gradle.kts settings.gradle.kts build.gradle.kts` | pass | `app/build.gradle.kts` 0 个匹配；剩余匹配仅在 settings、根 source roots / module group、`app-integration`。 |
| `.\gradlew.bat :build-logic:check --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 31s`；warning/deprecated/exception/failed/configProblems 均为 0。 |
| `.\gradlew.bat modulePluginCheck moduleQqCheck --configuration-cache --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 1m 13s`；warning/deprecated/exception/failed/configProblems 均为 0。 |
| `.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 1m 15s`；warning/deprecated/exception/failed/configProblems 均为 0。 |
| `.\gradlew.bat :app:testDebugUnitTest --dry-run --configuration-cache --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 14s`；taskLines=1248；warning/deprecated/exception/failed/configProblems 均为 0；configuration cache entry reused。 |
| `.\gradlew.bat architectureCheck --configuration-cache --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 54s`；warning/deprecated/exception/failed/configProblems 均为 0。 |
| `.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 1m 24s`；warning/deprecated/exception/failed/configProblems 均为 0；2079 tasks：931 executed、845 from cache、303 up-to-date。 |

无上下文 evaluator 独立复验结论：`pass`。其只读验收同样未发现阻塞项，且复跑 `:build-logic:check`、`modulePluginCheck moduleQqCheck`、`:app:testDebugUnitTest`、dry-run、`architectureCheck` 与 `clean assembleDebug` 均通过。

## 验收判定

- T01：通过。Gradle 默认性能开关、`build-logic` incremental 默认策略恢复、CI job 解串和 architecture report typed task 符合 Design / Todo。
- T02：通过。module group 已移除全局 `gradle.projectsEvaluated`，APK export finalizer 已移除 app `afterEvaluate`，app 单测 runtime/friend-path 已按测试域显式分组；返工后不再触发 app shell runtime module boundary。
- Design-level result：`pass`。

## 残余风险

- 未运行远端 GitHub Actions；CI YAML 变更只经过本地结构核对和等效 Gradle 命令验证。
- `:app:testDebugUnitTest` dry-run 任务图仍为 1248 行；这符合本包“先治理分组、不迁移测试”的边界，但不是最终构建性能最优状态。
- `Needs uth-docs scoped-sync` 仍保留；应同步 `docs/context/01-验证构建治理.md` 与可能的 `docs/module-build-guide.md`。

## 后续路由

- 文档同步：`uth-docs scoped-sync`。
- Git / PR：如需提交，进入 `uth-git` 并等待用户确认 Git 计划。

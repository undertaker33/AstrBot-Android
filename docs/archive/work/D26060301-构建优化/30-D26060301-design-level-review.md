# D26060301 Design-level 验收记录

更新时间：2026-06-03 22:18 +08:00

## 场景

- Scene：`uth-review`
- Mode：`design-level-acceptance`
- Review target：D26060301 Design、T01 / T02 Todo、T01 / T02 Feedback、当前工作区 diff。
- Acceptance basis：`00-D26060301-design.md` 的验证设计、T01 / T02 完成判定、仓库 `AGENTS.md` 对稳定节点 `clean assembleDebug` 和 architecture contract 的要求。
- Git 写入：未执行。

## 结论

Design-level 验收未通过。

阻塞项是完整 `:app:testDebugUnitTest` 失败。Design 在 CI 变更后要求确认 app debug unit tests pass；当前 T01 / T02 Feedback 只记录了 `:app:testDebugUnitTest --dry-run`，本轮补跑完整命令后发现 2 个 architecture contract 失败。

## 阻塞发现

### B1. T02 的 app 单测 runtime 分组把 runtime module 字符串写入 app build 脚本，触发 app shell 边界合同

- 证据文件：`app/build.gradle.kts`
- 触发行：`appUnitTestRuntimeProjectGroups` 中包含 `:core:runtime-audio` 与 `:core:runtime-container`。
- 失败合同：
  - `RuntimeAudioBoundaryContractTest.core_runtime_audio_module_must_be_registered_and_reported`
  - `RuntimeContainerBoundaryContractTest.runtime_container_module_must_be_registered_reported_and_consumed`
- 失败原因：两个 architecture contract 直接读取 `app/build.gradle.kts` 文本，并断言 app shell 不应直接包含 `:core:runtime-audio` / `:core:runtime-container`，因为这些 runtime wiring 应由 `app-integration` 持有。

本问题不是 warning waiver 范围；它会让 `:app:testDebugUnitTest` 返回非零退出码，并影响 GitHub Actions 中 `app-tests` job 的真实可用性。

## 非阻塞观察

- 根 `build.gradle.kts` 仍有一个用于 `architectureDebugUnitTest` 注册的 `afterEvaluate`。该项不属于 T02 的 APK export finalizer 范围，且已有 `architectureCheck --configuration-cache` 通过记录；但它仍是后续构建脚本治理风险点。
- `:app:testDebugUnitTest --dry-run` 任务线仍为 1248 行。T02 明确第一轮不删除 runtime project，因此这不是本轮阻塞项；后续若要达成实际任务图下降，需要单独迁移或拆分 app 测试入口。

## 本轮验证

| Command | Result | Notes |
| --- | --- | --- |
| `git diff --check` | pass | 无空白错误；仅输出仓库当前 LF/CRLF 提示。 |
| `rg -n "gradle\.projectsEvaluated\|afterEvaluate\|org\.gradle\.configuration-cache\.problems\|kotlin\.incremental=false" build.gradle.kts app\build.gradle.kts gradle.properties build-logic\gradle.properties` | review evidence | 未发现 `gradle.projectsEvaluated`、根级 `configuration-cache.problems=warn` 或 `kotlin.incremental=false`；仅 `build.gradle.kts:539` 仍有既有 `afterEvaluate`。 |
| `rg -n "needs:\|module-boundary-checks:\|architecture-and-assemble:\|app-tests:" .github\workflows\ci.yml` | review evidence | CI 依赖图已变为 `module-boundary-checks -> tooling`、`app-tests -> tooling`、`architecture-and-assemble -> tooling + module-boundary-checks + app-tests`。 |
| `.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon --stacktrace` | fail | `BUILD FAILED in 1m 29s`；1392 tests completed、2 failed、16 skipped；warning=0、deprecated=0、configuration cache problems=0；失败为 architecture contract assertion。 |

## 验收判定

- T01：静态范围与既有验证记录符合 Todo，但 Design-level 仍受完整 app unit test gate 阻塞。
- T02：配置脚本治理主体已落地，但 app 单测 runtime 分组与现有 architecture contract 冲突，不能验收通过。
- Design-level result：`fail`。

## 后续路由

- 下一场景：`uth-dev`。
- 修复方向：让 app 单测 runtime / classpath 治理不再把 `:core:runtime-audio`、`:core:runtime-container` 等 app shell 禁止项以直接字符串形式落入 `app/build.gradle.kts`，或调整测试入口归属，使 `:app:testDebugUnitTest` 与 architecture contract 同时通过。
- 修复后重新进入 `uth-review`，至少补跑：

```powershell
.\gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --dry-run --configuration-cache --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --configuration-cache --console=plain --no-daemon --stacktrace
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

- `Needs uth-docs scoped-sync` 仍保留；应在验收通过后再同步 `docs/context/01-验证构建治理.md` 与可能的 `docs/module-build-guide.md`。

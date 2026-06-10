# D26060701-T06 ConfigDetail 地理围栏装载 Feedback

更新时间：2026-06-08 06:10 +08:00

## 场景

- Scene：`uth-dev`
- Mode：`todo-implementation`
- Method：`uth-sp-subagent-driven-development`
- Todo：`docs/work/D26060701-地理围栏完整能力设计/60-D26060701-T06-todo-ConfigDetail地理围栏装载.md`
- Worker Prompt：`docs/work/D26060701-地理围栏完整能力设计/prompts/P260608-0441-T06-worker-ConfigDetail地理围栏装载.md`
- 写入前澄清状态：无开放用户问题；本轮只按已批准 Design 与 T06 Todo 回填实现反馈。
- Git 写入：未执行。

## Subagents

| Role | Agent | Result | Notes |
| --- | --- | --- | --- |
| worker | Avicenna `019ea3d3-bbf2-71a1-9bc0-8ffeeca5b6aa` | `DONE_WITH_CONCERNS` | 已完成实现与返修；concern 为当前环境无 `adb`，无法跑 connected/manual 设备验证。 |
| spec reviewer | Faraday `019ea3f7-48bb-7743-991c-9110aad3d198` | `APPROVED` | 规格复核通过，无 findings。 |
| quality reviewer | Mendel `019ea400-afeb-7840-b4da-3e3c718f719f` | `APPROVED_AFTER_REWORK` | 首轮提出 Q1/Q2；原 worker 返修后由同一 reviewer 复核通过。 |

## Changed Files

- `feature/config/presentation/build.gradle.kts`
- `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/ConfigDetailScreen.kt`
- `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/detail/ConfigDrawerTree.kt`
- `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/detail/ConfigNavModels.kt`
- `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/geofence/ConfigGeofenceBindingPresentation.kt`
- `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/geofence/ConfigGeofenceBindingViewModel.kt`
- `app/src/main/java/com/elymbot/android/ui/navigation/ElymBotAppScaffoldParts.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- `app/src/test/java/com/elymbot/android/ui/config/ConfigGeofenceBindingPresentationTest.kt`
- `app/src/test/java/com/elymbot/android/ui/config/ConfigDetailGeofenceSectionTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/GeofenceArchitectureContractTest.kt`

## Implementation Summary

- 在 ConfigDetail 平台设置中新增 `ConfigSection.Geofence`，接入 `config_nav_group_platform`，并在 app navigation 中把“去配置地理围栏”的入口连到 `AppDestination.GeofenceRules`。
- 新增 ConfigDetail 地理围栏 section 与管理弹窗：展示当前装载数量、前两条摘要、空状态、跳转入口、多选规则、当前 config 下的 binding enabled 开关和保存失败提示。
- 新增 config presentation 侧的 geofence binding presentation/controller/ViewModel，依赖 `feature/geofence:api` port 与 model，不直接访问 geofence DAO/data/runtime implementation。
- 保存语义为独立即时保存：只写 `config_geofence_bindings`，对未选中或 stale 的 rule binding 执行删除，对选中的 rule upsert binding，并通知 `GeofenceRuntimeReconciliationPort`。
- 保持“我的-地理围栏配置”为规则内容唯一真源；ConfigDetail 不创建/编辑规则坐标、半径、触发器、action prompt 或 target。
- 未向 `ConfigProfile` 增加 geofence 坐标、半径、action 等字段；未复用 Resource Center projection 承载 geofence binding。

## Review Findings And Rework

- Q1：质量复核发现 dialog draft 曾以整个 `presentation` 为 key，保存失败或 live flow emission 可能重置用户草稿。返修后改为按 dialog session/profile id 初始化 draft，live 数据只合并规则可用性并剪掉已删除 rule，不替换用户未保存编辑；保存失败保持弹窗和草稿。
- Q1 覆盖测试：`ConfigGeofenceBindingPresentationTest.draft_survives_live_binding_emission_and_late_save_failure`、`ConfigDetailGeofenceSectionTest.dialog_draft_is_session_keyed_and_merges_live_rule_availability`。
- Q2：质量复核发现摘要曾混入硬编码英文。返修后 presentation 输出结构化 `ConfigGeofenceBindingSummary`，Compose 侧通过中英文资源渲染 enabled/disabled、region fallback、trigger 和 action 类型。
- Q2 覆盖测试：`ConfigGeofenceBindingPresentationTest.presentation_carries_structured_summary_fields_for_localized_rendering`、`ConfigDetailGeofenceSectionTest.geofence_summaries_are_localized_in_compose_resources`。

## Verification

| Time | Command | Result | Evidence |
| --- | --- | --- | --- |
| 2026-06-08 06:10 +08:00 | `uth-utf8-guard` pre-write | pass | `docs/current-state.md`、T06 Todo、T06 worker prompt 通过 UTF-8 guard。 |
| 2026-06-08 06:10 +08:00 | `.\gradlew.bat :app:testDebugUnitTest --tests "*Config*Geofence*" --tests "*Geofence*Binding*" --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 19s`。 |
| 2026-06-08 06:10 +08:00 | `.\gradlew.bat moduleConfigCheck --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 15s`。 |
| 2026-06-08 06:10 +08:00 | `.\gradlew.bat moduleGeofenceCheck --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 16s`。 |
| 2026-06-08 06:10 +08:00 | `.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 19s`。 |
| 2026-06-08 06:10 +08:00 | `.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace` | pass | `BUILD SUCCESSFUL in 43s`。 |
| 2026-06-08 06:10 +08:00 | `git diff --check` | pass | exit 0；仅有仓库既有 LF/CRLF 转换提示，无 whitespace error。 |
| 2026-06-08 06:10 +08:00 | `where.exe adb` | blocked | 当前环境输出 `INFO: Could not find files for the given pattern(s).`；未运行 connected/manual 设备验证。 |
| 2026-06-08 06:13 +08:00 | `uth-utf8-guard` post-write | pass | `docs/current-state.md`、T06 Feedback、T06 worker prompt 通过 UTF-8 guard。 |

## Risks And Unverified Items

- 未运行 `connectedDebugAndroidTest`，也未在 emulator/真实设备上手动点击 ConfigDetail 地理围栏装载弹窗；阻塞原因为当前环境未找到 `adb`。
- 未覆盖真实设备上的“配置详情页 -> 平台设置 -> 地理围栏 -> 管理弹窗 -> 保存 -> runtime 重注册”端到端人工流程。
- 本 Todo 未做整包验收和 context 文档同步；`Needs uth-docs scoped-sync` 等整包状态留给 T07/后续 `uth-review` 或 `uth-docs`。

## Rollback Notes

- 如需回滚 T06，优先撤销 config presentation 的 geofence binding UI/ViewModel、`ConfigSection.Geofence` navigation wiring、app navigation 回调和对应测试/字符串。
- 回滚时不要删除 T01-T05 已建立的 geofence 数据真源、runtime、Agent prompt 创建、我的页配置 UI、地图选择器与权限 UX。

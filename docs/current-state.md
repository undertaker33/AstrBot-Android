# 当前项目状态

更新时间：2026-06-10 15:03 +08:00

## 文档基线

- UTH 启用：yes
- 文档语言：`zh-CN`
- 当前文档基线：`full-project-docs-complete`
- 本轮场景：`uth-git`
- 本轮模式：`release-pr-closeout` + `ci-workflow-update`
- 本轮同步范围：在已同步 `D26060701` 地理围栏模块组事实基础上，更新 CI 功能模块 gate，准备 `v1.2.0` release commit 并复用当前 PR 指向 `master`。
- 本轮边界：收口当前工作区源码、测试、构建脚本、治理文档和 changelog；不创建 tag，不直接合并 `master`，connected/manual 真实设备验证仍按既有记录保持 blocked/unverified。

## 当前事实入口

| 入口 | 路径 | 说明 |
| --- | --- | --- |
| 文档入口 | `docs/README.md` | 新窗口文档读取入口 |
| 状态入口 | `docs/current-state.md` | 当前状态、活动任务、Git 锚点与后续路由 |
| 上下文索引 | `docs/context/README.md` | 当前事实层索引 |
| 模块拆分 | `docs/context/00-模块拆分.md` | 已确认模块队列、代码事实范围和清理规则 |
| 地理围栏上下文 | `docs/context/13-地理围栏.md` | 当前工作区 `feature/geofence` 模块组事实 |
| 版本锚点 | `docs/changelogs/version-git-anchors.md` | release commit / tag / changelog 覆盖索引 |
| 归档入口 | `docs/archive/README.md` | 已完成或历史任务材料入口 |

## Git 与版本状态

- 当前分支：`codex/ColorOS16(RealmeUI7)`
- 当前 HEAD：`b069daf release: v1.1.2`；本轮准备生成 `release: v1.2.0`。
- 本地 remote-tracking `origin/codex/ColorOS16(RealmeUI7)`：`b069daf`
- 本地 `master`：`988a523 Merge release v1.1.0`
- 本地 remote-tracking `origin/master`：`72b9aea`
- 当前 App 版本真源：`app/build.gradle.kts` 为 `versionName = "1.2.0"`、`versionCode = 84`。
- `v1.1.0`：本地 tag `v1.1.0` 指向 merge commit `988a523`，release commit 为 `104eb3a`，正文为 `changelogs/v1.1.0.md`。
- `v1.1.1`：release commit 为 `dea5d75`，正文为 `changelogs/v1.1.1.md`；本轮未发现本地 `v1.1.1` tag。
- `v1.1.2`：release commit 为 `b069daf`，正文为 `changelogs/v1.1.2.md`；本轮未发现本地 `v1.1.2` tag。
- `v1.2.0`：本轮作为 PR 到 `master` 的 release commit 准备中，正文为 `changelogs/v1.2.0.md`；tag / merge 锚点待发布闭环补齐。
- `v1.0.4`：当前仍只发现 release commit `4fedf19` 与正文 `changelogs/v1.0.4.md`，未发现本地 tag `refs/tags/v1.0.4`。
- 当前工作区 `D26060701` 地理围栏能力、CI gate 和 `v1.2.0` 版本源将随本轮 release commit 收口；合入 / tag 待 PR 发布闭环。

## 当前活动任务

### D26052102 插件 API 对齐 AstrBot 能力补齐

- 路径：`docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- 状态：`T01` 到 `T12` 已完成实现、验证、复核和验收记录；T06/T09 非阻塞审计字段增强已收口。
- 当前结论：设计内无剩余非验证类 Todo 待派发；如需关闭整包，进入 Design-level 验收；如需提交，按用户确认进入 `uth-git`。
- 长期事实：已同步到 `docs/context/09-插件平台.md`，但真实设备、真实网络、真实 provider、真实插件包验证仍按任务记录排除，不应写成已覆盖。

### D26052201 插件任意会话发送能力设计

- 路径：`docs/work/D26052201-插件任意会话发送能力设计/`
- 状态：已完成 `uth-design` / `design-authoring`；尚未拆 Todo、实现、验收或 Git 写入。
- 关键决策：不放宽现有 `conversationId` 当前会话 guard；任意目标发送必须另走宿主授权的 `targetId` / `PluginMessageRoute` 模型。
- 当前结论：不得把 `targetId`、target resolver、目标授权、目标 scheduled handler 写成生产能力；继续实现时进入 `uth-dev` 拆 Todo。

## 已归档任务材料

以下材料仅作历史证据，不再作为当前事实入口：

- `docs/archive/work/D26051801-包名统一ElymBot/`
- `docs/archive/work/D26051901-插件指令管理员权限/`
- `docs/archive/work/D26052001-备份顶栏占位与APIKey可选备份/`
- `docs/archive/work/D26052101-应用内更新/`
- `docs/archive/work/D26060301-构建优化/`
- `docs/archive/work/D26060501-NapCat安卓权限与启动兼容/`
- `docs/archive/work/D26060701-地理围栏完整能力设计/`
- `docs/archive/LW-Work/LW26051801-编译链升级AGP9.md`
- `docs/archive/LW-Work/LW26051901-UI资源配置与备份聊天修正.md`
- `docs/archive/LW-Work/LW26052201-QQ普通群消息历史沉淀.md`

归档文件中的旧提交号、旧路径、旧版本号和旧完成态不得覆盖当前 `docs/context/` 与源码事实。

## 近期高信号变化

- `v1.1.1`：补齐 QQ 普通群公共历史沉淀、Host API 群历史映射、插件发送/stream session id 解析，以及 pseudo streaming 文本/附件分流；版本源更新为 `versionName = "1.1.1"`、`versionCode = 82`。
- `v1.1.2`：补齐 NapCat / 容器运行时兼容性预检、外部存储 optional bind、前台服务启动失败分类、CI 并行验证与 Gradle 缓存配置；版本源更新为 `versionName = "1.1.2"`、`versionCode = 83`。
- `v1.2.0`：收口地理围栏模块组、Room v24 迁移、设置页与 ConfigDetail 入口、运行时 reconciliation、宿主内部 active capability，以及 CI feature module gate；版本源更新为 `versionName = "1.2.0"`、`versionCode = 84`。
- 地理围栏：本轮新增 `:feature:geofence:api/data/impl/presentation/runtime`，新增 Room v24 地理围栏表、运行时注册/触发/执行链、设置页入口、ConfigDetail 绑定入口，以及 Agent 内部 active capability 工具；当前事实见 `docs/context/13-地理围栏.md`。
- 构建治理：根 `build.gradle.kts` 已加入 `moduleGeofenceBuild` / `moduleGeofenceCheck`，并将 `feature/geofence/**/src/main/java` 纳入 architecture source roots；`app/build.gradle.kts` app 单测分组新增 geofence presentation / runtime classpath；CI `module-boundary-checks` 同步覆盖 Plugin、QQ、Geofence、Settings 和 Config。
- App 与 Manifest：当前工作区声明 foreground/coarse/background location 权限与非导出 `GeofenceTransitionReceiver`；启动链在 Cron reconciliation 后调用 `GeofenceRuntimeReconciliationPort.reconcileAsync(appScope)`。
- 插件边界：`create_geofence_rule` 等地理围栏工具属于 `ActiveCapabilityToolSourceProvider` 的宿主内部 active capability；`GeofenceActiveCapabilityFacade` 标注为 `INTERNAL_ACTIVE_CAPABILITY_ONLY`，不是公开 `hostApi.geofence.*`。
- 验证风险：D26060701 的自动化 gate 已有既有通过记录，但真实设备、connected/manual、Google Play services、地图渲染、后台定位授权、厂商 ROM 与真实 enter/exit/dwell transition 仍保持 blocked/unverified。

## 最新验证证据

| Time | Method | Result | Notes |
| --- | --- | --- | --- |
| 2026-05-22 | v1.1.0 release closeout | pass | 既有记录：`changelogs/v1.1.0.md` 写入，`v1.1.0` tag 指向 `988a523`。 |
| 2026-05-22 | LW26052201 QQ public group history | pass with risk | 既有记录：OneBot 入站、公共群历史沉淀、隔离 session 不污染、Host API 历史读取映射和全量 debug 构建通过；未做真实 QQ / NapCat 端到端人工验收。 |
| 2026-06-04 00:02 +08:00 | D26060301 Design-level review after fix | pass | 既有记录：返工后 `:app:testDebugUnitTest`、`architectureCheck`、`:build-logic:check`、module group checks 和 `clean assembleDebug` 通过；残余风险为远端 GitHub Actions 未运行、app dry-run 仍为 1248 任务线。 |
| 2026-06-05 19:09 +08:00 | D26060501 stable build + Design-level review | pass with risk | 既有记录：`RuntimeCompatibilityProbeTest`、NapCat 脚本合同、`moduleQqCheck`、`architectureCheck`、`clean assembleDebug` 和无上下文验收通过；残余风险为真实低版本 Android、厂商 ROM 和真实 NapCat 端到端启动未覆盖。 |
| 2026-06-08 06:51 +08:00 | D26060701 T07 controller stable build gate | pass | 既有记录：`moduleGeofenceCheck`、`moduleSettingsCheck`、`moduleConfigCheck`、`architectureCheck`、`:app:testDebugUnitTest --tests "*Geofence*"` 与 `clean assembleDebug` 均通过。 |
| 2026-06-08 06:54 +08:00 | D26060701 T07 docs / patch guard | pass with notice | 既有记录：T07 Feedback、run log、worker prompt 与 `docs/current-state.md` 通过 UTF-8 guard；`git diff --check` exit 0，仅有既有 LF/CRLF 转换提示。 |
| 2026-06-08 06:28 +08:00 | D26060701 connected/manual availability | blocked | 既有记录：`where.exe adb` 未找到 `adb`；connected/manual 设备 UI、前后台定位、地图渲染、真实 Play services geofence transition 与厂商 ROM 验证未运行。 |
| 2026-06-10 14:12 +08:00 | `uth-utf8-guard` pre-write | pass | 本轮写入前 `AGENTS.md` 与 `docs/**/*.md` 共 150 个 Markdown 文件通过 UTF-8 guard。 |
| 2026-06-10 14:28 +08:00 | `uth-utf8-guard` post-write | pass | 本轮写入后 `AGENTS.md` 与 `docs/**/*.md` 共 151 个 Markdown 文件通过 UTF-8 guard。 |
| 2026-06-10 15:03 +08:00 | v1.2.0 release PR static guards | pass | `git diff --check` 通过；`AGENTS.md`、`changelogs/v1.2.0.md` 与 `docs/**/*.md` 共 152 个 Markdown 文件通过 UTF-8 guard。 |
| 2026-06-10 15:03 +08:00 | v1.2.0 CI-equivalent Gradle gate | pass | `:build-logic:check`、`modulePluginCheck moduleQqCheck moduleGeofenceCheck moduleSettingsCheck moduleConfigCheck`、`:app:testDebugUnitTest` 与 `clean architectureCheck assembleDebug` 均通过；`ResourceCenterPresentationTest` 已同步新增 `MeEntryKind.Geofence` 入口后重跑通过。 |

## 当前事实来源

- `AGENTS.md`
- `.uth-governance/project.json`
- `docs/README.md`
- `docs/context/README.md`
- `docs/context/00-模块拆分.md`
- `docs/context/01-验证构建治理.md`
- `docs/context/02-应用壳层与集成.md`
- `docs/context/03-核心基础与数据库.md`
- `docs/context/04-核心运行时.md`
- `docs/context/06-Provider配置Bot与Persona.md`
- `docs/context/09-插件平台.md`
- `docs/context/11-资源设置备份.md`
- `docs/context/13-地理围栏.md`
- `docs/changelogs/version-git-anchors.md`
- `changelogs/v1.2.0.md`
- `docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- `docs/work/D26052201-插件任意会话发送能力设计/`
- `docs/archive/work/D26060701-地理围栏完整能力设计/`
- `git log --oneline 13467db..b069daf`
- `git diff --name-status 13467db..b069daf`
- `git status --short`
- `.github/workflows/ci.yml`
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/elymbot/android/di/startup/RuntimeLaunchStartupChain.kt`
- `app/src/main/java/com/elymbot/android/ui/navigation/AppDestinations.kt`
- `app/src/main/java/com/elymbot/android/ui/navigation/ElymBotAppScaffoldParts.kt`
- `core/db/src/main/java/com/elymbot/android/data/db/ElymBotDatabase.kt`
- `core/db/src/main/java/com/elymbot/android/data/db/core/DbMigrations.kt`
- `core/db/src/main/java/com/elymbot/android/data/db/geofence/**`
- `core/runtime-context/src/main/java/com/elymbot/android/core/runtime/context/RuntimeIngressContracts.kt`
- `feature/geofence/**`
- `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/geofence/**`
- `feature/plugin/api/src/main/java/com/elymbot/android/feature/plugin/domain/runtime/GeofenceActiveCapabilityFacade.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/ActiveCapabilityToolSourceProvider.kt`
- `feature/settings/presentation/src/main/java/com/elymbot/android/ui/settings/MeScreen.kt`

## 后续路由

- 普通开发：`uth-governance` -> `uth-dev`
- bug / 构建失败 / 回归：`uth-governance` -> `uth-debug`
- 验收 / 代码审查：`uth-governance` -> `uth-review`
- 文档同步：`uth-governance` -> `uth-docs`
- Git / PR / 发布 / tag：`uth-governance` -> `uth-git`

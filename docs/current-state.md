# 当前项目状态

更新时间：2026-05-22 18:30 +08:00

## 文档基线

- UTH 启用：yes
- 文档语言：`zh-CN`
- 当前文档基线：`full-project-docs-complete`
- 本轮场景：`uth-docs`
- 本轮模式：`scoped-sync` + `state-cleanup` + `archive-cleanup`
- 本轮完成范围：从上一轮文档锚点 `66eee69` 同步到当前分支 `4fedf19`，并纳入当前工作区中 `D26052102` 非阻塞审计字段收口与 `D26052201` 设计包事实。
- 本轮不运行 Gradle、单元测试、APK 构建或 Git 写入；下方验证结果均为既有可追溯记录或本轮文档/UTF-8 检查。

## 当前事实入口

| 入口 | 路径 | 说明 |
| --- | --- | --- |
| 文档入口 | `docs/README.md` | 新窗口文档读取入口 |
| 状态入口 | `docs/current-state.md` | 当前状态、活动任务、Git 锚点与后续路由 |
| 上下文索引 | `docs/context/README.md` | 当前事实层索引 |
| 模块拆分 | `docs/context/00-模块拆分.md` | 已确认模块队列、代码事实范围和清理规则 |
| 版本锚点 | `docs/changelogs/version-git-anchors.md` | release commit / tag / changelog 覆盖索引 |
| 归档入口 | `docs/archive/README.md` | 已完成或历史任务材料入口 |

## Git 与版本状态

- 当前分支：`codex/ColorOS16(RealmeUI7)`
- 当前 HEAD：`4fedf19 release: v1.0.4`
- 远端分支：`origin/codex/ColorOS16(RealmeUI7)` 指向 `4fedf19`。
- `master` / `origin/master`：指向 `0eafb33`，该提交带本地/远端 tag `v1.0.3`。
- `v1.0.4`：当前只发现 release commit `4fedf19`，未发现本地或远端 tag `refs/tags/v1.0.4`。
- 当前 App 版本真源：`app/build.gradle.kts` 为 `versionName = "1.0.4"`、`versionCode = 80`。
- 当前 Room 真源：`core/db/src/main/java/com/elymbot/android/data/db/ElymBotDatabase.kt` 为 `version = 23`，schema 同步到 `app/schemas/.../23.json` 与 `core/db/schemas/.../23.json`。

## 当前活动任务

### D26052102 插件 API 对齐 AstrBot 能力补齐

- 路径：`docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- 状态：`T01` 到 `T12` 已实现、验证、复核并完成验收复查；T06/T09 非阻塞审计字段增强已在当前工作区收口。
- 当前工作区增量：`PluginV2HostApiFoundation.kt`、`PluginV2HostLlmApi.kt`、`PluginV2MessageStreamApi.kt` 与对应测试仍有未提交修改；任务包新增 `131-D26052102-feedback-非阻塞审计字段收口.md` 和 worker prompt。
- 当前结论：无剩余 AstrBot 对齐能力缺口；仍需用户决定是否进入 Design-level 关闭或 `uth-git`。
- 背景同步：插件平台、聊天、QQ、Cron 与资源/备份上下文已在本轮按当前事实同步。

### D26052201 插件任意会话发送能力设计

- 路径：`docs/work/D26052201-插件任意会话发送能力设计/`
- 状态：已完成 `uth-design` / `design-authoring`，未拆 Todo，未实现，未验收，未 Git 写入。
- 关键决策：不放宽现有 `conversationId` 当前会话 guard；跨会话发送走宿主授权的 `targetId` / `PluginMessageRoute` 模型。
- 后续路由：如继续实现，进入 `uth-dev` 拆 Todo；实现验收后再由 `uth-docs` 同步长期上下文。

## 已归档任务包

本轮从当前工作入口移除并归档以下已完成或已进入发布链的任务材料：

- `docs/archive/work/D26051801-包名统一ElymBot/`
- `docs/archive/work/D26051901-插件指令管理员权限/`
- `docs/archive/work/D26052001-备份顶栏占位与APIKey可选备份/`
- `docs/archive/work/D26052101-应用内更新/`
- `docs/archive/LW-Work/LW26051801-编译链升级AGP9.md`
- `docs/archive/LW-Work/LW26051901-UI资源配置与备份聊天修正.md`

这些归档文件只作为历史证据；当前事实以 `docs/context/`、`app/build.gradle.kts`、代码和当前任务包为准。

## 近期高信号变化

- `v1.0.1`：新增应用内更新能力，包含 GitHub release 检查、下载、FileProvider 安装入口、忽略/稍后策略和安装后清理。
- `v1.0.2`：补齐插件 Host API 底座、宿主网络代理、Provider 只读查询、当前会话消息发送和当前会话历史读取。
- `v1.0.3`：补齐插件直接 LLM、上下文压缩、定时任务回调、流式输出和富消息链；Cron 可将 scheduled handler 唤醒到 Plugin V2。
- `v1.0.4`：补齐插件 Agent 注册 / `agent.run` 与 Filter 组合表达式 AST；当前分支已到 `4fedf19`。
- 当前工作区：继续补强 T06/T09 审计字段，增加 LLM provider/model/token usage 与 stream lifecycle 细粒度字段。
- 构建治理：CI 当前分为 tooling、architecture-and-assemble、app-tests；tooling 阶段包含 generated artifacts guard 和 `:build-logic:check`。

## 最新验证证据

| Time | Method | Result | Notes |
| --- | --- | --- | --- |
| 2026-05-22 14:55 +08:00 | D26052102 T11-T12 review gate | pass | 既有记录：fresh runtime/app 测试、`architectureCheck`、`modulePluginCheck`、`clean assembleDebug` 均通过；5 个验收日志 warning / deprecated / exception / failed 扫描计数均为 0。 |
| 2026-05-22 16:46 +08:00 | D26052102 non-blocking audit-field closeout | pass | 既有记录：runtime LLM/stream/audit 单测、app audit 合同、`architectureCheck`、`modulePluginCheck`、`clean assembleDebug` 均通过；真实设备/网络/provider/plugin package 验证按任务要求排除。 |
| 2026-05-22 16:51 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | 既有记录：`uth-dev` / `formal-dev` 非阻塞审计字段收口 closeout 通过。 |
| 2026-05-22 18:30 +08:00 | `uth-utf8-guard` pre-write | pass | 本轮写入前 16 个 Markdown 文件通过 UTF-8 guard。 |
| 2026-05-22 18:30 +08:00 | `uth-utf8-guard` post-write | pass | 本轮写入和归档移动后，`docs/**/*.md` 共 108 个 Markdown 文件通过 UTF-8 guard。 |
| 2026-05-22 18:30 +08:00 | git / remote anchor check | pass with gap | 本轮只读确认 `origin/codex/ColorOS16(RealmeUI7)=4fedf19`、`origin/master=0eafb33`；未发现 `refs/tags/v1.0.4`。 |
| 2026-05-22 18:35 +08:00 | changelog content write | pass | 补齐 `changelogs/v1.0.1.md` 到 `changelogs/v1.0.4.md`，并同步 `docs/changelogs/README.md` 与 `version-git-anchors.md`。 |

## 当前事实来源

- `AGENTS.md`
- `.uth-governance/project.json`
- `docs/README.md`
- `docs/context/README.md`
- `docs/context/00-模块拆分.md`
- `docs/context/01-验证构建治理.md` 到 `docs/context/12-语音资产与音频.md`
- `docs/changelogs/version-git-anchors.md`
- `git log --oneline 66eee69..4fedf19`
- `git diff --name-status 66eee69..4fedf19`
- `git status --short`
- `git ls-remote --heads origin master codex/ColorOS16(RealmeUI7)`
- `git ls-remote --tags origin refs/tags/v1.0.1 refs/tags/v1.0.2 refs/tags/v1.0.3 refs/tags/v1.0.4`
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `.github/workflows/ci.yml`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/elymbot/android/update/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `core/db/src/main/java/com/elymbot/android/data/db/**`
- `core/network/src/main/java/com/elymbot/android/core/runtime/network/RuntimeNetworkModels.kt`
- `core/runtime-context/src/main/java/com/elymbot/android/core/runtime/context/ResolvedRuntimeContextContracts.kt`
- `download/api/src/main/java/com/elymbot/android/download/DownloadModels.kt`
- `feature/chat/runtime/src/main/java/**`
- `feature/qq/runtime/src/main/java/**`
- `feature/cron/runtime/src/main/java/**`
- `feature/plugin/runtime/src/main/java/**`
- `feature/plugin/runtime/src/test/java/**`
- `feature/settings/**`

## 后续路由

- 普通开发：`uth-governance` -> `uth-dev`
- bug / 构建失败 / 回归：`uth-governance` -> `uth-debug`
- 验收 / 代码审查：`uth-governance` -> `uth-review`
- 文档同步：`uth-governance` -> `uth-docs`
- Git / PR / 发布 / tag：`uth-governance` -> `uth-git`

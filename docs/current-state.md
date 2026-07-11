# 当前项目状态

更新时间：2026-07-11 +08:00

## 文档基线

- UTH 启用：yes
- 文档语言：`zh-CN`
- 当前文档基线：`full-project-docs-complete`
- 本轮场景：`uth-dev`
- 本轮模式：`todo-implementation`
- 本轮完成范围：`D26071001` T01–T06 已实现；T02 metadata 失败后 draft 丢失问题已完成真实 manager I/O retry/discard 修复，并在修复后完成 T06 fresh 全链路验证；当前待同一 reviewer 复审，尚未宣称 Design-level 审查通过。
- 本轮以 `--rerun-tasks` fresh 执行 manager retry/discard 专项，并重新执行 T06 五条规定命令；`clean assembleDebug` 以 fresh clean build 通过，六份新日志 warning / deprecated / exception / failed 均为 0；未执行 Git 写入。

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
- 当前 Room 真源：`core/db/src/main/java/com/elymbot/android/data/db/ElymBotDatabase.kt` 为 `version = 24`，schema 同步到 `app/schemas/.../24.json` 与 `core/db/schemas/.../24.json`。

## 当前活动任务

### D26071001 人格页 UI 重构

- 路径：`docs/work/D26071001-UI重构-Bot页/`
- Design：`docs/work/D26071001-UI重构-Bot页/00-D26071001-design.md`
- 状态：Design 已确认，T01–T06 已实现；T02 Rework 1 已通过真实 manager I/O retry/discard，并已完成修复后的 T06 fresh 全链路验证，当前待同一 reviewer 复审。
- 已定边界：不聚合 Bot 与 Persona；机器人页和模型页保持原位、保持现状，只重构人格页且不改名；人格页只消费 Persona 数据及 Persona 自有封面资产。“角色 / Role”页面、导航和数据模型不属于本任务。
- 导航边界：撤销“角色｜模型”合并顶栏、人格改名和删除外层滑动链的旧方案；现有机器人 / 模型 / 人格页面位置、标题与外层导航行为不因本任务改变。
- 模式一基准：`Image/定稿/模式一定稿.png`；单卡铺满内容区，以卡片内部左右箭头切换 Persona；定稿图底栏不作为实现基准，沿用现有 Android 全局底栏。
- 模式二基准：`Image/定稿/模式二定稿.png`；双列等宽等高方卡、右列固定错位，不显示无作用域“当前人格”标记。
- 顶部展开基准：`Image/定稿/搜索框.png`、`Image/定稿/筛选弹窗.png`、`Image/定稿/三点.png`；三种展开状态互斥。
- 详情页基准：`Image/定稿/角色详情页定稿.png`；从人格卡编辑入口进入，只显示五项真实 Persona 业务字段及封面编辑；编辑面板仅有展开 / 收起两种稳定状态，拖动结束后吸附，并按各状态实际可见区域动态居中裁切封面。图片中的“编辑角色”实现时改为“编辑人格”。
- 双模式与标签约束：用户可在沉浸 / 列表模式间自由切换，模式选择持久化，两种模式共享 Persona-only 状态；人格最多三个标签并迁入 `persona_tags` 子表，列表、详情、搜索和筛选统一消费；不展示 Bot、Config、QQ 或无平台作用域的运行 / 当前人格状态。
- 封面持久化：用户选图后复制并规范化到 `filesDir/assets/persona-covers/`，Room 通过 `persona_cover_assets` 保存相对引用与纵向 / 方形两套裁切参数；导入依次完成纵向和方形裁切，更换 / 删除只清理应用内副本，绝不删除用户原文件。
- 备份边界：完整备份和 Persona 单模块备份均携带封面文件、标签和裁切参数；缺少新增字段的老包继续兼容，单张封面损坏时恢复 Persona 并警告回退默认图。
- Todo 队列：T01 Room 多标签与封面元数据 → T02 应用私有封面生命周期 → T03 备份恢复与老包兼容 → T04 人格目录双模式与顶栏交互 → T05 人格详情与两步裁切 → T06 全链路集成与回归验收。
- T01 Feedback：`docs/work/D26071001-UI重构-Bot页/11-D26071001-T01-feedback-Room多标签与封面元数据.md`。
- T02 Feedback：`docs/work/D26071001-UI重构-Bot页/21-D26071001-T02-feedback-应用私有封面生命周期.md`。
- T05 Feedback：`docs/work/D26071001-UI重构-Bot页/51-D26071001-T05-feedback-人格详情与两步裁切.md`。
- T03 Feedback：`docs/work/D26071001-UI重构-Bot页/31-D26071001-T03-feedback-备份恢复与老包兼容.md`。
- T04 Feedback：`docs/work/D26071001-UI重构-Bot页/41-D26071001-T04-feedback-人格目录双模式与顶栏交互.md`。
- T06 Feedback：`docs/work/D26071001-UI重构-Bot页/61-D26071001-T06-feedback-全链路集成与回归验收.md`。
- 当前 Todo：T01–T06 均已实现，无未完成 Todo。
- 后续路由：回到同一 `uth-review` reviewer 复审修复项与 Design-level 验收。

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
| 2026-07-11 +08:00 | D26071001 T02 Rework 1 后 T06 fresh 全链路复验 | pass | `*PersonaCover* --rerun-tasks` 专项与 T06 五条规定命令全部 exit 0；fresh `clean assembleDebug` 通过；六份新日志 warning / deprecated / exception / failed = 0/0/0/0，两份 schema 24 哈希一致。 |
| 2026-07-11 +08:00 | D26071001 T01 final verification | pass | `:core:db:testDebugUnitTest`、`:feature:persona:data:testDebugUnitTest`、`modulePersonaCheck`、`architectureCheck` 统一通过；warning / exception / failed marker 均为 0，两处 schema 24 哈希一致。 |
| 2026-07-11 +08:00 | D26071001 T02 final verification | pass | 三组 `*PersonaCover*` 窄测试、`architectureCheck` 与 `clean assembleDebug` 统一通过；warning / exception / failed marker 均为 0。 |
| 2026-07-11 +08:00 | D26071001 T04 final verification | pass | presentation 与 Persona/MainSwipe/GlobalTopBar app tests、`modulePersonaCheck`、`architectureCheck` 统一通过；warning / exception / failed marker 均为 0。 |
| 2026-07-11 +08:00 | D26071001 T04 Rework 1 | pass | portrait/square 非默认 zoom 策略、不同宽度 1:1 方卡与最终渲染源码合同通过；T04 全验证统一通过，warning / exception / failed marker 均为 0。 |
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

# 当前项目状态

更新时间：2026-05-25 21:10 +08:00

## 文档基线

- UTH 启用：yes
- 文档语言：`zh-CN`
- 当前文档基线：`full-project-docs-complete`
- 本轮场景：`uth-docs`
- 本轮模式：`scoped-sync` + `rules-maintenance` + `archive-cleanup`
- 本轮完成范围：从上一轮文档锚点 `4fedf19` 同步到当前分支 `13467db`，并纳入当前工作区 QQ 普通群公共历史沉淀、Host API 历史读取映射、QQ pseudo streaming 文本/附件分流，以及从归档目录和工作包提炼到 `AGENTS.md` 的跨模块易错点。
- 本轮不运行 Gradle、单元测试、APK 构建或 Git 写入；下方验证结果均为既有记录或文档 UTF-8 检查。

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
- 当前 HEAD：`release: v1.1.1`
- 本地 `master`：`988a523 Merge release v1.1.0`
- 本地 remote-tracking `origin/codex/ColorOS16(RealmeUI7)`：`13467db`，待推送当前 `release: v1.1.1`
- 本地 remote-tracking `origin/master`：`72b9aea`；本轮未执行 `git fetch`，因此不把它声明为远端最新状态。
- 当前 App 版本真源：`app/build.gradle.kts` 为 `versionName = "1.1.1"`、`versionCode = 82`。
- `v1.1.0`：本地 tag `v1.1.0` 指向 merge commit `988a523`，release commit 为 `104eb3a`，正文为 `changelogs/v1.1.0.md`。
- `v1.0.4`：当前仍只发现 release commit `4fedf19` 与正文 `changelogs/v1.0.4.md`，未发现本地或远端 tag `refs/tags/v1.0.4`。
- 当前工作区已生成 `v1.1.1` release commit：QQ 普通群公共历史沉淀、Host API session 映射与 QQ pseudo streaming 文本/附件分流，待推送并 PR 到 `master`。

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

### LW26052201 QQ 普通群消息历史沉淀

- 归档路径：`docs/archive/LW-Work/LW26052201-QQ普通群消息历史沉淀.md`
- 状态：已完成主窗口验收，本轮从 `docs/LW-Work/` 移入归档。
- 当前工作区增量：`QqMessageRuntimeService.kt`、`PluginHostCapabilityModule.kt`、`PluginV2HostIngressTest.kt`、`PluginHostCapabilityModuleTest.kt`、`QqStreamingReplyService.kt` 和新增 `QqStreamingReplyServiceAttachmentStreamingTest.kt`。
- 当前事实：QQ 普通群消息会沉淀到公共群 session；`hostApi.conversation.history` 在群场景优先读公共群历史；`hostApi.message.send` / stream 持久化时会把插件外部 conversation id 解析为本地 repository session。
- 剩余风险：未做真实 QQ / NapCat 连接下的端到端人工验收；代码仍未进入 Git closure。

## 已归档任务材料

以下材料仅作历史证据，不再作为当前事实入口：

- `docs/archive/work/D26051801-包名统一ElymBot/`
- `docs/archive/work/D26051901-插件指令管理员权限/`
- `docs/archive/work/D26052001-备份顶栏占位与APIKey可选备份/`
- `docs/archive/work/D26052101-应用内更新/`
- `docs/archive/LW-Work/LW26051801-编译链升级AGP9.md`
- `docs/archive/LW-Work/LW26051901-UI资源配置与备份聊天修正.md`
- `docs/archive/LW-Work/LW26052201-QQ普通群消息历史沉淀.md`

归档文件中的旧提交号、旧路径、旧版本号和旧完成态不得覆盖当前 `docs/context/` 与源码事实。

## 近期高信号变化

- `v1.1.0`：发布插件 V2 API 完整能力、应用内更新、Agent runtime、scheduled handler、流式输出、富消息链和 Filter AST；版本源更新为 `versionName = "1.1.0"`、`versionCode = 81`。
- `v1.1.1`：补齐 QQ 普通群公共历史沉淀、Host API 群历史映射、插件发送/stream session id 解析，以及 pseudo streaming 文本/附件分流；版本源更新为 `versionName = "1.1.1"`、`versionCode = 82`。
- CI 刷新：`.github/workflows/ci.yml` 当前包含 `tooling`、`module-boundary-checks`、`architecture-and-assemble`、`app-tests` 四段；`module-boundary-checks` 运行 `modulePluginCheck moduleQqCheck`。
- QQ 普通群公共历史：普通群消息在去重后进入公共群 session，群聊隔离只影响 bot LLM session；插件群分析应优先读取公共群历史。
- Host API 映射：`hostApi.conversation.history` 支持群场景公共历史映射；`hostApi.message.send` / stream 的宿主持久化不会把外部 `group:<gid>:user:<uid>` 直接当作 repository session。
- `AGENTS.md` 已补充从归档目录、工作包和 LW 中反复出现的跨模块易错点：任务包不等于当前事实、不要回滚他人工作区、Host API 不直连宿主内部、当前会话能力不等于任意目标发送、QQ session id / origin / OneBot target 必须分清。

## 最新验证证据

| Time | Method | Result | Notes |
| --- | --- | --- | --- |
| 2026-05-22 14:55 +08:00 | D26052102 T11-T12 review gate | pass | 既有记录：feature runtime/app 测试、`architectureCheck`、`modulePluginCheck`、`clean assembleDebug` 均通过；5 个验收日志 warning / deprecated / exception / failed 扫描计数均为 0。 |
| 2026-05-22 16:46 +08:00 | D26052102 non-blocking audit-field closeout | pass | 既有记录：runtime LLM/stream/audit 单测、app audit 合同、`architectureCheck`、`modulePluginCheck`、`clean assembleDebug` 均通过。 |
| 2026-05-22 16:51 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | 既有记录：`uth-dev` / `formal-dev` 非阻塞审计字段收口 closeout 通过。 |
| 2026-05-22 18:35 +08:00 | changelog content write | pass | 已补齐 `changelogs/v1.0.1.md` 到 `changelogs/v1.0.4.md`。 |
| 2026-05-22 | v1.1.0 release closeout | pass | 既有记录：`changelogs/v1.1.0.md` 写入，`v1.1.0` tag 指向 `988a523`。 |
| 2026-05-22 | LW26052201 QQ public group history | pass with risk | 既有记录：OneBot 入站、公共群历史沉淀、隔离 session 不污染、Host API 历史读取映射和全量 debug 构建通过；未做真实 QQ / NapCat 端到端人工验收。 |
| 2026-05-25 18:26 +08:00 | `uth-utf8-guard` pre-write | pass | 本轮写入前目标 Markdown 通过 UTF-8 guard。 |
| 2026-05-25 18:27 +08:00 | `uth-utf8-guard` post-write | pass | `AGENTS.md` 与 `docs/**/*.md` 共 110 个 Markdown 文件通过 UTF-8 guard。 |
| 2026-05-25 18:27 +08:00 | `git diff --check` | pass | 无空白错误；仅输出仓库当前 LF/CRLF 提示。 |

## 当前事实来源

- `AGENTS.md`
- `.uth-governance/project.json`
- `docs/README.md`
- `docs/context/README.md`
- `docs/context/01-验证构建治理.md`
- `docs/context/02-应用壳层与集成.md`
- `docs/context/07-聊天与会话.md`
- `docs/context/08-QQ_NapCat_OneBot.md`
- `docs/context/09-插件平台.md`
- `docs/changelogs/version-git-anchors.md`
- `docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- `docs/work/D26052201-插件任意会话发送能力设计/`
- `docs/archive/work/`
- `docs/archive/LW-Work/`
- `git log --oneline 4fedf19..HEAD`
- `git diff --name-status 4fedf19..HEAD`
- `git status --short`
- `.github/workflows/ci.yml`
- `app/build.gradle.kts`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/PluginHostCapabilityModule.kt`
- `feature/qq/runtime/src/main/java/com/elymbot/android/feature/qq/runtime/QqMessageRuntimeService.kt`
- `feature/qq/runtime/src/main/java/com/elymbot/android/feature/qq/runtime/QqStreamingReplyService.kt`
- `feature/qq/runtime/src/test/java/com/elymbot/android/feature/qq/runtime/QqStreamingReplyServiceAttachmentStreamingTest.kt`

## 后续路由

- 普通开发：`uth-governance` -> `uth-dev`
- bug / 构建失败 / 回归：`uth-governance` -> `uth-debug`
- 验收 / 代码审查：`uth-governance` -> `uth-review`
- 文档同步：`uth-governance` -> `uth-docs`
- Git / PR / 发布 / tag：`uth-governance` -> `uth-git`

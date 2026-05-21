# 当前项目状态

更新时间：2026-05-21 10:46 +08:00

## 接管收尾状态

- UTH 启用：yes
- 场景：`uth-docs`
- 模式：`module-governance` 收尾与旧文档清理
- 完成等级：`full-project-docs-complete`
- 项目标记：`.uth-governance/project.json`
- 文档语言：`zh-CN`
- 接管前备份：`docs/ONB26051701-pre-uth-docs-backup.zip`
- 接管快照：`docs/snapshots/ONB26051701-existing-project-handoff.md`
- 当前入口：`docs/README.md`
- 当前上下文索引：`docs/context/README.md`
- 模块拆分报告：`docs/context/00-模块拆分.md`
- 旧文档分类：`docs/context/old-doc-classification.md`

此前文档中出现的 `full-project-docs-complete` 结论已被本轮修复判定为旧证据；当前 `full-project-docs-complete` 只指本轮在 12 个编号模块上下文、旧文档分类、归档清理和当前状态索引一致后重新形成的完成态。

## 当前事实入口

| 入口 | 路径 | 说明 |
| --- | --- | --- |
| 文档入口 | `docs/README.md` | 新窗口文档读取入口 |
| 状态入口 | `docs/current-state.md` | 当前接管完成态、证据和后续路由 |
| 上下文索引 | `docs/context/README.md` | 当前事实层索引 |
| 模块拆分 | `docs/context/00-模块拆分.md` | 已确认模块队列、代码事实范围和清理规则 |
| 旧模块分类 | `docs/context/docs-00-11-classification.md` | 旧 `docs/00` 到 `docs/11` 的归档与替代关系 |
| 旧文档分类 | `docs/context/old-doc-classification.md` | 接管前文档与历史协作材料分类 |

## 已完成模块上下文

| Order | Module package | Context |
| --- | --- | --- |
| 1 | `verification-build-governance` | `docs/context/01-验证构建治理.md` |
| 2 | `app-shell-and-integration` | `docs/context/02-应用壳层与集成.md` |
| 3 | `core-foundation-and-db` | `docs/context/03-核心基础与数据库.md` |
| 4 | `core-runtime` | `docs/context/04-核心运行时.md` |
| 5 | `download-and-container-assets` | `docs/context/05-下载与容器资产.md` |
| 6 | `provider-config-bot-persona` | `docs/context/06-Provider配置Bot与Persona.md` |
| 7 | `chat-and-conversation` | `docs/context/07-聊天与会话.md` |
| 8 | `qq-napcat-onebot` | `docs/context/08-QQ_NapCat_OneBot.md` |
| 9 | `plugin-platform` | `docs/context/09-插件平台.md` |
| 10 | `cron-runtime` | `docs/context/10-Cron运行时.md` |
| 11 | `resource-settings-backup` | `docs/context/11-资源设置备份.md` |
| 12 | `voiceasset-audio` | `docs/context/12-语音资产与音频.md` |

## 旧文档清理

- 旧 `docs/00_*.md` 到 `docs/11_*.md` 已归档到 `docs/archive/pre-uth-docs/docs-00-11/`。
- 归档前已确认这些旧文件原路径存在于 `docs/ONB26051701-pre-uth-docs-backup.zip`。
- `docs/archive/takeover-repair/baseline.md`、`docs/archive/takeover-repair/feature-modules.md`、`docs/archive/takeover-repair/onboarding-followup-evidence.md` 已归档到 `docs/archive/takeover-repair/`，只作历史修复证据。
- `docs/archive/context-pre-numbering/core-data-runtime.md` 保持为旧未编号 context 迁移证据。
- 旧文档只作为历史辅助证据；当前事实以编号 context 和代码事实来源为准。

## 当前阻塞

- 文档接管收尾：无活跃阻塞。
- 当前正式任务 `D26052102`：插件 API 对齐 AstrBot 能力补齐已完成 `uth-dev` / `todo-breakdown`；Design：`docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`；Todo：`T01` 到 `T12` 已全量拆分到同一任务包。用户确认不暴露 AstrBot 风格别名，网络能力走宿主代理，`hostApi.callLlm` 默认绕过插件 LLM hooks。源码实现、评审和 Git 写入均未执行，实际派发顺序等待用户决定。
- 当前正式任务 `D26052101`：应用内更新已完成 `uth-dev` / `formal-dev` 实现与主控验证；Design：`docs/work/D26052101-应用内更新/00-D26052101-design.md`；Todo：`docs/work/D26052101-应用内更新/10-D26052101-T01-todo-应用内更新.md`；Feedback：`docs/work/D26052101-应用内更新/11-D26052101-T01-feedback-应用内更新.md`；Worker prompt：`docs/work/D26052101-应用内更新/prompts/P260521-0115-T01-worker-应用内更新.md`。Evaluator 结论为 `PASS_WITH_RISKS`，残余风险为未做设备级安装闭环；Git 写入未执行，等待人工验收后进入 `uth-git`。
- 当前正式任务 `D26051901`：QQ 斜杠指令管理员权限已实现并完成 `/help` 绕过权限的 debug 修复；Git 写入未执行，等待后续 `uth-git`。
- Git：当前 `master`、`origin/master`、`codex/ColorOS16(RealmeUI7)` 与 `origin/codex/ColorOS16(RealmeUI7)` 均指向 `66eee69`；本轮 `uth-design` 不执行 Git 写入。

## 最新验证证据

| Time | Method | Result | Notes |
| --- | --- | --- | --- |
| 2026-05-17 20:20 +08:00 | code-fact scan | pass | 第 12 模块 `voiceasset-audio` 已按当前源码、构建、Hilt、Room、runtime audio、脚本资产和测试入口核对 |
| 2026-05-17 21:05 +08:00 | backup path check | pass | 旧 `docs/00` 到 `docs/11` 原路径存在于 `docs/ONB26051701-pre-uth-docs-backup.zip` |
| 2026-05-17 21:05 +08:00 | archive cleanup | pass | 旧 `docs/00` 到 `docs/11` 与早期 seed / 失效证据已移动到 `docs/archive/` |
| 2026-05-17 21:05 +08:00 | `uth-utf8-guard` pre-write | pass | 写入前 58 个 Markdown 文件通过 UTF-8 guard |
| 2026-05-17 21:05 +08:00 | `uth-utf8-guard` post-write | pass | 写入后 58 个 Markdown 文件通过 UTF-8 guard |
| 2026-05-17 21:05 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | `uth-docs` 收尾与旧文档清理 closeout gate 通过 |
| 2026-05-17 21:30 +08:00 | changelog anchor scan | pass | 以 `git tag --list 'v*'`、`git log --grep='^Release v[0-9]'` 和 `app/build.gradle.kts` 版本号建立 `docs/changelogs/version-git-anchors.md` 索引；未写发布正文 |
| 2026-05-18 18:03 +08:00 | `clean architectureCheck` | pass | 首次窄构建命中 stale generated output；清理后架构入口通过 |
| 2026-05-18 18:03 +08:00 | `:build-logic:check` | pass | Gradle convention / build logic 回归通过 |
| 2026-05-18 18:03 +08:00 | `:app:testDebugUnitTest` | pass | App debug unit test 回归通过 |
| 2026-05-18 18:03 +08:00 | `clean assembleDebug` | pass | 日志保存到 `build/reports/D26051801-clean-assembleDebug.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-18 18:03 +08:00 | legacy exact-name scan | pass | 排除 `.git`、`.worktrees`、`build`、`.gradle`、`bin`、`logs`、APK artifacts 和 zip 后，项目自有旧名精确集合无命中 |
| 2026-05-18 18:03 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | `uth-dev` / `formal-dev` closeout 通过；positive claim evidence 与 code verification clean |
| 2026-05-18 20:44 +08:00 | version / branch anchor scan | pass | `app/build.gradle.kts` 为 `versionName = "1.0.0"`、`versionCode = 76`；`v1.0.0` tag 指向 `c25812e`，当前 `HEAD` 为 `66eee69` |
| 2026-05-18 20:44 +08:00 | generated artifacts guard scan | pass | `.gitignore` 排除 `artifacts/`；CI 新增 `Verify generated artifacts are not tracked` 步骤 |
| 2026-05-19 23:49 +08:00 | targeted `:app:testDebugUnitTest` | pass | 覆盖 `QqPluginDispatchServiceTest`、`ElymBotDatabaseSchemaContractTest`、`ConfigMappersTest`；`BUILD SUCCESSFUL in 35s` |
| 2026-05-19 23:56 +08:00 | `clean architectureCheck assembleDebug` | pass | 日志保存到 `build/reports/D26051901-plugin-command-admin-only-clean-architecture-assemble.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-19 23:59 +08:00 | `:app:testDebugUnitTest` | pass | 日志保存到 `build/reports/D26051901-plugin-command-admin-only-app-testDebugUnitTest.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-20 00:01 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | `uth-dev` / `formal-dev` closeout 通过；`code-verification-clean` |
| 2026-05-20 13:00 +08:00 | red regression test | fail expected | `QqPluginDispatchServiceTest.qq_bot_command_permission_blocks_non_admin_help_when_admin_only_enabled` 复现非管理员 `/help` 返回帮助文本 |
| 2026-05-20 13:03 +08:00 | focused QQ command tests | pass | `QqPluginDispatchServiceTest` 与 `BotCommandRouterProviderTest` 通过；覆盖 QQ 内置命令和插件命令权限入口 |
| 2026-05-20 13:06 +08:00 | `clean architectureCheck assembleDebug` | pass | 日志保存到 `build/reports/D26051901-slash-command-admin-only-debug-clean-architecture-assemble.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-20 13:07 +08:00 | `:app:testDebugUnitTest` | pass | 日志保存到 `build/reports/D26051901-slash-command-admin-only-debug-app-testDebugUnitTest.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-21 01:37 +08:00 | `:app:testDebugUnitTest --tests "com.elymbot.android.update.*"` | pass | 日志保存到 `build/reports/D26052101-app-update-tests.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-21 01:38 +08:00 | app update boundary unit tests | pass | `RuntimeNetworkModelsTest`、`MainActivityPluginDeepLinkTest`、`AndroidManifestRuntimeContractTest` 通过；日志保存到 `build/reports/D26052101-app-update-boundary-tests.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-21 01:40 +08:00 | `:app:compileDebugKotlin` | pass | 日志保存到 `build/reports/D26052101-app-update-compileDebugKotlin.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-21 01:41 +08:00 | `architectureCheck` | pass | 日志保存到 `build/reports/D26052101-app-update-architectureCheck.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-21 01:46 +08:00 | `clean assembleDebug` | pass | 日志保存到 `build/reports/D26052101-app-update-clean-assembleDebug.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-21 01:49 +08:00 | `:app:testDebugUnitTest` | pass | 全量 app debug unit test 通过；日志保存到 `build/reports/D26052101-app-update-app-testDebugUnitTest.log`；warning / deprecated / exception 扫描计数为 0 |
| 2026-05-21 02:40 +08:00 | `uth-utf8-guard` post-write | pass | `docs/current-state.md` 与 D26052101 task package 5 个 Markdown 文件通过 UTF-8 guard |
| 2026-05-21 02:40 +08:00 | `tools/uth-hooks/uth-hook.py` L3 closeout | pass | `uth-dev` / `formal-dev` closeout 通过；`positive-claim-evidence-present` 与 `code-verification-clean` |
| 2026-05-22 00:35 +08:00 | D26052102 T06-T10 review gate | pass with risk | 聚焦 runtime/app/app-integration 测试、`architectureCheck`、`modulePluginCheck`、`clean assembleDebug` 均通过；6 个日志 warning / deprecated / exception / failed 扫描计数均为 0；`130` 索引已更新；T06/T09 细粒度审计字段作为后续增强风险记录 |

## 当前事实来源

- `AGENTS.md`
- `README.md`
- `.uth-governance/project.json`
- `docs/README.md`
- `docs/context/README.md`
- `docs/context/00-模块拆分.md`
- `docs/context/01-验证构建治理.md` 到 `docs/context/12-语音资产与音频.md`
- `docs/context/docs-00-11-classification.md`
- `docs/context/old-doc-classification.md`
- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/elymbot/android/ElymBotApplication.kt`
- `app/src/main/java/com/elymbot/android/MainActivity.kt`
- `app/src/main/java/com/elymbot/android/di/**`
- `app/src/main/java/com/elymbot/android/ui/**`
- `app-integration/src/main/java/**`
- `architecture-tests/src/test/java/**`
- `core/**/src/main/java/**`
- `download/**/src/main/java/**`
- `feature/**/src/main/java/**`
- `app/src/test/java/**`
- `feature/**/src/test/java/**`
- `app/src/main/assets/runtime/scripts/**`

## 后续路由

- 普通开发：`uth-governance` -> `uth-dev`
- bug / 构建失败 / 回归：`uth-governance` -> `uth-debug`
- 验收 / 代码审查：`uth-governance` -> `uth-review`
- 文档同步：`uth-governance` -> `uth-docs`
- Git / PR / 发布：`uth-governance` -> `uth-git`

## 最近正式任务

- Scene: `uth-dev`
- Mode: `todo-breakdown`
- Task package: `docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- Active Design: `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
- Todo set:
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/10-D26052102-T01-todo-宿主API异步桥与权限底座.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/20-D26052102-T02-todo-宿主网络请求代理.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/30-D26052102-T03-todo-Provider模型只读查询.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/40-D26052102-T04-todo-当前会话消息发送.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/50-D26052102-T05-todo-当前会话历史只读查询.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/60-D26052102-T06-todo-直接LLM调用.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/70-D26052102-T07-todo-上下文压缩落地.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/80-D26052102-T08-todo-插件定时任务回调.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/90-D26052102-T09-todo-插件流式输出.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/100-D26052102-T10-todo-富消息链.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/110-D26052102-T11-todo-Agent能力注册.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/120-D26052102-T12-todo-Filter组合表达式.md`
- Remaining index:
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/130-D26052102-剩余插件API能力索引.md`
- Active Todo: not selected；T11 到 T12 仍待用户后续决定派发顺序。
- Goal: split all designed plugin API parity capabilities into formal Todo files after excluding Web API registration, platform adapter registration, DB direct access, text-to-image, and HTML rendering.
- Status: T01 到 T10 已有实现与验证记录；T11 到 T12 尚未开始；T06/T09 细粒度审计字段作为后续增强风险记录在 `130` 索引中；Git 写入未执行。
- Git baseline: pending；本轮 `uth-dev` 未执行 Git 写入。
- Docs sync: `Needs uth-docs scoped-sync`；T06 到 T10 已改变插件平台事实，后续需要进入 `uth-docs` 更新 `docs/context/09-插件平台.md`。

## D26052102 Phase B 实施状态（2026-05-21 16:22 +08:00）

- Scene：`uth-dev`
- Mode：`formal-dev`
- Task package：`docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- Completed scope：Phase A/T01 已完成；Phase B/T02-T05 已完成实现、主控验证和 evaluator 复核。
- Feedback：
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/21-D26052102-T02-feedback-宿主网络请求代理.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/31-D26052102-T03-feedback-Provider模型只读查询.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/41-D26052102-T04-feedback-当前会话消息发送.md`
  - `docs/work/D26052102-插件API对齐AstrBot能力补齐/51-D26052102-T05-feedback-当前会话历史只读查询.md`
- Evaluator：`019e4981-64d9-7be2-badc-c3d4d0539a40`，Phase B 复核结论为 `PASS`。
- Verification：
  - `.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*PluginV2HostNetworkApiTest*" --tests "*PluginV2MessageSendApiTest*" --tests "*PluginV2HostApiQuickJsCapabilitiesTest*" --console=plain --no-daemon --stacktrace`：pass。
  - `.\gradlew.bat :app-integration:testDebugUnitTest --tests "*PluginHostCapabilityModuleTest*" --console=plain --no-daemon --stacktrace`：pass。
  - `.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*HostNetwork*" --tests "*ProviderRead*" --tests "*MessageSend*" --tests "*ConversationHistory*" --tests "*PluginV2HostApiQuickJsCapabilitiesTest*" --console=plain --no-daemon --stacktrace`：pass。
  - `.\gradlew.bat :app:testDebugUnitTest --tests "*RuntimeNetworkModelsTest*" --tests "*PluginV2HostApiArchitectureContractTest*" --tests "*PluginPackageContractJsonTest*" --tests "*PluginV2BootstrapHostApiTest*" --tests "*PluginV2QuickJsCallbackLifecycleTest*" --tests "*PluginV2HostApiAsyncBridgeTest*" --tests "*PluginV2HostApiAuditLoggerTest*" --tests "*PluginV2HostApiPermissionPolicyTest*" --console=plain --no-daemon --stacktrace`：pass。
  - `.\gradlew.bat :app-integration:compileDebugKotlin :app:compileDebugKotlin architectureCheck --console=plain --no-daemon --stacktrace`：pass。
  - `.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace`：pass；日志 `build/reports/D26052102-phase-b-clean-assembleDebug.log`；warning / deprecated / exception 扫描为 0。
  - `.\gradlew.bat modulePluginCheck --console=plain --no-daemon --stacktrace`：pass；2026-05-21 16:39 已补齐 `app/src/main/res/values-zh/strings.xml` 中 26 个缺失翻译并收口既有 MissingTranslation；日志：`build/reports/D26052102-modulePluginCheck-translation-closeout.log`。
  - `.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace`：pass；2026-05-21 16:39 收口复验日志 `build/reports/D26052102-translation-closeout-clean-assembleDebug.log`；warning / deprecated / exception 扫描为 0。
- Active Todo（历史状态，已被 2026-05-22 T06-T10 验收更新）：T06 到 T12 仍待用户决定是否继续；剩余能力索引已补充；不得在未确认前推进 T06+。
- Git baseline：pending；本轮 `uth-dev` 未执行 Git 写入。
- Docs sync：`Needs uth-docs scoped-sync`。本轮实现改变插件平台事实，Design-level 或阶段验收后应同步 `docs/context/09-插件平台.md`。

## D26052102 T06-T10 验收状态（2026-05-22 00:35 +08:00）

- Scene：`uth-review`
- Review target：`T06 直接 LLM 调用`、`T07 上下文压缩落地`、`T08 插件定时任务回调`、`T09 插件流式输出`、`T10 富消息链`
- Result：`pass with risk`。功能、权限、结构与构建门禁通过；T06/T09 的 LLM provider/model/token usage 与 streamId/chunk/bytes/duration 专用审计字段可作为后续增强，不再作为 AstrBot 对齐能力缺口保留。
- Verification：
  - `.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*Llm*" --tests "*Context*Compress*" --tests "*Scheduled*" --tests "*Stream*" --tests "*MessageSegment*" --tests "*RichMessage*" --tests "*PluginV2HostApiQuickJsCapabilitiesTest*" --console=plain --no-daemon --stacktrace`：pass；日志 `build/reports/D26052102-T06-T10-feature-plugin-runtime-tests.log`。
  - `.\gradlew.bat :app:testDebugUnitTest --tests "*FutureToolSourceRegistryContextCompressionInjectionTest*" --tests "*ContextStrategyToolSourceProviderTest*" --tests "*CronJobRunCoordinatorTest*" --tests "*PluginV2RuntimeLoaderTest*" --tests "*PluginV2HostApiArchitectureContractTest*" --tests "*PluginV2BootstrapHostApiTest*" --tests "*PluginV2HostApiPermissionPolicyTest*" --tests "*PluginV2HostApiAuditLoggerTest*" --console=plain --no-daemon --stacktrace`：pass；日志 `build/reports/D26052102-T06-T10-app-tests.log`。
  - `.\gradlew.bat :app-integration:testDebugUnitTest --tests "*PluginHostCapabilityModuleTest*" --console=plain --no-daemon --stacktrace`：pass；日志 `build/reports/D26052102-T06-T10-app-integration-tests.log`。
  - `.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace`：pass；日志 `build/reports/D26052102-T06-T10-architectureCheck.log`。
  - `.\gradlew.bat modulePluginCheck --console=plain --no-daemon --stacktrace`：pass；日志 `build/reports/D26052102-T06-T10-modulePluginCheck.log`。
  - `.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace`：pass；日志 `build/reports/D26052102-T06-T10-clean-assembleDebug.log`。
- Log scan：上述 6 个日志的 `warning` / `deprecated` / `exception` / `failed` 扫描计数均为 0。
- Documents written：`docs/work/D26052102-插件API对齐AstrBot能力补齐/130-D26052102-剩余插件API能力索引.md` 与 `docs/current-state.md`。
- Active Todo：T11 与 T12 仍待用户决定是否派发。
- Git baseline：pending；本轮 `uth-review` 未执行 Git 写入。
- Docs sync：`Needs uth-docs scoped-sync`；如需背景上下文反映 T06-T10 当前代码事实，后续进入 `uth-docs` 更新 `docs/context/09-插件平台.md`。

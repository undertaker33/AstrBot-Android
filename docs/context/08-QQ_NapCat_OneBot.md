# QQ / NapCat / OneBot 模块上下文

更新时间：2026-06-06 11:41 +08:00

## 状态

- 场景：`uth-docs`
- 模式：`module-governance`
- 模块序号：08
- 模块：`qq-napcat-onebot`
- 模块状态：已按当前代码事实重新确认
- 完成等级：`full-project-docs-complete`
- 下一模块：`plugin-platform`
- 最近影响本模块的文档更新：`4fedf19..13467db` + 当前工作区 QQ 普通群公共历史沉淀增量 + `D26060501` NapCat 安卓权限与启动兼容事实；2026-06-10 全仓同步确认 `b069daf` 仍属 v1.1.2 release 基线，未新增本模块当前工作区事实。
- 本轮 Git 写入：无
- 本轮 Gradle / 测试命令：未运行；本文档场景只做文档治理

## 代码事实来源

本模块重新核对了以下当前源码、构建、运行时脚本、Hilt wiring 和测试入口：

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app-integration/build.gradle.kts`
- `feature/qq/api/build.gradle.kts`
- `feature/qq/data/build.gradle.kts`
- `feature/qq/impl/build.gradle.kts`
- `feature/qq/presentation/build.gradle.kts`
- `feature/qq/runtime/build.gradle.kts`
- `feature/qq/api/src/main/java/**`
- `feature/qq/data/src/main/java/**`
- `feature/qq/impl/src/main/java/**`
- `feature/qq/presentation/src/main/java/**`
- `feature/qq/runtime/src/main/java/**`
- `feature/qq/runtime/src/test/java/**`
- `feature/qq/impl/src/test/java/**`
- `app/src/main/java/com/elymbot/android/di/startup/BootstrapPrerequisitesStartupChain.kt`
- `app/src/main/java/com/elymbot/android/di/startup/RuntimeLaunchStartupChain.kt`
- `app/src/main/java/com/elymbot/android/di/hilt/QqRepositoryPortModule.kt`
- `app/src/main/java/com/elymbot/android/di/hilt/presentation/QqViewModelBindingsModule.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/QqPhase24PortModule.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/qq/QqContainerBridgeStatePortAdapter.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/qq/QqContainerBridgeStatePortModule.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/res/xml/network_security_config.xml`
- `app/src/main/assets/runtime/scripts/root_launcher.sh`
- `app/src/main/assets/runtime/scripts/start_napcat.sh`
- `app/src/main/assets/runtime/scripts/status_napcat.sh`
- `app/src/main/assets/runtime/scripts/logout_qq.sh`
- `core/runtime-container/src/main/java/com/elymbot/android/core/runtime/container/ContainerRuntimeInstaller.kt`
- `core/runtime-container/src/main/java/com/elymbot/android/core/runtime/container/ContainerBridgeRuntimeSupport.kt`
- `core/runtime-container/src/main/java/com/elymbot/android/core/runtime/container/RuntimeCompatibilityProbe.kt`
- `app/src/main/java/com/elymbot/android/core/runtime/container/ContainerBridgeService.kt`
- `app/src/main/java/com/elymbot/android/di/runtime/container/AndroidRuntimeBridgeController.kt`
- `app/src/test/java/com/elymbot/android/architecture/QqPhase24BoundaryContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/QqOneBotRuntimeGuardrailTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/NapCatRuntimeScriptContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/AndroidManifestRuntimeContractTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/RuntimeBridgeCompatibilityContractTest.kt`
- `core/runtime-container/src/test/java/com/elymbot/android/core/runtime/container/RuntimeCompatibilityProbeTest.kt`
- `app/src/test/resources/architecture/app-integration-allowlist.txt`
- `app/src/test/resources/architecture/dao-owner-allowlist.txt`
- `app/src/test/resources/architecture/global-singleton-allowlist.txt`
- `app/src/test/resources/architecture/runtime-persistence-allowlist.txt`
- `app/src/test/java/com/elymbot/android/feature/qq/runtime/**`
- `app/src/test/java/com/elymbot/android/runtime/qq/**`
- `app/src/test/java/com/elymbot/android/data/NapCat*Test.kt`
- `app/src/test/java/com/elymbot/android/ui/viewmodel/QQLogin*Test.kt`
- `app/src/test/java/com/elymbot/android/runtime/OneBot*Test.kt`

排除路径：

- `build/`
- `bin/`
- `.worktrees/`
- 生成物、IDE 缓存和二进制资产正文
- 旧文档正文自身

辅助证据：

- `AGENTS.md`
- `docs/archive/pre-uth-docs/docs-00-11/05_QQ登录_NapCat_OneBot运行时.md`
- `docs/archive/pre-uth-docs/docs-00-11/10_测试入口_回归面_已知风险.md`
- `docs/context/04-核心运行时.md`
- `docs/context/05-下载与容器资产.md`
- `docs/context/07-聊天与会话.md`

辅助证据不得覆盖当前代码事实。

## 模块职责

本模块当前覆盖：

- `:feature:qq:api`：QQ 登录、bridge state、platform config、conversation、runtime、scheduled delivery、plugin execution 与 presentation port contract。
- `:feature:qq:data`：NapCat 登录状态、本地 quick-login / saved account 存储、bridge config/runtime state、旧状态迁移、QQ conversation/platform config adapters。
- `:feature:qq:presentation`：QQ 登录界面、登录 ViewModel、单例轮询 tracker、quick login / password / captcha / new-device 操作入口。
- `:feature:qq:runtime`：OneBot reverse WebSocket、payload parser、bridge server、runtime graph factory、消息规则链、plugin ingress、bot command、LLM dispatch、streaming / voice / news reply、OneBot outbound gateway。
- `app` 启动链：通过 `QqStartupPort` 初始化并启动 QQ bridge runtime。
- `app-integration` wiring：只保留精确的 QQ/plugin port 接线和 container bridge state adapter。
- NapCat 容器脚本与安装链：`root_launcher.sh`、`start_napcat.sh`、runtime assets、container installer、progress label。
- NapCat 安卓启动兼容：runtime compatibility preflight、optional external storage bind、前台服务启动失败分类和 bridge state warning 展示。

## 非职责

- Plugin runtime、host capability 和 tool source 的整体治理不在本模块完成态；这里只确认 QQ runtime 如何通过 plugin API/capability port 消费它们。
- App Chat 发送链和 conversation store 已在 `chat-and-conversation` 确认；本模块只记录 QQ 如何复用 conversation port 与 session key。
- Cron runtime 的调度执行不在本模块完成态；这里只确认 `QqScheduledMessageSender` 是定时消息投递目标之一。
- Resource Center、settings backup、完整备份 UI 和 runtime cleanup 不在本模块完成态。
- STT/TTS 与 voiceasset 资产治理不在本模块完成态；这里只确认 QQ runtime 对 STT/TTS port 的调用边界。

## 当前 Gradle 与模块边界

`settings.gradle.kts` 当前声明：

- `:feature:qq:api`
- `:feature:qq:data`
- `:feature:qq:impl`
- `:feature:qq:presentation`
- `:feature:qq:runtime`

源码数量：

- `feature/qq/api/src/main/java`：10 个 Kotlin/Java 文件
- `feature/qq/data/src/main/java`：10 个 Kotlin/Java 文件
- `feature/qq/impl/src/main/java`：0 个 Kotlin/Java 生产文件
- `feature/qq/presentation/src/main/java`：5 个 Kotlin/Java 文件
- `feature/qq/runtime/src/main/java`：32 个 Kotlin/Java 文件
- `feature/qq/impl/src/test/java`：1 个 Kotlin/Java 测试文件

依赖事实：

- `:feature:qq:api` 依赖 bot/chat/conversation API，并以 `api(project(":feature:plugin:api"))` 暴露 plugin API contract。
- `:feature:qq:data` 启用 Hilt + KSP，依赖 core db/logging/network/runtime/container、bot/config/conversation API 和 QQ API。
- `:feature:qq:impl` 当前没有生产源码，只依赖 QQ API/runtime；不要把它写成 QQ 生产 owner。
- `:feature:qq:presentation` 启用 Compose + Hilt，只依赖 core UI、QQ API、ZXing、lifecycle 和 Compose。
- `:feature:qq:runtime` 依赖 core runtime-context/llm/search/session/audio/container、chat runtime、cron runtime、bot/config/conversation/persona/plugin/provider/QQ API、NanoHTTPD websocket、Hilt 和 JSON。
- `app-integration` 以 `api(project(":feature:qq:api"))` 暴露 QQ API，以 `implementation` 接 `:feature:qq:data`、`:feature:qq:impl`、`:feature:qq:runtime`。
- `app/build.gradle.kts` 生产只直接依赖 `:feature:qq:presentation`；QQ data/runtime 通过 Hilt 和 app-integration 进入。
- root `build.gradle.kts` 当前有 `moduleQq*` 模块组，覆盖 QQ API/data/impl/runtime/presentation。

## Hilt 与启动主线

当前生产启动主线：

1. `AppBootstrapper -> AppStartupRunner`
2. `BootstrapPrerequisitesStartupChain` 调 `QqStartupPort.initialize()`，同时触发 `QqLoginStateBootstrapper.ensureReady()`
3. `RuntimeLaunchStartupChain` 调 `QqStartupPort.start()`
4. `QqRuntimeServicesModule` 提供 `QqOneBotRuntimeDependencies`、`QqRuntimeGraphFactory`、`QqBridgeRuntime`、`QqStartupPort`、`QqScheduledMessageSender`
5. `HiltQqOneBotBridgeRuntime` 作为生产 bridge runtime，内部按需构建 `QqOneBotRuntimeGraph`

不要把生产启动写回直接调用 `QqOneBotBridgeServer.start()` 或手写 static dependency provider。

当前已知债务：

- `app/src/test/resources/architecture/app-integration-allowlist.txt` 仍允许 `RuntimeServicesModule.kt` 中 `provideQqOneBotRuntimeDependencies` 作为 broad Hilt provider 债务。
- 这不代表 QQ runtime 已完全细粒度 factory 化；它只是当前允许的精确 wiring debt。

## QQ data / 登录 / bridge state

`QqDataBootstrapModule` 当前绑定：

- `NapCatLoginLocalStoreOwner -> QqLoginStateBootstrapper`
- `FeatureQqConversationPortAdapter -> QqConversationPort`
- `FeatureQqPlatformConfigPortAdapter -> QqPlatformConfigPort`
- `QqLoginRepositoryAdapter -> QqLoginRepositoryPort`
- `NapCatBridgeStateOwner -> QqBridgeStatePort`

当前登录链事实：

- `NapCatLoginRepository` 是 Hilt `@Singleton` 实例，不是 static object。
- `NapCatLoginService` 通过 `ElymBotHttpClient` 访问 NapCat WebUI，登录错误会区分 auth、business reject、malformed response 和 network failure 等类别。
- `NapCatLoginLocalStore` 使用 `AppPreferenceDao` 与 `SavedQqAccountDao`，迁移旧 `SharedPreferences` quick-login / saved accounts。
- `QqLoginRepositoryAdapter` 通过 `QqLoginRepositoryPort` 暴露 refresh、QR refresh、quick login、password/captcha/new-device login、logout、backup restore。
- `NapCatBridgeRepository.kt` 当前主类名是 `NapCatBridgeStateOwner`，实现 `QqBridgeStatePort`，负责 bridge config、runtime state、progress、error 和 legacy bridge config 迁移。
- `FeatureQqConversationPortAdapter` 通过 `ConversationRepositoryPort` 接入 conversation store。
- `FeatureQqPlatformConfigPortAdapter` 通过 `BotRepositoryPort` 与 `ConfigRepositoryPort` 解析 QQ bot、whitelist、wake words、rate limit、session isolation 和 reply flags。

当前已知债务：

- `app/src/test/resources/architecture/dao-owner-allowlist.txt` 仍记录 `feature/qq/data/NapCatLoginLocalStore.kt | SavedQqAccountDao`，原因是 QQ login local store 持有 saved QQ account persistence 但 DAO 名未 QQ 前缀化。
- `global-singleton-allowlist.txt` 只保留 `feature/qq/runtime/QqSessionKeyFactory.kt` 作为 stateless permanent mapper；不要新增 QQ static owner allowlist。

## Presentation / UI 入口

QQ 登录 UI 当前物理位于 `feature/qq/presentation/src/main/java/**`。

需要注意路径与 package 不完全一致：

- `QQLoginViewModel.kt` 物理在 `feature/qq/presentation`，package 是 `com.elymbot.android.ui.viewmodel`。
- `QQLoginScreens.kt` / `QqLoginComponents.kt` 物理在 `feature/qq/presentation`，package 仍使用 `com.elymbot.android.ui.qqlogin` 相关入口。
- `QqPresentationBindingsModule` 把 `DefaultQQLoginViewModelBindings` 绑定为 `QQLoginViewModelBindings`。
- `app/src/main/java/com/elymbot/android/ui/navigation/ElymBotAppScaffoldParts.kt` 通过 `AppDestination.QQAccount` 与 `AppDestination.QQLogin` 接入 QQ account / login 页面。

`QQLoginViewModel` 当前使用 `QQLoginPollingTracker` 管理多个可见登录 screen 的共享 polling job；当 bridge ready、未登录且 QR 为空时会自动触发一次 QR refresh。

## OneBot / runtime graph / 消息主链

当前 OneBot 入站主线：

1. `OneBotReverseWebSocketTransport` 在 `ws://127.0.0.1:6199/ws` 监听 reverse WS。
2. `isAuthorizedOneBotReverseWebSocket(headers, authToken)` 要求 auth token 非空，并匹配 `Authorization` header；支持 `Bearer <token>` 或直接 token。
3. `QqOneBotBridgeServer.handlePayload(...)` 先处理 action response，再把事件交给 adapter。
4. `OneBotPayloadParser` 解析 OneBot payload。
5. `OneBotServerAdapter` 调用 `QqRuntimePort`。
6. `QqOneBotRuntimeGraphFactory` 组装 `QqOneBotOutboundGateway`、`QqReplySender`、`QqRuntimeProfileResolver`、`QqPluginDispatchService`、`QqBotCommandRuntimeService`、`QqStreamingReplyService`、`QqMessageRuntimeService`。
7. `QqMessageRuntimeService.handleIncomingMessage(...)` 执行 bot resolve、wake/whitelist/rate-limit/keyword/duplicate guard、session lock、bot command/plugin ingress、conversation append、runtime context resolve、LLM dispatch 和 reply delivery。
8. `QqReplySender` 与 `QqOneBotOutboundGateway` 发送 OneBot action。

当前普通群公共历史事实：

- `QqMessageRuntimeService` 在 OneBot message id 去重后，对普通群消息先写入公共群 session，session id 形如 `qq-<botId>-group-<groupId>`。
- 公共群历史使用非隔离语义并保留最近 100 条；超过上限后保留最后 100 条。
- 开启群聊隔离时，bot LLM 对话仍写入 isolated session，形如 `qq-<botId>-group-<groupId>-user-<userId>`；公共群历史不应被 isolated session 覆盖。
- 不触发自动回复的普通群消息也可进入公共群历史；这使插件群分析不再依赖 bot 对话上下文。
- 非隔离 LLM 路径会复用已沉淀的公共群 user message，避免同一入站消息重复追加。
- 本轮未做真实 QQ / NapCat 连接下的端到端人工验收；当前证据来自既有 LW 验收记录、OneBot 入站单测、Host API 历史读取映射和全量 debug 构建记录。

当前 reverse WS 与 cleartext 约束：

- `app/src/main/AndroidManifest.xml` 显式 `android:usesCleartextTraffic="false"`。
- `network_security_config.xml` 只允许 `127.0.0.1` 和 `localhost` cleartext。
- QQ / NapCat / reverse WS 只能写成本地 loopback 集成，不要写成任意 cleartext 或外部 websocket 入口。

`QqOneBotBridgeServer` 当前应保持薄 glue：

- `QqOneBotRuntimeGuardrailTest` 限制 bridge server 行数和禁止旧 business function 回流。
- `NoManualRuntimeSubgraphContractTest` / `StrictHiltOnlyFinalContractTest` 约束 runtime graph 不要直接手写 new 高复杂服务。

## Plugin / command / LLM 边界

QQ runtime 当前通过 API/capability port 消费 plugin 能力：

- `QqPluginDispatchService`
- `QqPluginExecutionPort`
- `PluginV2MessageDispatchPort`
- `PluginHostCapabilityGateway`
- `PluginHostCapabilityGatewayFactory`
- `PluginWorkspacePathPort`

`QqPhase24BoundaryContractTest` 明确约束：

- QQ runtime 不依赖 `:feature:plugin:data` 或 `:feature:plugin:runtime`。
- QQ runtime 生产源码不得 import `com.elymbot.android.feature.plugin.runtime.*`。
- QQ runtime 不通过 chat runtime 的 `api` 暴露拿到 plugin runtime。
- QQ runtime 不直接 import `PluginStoragePaths`，而是通过 `PluginWorkspacePathPort` 获取 plugin private root。

Bot command 当前复用 `feature/chat/runtime` 的 `BotCommandRouter` 与 `AndroidBotCommandStringResolver`，QQ slash command 输出不应再写成 app res 或 QQ runtime 自有字符串。

`QqSlashCommandPermissionPolicy` 当前集中判断插件 slash command 管理员限制：当 config/profile 中 `pluginCommandsAdminOnlyEnabled` 开启时，非管理员插件命令会被阻断；内置 bot command 仍走 `QqBotCommandRuntimeService` / `BotCommandRouter` 的既有路径。

LLM 边界：

- `DefaultQqProviderInvoker` 通过 `LlmClientPort` 发起 provider request，不直接调用旧 `ChatCompletionService`。
- `QqMessageRuntimeService` 通过 `RuntimeContextResolverPort.resolve(...)` 构造 runtime context，再通过 `RuntimeLlmOrchestratorPort.dispatchLlm(...)` 进入统一 LLM pipeline。
- `ScheduledTaskIntentFallbackResponder` 注入 QQ runtime，用于 proactive fallback 场景。
- `QqStreamingReplyService` 支持 pseudo streaming、voice streaming 和 news web_search facts direct delivery。

## NapCat 容器与脚本

当前容器安装与运行事实：

- `ContainerRuntimeInstaller` 复制 runtime scripts、native runtime links、runtime assets，并把 bridge defaults 写成 `ws://127.0.0.1:6199/ws` 与 `http://127.0.0.1:6099`。
- `root_launcher.sh` 优先使用 bundled NapCat/QQ/launcher/offline assets；bundled assets 不完整时 fallback 到 upstream installer 下载。
- `root_launcher.sh` 对已有安装的 launcher shim 缺失是容忍的，不直接 hard fail。
- `start_napcat.sh` 通过 proot 启动 Ubuntu rootfs，准备 fake proc binds、apt mirror、runtime env、progress 文件和 `/root/elymbot_napcat_entry.sh`。
- `start_napcat.sh` 的长运行 proot 命令不再无条件 bind `/sdcard` 或 `/storage/emulated/0`；脚本只在 host path 存在且可读时加入 optional external storage bind，并记录 skipped/added 日志。
- `RuntimeCompatibilityProbe.runPreflight()` 在 NapCat 启动前检查 rootfs、native runtime link、proot smoke、外部存储 bind 可读性与 Android 13+ 通知权限；blocking issue 阻断启动，non-blocking issue 写入 bridge progress/warning。
- `AndroidRuntimeBridgeController` 对 service 启动失败做分类日志，区分 service permission/security policy、Android 12+ foreground service background start restriction、foreground service failure 和普通 service start failure。
- `app/src/main/AndroidManifest.xml` 保持 `FOREGROUND_SERVICE_DATA_SYNC` 与 `ContainerBridgeService` 的 `foregroundServiceType="dataSync"`；本轮没有新增外部存储权限。
- `logout_qq.sh` 停止 runtime，但明确不清空 quick-login history。
- `ContainerBridgeRuntimeSupport` 读取 `runtime/run/napcat_progress*`，并保留 network-install progress label，例如 `download-installer`、`installer-downloaded`、`run-installer`。

因此旧文档不能再写成“只支持 bundled 离线安装”。当前真实口径是 bundled first、缺失时 upstream fallback。

## 合同与回归入口

本轮只读取合同和单元测试，不运行测试。相关入口包括：

- `QqPhase24BoundaryContractTest`
- `QqOneBotRuntimeGuardrailTest`
- `NapCatRuntimeScriptContractTest`
- `AndroidManifestRuntimeContractTest`
- `RuntimeBridgeCompatibilityContractTest`
- `RuntimeCompatibilityProbeTest`
- `FeatureFirstBoundaryContractTest`
- `ModuleDependencyGraphContractTest`
- `RepositoryPortSourceContractTest`
- `StaticRepositoryUsageContractTest`
- `StrictHiltOnlyContractTest`
- `StrictHiltOnlyFinalContractTest`
- `RuntimeContainerBoundaryContractTest`
- `RuntimePersistenceBoundaryContractTest`
- `PostHiltRound2PluginRuntimeContractTest`
- `PostHiltRound3HostCapabilityContractTest`
- `NoManualRuntimeSubgraphContractTest`
- `QqStreamingReplyServiceTest`
- `QqStreamingReplyServiceAttachmentStreamingTest`
- `QqRuntimeProfileResolverTest`
- `QqPluginDispatchServiceTest`
- `QqOneBotOutboundGatewayTest`
- `QqNewsReplyFormatterTest`
- `OneBotServerAdapterTest`
- `OneBotReverseWebSocketTransportTest`
- `OneBotPayloadParserTest`
- `OneBotBridgeServerTest`
- `OneBotPayloadCodecTest`
- `QqWhitelistMatcherTest`
- `QqWakeWordPolicyTest`
- `QqSessionKeyFactoryTest`
- `QqReplyPolicyEvaluatorTest`
- `QqReplyFormatterTest`
- `QqRateLimiterTest`
- `QqKeywordDetectorTest`
- `QqConversationTitleResolverTest`
- `NapCatLoginServiceTest`
- `NapCatLoginRepositoryTest`
- `NapCatLoginDiagnosticsTest`
- `NapCatBridgeRepositoryTest`
- `QQLoginViewModelTest`
- `QQLoginPollingTrackerTest`

对应回归命令仍由后续验证场景执行，文档场景不得声称已运行：

```powershell
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleQqCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:qq:runtime:testDebugUnitTest --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Qq*" --tests "*QQ*" --tests "*NapCat*" --tests "*OneBot*" --console=plain --no-daemon --stacktrace
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

## 当前风险与阻塞项

- 本轮未运行 Gradle 或测试，因此不能把本模块状态扩展为构建通过或测试通过。
- 当前工作区已新增 QQ 普通群公共历史沉淀、pseudo streaming 文本+附件分流，以及 NapCat 安卓启动兼容代码；本文档只同步代码事实和既有验收记录，不在 `uth-docs` 中重新运行测试。
- 真实低版本 Android、厂商 ROM 和真实 NapCat 端到端启动仍未由本轮文档场景覆盖。
- `feature/qq/impl` 当前无生产源码，不应写成 QQ 生产 owner。
- `QqRuntimeServicesModule.provideQqOneBotRuntimeDependencies` 仍在 app-integration allowlist 中作为 broad Hilt provider 债务。
- `NapCatLoginLocalStore.kt` 仍在 DAO owner allowlist 中持有 `SavedQqAccountDao`，后续需要命名或 port 边界进一步收口。
- `QqSessionKeyFactory.kt` 是 permanent stateless singleton allowlist；不得把有状态 QQ owner 伪装成 permanent singleton。
- QQ runtime 依赖 `:feature:chat:runtime` 使用 bot command 资源/解析器；该依赖已经由合同限制不能透传 plugin runtime API，但后续 plugin-platform 仍需复核 plugin 边界。
- `QqOneBotBridgeServer` 中 auth token 是源码常量，并由 root launcher 写入 onebot config；文档不得泄露为部署建议或外部可配置安全承诺。
- QQ 登录 UI 的物理路径与 Kotlin package 不一致；新窗口需要双重确认。

## 旧文档判断

- `docs/archive/pre-uth-docs/docs-00-11/05_QQ登录_NapCat_OneBot运行时.md`：保留为辅助证据。其 feature-first 方向、Hilt 主线、reverse WS 授权、NapCat 登录链和脚本 fallback 大体仍被当前代码支持；但其中提交号、旧版本叙述和部分路径顺序不能作为当前事实入口。
- `docs/archive/pre-uth-docs/docs-00-11/10_测试入口_回归面_已知风险.md`：保留为验证辅助入口；QQ / NapCat / OneBot 测试入口需要以后续实际验证为准。
- `docs/archive/pre-uth-docs/docs-00-11/00_当前基线与迁移摘要.md`：只作为跨模块迁移辅助证据，不能替代本模块代码事实。
- 旧 `docs/00` 到 `docs/11` 已在收尾阶段归档至 `docs/archive/pre-uth-docs/docs-00-11/`，仅作为历史辅助证据。

## 下一步

`qq-napcat-onebot` 模块上下文已完成。按新版 UTH module-governance 规则，下一步继续治理第 9 模块 `plugin-platform`。

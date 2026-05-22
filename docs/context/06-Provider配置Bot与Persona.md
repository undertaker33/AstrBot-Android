# provider-config-bot-persona 模块上下文

更新时间：2026-05-22 18:30 +08:00

## 状态

- 场景：`uth-docs`
- 模式：`module-governance`
- 模块：`provider-config-bot-persona`
- 模块状态：已按当前代码事实重新确认
- 完成等级：`full-project-docs-complete`
- 下一模块：`chat-and-conversation`
- 本轮 scoped-sync：`66eee69..4fedf19`，并按当前工作区状态核对文档
- 本轮 Git 写入：无
- 本轮 Gradle / 测试命令：未运行；本文档场景只做文档治理

## 代码事实来源

本模块重新核对了以下当前源码、构建和测试入口：

- `settings.gradle.kts`
- `build.gradle.kts`
- `app-integration/build.gradle.kts`
- `feature/provider/api/build.gradle.kts`
- `feature/provider/data/build.gradle.kts`
- `feature/provider/impl/build.gradle.kts`
- `feature/provider/runtime/build.gradle.kts`
- `feature/provider/presentation/build.gradle.kts`
- `feature/config/api/build.gradle.kts`
- `feature/config/data/build.gradle.kts`
- `feature/config/impl/build.gradle.kts`
- `feature/config/presentation/build.gradle.kts`
- `feature/bot/api/build.gradle.kts`
- `feature/bot/data/build.gradle.kts`
- `feature/bot/impl/build.gradle.kts`
- `feature/bot/presentation/build.gradle.kts`
- `feature/persona/api/build.gradle.kts`
- `feature/persona/data/build.gradle.kts`
- `feature/persona/impl/build.gradle.kts`
- `feature/persona/presentation/build.gradle.kts`
- `feature/provider/api/src/main/java/**`
- `feature/provider/data/src/main/java/**`
- `feature/provider/runtime/src/main/java/**`
- `feature/provider/presentation/src/main/java/**`
- `feature/config/api/src/main/java/**`
- `feature/config/data/src/main/java/**`
- `feature/config/presentation/src/main/java/**`
- `feature/bot/api/src/main/java/**`
- `feature/bot/data/src/main/java/**`
- `feature/bot/presentation/src/main/java/**`
- `feature/persona/api/src/main/java/**`
- `feature/persona/data/src/main/java/**`
- `feature/persona/presentation/src/main/java/**`
- `app-integration/src/main/java/com/elymbot/android/app/integration/provider/ProviderRepositoryBindings.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/config/ConfigRepositoryBindings.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/config/ConfigTransactionBindings.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/bot/BotRepositoryBindings.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/persona/PersonaRepositoryBindings.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/profile/ProfileReferenceCheckerBindings.kt`
- `app-integration/src/main/java/com/elymbot/android/di/BackupDataPortAdapter.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/SearchRuntimeModule.kt`
- `app/src/main/java/com/elymbot/android/di/RuntimeContextDataPorts.kt`
- `core/runtime-context/src/main/java/com/elymbot/android/core/runtime/context/RuntimeContextResolver.kt`
- `core/runtime-context/src/main/java/com/elymbot/android/core/runtime/context/PromptAssembler.kt`
- `app/src/test/java/com/elymbot/android/architecture/RepositoryPortSourceContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/FeatureFirstBoundaryContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/FeatureImportBoundaryContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/StaticRepositoryUsageContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/StrictHiltOnlyContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/PostHiltRound1ContractTest.kt`
- `app/src/test/java/com/elymbot/android/ui/viewmodel/ProviderViewModelTest.kt`
- `app/src/test/java/com/elymbot/android/ui/viewmodel/ConfigViewModelTest.kt`
- `app/src/test/java/com/elymbot/android/ui/viewmodel/BotViewModelTest.kt`
- `app/src/test/java/com/elymbot/android/data/HiltViewModelDependenciesTransactionTest.kt`
- `app/src/test/java/com/elymbot/android/feature/provider/ProviderCatalogSearchCapabilityTest.kt`
- `app/src/test/java/com/elymbot/android/feature/config/data/FeatureConfigRepositorySelectedStateTest.kt`
- `app/src/test/java/com/elymbot/android/feature/bot/data/FeatureBotRepositorySelectedStateTest.kt`
- `app/src/test/java/com/elymbot/android/data/PersonaRepositoryTest.kt`

排除路径：

- `build/`
- `bin/`
- `.worktrees/`
- 生成物、IDE 缓存和二进制资源
- 旧文档正文自身

辅助证据：

- `AGENTS.md`
- `docs/archive/pre-uth-docs/docs-00-11/00_当前基线与迁移摘要.md`
- `docs/archive/pre-uth-docs/docs-00-11/03_Provider_Config_Bot_Persona.md`
- `docs/archive/pre-uth-docs/docs-00-11/10_测试入口_回归面_已知风险.md`

辅助证据不得覆盖当前代码事实。

## 模块职责

本模块当前覆盖：

- Provider catalog、provider profile、provider capability、provider repository port 与 Room-backed store。
- Config profile、运行时策略字段、selected config state、config repository port 与 Room-backed store。
- Bot profile、selected bot state、QQ 绑定、默认 provider/persona/config 绑定与 Room-backed store。
- Persona profile、persona enabled tools、persona repository port 与 Room-backed store。
- Provider runtime port：模型拉取、multimodal/native streaming/STT/TTS 探测、语音资产列表与绑定、TTS asset state、Sherpa readiness、speech synthesis。
- Config / Bot 删除事务：`Phase3DataTransactionService` 与 `RoomPhase3DataTransactionService`。
- Provider / Persona 删除引用保护：`ProviderReferenceChecker`、`PersonaReferenceChecker` 与 `ProfileReferenceCheckerBindings`。
- app-integration 层的 Hilt 绑定和 runtime/search/backup/context 聚合用法。

## 非职责

- App Chat 与 conversation 消息链路不在本模块完成，交给 `chat-and-conversation`。
- QQ / NapCat / OneBot runtime 不在本模块完成，交给 `qq-napcat-onebot`。
- Plugin 平台、tool source、host capability 不在本模块完成，交给 `plugin-platform`。
- Resource Center 完整 owner 不在本模块完成；这里只确认 Config 与 RuntimeContextResolver 通过 `ResourceCenterPort.compatibilitySnapshotForConfig(...)` 消费投影。
- Voiceasset 完整 owner 不在本模块完成；这里只确认 Provider runtime 经 `RuntimeAssetPort` 和 `TtsVoiceAssetPort` 使用语音资产能力。
- 不得把 `impl` 模块写成生产业务真源；当前四个 `impl` 模块主要是 `api` + `data` 聚合壳。

## 当前 Gradle 与模块边界

`settings.gradle.kts` 当前声明以下模块：

- `:feature:provider:api`
- `:feature:provider:data`
- `:feature:provider:impl`
- `:feature:provider:runtime`
- `:feature:provider:presentation`
- `:feature:config:api`
- `:feature:config:data`
- `:feature:config:impl`
- `:feature:config:presentation`
- `:feature:bot:api`
- `:feature:bot:data`
- `:feature:bot:impl`
- `:feature:bot:presentation`
- `:feature:persona:api`
- `:feature:persona:data`
- `:feature:persona:impl`
- `:feature:persona:presentation`

`build.gradle.kts` 当前把上述 source roots 纳入 `architectureMainSourceRoots`，并提供模块组任务：

- `moduleProvider*`
- `moduleConfig*`
- `moduleBot*`
- `modulePersona*`

本轮只读取这些入口，未运行 Gradle 任务。

依赖事实：

- `:feature:provider:api` 依赖 `:feature:voiceasset:api`，并 implementation 依赖 chat/conversation API。
- `:feature:provider:data` 依赖 `:core:common`、`:core:db`、`:core:logging`、`:core:runtime-search` 与 provider API。
- `:feature:provider:runtime` 启用 Hilt，依赖 `:core:runtime-audio`、`:core:runtime-llm`、`:feature:provider:api`、`:feature:voiceasset:api` 与 conversation API。
- `:feature:config:data` 依赖 `:core:db`、`:feature:config:api` 与 `:feature:resource:api`。
- `:feature:bot:data` 依赖 `:feature:bot:api` 与 `:feature:config:api`。
- `:feature:persona:data` 依赖 persona API 与基础 core 依赖。
- 四个 presentation 模块均启用 Compose + Hilt；`ProviderViewModel`、`ConfigViewModel`、`BotViewModel`、`PersonaViewModel` 仍声明在 `com.elymbot.android.ui.viewmodel` package，不能只按物理路径推断 package。

## Provider

Provider 当前主真源：

- domain model：`feature/provider/api/src/main/java/com/elymbot/android/feature/provider/domain/model/ProviderProfile.kt`
- repository port：`feature/provider/api/src/main/java/com/elymbot/android/feature/provider/domain/ProviderRepositoryPort.kt`
- data store：`feature/provider/data/src/main/java/com/elymbot/android/feature/provider/data/FeatureProviderRepository.kt`
- port adapter：`feature/provider/data/src/main/java/com/elymbot/android/feature/provider/data/FeatureProviderRepositoryPortAdapter.kt`
- Hilt binding：`app-integration/src/main/java/com/elymbot/android/app/integration/provider/ProviderRepositoryBindings.kt`

当前 Provider 事实：

- `ProviderProfile` 包含 `baseUrl`、`model`、`apiKey`、`providerType`、`capabilities`、启用状态、multimodal/native streaming/STT/TTS probe 状态与 `ttsVoiceOptions`。
- `FeatureProviderRepositoryStore` 使用 `ProviderAggregateDao`，启动时从 Room 观察 provider aggregates；Room 为空时从 legacy SharedPreferences 导入或 seed 默认 provider。
- `FeatureProviderRepositoryPortAdapter` 是当前 production semantic adapter；旧 `LegacyProviderRepositoryAdapter` 不应写成当前生产入口。
- `ProviderRepositorySearchProvider` 从 `ProviderRepositoryPort.snapshotProfiles()` 中筛选 enabled 且带 `ProviderCapability.SEARCH` 的 provider，并映射到 Tavily / Brave / BoCha / Baidu AI search provider。
- `ProviderCatalog.kt` 当前把 `TAVILY_SEARCH`、`BRAVE_SEARCH`、`BOCHA_SEARCH`、`BAIDU_AI_SEARCH` 的默认 capability 映射为 `SEARCH`。

删除边界：

- Provider 删除通过 `FeatureProviderRepositoryStore.delete(id)`。
- 当前代码调用 `ProviderReferenceChecker.isInUse(id)`；生产 checker 由 `ProfileReferenceCheckerBindings` 提供。
- checker 会检查 config 的默认 chat/vision/STT/TTS provider 与 bot 的 `defaultProviderId`。
- 删除被引用 provider 时抛出 `ProviderInUseException`。

## Provider runtime

当前 Provider runtime 主真源：

- contract：`feature/provider/api/src/main/java/com/elymbot/android/feature/provider/api/runtime/ProviderRuntimePort.kt`
- implementation：`feature/provider/runtime/src/main/java/com/elymbot/android/feature/provider/runtime/DefaultProviderRuntimePort.kt`
- Hilt binding：`feature/provider/runtime/src/main/java/com/elymbot/android/feature/provider/runtime/ProviderRuntimeModule.kt`

`ProviderRuntimePort` 当前暴露：

- `providers`
- `voiceAssets`
- `fetchModels(...)`
- multimodal/native streaming 规则检测和 probe
- STT/TTS probe
- voice choices
- reference audio import / clear / delete
- voice binding save / rename / delete
- TTS asset state
- Sherpa framework/STT readiness
- speech synthesis

`DefaultProviderRuntimePort` 当前注入：

- `ProviderRepositoryPort`
- `LlmProviderProbePort`
- `AudioRuntimePort`
- `RuntimeAssetPort`
- `TtsVoiceAssetPort`

这说明 Provider runtime 是跨 provider、core runtime-llm/audio 与 voiceasset API 的 facade。它不是数据真源；数据真源仍是 provider data store、voiceasset ports 与对应底层 owner。

## Config

Config 当前主真源：

- domain model：`feature/config/api/src/main/java/com/elymbot/android/feature/config/domain/model/ConfigProfile.kt`
- repository port：`feature/config/api/src/main/java/com/elymbot/android/feature/config/domain/ConfigRepositoryPort.kt`
- data store：`feature/config/data/src/main/java/com/elymbot/android/feature/config/data/FeatureConfigRepository.kt`
- port adapter：`feature/config/data/src/main/java/com/elymbot/android/feature/config/data/FeatureConfigRepositoryPortAdapter.kt`
- Hilt binding：`app-integration/src/main/java/com/elymbot/android/app/integration/config/ConfigRepositoryBindings.kt`

当前 Config 事实：

- `ConfigProfile` 仍是运行时策略入口，包含默认 provider、STT/TTS、streaming、web search、proactive、scheduled task 上下文、QQ 规则、插件命令管理员限制、context policy、legacy `mcpServers` 与 `skills`。
- `FeatureConfigRepositoryStore` 使用 `ConfigAggregateDao` 与 `AppPreferenceDao`。
- selected config 真源是 `AppPreferenceDao.observeValue("selected_config_profile_id")`，`select(id)` 只持久化 selected ID；最终状态由 DAO + preference flow 回流。
- `restoreProfiles(...)` 会持久化 restored profiles 和 resolved selected ID，selected state 仍回到 AppPreference。
- `ConfigViewModel.delete(profileId)` 当前委托 `Phase3DataTransactionService.deleteConfigProfile(profileId)`，不是在 ViewModel 中手动串联删除和 bot binding 回退。
- `ConfigViewModel.save(profile)` 当前会等待 saved profile 在 flow 中可见，再同步 selected bot 的 config/default provider 绑定。
- `ConfigViewModel` 当前仍直接注入 `LlmProviderProbePort` 用于 speech synthesis；不要把它误写成所有 provider runtime 调用都只经 `ProviderRuntimePort`。

Runtime context 关系：

- `ProductionRuntimeContextDataPort` 注入 `ConfigRepositoryPort`、`ProviderRepositoryPort`、`PersonaRepositoryPort`、`ConversationRepositoryPort` 和 `ResourceCenterPort`。
- `RuntimeContextResolver` 通过 `resolveConfig(...)`、`listProviders()`、`findEnabledPersona(...)`、`session(...)`、`compatibilitySnapshotForConfig(...)` 组装 runtime context。
- `RuntimeSkillProjectionResolver.fromResourceCenterSnapshot(...)` 负责从 Resource Center compatibility snapshot 生成 prompt skills、tool skills 与 MCP server projection。
- `PromptAssembler` 会按 `webSearchEnabled` 和 scheduled-task trigger 注入对应 guidance；scheduled task 上下文只在 `includeScheduledTaskConversationContext` 打开且触发源为 `SCHEDULED_TASK` 时读取。

## Bot

Bot 当前主真源：

- domain model：`feature/bot/api/src/main/java/com/elymbot/android/feature/bot/domain/model/BotProfile.kt`
- repository port：`feature/bot/api/src/main/java/com/elymbot/android/feature/bot/domain/BotRepositoryPort.kt`
- data store：`feature/bot/data/src/main/java/com/elymbot/android/feature/bot/data/FeatureBotRepository.kt`
- port adapter：`feature/bot/data/src/main/java/com/elymbot/android/feature/bot/data/FeatureBotRepositoryPortAdapter.kt`
- Hilt binding：`app-integration/src/main/java/com/elymbot/android/app/integration/bot/BotRepositoryBindings.kt`

当前 Bot 事实：

- `BotProfile` 包含 platform、display name、QQ UIN 绑定、trigger words、auto reply、conversation persistence、bridge endpoint、default provider/persona/config 与 status。
- `FeatureBotRepositoryStore` 使用 `BotAggregateDao` 与 `AppPreferenceDao`，并通过 `Provider<ConfigRepositoryPort>` 解析 config 绑定。
- selected bot 真源是 `AppPreferenceDao.observeValue("selected_bot_id")`；`select(botId)` 只持久化 selected ID，最终状态由 DAO + preference flow 回流。
- `save(profile)` 和 `create(...)` 当前会保存 bot profile 并持久化 selected bot；`select(...)` 不应被写成直接改 UI 内存状态。
- `BotViewModel.deleteSelected(...)` 当前委托 `Phase3DataTransactionService.deleteBotProfile(botId)`。

## Persona

Persona 当前主真源：

- domain model：`feature/persona/api/src/main/java/com/elymbot/android/feature/persona/domain/model/PersonaProfile.kt`
- tool snapshot：`feature/persona/api/src/main/java/com/elymbot/android/feature/persona/domain/model/PersonaToolEnablementSnapshot.kt`
- repository port：`feature/persona/api/src/main/java/com/elymbot/android/feature/persona/domain/PersonaRepositoryPort.kt`
- data store：`feature/persona/data/src/main/java/com/elymbot/android/feature/persona/data/FeaturePersonaRepository.kt`
- port adapter：`feature/persona/data/src/main/java/com/elymbot/android/feature/persona/data/FeaturePersonaRepositoryPortAdapter.kt`
- Hilt binding：`app-integration/src/main/java/com/elymbot/android/app/integration/persona/PersonaRepositoryBindings.kt`

当前 Persona 事实：

- `PersonaProfile` 包含 system prompt、enabled tools、default provider、max context messages 与 enabled 状态。
- `FeaturePersonaRepositoryStore` 使用 `PersonaAggregateDao`；Room 为空时从 legacy SharedPreferences 导入或 seed 默认 persona。
- `FeaturePersonaRepositoryPortAdapter.snapshotToolEnablement(...)` 当前返回 `PersonaToolEnablementSnapshot`。
- Persona 删除先过 `ProfileDeletionGuard.requireCanDelete(...)`，再由 `PersonaReferenceChecker.isInUse(id)` 检查 bot 的 `defaultPersonaId`，被引用时抛出 `PersonaInUseException`。

## Config / Bot 删除事务

当前事务入口：

- `feature/config/api/src/main/java/com/elymbot/android/feature/config/domain/Phase3DataTransactionService.kt`
- `feature/config/data/src/main/java/com/elymbot/android/feature/config/data/RoomPhase3DataTransactionService.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/config/ConfigTransactionBindings.kt`

当前事务事实：

- `deleteConfigProfile(profileId)` 在 Room transaction 中列出 configs、阻止删除最后一个 config、计算 fallback selected config、把引用已删除 config 的 bot rebinding 到 fallback config，然后写回 config preference 与 bot 表。
- `deleteBotProfile(botId)` 在 Room transaction 中阻止删除最后一个 bot、计算 fallback selected bot、删除该 bot 相关 conversation sessions；如果没有剩余会话则创建默认 app chat session。
- 这些顺序属于 data/transaction service，不应回退到 ViewModel 或 Screen 手动串联。
- `PostHiltRound1ContractTest` 明确禁止恢复 `Phase3DataTransactionServiceRegistry` 这类 static registry callback。

## Backup / runtime / search 聚合关系

- `HiltAppBackupDataPort` 直接注入四个 `Feature*RepositoryStore` 生成 backup snapshot 与 restore profile，外部 selected state 来自 bot/config repository store。
- `HiltConversationBackupDataPort.selectedBotId()` 通过 injected `BotRepositoryPort` 获取 selected bot。
- `SearchRuntimeModule` 把 `ProviderRepositorySearchProvider` 和 `LocalMetaSearchFallbackProvider` 组成 `UnifiedSearchPort`。
- `StaticRepositoryUsageContractTest` 当前 allowlist 文件为空，新增生产 static repository facade 使用应视为回归。
- `FeatureImportBoundaryContractTest` 当前 feature import allowlist 文件为空，新增未登记 forbidden feature import 应视为回归。

## 合同与回归入口

本轮只读取合同和单元测试，不运行测试。相关入口包括：

- `RepositoryPortSourceContractTest`
- `FeatureFirstBoundaryContractTest`
- `FeatureImportBoundaryContractTest`
- `StaticRepositoryUsageContractTest`
- `StrictHiltOnlyContractTest`
- `PostHiltRound1ContractTest`
- `ProviderViewModelTest`
- `ConfigViewModelTest`
- `BotViewModelTest`
- `PersonaRepositoryTest`
- `ProviderCatalogSearchCapabilityTest`
- `FeatureConfigRepositorySelectedStateTest`
- `FeatureBotRepositorySelectedStateTest`
- `HiltViewModelDependenciesTransactionTest`
- `RuntimeContextResolverProviderTest`
- `Task8Phase1And2VerificationTest`
- `Task8Phase3VerificationTest`
- `AppBackupRepositoryCompatibilityTest`

对应回归命令仍由后续验证场景执行，文档场景不得声称已运行：

```powershell
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleProviderCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleConfigCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleBotCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat modulePersonaCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Provider*" --tests "*Config*" --tests "*Bot*" --tests "*Persona*" --console=plain --no-daemon --stacktrace
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

## 当前风险与阻塞项

- `ConfigViewModel` 仍直接注入 `LlmProviderProbePort` 处理 speech synthesis；如果后续把文档写成所有 provider runtime UI 行为都已完全经 `ProviderRuntimePort` 收口，就是过度声明。
- `RoomPhase3DataTransactionService.kt` 中默认 app chat session title 的中文字符串在源码显示上需要后续代码场景核对；本场景不修改代码，不把它当作已修复问题。
- Provider / Persona 删除保护依赖当前 Hilt 注入的 reference checker；绕过 checker 或恢复静态 registry 都是回归。
- Bot / Config selected state 的真源是 AppPreference flow；把 `select(...)` 写成同步改内存状态会与当前 selected-state tests 冲突。
- 四个 presentation ViewModel 的物理路径已经在 feature 下，但 package 仍是 `com.elymbot.android.ui.viewmodel`；不要只按路径推断 import/package。
- 本轮未运行 Gradle 或测试，因此不能把本模块状态扩展为构建通过或测试通过。

## 旧文档判断

- `docs/archive/pre-uth-docs/docs-00-11/03_Provider_Config_Bot_Persona.md`：保留为辅助证据；其中 Provider runtime、selected state、Phase3 transaction、reference guard 的方向仍有代码证据，但路径写法如 `feature/config/domain/*` 需要按当前 `feature/config/api/src/main/java/**` 口径修正。
- `docs/archive/pre-uth-docs/docs-00-11/10_测试入口_回归面_已知风险.md`：保留为验证辅助入口；本模块相关测试入口需以后续实际验证为准。
- `docs/archive/pre-uth-docs/docs-00-11/00_当前基线与迁移摘要.md`：只作为跨模块迁移辅助证据，不能替代本模块代码事实。
- 旧 `docs/00` 到 `docs/11` 已在收尾阶段归档至 `docs/archive/pre-uth-docs/docs-00-11/`，仅作为历史辅助证据。

## 下一步

`provider-config-bot-persona` 模块上下文已完成。按新版 UTH module-governance 规则，下一步继续治理第 7 模块 `chat-and-conversation`；本文件只保留第 6 模块当前事实。

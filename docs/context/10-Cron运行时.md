# Cron 运行时模块上下文

更新时间：2026-05-22 18:30 +08:00

## 状态

- 场景：`uth-docs`
- 模式：`module-governance`
- 模块序号：10
- 模块：`cron-runtime`
- 模块状态：已按当前代码事实重新确认
- 完成等级：`full-project-docs-complete`
- 下一模块：`resource-settings-backup`
- 最近影响本模块的 scoped-sync：`66eee69..4fedf19`；2026-05-25 全仓同步未发现本模块新增代码事实。
- 本轮 Git 写入：无
- 本轮 Gradle / 测试命令：未运行；本文档场景只做文档治理

## 代码事实来源

本模块重新核对了以下当前源码、构建、Hilt wiring、启动入口、UI 入口和测试入口：

- `settings.gradle.kts`
- `build.gradle.kts`
- `app/build.gradle.kts`
- `app-integration/build.gradle.kts`
- `feature/cron/api/build.gradle.kts`
- `feature/cron/data/build.gradle.kts`
- `feature/cron/impl/build.gradle.kts`
- `feature/cron/presentation/build.gradle.kts`
- `feature/cron/runtime/build.gradle.kts`
- `feature/cron/api/src/main/java/**`
- `feature/cron/data/src/main/java/**`
- `feature/cron/data/src/test/java/**`
- `feature/cron/impl/src/test/java/**`
- `feature/cron/presentation/src/main/java/**`
- `feature/cron/runtime/src/main/java/**`
- `feature/cron/runtime/src/test/java/**`
- `app-integration/src/main/java/com/elymbot/android/app/integration/cron/CronRepositoryBindings.kt`
- `app-integration/src/main/java/com/elymbot/android/app/integration/cron/CronRuntimeReconciliationBinding.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/runtime/CronRuntimeServicesModule.kt`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/runtime/DefaultScheduledMessageDeliveryPortAdapter.kt`
- `app/src/main/java/com/elymbot/android/ElymBotApplication.kt`
- `app/src/main/java/com/elymbot/android/di/startup/RuntimeLaunchStartupChain.kt`
- `app/src/main/java/com/elymbot/android/ui/navigation/AppDestinations.kt`
- `app/src/main/java/com/elymbot/android/ui/navigation/ElymBotAppScaffoldParts.kt`
- `feature/settings/presentation/src/main/java/com/elymbot/android/ui/settings/MeScreen.kt`
- `feature/settings/presentation/src/main/java/com/elymbot/android/ui/settings/MeEntryComponents.kt`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/ActiveCapabilityRuntimeFacade.kt`
- `core/runtime-context/src/main/java/com/elymbot/android/core/runtime/context/PromptAssembler.kt`
- `app/src/test/java/com/elymbot/android/architecture/ModuleDependencyGraphContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/HiltExitContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/RepositoryPortSourceContractTest.kt`
- `app/src/test/java/com/elymbot/android/architecture/StaticRepositoryUsageContractTest.kt`
- `app/src/test/java/com/elymbot/android/feature/cron/**`
- `app/src/test/java/com/elymbot/android/runtime/cron/CronJobRunCoordinatorTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/toolsource/ActiveCapabilityRuntimeFacadeTest.kt`
- `app/src/test/java/com/elymbot/android/runtime/plugin/toolsource/ActiveCapabilityToolSourceProviderTest.kt`

源码数量：

- `feature/cron/api/src/main/java`：11 个 Kotlin/Java 文件
- `feature/cron/data/src/main/java`：2 个 Kotlin/Java 文件
- `feature/cron/data/src/test/java`：1 个 Kotlin/Java 文件
- `feature/cron/impl/src/main/java`：0 个 Kotlin/Java 生产文件
- `feature/cron/impl/src/test/java`：1 个 Kotlin/Java 文件
- `feature/cron/presentation/src/main/java`：6 个 Kotlin/Java 文件
- `feature/cron/runtime/src/main/java`：18 个 Kotlin/Java 文件
- `feature/cron/runtime/src/test/java`：4 个 Kotlin/Java 文件

排除路径：

- `build/`
- `bin/`
- `architecture-tests/bin/`
- `.worktrees/`
- 生成物、IDE 缓存和二进制资产正文
- 旧文档正文自身

辅助证据：

- `AGENTS.md`
- `docs/archive/pre-uth-docs/docs-00-11/09_设置_日志_运行时清理_备份入口.md`
- `docs/archive/pre-uth-docs/docs-00-11/10_测试入口_回归面_已知风险.md`
- `docs/context/07-聊天与会话.md`
- `docs/context/08-QQ_NapCat_OneBot.md`
- `docs/context/09-插件平台.md`

辅助证据不得覆盖当前代码事实。

## 模块职责

本模块当前覆盖：

- `:feature:cron:api`：Cron domain model、repository / scheduler / run-now ports、`CronJobUseCases`、cron expression parser、active capability target context、prompt strings 和自然语言时间解析。
- `:feature:cron:data`：Room-backed `FeatureCronJobRepositoryStore` 与 `FeatureCronJobRepositoryPortAdapter`，负责 cron jobs 与 execution records 的持久化映射。
- `:feature:cron:presentation`：设置侧 Cron Jobs 页面、编辑 draft、列表分页、run history、pause/resume/delete/update/create 入口和 presentation controller。
- `:feature:cron:runtime`：WorkManager 调度、Hilt worker、启动 reconciliation、执行 coordinator、scheduled task LLM pipeline、delivery port、intent fallback guard 和 resource-backed prompt strings。
- `app-integration` wiring：通过 Hilt 提供 cron repository port、runtime reconciliation port、runtime services、delivery port、scheduler port、run-now port 和 active capability task port。
- `app` 入口：Application 提供 `HiltWorkerFactory` 给 WorkManager；`RuntimeLaunchStartupChain` 启动时触发 cron reconciliation；navigation 接入 `cron-jobs`。

## 非职责

- Resource Center、settings hub、logs、runtime cleanup 和 backup 覆盖面不在本模块完成态；这些留给第 11 模块 `resource-settings-backup`。
- Plugin platform 的 ToolSource registry、host capability 和 LLM orchestration 已在第 9 模块确认；本模块只确认 active capability 如何使用 Cron ports。
- App Chat / QQ 的消息入口已在第 7、8 模块确认；本模块只确认 scheduled delivery port 如何投递到 App Chat 或 QQ。
- Provider、Config、Bot、Persona 的 profile / selected state 已在第 6 模块确认；本模块只记录 scheduled task target context 对这些 port 的依赖。
- STT/TTS、voiceasset 和 audio runtime 仍待后续 `voiceasset-audio` 模块确认。

## 当前 Gradle 与模块边界

`settings.gradle.kts` 当前声明：

- `:feature:cron:api`
- `:feature:cron:data`
- `:feature:cron:impl`
- `:feature:cron:presentation`
- `:feature:cron:runtime`

依赖事实：

- `:feature:cron:api` 依赖 Kotlin coroutines 与 `javax.inject`，不依赖 app、data、runtime 或 presentation。
- `:feature:cron:data` 依赖 core common/db/logging 与 cron API，提供 Room-backed store 和 port adapter。
- `:feature:cron:presentation` 启用 Compose + Hilt + KSP，依赖 core common/logging/runtime/runtime-context/ui，以及 bot/config/conversation/cron/provider API。
- `:feature:cron:runtime` 启用 Hilt + Hilt WorkManager，依赖 core common/logging/runtime/runtime-context/runtime-llm、bot/conversation/cron/plugin API、plugin impl、WorkManager 和 Hilt worker。
- `:feature:cron:impl` 当前没有生产 Kotlin/Java 文件，是聚合壳，`api` 暴露 cron API/data/presentation/runtime，并复用 app resources；不要把它写成生产 owner。
- `app-integration` 以 `api(project(":feature:cron:api"))` 暴露 cron API，以 `implementation` 接 `:feature:cron:data`、`:feature:cron:impl`、`:feature:cron:runtime`。
- `app/build.gradle.kts` 生产只直接 `implementation(project(":feature:cron:presentation"))`；cron data/runtime/impl 通过 `app-integration` 和 Hilt 进入生产图。
- root `build.gradle.kts` 当前有 `moduleCronBuild` / `moduleCronCheck` 模块组，覆盖 cron API/data/impl/runtime/presentation。

## Hilt 与启动主线

当前生产主线：

1. `ElymBotApplication` 是 `@HiltAndroidApp`，实现 `Configuration.Provider`，通过注入的 `HiltWorkerFactory` 配置 WorkManager。
2. `AppBootstrapper -> AppStartupRunner -> RuntimeLaunchStartupChain` 是启动链入口。
3. `RuntimeLaunchStartupChain.run()` 调 `cronRuntimeReconciliationPort.reconcileAsync(appScope)`。
4. `CronRuntimeReconciliationModule` 将 `HiltCronRuntimeReconciliationPort` 绑定到 `CronRuntimeReconciliationPort`。
5. `HiltCronRuntimeReconciliationPort` 委托 `CronJobReconciler.reconcileAsync(scope)`。
6. `CronJobReconciler` 读取 `CronJobRepositoryPort.listEnabled()`，先 `scheduler.cancelAll()`，再对 enabled jobs 重新 `schedule(...)`。
7. `CronRuntimeServicesModule` 提供 `ScheduledTaskRuntimeDependencies`、`ScheduledTaskExecutor`、`CronSchedulerPort`、`ActiveCapabilityTaskPort`、`CronJobRunNowPort`、`CronRescheduler` 和 `CronJobRunCoordinator`。
8. `PluginV2ScheduledTaskDispatchPortAdapter` 当前由 app-integration 提供，把 cron scheduled task 回调投递到 Plugin V2 scheduled handler。

不要恢复 `ScheduledTaskRuntimeExecutor.configureRuntimeDependenciesProvider { ... }`、手写 WorkManager factory、static scheduler facade、AppBootstrapper 手动安装 runtime dependencies 或旧 `LegacyCron*Adapter`。

## Data / persistence / execution records

当前数据真源：

- `FeatureCronJobRepositoryStore` 持有 `CronJobDao`、`CronJobExecutionRecordDao` 和 `RuntimeLogger`。
- `jobs` 是从 `dao.observeAll()` 收集到的 `StateFlow<List<CronJob>>`。
- `create/update/delete/getByJobId/listAll/listEnabled/updateStatus` 走 Room DAO。
- `recordExecutionStarted/updateExecutionRecord/listRecentExecutionRecords/latestExecutionRecord` 走 `CronJobExecutionRecordDao`。
- mapper 会把旧 `payloadJson.target` 与 `session` 中的 target 字段回填到扁平字段：`platform`、`conversationId`、`botId`、`configProfileId`、`personaId`、`providerId`、`origin`。

当前 backup 仍不覆盖 Cron jobs 和 Cron execution records；这属于第 11 模块的 backup 覆盖面事实，不应在本模块写成已解决。

## Scheduling / worker / run-now

当前执行链：

- `FeatureCronSchedulerPortAdapter` 实现 `CronSchedulerPort`，通过 `WorkManagerCronJobScheduler` schedule / cancel / cancelAll。
- `WorkManagerCronRescheduler` 实现 `CronRescheduler`，用于 recurring job 执行成功后的重排。
- `WorkManagerCronJobScheduler` 以 `OneTimeWorkRequestBuilder<CronJobWorker>()` 创建任务，用 unique work name `cron_job_$jobId` 和 `ExistingWorkPolicy.REPLACE` 调度。
- `CronJobWorker` 是 `@HiltWorker`，读取 `jobId` input data 后委托 `CronJobRunCoordinator.runDueJob(...)`。
- `CronJobRunCoordinator` 负责 execution record、状态更新、missing context 拒绝、delivery summary、run-once 删除、recurring next fire time 重算、retry/failure 分类和连续失败熔断。
- `CoordinatorCronJobRunNowPort` 通过同一个 coordinator 执行 `runNow(jobId)`，trigger 为 `run_now`。

核心语义：

- disabled job 执行时返回 `Skipped`。
- run-once 成功后删除 job。
- recurring 成功后重算 `nextRunTime` 并重新 schedule。
- retryable failure 返回 `Retry`；连续失败达到阈值后禁用 job 并标记 `unhealthy`。
- delivery count 为 0 会被视为失败，不能静默完成。

## Scheduled task runtime / delivery

`ScheduledTaskRuntimeExecutor` 当前把 cron job 转成一次 `IngressTrigger.SCHEDULED_TASK` 的 runtime turn：

- 使用 `RuntimeContextResolverPort.resolve(...)` 解析 runtime context。
- 用 `BotRepositoryPort.snapshotProfiles()` 找到启用 auto reply 的 bot。
- 校验 bot 的 `configProfileId` 与 scheduled task target 一致。
- 构造 role 为 `scheduled_task`、id 为 `cron:$jobId` 的 `ConversationMessage`。
- 通过 `RuntimeLlmOrchestratorPort.dispatchLlm(...)` 进入共享 LLM pipeline。
- 使用 `ScheduledTaskLlmCallbacksFactory`、`ScheduledTaskProviderInvocationService`、`PluginHostCapabilityGateway` 和 `ScheduledMessageDeliveryPort` 完成 provider invocation 与最终投递。
- 如果 job 目标为插件 scheduled handler，Cron 通过 `PluginV2ScheduledTaskDispatchPortAdapter` / `PluginV2ScheduledHandlers` 唤醒对应 V2 handler；该路径已属于 plugin runtime 能力，不应写成 Cron 自己执行插件脚本。

`DefaultScheduledMessageDeliveryPort` 当前支持：

- App Chat：写入 `ConversationRepositoryPort.appendMessage(...)`。
- QQ OneBot：调用 `QqScheduledMessageSender.sendScheduledMessage(...)`，成功后同步写入 App Chat conversation 作为本地记录。
- 其他 platform：返回 `unsupported_platform`。

## Active capability 与提醒 fallback

`ActiveCapabilityRuntimeFacade` 当前通过注入的 `CronJobRepositoryPort`、`CronSchedulerPort`、`CronJobRunNowPort`、`ActiveCapabilityNaturalLanguageParser` 和 `ActiveCapabilityPromptStrings` 管理 future task：

- `createFutureTask(...)`
- `deleteFutureTask(...)`
- `listFutureTasks()`
- `updateFutureTask(...)`
- `pauseFutureTask(...)`
- `resumeFutureTask(...)`
- `listFutureTaskRuns(...)`
- `runFutureTaskNow(...)`

缺失 target context 会返回 structured error `missing_context`，不会静默创建。`runFutureTaskNow(...)` 不直接 new coordinator，而是通过 `CronJobRunNowPort`。

`ScheduledTaskIntentGuard` 与 `ScheduledTaskIntentFallbackResponder` 是 host fallback：当模型有提醒意图但没有调用 `create_future_task`，且 `proactiveEnabled` 为 true 时，host 尝试通过 `ActiveCapabilityTaskPort` 创建任务，再生成 follow-up reply。对于 `IngressTrigger.SCHEDULED_TASK`，`ActiveCapabilityToolSourceProvider` 会隐藏 future task 管理工具，避免定时任务自我递归。

## Presentation / UI 入口

当前 UI 主线：

- `CronJobsViewModel` 是 `@HiltViewModel`，注入 `CronJobRepositoryPort`、`CronJobsPresentationController`、`BotRepositoryPort`、`ConversationRepositoryPort`、`ConfigRepositoryPort`、`ProviderRepositoryPort` 和 `RuntimeLogger`。
- `CronJobsPresentationController` 只通过 `CronJobUseCases` 和 `ActiveCapabilityTaskPort` 操作 cron job，不在 UI 内直接接 scheduler / coordinator。
- `CronJobsScreen` 支持 create/edit、pause/resume、delete、run history dialog、pagination 和 target context draft。
- `MeScreen` 暴露 Cron Jobs 入口，`AppDestinations.CronJobs` route 为 `cron-jobs`，`ElymBotAppScaffoldParts` 通过 `CronJobsScreen(onBack = ...)` 接入。

需要注意路径与 package 不完全一致：Cron UI 物理位于 `feature/cron/presentation`，但 package 仍是 `com.elymbot.android.ui.settings`。

## 合同与回归入口

本轮只读取合同和单元测试，不运行测试。相关入口包括：

- `ModuleDependencyGraphContractTest`
- `HiltExitContractTest`
- `RepositoryPortSourceContractTest`
- `StaticRepositoryUsageContractTest`
- `CronCompatContractTest`
- `CronResourceContractTest`
- `CronImplModuleContractTest`
- `FeatureCronJobRepositoryStoreTest`
- `CronJobUseCasesTest`
- `CronJobRunCoordinatorTest`
- `CronJobReconcilerTest`
- `ScheduledTaskRuntimeExecutorTest`
- `ScheduledMessageDeliveryPortTest`
- `ScheduledMessageDeliveryPortContractTest`
- `PluginV2ScheduledDispatchTest`
- `PluginV2ScheduledHandlerLifecycleTest`
- `PluginV2ScheduledHandlerRegistryTest`
- `ScheduledTaskIntentGuardTest`
- `ScheduledTaskIntentFallbackResponderTest`
- `CronJobsPresentationControllerTest`
- `CronJobEditorDraftTest`
- `CronJobsPresentationTest`
- `CronJobsViewModelRequestTest`
- `ActiveCapabilityRuntimeFacadeTest`
- `ActiveCapabilityToolSourceProviderTest`
- `LlmSourceContractTest`

对应回归命令仍由后续验证场景执行，文档场景不得声称已运行：

```powershell
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleCronCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:cron:data:testDebugUnitTest --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:cron:runtime:testDebugUnitTest --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Cron*" --tests "*ScheduledTask*" --tests "*ActiveCapability*" --console=plain --no-daemon --stacktrace
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

## 当前风险与阻塞项

- 本轮未运行 Gradle 或测试，因此不能把本模块状态扩展为构建通过或测试通过。
- `feature/cron/impl` 当前无生产源码，不应写成 cron 生产 owner。
- `WorkManagerCronJobScheduler` 当前通过 one-time work 表达 recurring，重排依赖 `CronJobRunCoordinator.completeJob(...)`，修改 recurring 时要同时看 scheduler 和 coordinator。
- `ScheduledTaskRuntimeExecutor` 对 bot auto reply、config profile、provider target 都有硬校验，UI 或 ToolSource 新建任务时不能省略 target context。
- `DefaultScheduledMessageDeliveryPort` 的 QQ 成功路径会同时落 App Chat 本地记录；修改投递语义时需同时确认 QQ 和 conversation 边界。
- host fallback 只在 `proactiveEnabled` 且存在提醒意图时尝试创建；不要把它写成所有模型漏调工具都会自动创建任务。
- scheduled task trigger 下 future task 工具应隐藏，防止计划任务自我递归。
- backup 当前仍不覆盖 cron jobs / execution records；不要在后续设置或备份文档中写成已覆盖。

## 旧文档判断

- `docs/archive/pre-uth-docs/docs-00-11/09_设置_日志_运行时清理_备份入口.md`：保留为辅助证据。其 Cron UI 迁移、Hilt ViewModel、Hilt worker、WorkManager scheduler、run history 和 backup 未覆盖 Cron 的方向仍被当前代码支持；但设置、资源、日志、运行时清理和 backup 细节要留到第 11 模块确认。
- `docs/archive/pre-uth-docs/docs-00-11/10_测试入口_回归面_已知风险.md`：保留为验证辅助入口。Cron / active capability 测试清单大体仍可参考，但实际验证状态以后续验证场景为准。
- `docs/context/09-插件平台.md`：仅作 active capability / ToolSource 上游交叉证据，不作为 cron runtime 当前入口。
- 旧 `docs/00` 到 `docs/11` 已在收尾阶段归档至 `docs/archive/pre-uth-docs/docs-00-11/`，仅作为历史辅助证据。

## 下一步

`cron-runtime` 模块上下文已完成。按新版 UTH module-governance 规则，下一步继续治理第 11 模块 `resource-settings-backup`。

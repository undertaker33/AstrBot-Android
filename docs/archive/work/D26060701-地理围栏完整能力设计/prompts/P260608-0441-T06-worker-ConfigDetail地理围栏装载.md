# P260608-0441 T06 Worker Prompt：ConfigDetail 地理围栏装载

更新时间：2026-06-08 04:41 +08:00

## 角色与模式

- Scene：`uth-dev`
- Mode：`todo-implementation`
- Method：`uth-sp-subagent-driven-development`
- 你是本 Todo 的实现 worker。
- 当前主窗口只做总控、验收和文档回填；你负责实际代码修改。
- 你不孤立工作：工作区已有 T01、T02、T03、T04、T05 以及其他窗口改动。不要回滚、重排或清理无关改动；必须基于当前工作区适配。
- 禁止 Git 写入：不要 `commit`、`push`、`reset`、`checkout`、`stash`、切分支或改 worktree。

## 必读材料

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/current-state.md`
4. `docs/context/01-验证构建治理.md`
5. `docs/context/02-应用壳层与集成.md`
6. `docs/context/06-Provider配置Bot与Persona.md`
7. `docs/context/11-资源设置备份.md`
8. `docs/work/D26060701-地理围栏完整能力设计/00-D26060701-design.md`
9. `docs/work/D26060701-地理围栏完整能力设计/60-D26060701-T06-todo-ConfigDetail地理围栏装载.md`
10. `docs/work/D26060701-地理围栏完整能力设计/11-D26060701-T01-feedback-模块与数据真源基线.md`
11. `docs/work/D26060701-地理围栏完整能力设计/41-D26060701-T04-feedback-我的页地理围栏配置UI.md`
12. `docs/work/D26060701-地理围栏完整能力设计/51-D26060701-T05-feedback-地图选择器与当前位置权限UX.md`

本 Todo 不需要查询新的库/SDK 文档；只有当你主动新增未在仓库使用过的库、框架、SDK、API 或 CLI 时，才按 `AGENTS.md` 规则使用 Context7。

## 当前代码事实

### ConfigDetail 插入点

- Config detail 页面主文件：
  - `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/ConfigDetailScreen.kt`
- 平台设置 section 定义：
  - `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/detail/ConfigNavModels.kt`
  - `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/detail/ConfigDrawerTree.kt`
  - `feature/config/presentation/src/main/java/com/elymbot/android/feature/config/presentation/detail/sections/PlatformSettingsSection.kt`
- 当前平台分组 `config_nav_group_platform` 包含：
  - `Admin`
  - `Session`
  - `Wake`
  - `Reply`
  - `Whitelist`
  - `IgnorePermission`
  - `RateLimit`
  - `Keyword`
- `ConfigDetailScreen` 当前使用 `ConfigViewModel` 保存 `ConfigProfile` 与 Resource Center projections。
- Resource Center 的管理弹窗在 `ConfigDetailScreen.kt` 中，函数包括：
  - `ResourceSelectionSummary`
  - `ResourceSelectionDialog`
  - `ResourceSelectionState`
  - `buildProjectionUpdates`
- T06 不应复用 Resource Center projection 承载 geofence binding。

### Geofence 真源与 runtime port

- geofence 唯一真源是 `feature/geofence`：
  - `feature/geofence/api/src/main/java/com/elymbot/android/feature/geofence/domain/GeofenceRuleRepositoryPort.kt`
  - `feature/geofence/api/src/main/java/com/elymbot/android/feature/geofence/domain/model/GeofenceModels.kt`
  - `feature/geofence/api/src/main/java/com/elymbot/android/feature/geofence/domain/runtime/GeofenceRuntimePorts.kt`
- `GeofenceRuleRepositoryPort` 已有：
  - `rules: StateFlow<List<GeofenceRule>>`
  - `bindings: StateFlow<List<ConfigGeofenceBinding>>`
  - `listConfigBindings(configId)`
  - `upsertConfigBinding(binding)`
  - `deleteConfigBinding(configId, ruleId)`
- `ConfigGeofenceBinding` 只包含：
  - `configId`
  - `ruleId`
  - `enabled`
  - `sortIndex`
  - `createdAt`
  - `updatedAt`
- runtime 重注册通知口：
  - `GeofenceRuntimeReconciliationPort.reconcileAsync(scope)`
  - `GeofenceRuntimeReconciliationPort.reconcileNow()`
- 当前 Hilt bindings：
  - `app-integration/src/main/java/com/elymbot/android/app/integration/geofence/GeofenceRepositoryBindings.kt`
  - `feature/geofence/runtime/src/main/java/com/elymbot/android/feature/geofence/runtime/GeofenceRuntimeBindings.kt`

### 导航事实

- `AppDestination.GeofenceRules` 已存在：
  - `app/src/main/java/com/elymbot/android/ui/navigation/AppDestinations.kt`
- `ConfigDetailScreen` 当前入口在：
  - `app/src/main/java/com/elymbot/android/ui/navigation/ElymBotAppScaffoldParts.kt`
- `ConfigDetailScreen` 当前参数有 `onOpenResourceCenter`；T06 可新增 `onOpenGeofenceRules` 回调，并在 nav graph 中接到 `AppDestination.GeofenceRules.route`。

## 目标

实现 T06：在“配置-配置详情页-平台设置”中新增“地理围栏”项，让用户通过弹窗从“我的-地理围栏配置”真源中选择已有 geofence rules，并装载到当前 `ConfigProfile`。

必须满足：

- “我的-地理围栏配置”仍是规则内容唯一真源。
- ConfigDetail 只管理当前 config 与 rule 的 binding，不编辑或复制坐标、半径、action prompt、target、trigger 等规则内容。
- ConfigProfile 不新增 geofence 坐标/半径/action 字段。
- ConfigDetail 不直接访问 geofence DAO、Room entity、store 或 data implementation。
- 保存 binding 后通知 runtime reconciliation，但 UI 不直接操作 `GeofencingClient`。

## 非目标

- 不实现地理围栏规则创建/编辑；那属于“我的-地理围栏配置”页面。
- 不实现地图选择器或当前位置权限；T05 已负责。
- 不实现 Agent prompt 创建工具；T03 已负责。
- 不修改 Resource Center projection 语义来承载 geofence。
- 不暴露插件 API，不新增 `hostApi.geofence`。

## 允许修改范围

- `feature/config/presentation/src/main/java/**`
- `feature/config/presentation/build.gradle.kts`
- `feature/geofence/api/**`
- `feature/geofence/presentation/**`
- `app-integration/src/main/java/com/elymbot/android/app/integration/geofence/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `app/src/main/java/com/elymbot/android/ui/navigation/**`
- `app/src/main/res/values*/**`
- `app/src/test/java/com/elymbot/android/ui/config/**`
- `app/src/test/java/com/elymbot/android/feature/geofence/**`
- `app/src/test/java/com/elymbot/android/architecture/**`

如必须修改其他文件，最终报告中说明原因。不要修改 geofence data/schema，除非编译或已有 port 合同无法满足 T06，并且最终报告必须说明为什么。

## 推荐实现方向

采用独立即时保存，不把 geofence binding 塞进 `ConfigProfile` draft，也不进入 ConfigDetail 的 unsaved-change 逻辑。

建议结构：

1. 在 `feature/config/presentation` 中新增可测试的 binding presentation / controller / ViewModel。
   - 可添加 `implementation(project(":feature:geofence:api"))`。
   - 只依赖 `GeofenceRuleRepositoryPort`、`ConfigGeofenceBinding`、`GeofenceRule`、`GeofenceRuntimeReconciliationPort` 等 API port/model。
   - 不依赖 `feature/geofence:data`、DAO、Room entity、Resource Center repository 或 runtime implementation。
2. 在 `ConfigDetailScreen` 中通过 `hiltViewModel` 获取 binding ViewModel。
3. 在平台设置分组中新增 `ConfigSection.Geofence` 并加入 `config_nav_group_platform`。
4. 新增 `GeofenceBindingSettingsSection`，视觉上参考白名单/Resource Selection 的“Manage”入口：
   - 标题：`地理围栏`
   - 展示当前装载数量。
   - 展示前 2 条规则摘要，摘要至少包含规则名和启用状态；可附带区域/触发/action 简短描述。
   - 提供 `Manage` 按钮打开弹窗。
5. 新增 `GeofenceBindingDialog`：
   - 从 geofence repository port 的 `rules` 与当前 config 的 `bindings` 构建列表。
   - 没有 rule 时显示空状态“先去我的-地理围栏配置创建”，并提供跳转按钮。
   - 有 rule 时支持多选装载。
   - 被选中的 binding 可在当前 config 下启用/禁用。
   - 保存时只写 `config_geofence_bindings`。
   - 保存后调用 `GeofenceRuntimeReconciliationPort`；优先 `reconcileAsync(viewModelScope)` 或 `reconcileNow()`，二者选一并测试固定。
6. 跳转入口：
   - `ConfigDetailScreen` 新增 `onOpenGeofenceRules` 参数。
   - 空状态/弹窗内按钮调用该回调。
   - app nav graph 将其接到 `AppDestination.GeofenceRules.route`。

## 必须实现细节

### 1. Config section

- 新增 `ConfigSection.Geofence`。
- 加入 `config_nav_group_platform`，建议放在 `Whitelist` 附近或其后，保持平台设置语义。
- 新增英文/中文字符串：
  - `config_section_geofence`
  - `config_section_geofence_desc`
  - binding count、empty state、manage dialog title、跳转入口、保存中/保存失败/重注册提示等必要文案。
- section 标题中文显示为“地理围栏”。
- 不新增可编辑坐标、半径、action prompt 字段。

### 2. Binding presentation

新增可单测模型，命名可调整：

- `ConfigGeofenceBindingPresentation`
- `ConfigGeofenceBindingListItem`
- `ConfigGeofenceBindingDraft`

必须能表达：

- all rules
- current config bindings
- selected rule ids
- per-binding enabled state
- sortIndex
- loaded count
- first 2 summary labels
- empty rules state
- missing/stale binding：如果 binding 指向已删除 rule，UI 不应崩溃；保存时应删除或不再保留 stale binding，并记录测试。

### 3. 保存语义

- 弹窗确认保存时：
  - 对 draft 中选中的 rule upsert `ConfigGeofenceBinding(configId, ruleId, enabled, sortIndex)`。
  - 对当前 config 已存在但 draft 未选中的 rule 执行 `deleteConfigBinding(configId, ruleId)`。
  - 不调用 `createRule`、`updateRule`、`replaceRegions`、`pauseRule`、`resumeRule`。
  - 不修改 `ConfigProfile`。
  - 不修改 Resource Center projections。
- 保存成功后关闭弹窗并清理错误状态。
- 保存失败时显示错误，不静默关闭。
- 保存后触发 runtime reconciliation。

### 4. UI 与交互

- Section 中显示：
  - 当前装载数量。
  - 前 2 条规则摘要。
  - 空状态和跳转入口。
  - `Manage` 按钮。
- Dialog 中显示：
  - 无规则时的空状态和跳转入口。
  - 有规则时的可勾选列表。
  - 每条已选 binding 的启用/禁用开关。
  - Save / Cancel。
- 建议增加稳定 test tags：
  - `config-geofence-section`
  - `config-geofence-manage`
  - `config-geofence-dialog`
  - `config-geofence-empty`
  - `config-geofence-open-rules`
  - `config-geofence-rule-checkbox-<ruleId>`
  - `config-geofence-binding-enabled-<ruleId>`

### 5. 架构约束

必须新增或扩展 architecture/source contract，覆盖：

- Config presentation 不 import：
  - `com.elymbot.android.data.db.geofence`
  - `GeofenceRuleDao`
  - `FeatureGeofenceRuleRepositoryStore`
  - `FeatureGeofenceRuleRepositoryPortAdapter`
  - `feature.geofence.data`
- `ConfigProfile` 不新增 geofence 坐标、半径、action prompt 等字段。
- Resource Center projection 不复用承载 geofence binding；不要在 `buildProjectionUpdates` 或 `ConfigResourceProjection` 里塞 geofence。

## 必须补测试

至少新增/更新：

- `ConfigGeofenceBindingPresentationTest`
  - 无规则时显示空状态和跳转入口。
  - 有规则时可多选装载。
  - 保存只写 binding。
  - 禁用当前 config 下某条 binding 不修改 rule 本体。
  - stale binding 不导致崩溃，保存后不保留 stale rule id。

- `ConfigDetailGeofenceSectionTest`
  - 平台设置抽屉包含“地理围栏”。
  - Section 显示装载数量和前 2 条摘要。
  - 管理弹窗可打开。
  - 空状态跳转 callback 被调用。

- architecture/source contract
  - Config presentation 不 import geofence data DAO。
  - ConfigProfile 不新增 geofence 坐标/半径/action 字段。
  - Resource Center projection 不被复用承载 geofence binding。

如果 Compose UI test 难以在当前 test harness 下直接运行，先用 JVM presentation/contract tests 固定行为，并在最终报告说明未覆盖的真实 UI/设备项。

## 验证命令

必须至少运行：

```powershell
.\gradlew.bat moduleConfigCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleGeofenceCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Config*Geofence*" --tests "*Geofence*Binding*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
git diff --check
```

如果改动影响 app navigation 或 Hilt graph，请增加：

```powershell
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

如当前环境没有 `adb`，不要伪造 connected test 结果；报告 blocked 原因。

## 自检要求

完成前自行检查：

- 没有 Git 写入。
- 没有回滚或清理无关改动。
- 没有把 geofence rule 内容复制进 ConfigProfile。
- 没有在 ConfigDetail 内创建/编辑坐标、半径、action prompt。
- 没有 import geofence DAO/data implementation。
- 保存 binding 后有 runtime reconciliation。
- T04/T05 的“我的-地理围栏配置”仍是唯一规则维护入口。

## 最终报告格式

请按下面格式回复总控：

```text
STATUS: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED

changed files:
- ...

implementation summary:
- ...

verification commands with pass/fail:
- PASS/FAIL/BLOCKED: ...

unverified items:
- ...

concerns / risks:
- ...
```

如果有 `NEEDS_CONTEXT` 或 `BLOCKED`，明确说明缺什么上下文或阻塞在哪个文件/命令，不要泛泛而谈。

# P260607-2248 T03 worker - Agent 内部创建与触发动作

## 调度信息

- Scene: `uth-dev`
- Mode: `todo-implementation`
- Task package: `docs/work/D26060701-地理围栏完整能力设计/`
- Todo: `30-D26060701-T03-todo-Agent内部创建与触发动作.md`
- Worker role: implementation worker
- Controller constraint: 当前主窗口只做总控，不参与代码编写；你负责本 Todo 的代码与测试改动。
- Git writes: forbidden. 不得执行 commit / push / branch / reset / checkout。
- Brainstorming persistence gate: `no_open_user_questions=true`。用户已要求继续串行实施，本 Todo 无需再向用户确认。

## 必读文件

开始写代码前至少读取：

1. `docs/README.md`
2. `AGENTS.md`
3. `docs/current-state.md`
4. `docs/work/D26060701-地理围栏完整能力设计/00-D26060701-design.md`
5. `docs/work/D26060701-地理围栏完整能力设计/30-D26060701-T03-todo-Agent内部创建与触发动作.md`
6. `docs/context/04-核心运行时.md`
7. `docs/context/06-Provider配置Bot与Persona.md`
8. `docs/context/07-聊天与会话.md`
9. `docs/context/09-插件平台.md`

还要读取 T01/T02 已落地的相关代码和测试，按当前工作区事实适配，不得回滚、重排或清理其他 worker 的改动。

## 当前代码事实

- `core/runtime-context/.../RuntimeIngressContracts.kt` 中 `IngressTrigger` 当前只有 `USER_MESSAGE`、`COMMAND`、`SCHEDULED_TASK`、`PLUGIN_EVENT`。
- `ToolSourceContext` 已有 `ingressTrigger` 字段，默认 `USER_MESSAGE`，可用于控制工具可见性。
- `feature/geofence/runtime/.../GeofenceTransitionProcessor.kt` 当前在 transition 校验、节流、执行记录写入后，把成功记录的 `deliverySummary` 写成 `geofence transition received; action dispatch pending T03`。T03 需要替换为真实动作执行结果。
- `feature/geofence/api/.../GeofenceModels.kt` 已有 `GeofenceActionType`：`AGENT_PROMPT`、`SEND_MESSAGE`、`WEATHER_FORECAST`、`NEWS_DIGEST`、`HOST_CAPABILITY`。
- `GeofenceRuleRepositoryPort` 已提供 rule / region / binding / execution record 的 repository 真源方法；`我的-地理围栏配置` 仍是唯一真源。
- `RuntimeLlmOrchestratorPort` 的真实 contract 在 `feature/plugin/api/src/main/java/com/elymbot/android/feature/plugin/domain/runtime/PluginRuntimeContracts.kt`；`feature/plugin/runtime/.../RuntimeLlmOrchestratorPort.kt` 只是 runtime 包兼容入口。
- `ScheduledTaskRuntimeExecutor` 是构造 `RuntimeIngressEvent`、设置 `IngressTrigger.SCHEDULED_TASK`、通过 `RuntimeContextResolverPort` + `RuntimeLlmOrchestratorPort` 执行 host-owned Agent turn 的近似参考。
- `DefaultRuntimeLlmOrchestrator` 会把 `ctx.toolSourceContext` 传给 plugin LLM pipeline。
- `FutureToolSourceRegistry` 当前按 `PluginToolSourceKind` 建 `providersByKind`，不要盲目新增第二个同 kind provider 导致 invoke 路由覆盖。若新增 reserved source kind，必须同步 core/runtime-tool、plugin api/runtime 映射和合同测试；更保守的路径是把 geofence 工具作为 `ACTIVE_CAPABILITY` 下 `ownerId = "cap.geofence"` 的 host-owned 工具接入现有 active capability 工具源。

## 目标

实现宿主内部 Agent 地理围栏能力：

1. 用户可通过 Agent prompt 调用内部工具创建、更新、列出、删除、暂停、恢复 geofence rule。
2. geofence transition 可按 rule 执行 `send_message`、`agent_prompt`、`weather_forecast`、`news_digest` 或第一版 `host_capability` unsupported 处理。
3. geofence-triggered Agent turn 默认不暴露 geofence create/update/delete/pause/resume 工具，避免递归。
4. 严格不向插件 Host API 暴露 `hostApi.geofence.*`，插件不得读取 geofence domain model、坐标或 execution record。

## 非目标

- 不实现地图选择器。
- 不实现“我的”页 UI。
- 不实现 ConfigDetail 装载弹窗。
- 不放宽插件任意目标发送能力。
- 不承诺真实设备 geofence 准实时触发。
- 不把 geofence rule 内容复制进 ConfigProfile。

## 允许改动范围

主要允许：

- `core/runtime-context/src/main/java/**`
- `feature/geofence/api/**`
- `feature/geofence/runtime/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `app-integration/src/main/java/com/elymbot/android/app/integration/geofence/**`
- `app/src/test/java/com/elymbot/android/runtime/geofence/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/toolsource/**`
- `app/src/test/java/com/elymbot/android/architecture/**`

如为正确接入 reserved ToolSource kind 或 Gradle 依赖必须触碰以下文件，可以最小化修改并在回传中标明理由：

- `core/runtime-tool/src/main/java/**`
- `feature/plugin/api/src/main/java/**`
- `feature/plugin/runtime/build.gradle.kts`
- `app-integration/build.gradle.kts`
- `feature/geofence/runtime/build.gradle.kts`

禁止：

- Git 写入。
- 恢复 static registry / service locator / 手写 production subgraph。
- 新增 `hostApi.geofence`、插件 geofence manifest 权限或插件 geofence handler。
- 让 `core/**` 依赖 `feature/**`。
- 在普通 runtime log 输出完整经纬度。

## 必须实现

### 1. Ingress trigger

- 新增 `IngressTrigger.GEOFENCE_EVENT`。
- geofence-triggered action 构造的 `RuntimeIngressEvent` 必须使用该 trigger。
- raw payload 至少包含：`ruleId`、`ruleName`、`regionId`、`regionLabel`、`transition`、`latitude`、`longitude`、`radiusMeters`、`occurredAt`、`configId`。
- 坐标可进入 execution record 的 payload/snapshot，但不要写入普通 runtime log 明文。

### 2. Geofence action executor

- 新增清晰的 geofence action executor / port 模型，避免 `GeofenceTransitionProcessor` 直接依赖插件或聊天具体实现。
- `GeofenceTransitionProcessor` 在 rule enabled、transition allowed、minimum interval 通过后执行 action；只有 action 成功才写成功完成记录。
- action 失败时写 failed completion record，`errorCode` 清晰，例如 `web_search_disabled`、`delivery_failed`、`unsupported_action`、`missing_target_context`。
- `send_message` 走宿主投递 port，不走插件 Host API。
- `agent_prompt` 通过 `RuntimeContextResolverPort` + `RuntimeLlmOrchestratorPort` 执行 host-owned Agent turn，参考 `ScheduledTaskRuntimeExecutor`。
- `weather_forecast` 和 `news_digest` 本质是预置 prompt 模板 + geofence payload；必须检查目标 ConfigProfile 的 `webSearchEnabled`，未开启时失败，不能绕过策略。
- `host_capability` 第一版可返回 `unsupported_action`，不要做泛化宿主能力执行。

### 3. Agent 内部工具

实现内部工具：

- `create_geofence_rule`
- `update_geofence_rule`
- `list_geofence_rules`
- `delete_geofence_rule`
- `pause_geofence_rule`
- `resume_geofence_rule`

语义要求：

- 工具仅对宿主 Agent runtime 可见，不进入插件 Host API。
- 普通用户消息 turn 中可见；`IngressTrigger.GEOFENCE_EVENT` turn 中隐藏 create/update/delete/pause/resume，避免自我递归。
- 如 prompt/tool payload 使用“当前位置”但前台定位权限不足，返回 structured error `permission_required`；Agent 不能绕过系统权限。
- 如缺坐标、当前位置或地图选择结果，返回 `missing_location`；不要猜地址。
- 创建成功但后台定位权限不足时，保存 rule，状态为 `permission_required`，并在返回 JSON 中说明“已保存但未启用后台触发”。
- 更新、删除、暂停、恢复后触发 geofence runtime reconciliation。
- 工具 schema 覆盖名称、位置、半径、trigger、action、目标上下文、minimum trigger interval。
- list 返回 rule 摘要和必要 region/action 信息；避免在 runtime log 输出完整坐标。

### 4. 插件边界

- 新增或更新 architecture / plugin boundary contract，证明不存在 `hostApi.geofence`。
- 插件 API / QuickJS Host API 不得导入 geofence domain model。
- 如果新增 `ToolSourceKind` / `PluginToolSourceKind`，必须标记为 reserved/internal source，不得变成插件 manifest 可声明能力。

## 必须补测试

至少覆盖：

- `GeofenceAgentToolSourceProviderTest`
  - schema 可创建 rule。
  - 缺位置返回 `missing_location`。
  - 使用当前位置但无权限返回 `permission_required`。
  - 创建成功但后台权限不足时 rule 保存为 `permission_required`。
  - geofence-triggered turn 不暴露 create/update/delete/pause/resume。
- `GeofenceActionExecutorTest`
  - `send_message` action 投递成功。
  - `agent_prompt` 构造 `IngressTrigger.GEOFENCE_EVENT` ingress。
  - `weather_forecast` 在目标 config 未开启 web search 时失败并返回明确 code。
  - `news_digest` prompt 包含 transition 和区域上下文。
  - execution record 成功/失败摘要被写入。
- plugin boundary contract
  - `hostApi.geofence` 不存在。
  - plugin API 不引用 geofence domain model。

可根据实际包位置命名测试类，但回传中必须说明覆盖关系。

## 建议验证命令

按顺序运行，避免 Gradle/Kotlin cache 并发问题：

```powershell
.\gradlew.bat :feature:geofence:runtime:testDebugUnitTest --tests "*Geofence*Action*" --tests "*Geofence*Agent*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Geofence*" --tests "*ToolSource*" --tests "*Plugin*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

如果你触碰了 plugin/runtime 或 Gradle 依赖，额外运行相关窄口：

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleGeofenceCheck --console=plain --no-daemon --stacktrace
```

## 回传格式

完成后按以下格式回复：

```text
STATUS: DONE | DONE_WITH_CONCERNS | NEEDS_CONTEXT | BLOCKED

Changed files:
- ...

Implementation summary:
- ...

Verification:
- command: ...
  result: pass/fail
  warnings: N
  exceptions: N

Unverified:
- ...

Concerns / risks:
- ...

Notes for reviewers:
- ...
```

不要声明完整 Design 完成；本轮只完成 T03。

## Rework 1 - 2026-06-07 23:11

Reason:

Spec evaluator `019ea2b9-a6df-77a3-9f8e-977f4d1d1675` found T03 compliance gaps:

- S1: `weather_forecast` / `news_digest` may check `webSearchEnabled` against the wrong ConfigProfile because `ConfigRepositoryPort.resolve(configId)` can fall back to another config when the target id is missing. `create/update` also do not precisely validate `config_profile_id`.
- S2: `use_current_location` is not actually implemented. Create only checks foreground permission, then still requires explicit latitude/longitude. Update does not check `use_current_location`, so missing coordinates can silently keep the old region and report success.
- S3: T03 tests do not fully cover update/list/delete/pause/resume, reconciliation after management tools, `host_capability -> unsupported_action`, full raw payload fields, and update current-location permission behavior.

Additional instructions:

1. Fix S1:
   - Do not use a fallback config lookup for geofence target policy checks.
   - Add or use an exact ConfigProfile lookup. Missing target config must fail with `missing_target_context`.
   - `weather_forecast` and `news_digest` must only pass when the exact target ConfigProfile has `webSearchEnabled = true`.
   - Agent create/update tools must validate `config_profile_id` precisely when present or required; do not silently write invalid config bindings.

2. Fix S2:
   - Implement trustworthy `use_current_location` semantics for create and update.
   - If `use_current_location = true` and foreground permission is missing, return structured error `permission_required`.
   - If foreground permission exists but no trusted current-location value is available, return `missing_location`; do not guess address or coordinates.
   - It is acceptable to source trusted current location from a small injected/adapter port or from explicit trusted metadata, but the behavior must be testable and must not bypass system permissions.
   - Create and update should share the same location resolution semantics where practical.

3. Fix S3:
   - Add focused tests for update/list/delete/pause/resume tool invocation and reconciliation after mutating operations.
   - Add tests for `host_capability -> unsupported_action`.
   - Add full raw payload field assertions for `GEOFENCE_EVENT`.
   - Add update `use_current_location` no-permission / missing-current-location coverage.

Validation:

Run at least:

```powershell
.\gradlew.bat :feature:geofence:runtime:testDebugUnitTest --tests "*Geofence*Action*" --tests "*Geofence*Agent*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Geofence*" --tests "*ToolSource*" --tests "*Plugin*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleGeofenceCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

Return requirements:

- Keep the original T03 return format.
- Include which findings were fixed and which tests prove them.
- Do not perform Git writes.

## Rework 2 - 2026-06-08 00:00

Reason:

Quality evaluator `019ea2e9-4fe6-7cc1-9ccd-9e9261c4ab1e` found implementation quality risks:

- Q1: Weather/news prompts include full coordinates and the shared plugin LLM pipeline can expose LLM input snapshots to plugins, violating the plugin boundary that plugins cannot read geofence coordinates. Also, create/update/delete/pause/resume can return success after failed config binding or failed reconciliation.
- Q2: Transition completion updates the full stale rule and forces `status = ACTIVE`, so a long action can resurrect a paused/edited rule. Target resolution is too permissive: unknown platform falls back to App Chat and explicit bot/config mismatches can run under the wrong context.
- Q3: Geofence mutating tools are hidden from `listBindings` during `GEOFENCE_EVENT`, but `invoke` can still execute stale descriptors. Plugin-boundary tests are weak because they would not catch geofence management symbols in plugin API.

Additional instructions:

1. Fix coordinate leakage through plugin LLM hooks:
   - `GEOFENCE_EVENT` agent/weather/news turns must not expose full latitude/longitude to plugin-visible LLM hooks or plugin-visible event snapshots.
   - Prefer bypassing plugin LLM hooks for geofence-triggered turns, or provide a redacted hook-visible event/request while exact coordinates remain available only to host/provider execution as necessary.
   - Add a regression test proving plugin hook-visible payload/input does not contain exact coordinates such as `31.2304` / `121.4737`.

2. Fix success-after-failure paths in `GeofenceActiveCapabilityFacadeAdapter`:
   - Do not ignore `upsertConfigBinding` failures.
   - Do not return `success=true` when reconciliation fails for mutating operations.
   - Either make creation/binding effectively atomic with compensation on failure, or return structured failure and avoid orphan success.
   - Add tests for binding failure and reconciliation failure.

3. Fix stale rule overwrite after transition action:
   - Do not update a full stale `rule.copy(status = ACTIVE)` after action completion.
   - Reread/merge only `lastTriggeredAt`, or add a repository method that updates last-trigger metadata without resurrecting paused/deleted/edited rules.
   - Add a test simulating pause/update during action execution.

4. Tighten target validation:
   - Validate `target_platform` on create/update; unknown values must return structured error, not fall back to App Chat.
   - Validate explicit `targetBotId` belongs to the exact target config before action execution or persistence.
   - Ensure config/provider/persona override behavior cannot run under the wrong ConfigProfile.
   - Add tests for invalid platform and bot/config mismatch.

5. Enforce invocation-level geofence recursion guard:
   - During `IngressTrigger.GEOFENCE_EVENT`, direct invoke of mutating geofence tools must fail with a structured code such as `geofence_tools_hidden_during_geofence_event`.
   - Availability should also reflect this where the current identity/sourceRef allows it.
   - Add invocation-level test.

6. Strengthen plugin boundary contract:
   - Ensure geofence management facade in `feature/plugin/api` is clearly not a plugin Host API surface, or move it if a cleaner boundary exists within the allowed scope.
   - Strengthen tests to forbid accidental plugin-visible geofence API/Host API exposure beyond an explicit internal whitelist.

Validation:

Run at least:

```powershell
.\gradlew.bat :feature:geofence:runtime:testDebugUnitTest --tests "*Geofence*Action*" --tests "*Geofence*Agent*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Geofence*" --tests "*ToolSource*" --tests "*Plugin*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleGeofenceCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

Return requirements:

- Keep the original T03 return format.
- Include which Q1/Q2/Q3 findings were fixed and which tests prove them.
- Do not perform Git writes.

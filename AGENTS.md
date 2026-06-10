<!-- context7 -->
当用户询问库、框架、SDK、API、CLI 工具或云服务时，使用 Context7 MCP 获取最新文档，即使是 React、Next.js、Prisma、Express、Tailwind、Django 或 Spring Boot 这类常见技术也一样。这里包括 API 语法、配置、版本迁移、库相关调试、安装说明和 CLI 工具用法。即使你觉得自己已经知道答案，也要优先使用它，因为训练数据未必反映最新变化。查询库文档时，优先于网页搜索使用它。

不要用于：重构、从零编写脚本、调试业务逻辑、代码审查，或解释通用编程概念。

## Steps

1. 总是先用库名和用户的问题执行 `resolve-library-id`，除非用户已经提供了 `/org/project` 格式的精确库 ID。
2. 按以下标准选择最合适的匹配项（ID 格式：`/org/project`）：名称完全匹配、描述相关性、代码片段数量、来源可信度（优先 High/Medium）、以及基准分数（越高越好）。如果结果不合适，尝试其他名称或查询方式，例如用 "next.js" 而不是 "nextjs"，或者重新表述问题。用户提到具体版本时，要使用对应版本的 ID。
3. 使用选中的库 ID 和用户的完整问题执行 `query-docs`，不要只输入单个词。
4. 基于获取到的文档回答。
<!-- context7 -->

# Repo Agent Workflow

仓库约定：根 `AGENTS.md` 是新窗口实际 agent 入口，也是背景文档体系的全局规则层。
后续如果调整跨模块规则、高频误判或新窗口最小动作，应优先维护本文件；模块内部链路、模块私有事实、模块级测试入口仍回到 `docs/context/01` 到 `docs/context/12` 的编号上下文。

## UTH Governance

- 本项目已启用 UTH。项目标记：`.uth-governance/project.json`。
- 工程任务先由 `uth-governance` 判定场景，再进入对应 `uth-*` 技能。
- 文档入口：`docs/README.md`；当前状态索引：`docs/current-state.md`；上下文索引：`docs/context/README.md`。
- 本地 hook runner：`tools/uth-hooks/uth-hook.py`。
- 不要默认扫描全部文档；先读 `docs/README.md` 和 `docs/current-state.md`，再按场景补读所需文件。
- 不执行 Git 写入，除非 `uth-git` 已激活且用户已确认 Git 计划。

所有新窗口在开始代码编写前，至少先读：

1. `docs/README.md`
2. 本文件 `AGENTS.md`
3. `docs/context/` 下对应编号模块文档

如果同一类错误再次重复出现两次及以上，把它补回本文件，不要只留在会话里。

## 1. 开始前默认阅读顺序

1. `docs/README.md`
2. 本文件 `AGENTS.md`
3. 目标模块文档（`docs/context/01` 到 `docs/context/12`）
4. 如基线不清或问题跨模块，再补 `docs/context/00-模块拆分.md`
5. 只有需要旧证据时，再按 `docs/context/docs-00-11-classification.md` 打开 `docs/archive/pre-uth-docs/docs-00-11/` 中的归档旧文档

## 2. 当前全局规则

### 2.1 先按 feature-first 找代码

- 优先看 `feature/<module>/data`、`domain`、`presentation`、`runtime`
- 根目录 `data/*`、`runtime/*`、`ui/*` 多数只剩基础设施、compat seam 或 app shell
- 不要因为旧路径搜不到就判断能力已经删除
- `:app` 当前不再直接依赖 feature data/runtime/impl 生产主线；App 侧只按 Manifest、Application、Activity、navigation root、theme/resources、app chrome 与 thin platform adapter 理解
- `:feature:qq:data/runtime/presentation`、`:feature:voiceasset:data/presentation`、`:feature:provider:runtime` 已是第 24-26 期后的当前模块边界；空 `impl` 模块只按兼容聚合壳或待清理模块理解，不是生产 owner

### 2.2 生产依赖注入唯一合法方法是 Hilt

- 生产依赖注入只能通过 Hilt graph、constructor injection、`di/hilt/*`、`@HiltAndroidApp`、`@AndroidEntryPoint`、`@HiltViewModel`、`@HiltWorker` 和 Hilt 提供的 production port 完成
- 不要恢复手写 app container、dependency holder、global registry、service locator、static install/configure callback、手写 runtime subgraph、生产 `ViewModelProvider.Factory`
- `RuntimeContextDataRegistry`、`AppBackupDataRegistry`、`ConversationBackupDataRegistry`、`ContainerBridgeStateRegistry`、`PluginRuntime*Provider`、`PluginExecutionHostApi` 这类 compat/test residue 不是第二套生产 DI 框架
- 启动顺序以 `AppBootstrapper -> AppStartupRunner -> di/startup/*` 这条主线理解

### 2.3 core 与 feature 的边界不能倒置

- `core/**` 不依赖 `feature/*`
- feature domain 不直接依赖 Android / Compose / 旧单例
- 模块边界判断先看 architecture/source contract，再看实现

### 2.4 共享上下文与资源真源已经收口

- 共享 runtime context 先看 `core/runtime/context/*`
- MCP / skill / tool 资源先看 Resource Center
- `ProviderRuntimePort` contract 在 `feature/provider/api/src/main/java/com/elymbot/android/feature/provider/api/runtime/ProviderRuntimePort.kt`，production implementation 在 `feature/provider/runtime`；voiceasset presentation 不依赖 provider runtime implementation
- 不要在 App Chat、QQ、Cron、Plugin 各自复制一份上下文或资源投影逻辑

### 2.5 状态切换、删除保护、事务边界不要回到 UI 层手拼

- Bot / Config 的 selected state 以持久化 flow 回流为准
- Provider / Persona 删除前先过引用保护
- Config / Bot 删除事务已经下沉，不要回到 ViewModel 手动串联

### 2.6 Plugin、QQ、Cron、Backup 按当前主线理解

- Plugin runtime、host capability、LLM orchestration 走 port + Hilt wiring，不走 static API
- QQ bridge 只保留薄胶水层；reverse WS 授权比旧口径更严格
- Cron 生产执行走 Hilt-owned runtime closure，不回到静态 dependency provider
- Backup restore 是 durable / 可回滚恢复，但覆盖面仍落后于新模型

### 2.7 测试和合同优先级很高

- 改结构、入口、DI、Manifest、plugin runtime、QQ/NapCat、Cron、backup 时，优先回看目标编号 context 中的测试入口；需要旧测试索引时再查 `docs/archive/pre-uth-docs/docs-00-11/10_测试入口_回归面_已知风险.md`
- 自动化覆盖强的地方，先看 architecture/source contract 再下判断
- `architectureCheck` 当前包含 `:architecture-tests:test`；`build-logic` 已落地，Gradle convention 相关回归要同时看 `:build-logic:check`
- 当前 allowlist 基线：static repository usage allowlist 为 0；global singleton allowlist 为 2 permanent、0 temporary；后续新增 singleton allowlist 项必须证明为 stateless permanent 或 test-only，不得把 production debt 改名为 permanent
- 任务推进到稳定节点后，必须确保 `.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace` 无告警通过；不要只反复跑窄范围 `compileDebugKotlin`，避免后续全量编译被累积告警或构建状态拖到半小时级别

## 3. 当前高频易错点

1. 当前项目已经是 ElymBot 口径，不要按旧 `ElymBot` 工作区思考。
2. 物理路径迁了不等于 Kotlin package 一定跟着迁；路径和 package 要双重确认。
3. 旧 `docs/archive/pre-uth-docs/docs-00-11/11_全链路执行流程图.md` 已归档为辅助图，不是默认必读，也不是主真源。
4. 模块细节不要继续堆回本文件；只保留跨模块规则和重复误判。
5. 如果一个问题已经明显落在某个模块里，就直接回该模块文档，不要先在总文档里兜圈。
6. `Feature*PortAdapter` / `FeatureQq*PortAdapter` 已是当前生产命名，不要再把 `Legacy*Adapter` 写成现状。
7. QQ / plugin / toolsource 的新生产主线都不接受手写 subgraph 或 static bypass；看到 compat bridge 时先确认它是不是仅给测试保留。
8. 不要把第 23 期 `:feature:qq:impl`、`:feature:voiceasset:api`、`:feature:plugin:runtime` 过渡依赖写成当前 app direct dependency 事实。
9. Design、Todo、Feedback、LW 和 archive 只能作为证据线索；是否已成为当前事实必须看源码、验证记录和 `docs/context/` 同步状态，不能把“已设计”写成“已实现”。
10. 工作区可能已有其他窗口或 worker 的半成品/已验收未提交改动；不要回滚、重排或清理无关改动，必须先按当前工作区基线适配。
11. 插件 Host API 对齐的是 ElymBot 宿主代理能力，不暴露 AstrBot 风格别名，也不允许插件直连 Room DAO、OneBot socket、adapter 实例、provider secret 或自建网络通道。
12. `hostApi.message.send`、`hostApi.message.openStream` 和 `hostApi.conversation.history` 仍按当前会话能力理解；`D26052201` 的任意目标发送只是设计包，未拆 Todo/实现前不得写成生产能力。
13. QQ 会话要区分本地 conversation id、`originSessionId` 和 OneBot 目标 id；群聊隔离只隔离 bot LLM 上下文，插件做群分析时优先读取公共群历史，而不是把 `group:<gid>:user:<uid>` 当作公共群历史真源。
14. 构建优化当前基线是 configuration cache / build cache / parallel execution + lazy wiring；不要把 module group check 改回全局 `gradle.projectsEvaluated`，也不要把 app APK export 改回 app 级 `afterEvaluate`。
15. `:app:testDebugUnitTest` 的 runtime / friend-path 治理按测试域分组；不要重新把 `:core:runtime-audio` 或 `:core:runtime-container` 字符串直接塞回 `app/build.gradle.kts` 的 app 单测任务图。
16. NapCat Android 兼容问题先看 `RuntimeCompatibilityProbe`、`start_napcat.sh` 条件 external storage bind、`ContainerBridgeService` / `AndroidRuntimeBridgeController` 启动分类；不要通过新增 `READ_EXTERNAL_STORAGE`、`WRITE_EXTERNAL_STORAGE`、`READ_MEDIA_*` 或 `MANAGE_EXTERNAL_STORAGE` 兜底。
17. `RuntimeCompatibilityProbe` 是 `:core:runtime-container` 的诊断模型与合同，`ContainerBridgeService` 是 app 侧 Android service adapter；不要把 Android service owner 倒置到 core，也不要把二者混成同一个边界。
18. 地理围栏 owner 是 `feature/geofence` 模块组；`ConfigDetail` 只管理 `config_geofence_bindings` 引用，不把坐标、半径、action prompt、region 或 binding 字段塞回 `ConfigProfile`，也不要复用 Resource Center projection 承载 geofence 绑定。
19. `create_geofence_rule` 等地理围栏 Agent 工具是宿主内部 active capability，经 `GeofenceActiveCapabilityFacade` 接线，不是公开 `hostApi.geofence.*`；`GEOFENCE_EVENT` 下 mutation 工具应隐藏，避免地理围栏事件自我递归修改规则。
20. 地理围栏自动化 gate 通过不等于真实设备能力已验收；没有 connected/manual、Google Play services、后台定位授权、地图渲染、厂商 ROM 和真实 enter/exit/dwell transition 证据时，必须保持 blocked/unverified 口径。

## 4. 模块细节应该去哪里找

- 验证、构建、治理：`docs/context/01-验证构建治理.md`
- 入口、构建、导航壳层：`docs/context/02-应用壳层与集成.md`
- 数据真源、Room、Repository、备份基础：`docs/context/03-核心基础与数据库.md`
- core runtime、context、container、audio、search、secret、session、tool：`docs/context/04-核心运行时.md`
- 下载、rootfs、容器资产：`docs/context/05-下载与容器资产.md`
- Provider、Config、Bot、Persona：`docs/context/06-Provider配置Bot与Persona.md`
- App Chat 与会话：`docs/context/07-聊天与会话.md`
- QQ 登录、NapCat、OneBot：`docs/context/08-QQ_NapCat_OneBot.md`
- 插件平台、插件 UI、市场、安装、执行、治理：`docs/context/09-插件平台.md`
- Cron 运行时：`docs/context/10-Cron运行时.md`
- 设置、日志、资源、运行时清理、备份入口：`docs/context/11-资源设置备份.md`
- STT、TTS、声音克隆、语音资产：`docs/context/12-语音资产与音频.md`
- 地理围栏、位置权限、地图选择、Config 绑定、运行时触发：`docs/context/13-地理围栏.md`
- 旧 `docs/00` 到 `docs/11`：只在 `docs/context/docs-00-11-classification.md` 指向的 archive 路径中作为历史辅助证据读取

## 5. 新窗口开始开发时的最小动作

1. 先确认当前问题属于哪个模块。
2. 先读该模块的 `domain` / `port` / `use case`，再看 `data`、`presentation`、`runtime`。
3. 如果问题跨模块或基线不清，再补 `docs/context/00-模块拆分.md`。
4. 如果需要旧执行链或旧测试索引作为辅助证据，先读 `docs/context/docs-00-11-classification.md`，再打开对应 archive 文件。
5. 改完至少回看 `docs/10` 中对应回归面，或明确说明本轮没覆盖哪部分。

## 6. 背景文档更新代理规则

当任务是维护 `docs/` 背景文档体系，而不是实现代码或编写任务方案时，按下面规则执行：

1. 先判定变更属于哪一层，只改最少必要文件。
2. 默认只根据现有文档体系和用户提供的信息更新；除非用户明确要求代码核对或扫描代码，不主动扩展为代码审计。
3. 检查模块私有内容是否被错误上浮到 `docs/README.md` 或本文件。
4. 检查同一段正文是否在多个层级重复；能引用就不要复述。
5. 检查旧 `docs/archive/pre-uth-docs/docs-00-11/11_全链路执行流程图.md` 是否仍保持“归档辅助图、默认非必读、非主真源”定位。
6. 更新 `docs/context/01` 到 `docs/context/12` 时，保留代码事实来源、排除路径、模块职责、边界和测试入口说明。
7. 输出时按固定顺序给出：重构/更新计划、准备修改或新增的文件、每个文件的修改理由、修改后的文档初稿、自检结果。

## 7. 回填规则

- 只有跨模块、重复出现两次以上的误判，才回填到本文件。
- 只属于单模块的事实，不回填到本文件，直接改对应模块文档。
- 如果统一基线发生变化，先改 `docs/current-state.md` 与 `docs/context/00-模块拆分.md`，再回头调整 `docs/README.md` 和本文件。

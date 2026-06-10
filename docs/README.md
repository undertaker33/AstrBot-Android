# ElymBot 背景文档入口

本目录是本地背景文档区，用于帮助新窗口快速装载当前状态、治理规则和模块上下文。代码事实优先于旧文档；旧文档只作为历史辅助证据。

## UTH 接管入口

- 项目标记：`../.uth-governance/project.json`
- 当前状态索引：`current-state.md`
- 上下文索引：`context/README.md`
- 模块拆分报告：`context/00-模块拆分.md`
- `docs/00` 到 `docs/11` 分类：`context/docs-00-11-classification.md`
- 旧文档分类：`context/old-doc-classification.md`
- 治理规则索引：`_governance/README.md`
- Changelog 锚点索引：`changelogs/README.md`
- 接管快照：`snapshots/ONB26051701-existing-project-handoff.md`
- 接管前文档备份：`ONB26051701-pre-uth-docs-backup.zip`

当前 UTH 状态：existing-project takeover 文档接管收尾已完成。原 12 个编号模块上下文均已写入，旧 `docs/00_*.md` 到 `docs/11_*.md` 已归档，早期 seed / 失效完成证据已移出 current context。最新文档同步已覆盖 `13467db..b069daf` 的 `v1.1.1` / `v1.1.2` release 事实，以及当前工作区 `D26060701` 地理围栏模块组增量；地理围栏已追加为 `context/13-地理围栏.md`。

当前完成等级：`full-project-docs-complete`。本轮全仓文档更新完成等级为 `full-project-docs-complete`。这些结论只代表文档治理基线完成，不代表本轮运行过 Gradle、单元测试或 APK 构建。

## 默认加载顺序

每个新窗口默认按下面顺序装载：

1. `README.md`
2. `../AGENTS.md`
3. `current-state.md`
4. `context/README.md`
5. 目标模块对应的编号上下文

旧模块正文已经归档到 `archive/pre-uth-docs/docs-00-11/`，不得直接作为当前事实入口。需要历史辅助证据时，先读 `context/docs-00-11-classification.md`，再按需打开归档文件。

## 当前入口结构

| Layer | File | Role |
| --- | --- | --- |
| 状态入口 | `current-state.md` | 当前完成态、阻塞项、事实来源和后续路由 |
| 上下文索引 | `context/README.md` | 当前可用 context、seed、历史证据的读取规则 |
| 拆分报告 | `context/00-模块拆分.md` | 模块拆分队列、代码事实依据和连续治理规则 |
| 旧模块分类 | `context/docs-00-11-classification.md` | 旧 `docs/00` 到 `docs/11` 的归档与替代关系 |
| 旧文档分类 | `context/old-doc-classification.md` | 接管前旧文档与协作材料分类 |
| Changelog 索引 | `changelogs/README.md` | 发布 changelog 的版本化 git 锚点索引；不承载发布正文 |
| 归档入口 | `archive/README.md` | 已完成或失效历史材料，不作为当前事实来源 |

## 当前编号上下文

- `context/01-验证构建治理.md`
- `context/02-应用壳层与集成.md`
- `context/03-核心基础与数据库.md`
- `context/04-核心运行时.md`
- `context/05-下载与容器资产.md`
- `context/06-Provider配置Bot与Persona.md`
- `context/07-聊天与会话.md`
- `context/08-QQ_NapCat_OneBot.md`
- `context/09-插件平台.md`
- `context/10-Cron运行时.md`
- `context/11-资源设置备份.md`
- `context/12-语音资产与音频.md`
- `context/13-地理围栏.md`

## 旧文档地位

- `archive/pre-uth-docs/docs-00-11/`：旧 `docs/00_*.md` 到 `docs/11_*.md`，只作为历史辅助证据。
- `archive/takeover-repair/`：早期 `baseline.md`、`feature-modules.md`、`onboarding-followup-evidence.md`，只作为接管修复历史证据。
- `archive/context-pre-numbering/`：旧未编号 context 迁移证据。

归档文件中的旧提交号、旧路径、旧版本号和早期完成态不得覆盖当前编号 context。

## 文档维护边界

- 任务文档不进入本入口层。
- 旧文档与代码事实冲突时，以代码事实为准。
- 不要把模块正文堆回 `README.md` 或 `../AGENTS.md`。
- 不要把归档的 `docs/archive/pre-uth-docs/docs-00-11/11_全链路执行流程图.md` 升级为默认必读。
- 本轮 `uth-docs` 不代表代码验证通过；需要验证时进入 `uth-review` 或 `uth-debug`。

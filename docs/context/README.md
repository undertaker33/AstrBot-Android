# 上下文索引

`docs/context/` 是 UTH 的当前事实层入口。代码事实优先，旧 Design、Feedback、Run Log、worker Prompt、LW 记录、归档文档、ADR 或旧模块文档不得直接当作当前事实。

当前状态：`full-project-docs-complete`。12 个编号模块上下文均已写入，旧模块文档和早期 seed 已从当前上下文层移入 archive。本轮 `uth-docs` 已按 `66eee69..4fedf19` 与当前工作区增量完成 scoped-sync；当前活动任务只保留在 `docs/current-state.md` 指定的任务包中。

## 当前可用文档

| Context | Status | Notes |
| --- | --- | --- |
| `00-模块拆分.md` | current | 已确认的模块拆分结果、模块队列、代码事实范围和收尾规则 |
| `docs-00-11-classification.md` | current | 旧 `docs/00` 到 `docs/11` 的归档与替代关系 |
| `old-doc-classification.md` | current | 接管前旧文档与协作材料分类 |
| `01-验证构建治理.md` | current | `verification-build-governance` 模块上下文 |
| `02-应用壳层与集成.md` | current | `app-shell-and-integration` 模块上下文 |
| `03-核心基础与数据库.md` | current | `core-foundation-and-db` 模块上下文 |
| `04-核心运行时.md` | current | `core-runtime` 模块上下文 |
| `05-下载与容器资产.md` | current | `download-and-container-assets` 模块上下文 |
| `06-Provider配置Bot与Persona.md` | current | `provider-config-bot-persona` 模块上下文 |
| `07-聊天与会话.md` | current | `chat-and-conversation` 模块上下文 |
| `08-QQ_NapCat_OneBot.md` | current | `qq-napcat-onebot` 模块上下文 |
| `09-插件平台.md` | current | `plugin-platform` 模块上下文 |
| `10-Cron运行时.md` | current | `cron-runtime` 模块上下文 |
| `11-资源设置备份.md` | current | `resource-settings-backup` 模块上下文 |
| `12-语音资产与音频.md` | current | `voiceasset-audio` 模块上下文 |

## 归档证据

| Archive | Status | Notes |
| --- | --- | --- |
| `../archive/pre-uth-docs/docs-00-11/` | historical evidence | 旧 `docs/00_*.md` 到 `docs/11_*.md`，已确认原路径存在于接管前备份 zip |
| `../archive/takeover-repair/` | historical evidence | 早期 `baseline.md`、`feature-modules.md`、`onboarding-followup-evidence.md`，不再代表当前完成态 |
| `../archive/context-pre-numbering/core-data-runtime.md` | historical evidence | 旧未编号 context 迁移证据 |

## 模块拆分队列

| Order | Package | Status | Context |
| --- | --- | --- | --- |
| 1 | verification-build-governance | completed | `01-验证构建治理.md` |
| 2 | app-shell-and-integration | completed | `02-应用壳层与集成.md` |
| 3 | core-foundation-and-db | completed | `03-核心基础与数据库.md` |
| 4 | core-runtime | completed | `04-核心运行时.md` |
| 5 | download-and-container-assets | completed | `05-下载与容器资产.md` |
| 6 | provider-config-bot-persona | completed | `06-Provider配置Bot与Persona.md` |
| 7 | chat-and-conversation | completed | `07-聊天与会话.md` |
| 8 | qq-napcat-onebot | completed | `08-QQ_NapCat_OneBot.md` |
| 9 | plugin-platform | completed | `09-插件平台.md` |
| 10 | cron-runtime | completed | `10-Cron运行时.md` |
| 11 | resource-settings-backup | completed | `11-资源设置备份.md` |
| 12 | voiceasset-audio | completed | `12-语音资产与音频.md` |

完整说明见 `00-模块拆分.md`。

## 读取规则

- 新任务先读 `../current-state.md`，再读本索引。
- 涉及模块治理时，按 `00-模块拆分.md` 找到对应编号 context。
- 旧 `docs/00` 到 `docs/11` 已归档；需要旧证据时先读 `docs-00-11-classification.md`，再按 archive 路径打开。
- 归档 seed、旧完成证据和旧未编号 context 只能提示历史方向，不能替代源码扫描。
- 若继续修正文档体系，优先使用编号 context 与代码事实；旧 seed 文档只能提示核对方向，不能替代源码扫描。

## 基线

- Initial baseline commit：`6b97b4a`
- Latest scoped sync：`66eee69..4fedf19` + current worktree docs/code facts
- Source：`uth-docs existing-project takeover repair` and 2026-05-22 scoped-sync
- Updated at：2026-05-22 18:30 +08:00
- Completion level：`full-project-docs-complete`

# 版本 Git 锚点索引

更新时间：2026-06-06 18:40 +08:00

## 定位

本文件是 changelog 的 git 锚点索引。发布正文位于仓库根目录 `changelogs/`。

## 证据来源

本次只读取以下 git / 文件证据：

- `git tag --list 'v*' --sort=version:refname`
- `git for-each-ref refs/tags --sort=version:refname --format=...`
- `git log --all --grep='^Release v[0-9]'`
- `git log --first-parent --reverse --date=short`
- `git log v0.7.5..HEAD --reverse --stat`
- `git tag --points-at`
- `git rev-parse --short HEAD master origin/master origin/codex/ColorOS16(RealmeUI7)`
- `git ls-remote --heads origin master codex/ColorOS16(RealmeUI7)`
- `git ls-remote --tags origin refs/tags/v1.0.1 refs/tags/v1.0.2 refs/tags/v1.0.3 refs/tags/v1.0.4 refs/tags/v1.1.0 refs/tags/v1.1.1 refs/tags/v1.1.2`
- `app/build.gradle.kts`
- `changelogs/v*.md`

本次没有执行 Gradle、测试、发布命令或 Git 写入。

## 命名规则

- `v0.1.x` 到 `v0.7.x`：按小版本聚合，每个小版本一份 `changelogs/v0.N.x.md`。
- `v0.7.5` 之后：每个 patch 版本一份独立正文，例如 `changelogs/v0.7.6.md`、`changelogs/v0.8.14.md`。
- 每份正文只保留 `新增` 和 `修复` 两个章节，内容面向用户，不写成 commit log。

## 当前覆盖

| 范围 | Git 锚点 | 正文文件 | 状态 |
| --- | --- | --- | --- |
| `v0.1.x` | tag `v0.1.0`、`v0.1.5` | `changelogs/v0.1.x.md` | 已有 |
| `v0.2.x` | tag `v0.2.4` 到 `v0.2.9` | `changelogs/v0.2.x.md` | 已有 |
| `v0.3.x` | tag `v0.3.0` 到 `v0.3.9` | `changelogs/v0.3.x.md` | 已有 |
| `v0.4.x` | tag `v0.4.0` 到 `v0.4.2`、`v0.4.5` 到 `v0.4.9` | `changelogs/v0.4.x.md` | 已有 |
| `v0.5.x` | tag `v0.5.0` 到 `v0.5.8` | `changelogs/v0.5.x.md` | 已有 |
| `v0.6.x` | tag `v0.6.0` 到 `v0.6.3`、`v0.6.5` 到 `v0.6.9` | `changelogs/v0.6.x.md` | 已有 |
| `v0.7.0` 到 `v0.7.5` | tag `v0.7.0` 到 `v0.7.5` | `changelogs/v0.7.x.md` | 已有 |
| `v0.7.6` 到 `v0.7.9` | tag `v0.7.6` 到 `v0.7.9` | `changelogs/v0.7.6.md` 到 `changelogs/v0.7.9.md` | 已有 |
| `v0.8.0` 到 `v0.8.14` | tag `v0.8.0` 到 `v0.8.14` | `changelogs/v0.8.0.md` 到 `changelogs/v0.8.14.md` | 已有 |
| `v0.9.0` | tag `v0.9.0`，release commit `a97d398`，merge commit `6078ff3` | `changelogs/v0.9.0.md` | 已有 |
| `v0.9.1` | release commit `e0d0822`，历史 `app/build.gradle.kts` 曾为 `versionName = "0.9.1"` | `changelogs/v0.9.1.md` | 已有正文，缺少 tag |
| `v0.9.2` | release commit `a9aace9` | `changelogs/v0.9.2.md` | 已有正文，缺少 tag |
| `v0.9.3` | tag `v0.9.3`，release commit `39a0c10` | `changelogs/v0.9.3.md` | 已有 |
| `v1.0.0` | tag `v1.0.0`，PR merge commit `c25812e`，release commit `2a2e718` | `changelogs/v1.0.0.md` | 已有 |
| `v1.0.1` | tag `v1.0.1`，merge commit `a2a7dcd`，release commit `da77e3e` | `changelogs/v1.0.1.md` | 已有 |
| `v1.0.2` | tag `v1.0.2`，merge commit `0cf61cc`，release commit `0308320` | `changelogs/v1.0.2.md` | 已有 |
| `v1.0.3` | tag `v1.0.3`，merge commit `0eafb33`，release commit `1d5abb7` | `changelogs/v1.0.3.md` | 已有 |
| `v1.0.4` | release commit `4fedf19` | `changelogs/v1.0.4.md` | 已有正文，缺少 tag |
| `v1.1.0` | tag `v1.1.0`，merge commit `988a523`，release commit `104eb3a` | `changelogs/v1.1.0.md` | 已有 |
| `v1.1.1` | release commit 已生成，PR / tag 待发布闭环补齐 | `changelogs/v1.1.1.md` | 已有正文 |
| `v1.1.2` | release commit 待生成，PR / tag 待发布闭环补齐 | `changelogs/v1.1.2.md` | 已有正文 |

## 当前缺口

- `v0.9.1` 和 `v0.9.2` 尚未发现对应 tag；当前只保留 release commit 与正文文件锚点。
- `v0.9.0` tag 锚在 PR merge commit `6078ff3`，不是直接锚在 `Release v0.9.0` commit `a97d398`；正式发布说明以 tag/merge 闭环为准。
- `v1.0.1` 到 `v1.0.3` 已有 tag 与 changelog 正文文件。
- `v1.0.4` 有 release commit `4fedf19` 和 `changelogs/v1.0.4.md`；本地和远端均未发现 `refs/tags/v1.0.4`。
- `v1.1.0` 已形成 tag / merge / changelog 闭环；当前 App 版本源为 `versionName = "1.1.0"`、`versionCode = 81`。
- `v1.1.1` 当前 release commit 已生成，准备通过 PR 合入 `master`；tag 与 merge 锚点待 PR 合并/发布流程补齐。
- `v1.1.2` 当前作为 PR 到 `master` 的版本提交准备中；tag 与 merge 锚点待 PR 合并/发布流程补齐。

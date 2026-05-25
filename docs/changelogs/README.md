# Changelog 索引

本目录只维护 changelog 的治理索引和锚点说明。发布 changelog 正文属于 `uth-git` 或发布流程。

## 当前索引

- `version-git-anchors.md`：以带版本号的 git tag / release commit 为锚点，说明根目录 `changelogs/v*.md` 的覆盖范围、命名规则和缺口。

## 发布正文位置

当前发布正文位于仓库根目录 `changelogs/`：

- `changelogs/v0.1.x.md`
- `changelogs/v0.2.x.md`
- `changelogs/v0.3.x.md`
- `changelogs/v0.4.x.md`
- `changelogs/v0.5.x.md`
- `changelogs/v0.6.x.md`
- `changelogs/v0.7.x.md`
- `changelogs/v0.7.6.md` 到 `changelogs/v0.7.9.md`
- `changelogs/v0.8.0.md` 到 `changelogs/v0.8.14.md`
- `changelogs/v0.9.0.md`
- `changelogs/v0.9.1.md`
- `changelogs/v0.9.2.md`
- `changelogs/v0.9.3.md`
- `changelogs/v1.0.0.md`
- `changelogs/v1.0.1.md`
- `changelogs/v1.0.2.md`
- `changelogs/v1.0.3.md`
- `changelogs/v1.0.4.md`
- `changelogs/v1.1.0.md`
- `changelogs/v1.1.1.md`

当前规则：`v0.1.x` 到 `v0.7.x` 使用小版本聚合文件；`v0.7.5` 之后每个 patch 版本使用独立文件。

## 维护规则

- 不把 changelog 文件当作 commit log。
- 先用带版本号的 git 记录确定锚点，再决定是否需要发布正文。
- `v0.9.1` 目前有 `Release v0.9.1` 提交和历史 `app/build.gradle.kts` 版本号证据；尚未发现对应 `v0.9.1` tag。
- `v0.9.2` 目前有 release commit `a9aace9` 和 `changelogs/v0.9.2.md`；尚未发现对应 `v0.9.2` tag。
- `v0.9.3` 和 `v1.0.0` 已有 tag 与正文文件。
- `v1.0.1`、`v1.0.2`、`v1.0.3` 已有 tag 与正文文件。
- `v1.0.4` 当前只发现 release commit `4fedf19` 和正文文件，未发现本地或远端 tag。tag 补齐属于 `uth-git` / 发布流程。
- `v1.1.0` 已有 tag `v1.1.0`、merge commit `988a523` 和正文文件 `changelogs/v1.1.0.md`。
- `v1.1.1` 已有正文文件 `changelogs/v1.1.1.md`，tag / merge 锚点待发布闭环补齐。

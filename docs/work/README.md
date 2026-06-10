# 正式任务包

正式任务包放在这里：

```text
docs/work/DYYMMDDXX-task-title/
├─ 00-DYYMMDDXX-design.md
├─ 10-DYYMMDDXX-T01-todo-task.md
├─ 11-DYYMMDDXX-T01-feedback-task.md
├─ prompts/
└─ runs/
```

`uth-dev`、`uth-debug`、`uth-review` 等场景按任务需要创建和维护。不要把长期当前事实写进任务包；长期事实应由 `uth-docs` 写入 `docs/context/` 或入口索引。

## 当前活动任务包

- `D26052102-插件API对齐AstrBot能力补齐/`：能力补齐已完成，当前设计内 `T01` 到 `T12` 无剩余非验证类 Todo，等待 Design-level 关闭或 Git 路由。
- `D26052201-插件任意会话发送能力设计/`：已完成设计，尚未拆 Todo、实现或验收。

已进入发布链或不再作为活动路由入口的任务包应移动到 `docs/archive/work/`。

## 最近归档

- `D26060701-地理围栏完整能力设计/` 已于 2026-06-10 移入 `docs/archive/work/`；当前事实见 `docs/context/13-地理围栏.md`。

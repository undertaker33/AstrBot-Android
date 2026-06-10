# D26060501-T02 NapCat 脚本条件绑定与启动降级 Feedback

更新时间：2026-06-05 19:09 +08:00

## 状态

- Scene：`uth-dev`
- Mode：`todo-implementation`
- Todo：`T02 NapCat 脚本条件绑定与启动降级`
- 结果：已实现
- Todo 级验收：按用户要求跳过独立 Todo 级验收
- Git 写入：无

## 变更文件

- `app/src/main/assets/runtime/scripts/start_napcat.sh`
- `app/src/test/java/com/elymbot/android/architecture/NapCatRuntimeScriptContractTest.kt`

## 实现摘要

- 新增 `add_optional_bind_if_readable` 和 `prepare_external_storage_bind_args`。
- 移除长运行 NapCat proot 命令中的无条件 `/sdcard`、`/storage/emulated/0:/sdcard`、`/storage/emulated/0:/storage/emulated/0` bind。
- 外部存储路径存在且可读时才加入 optional bind；不可用时写入 `start_napcat: external storage bind skipped`。
- proot smoke test 保持在 optional external storage bind 之前，基础容器 smoke 不依赖外部存储。

## 验证

- 红灯：`.\gradlew.bat :app:testDebugUnitTest --tests "*NapCatRuntimeScriptContractTest*" --console=plain --no-daemon --stacktrace`
  - 结果：预期失败，当前脚本仍无条件 bind 外部存储。
- 绿灯：同一命令
  - 结果：`BUILD SUCCESSFUL in 33s`。
- 回归：`.\gradlew.bat :core:runtime-container:testDebugUnitTest --tests "*ContainerRuntimeScriptsTest*" --console=plain --no-daemon --stacktrace`
  - 结果：`BUILD SUCCESSFUL in 38s`。
- 包级过滤回归：`.\gradlew.bat :app:testDebugUnitTest --tests "*NapCat*" --tests "*Container*" --tests "*RuntimeBridge*" --console=plain --no-daemon --stacktrace`
  - 结果：`BUILD SUCCESSFUL in 1m 4s`。
  - 日志：`build/uth-d26060501/02-app-napcat-container-runtimebridge-testDebugUnitTest.log`
  - warning/deprecated/exception/failed 扫描：0 / 0 / 0 / 0。

## 未覆盖项

- 未在真实设备验证 `/sdcard`、`/storage/emulated/0` 不同 ROM 权限表现。
- 未执行真实 NapCat 安装登录端到端流程。

## 风险与回滚

- 某些 ROM 可能只暴露 `/sdcard` 而没有 `/storage/emulated/0`；脚本已在 fallback 分支尝试 `/sdcard` 到 `/sdcard`。
- 回滚方式：恢复 `start_napcat.sh` 的旧 bind 命令并删除合同测试，但这会重新引入外部存储硬依赖。

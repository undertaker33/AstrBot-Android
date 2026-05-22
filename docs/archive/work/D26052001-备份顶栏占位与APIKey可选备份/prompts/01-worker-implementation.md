# Worker Prompt：D26052001-T01 备份顶栏占位与 API-Key 可选备份

你不是代码库里唯一的执行者。工作树已有其他未提交改动，尤其是 D26051901 插件管理员权限相关改动。不要回退、重排或“清理”你没有负责的改动；如果必须编辑同一文件，只做本任务需要的最小增量。

## 场景

- Scene: `uth-dev`
- Mode: `formal-dev`
- Task package: `docs/archive/work/D26052001-备份顶栏占位与APIKey可选备份/`
- Design: `00-D26052001-design.md`
- Todo: `10-D26052001-T01-todo-备份顶栏占位与APIKey可选备份.md`
- Git writes: forbidden

## 目标

实现以下需求：

1. 增加备份三级页顶部占位，避免第一张卡片被全局顶栏遮挡。
2. 全局顶栏在各备份模块页显示对应模块标题，而不是全部显示“数据备份”。
3. 完整备份和模型备份点击“立即备份”后先弹确认弹窗，弹窗中有默认关闭的“是否备份 API-Key”开关；开关打开时才把 Provider `apiKey` 写入备份。

## 允许写入范围

- `feature/settings/presentation/src/main/java/com/elymbot/android/ui/settings/SettingsSubPageScaffold.kt`
- `feature/settings/presentation/src/main/java/com/elymbot/android/ui/settings/BackupScreen.kt`
- `feature/settings/presentation/src/main/java/com/elymbot/android/ui/settings/SettingsBackupViewModel.kt`
- `feature/settings/api/src/main/java/com/elymbot/android/feature/settings/api/SettingsBackupPort.kt`
- `app/src/main/java/com/elymbot/android/feature/settings/AppSettingsBackupPortAdapter.kt`
- `app/src/main/java/com/elymbot/android/core/db/backup/AppBackupRepository.kt`
- `core/ui/src/main/java/com/elymbot/android/ui/app/TopBarRegistration.kt`
- `app/src/main/java/com/elymbot/android/ui/app/ElymBotApp.kt`
- `app/src/main/java/com/elymbot/android/ui/app/GlobalTopBar.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-zh/strings.xml`
- 直接相关测试文件，优先放在 `app/src/test/java/com/elymbot/android/data/backup/`

## 关键约束

- 不修改数据库 schema / migration。
- 不修改 `docs/current-state.md`。
- 不扩大 backup 覆盖范围到 Resource Center、Cron 或 Plugin。
- 不改变旧备份导入兼容性：旧 manifest 里的 `apiKey` 仍应能导入。
- 默认行为必须是“不备份 API-Key”。
- 只有完整备份和模型备份弹出 API-Key 开关；Bot、Persona、Config、Conversation、TTS 不弹。
- 对 `app/src/main/java/com/elymbot/android/core/db/backup/AppBackupRepository.kt` 的既有未提交改动要保留，尤其不要删除 `pluginCommandsAdminOnlyEnabled` 的备份写出。

## 实现提示

- `SettingsSubPageScaffold` 应改为 `SecondaryTopBarPlaceholder + WindowInsets.safeDrawing` 模式，不再自己画 `Row + IconButton + Text`。
- `SecondaryTopBarStrings` 增加 backup 子标题字段，`ElymBotApp` 从现有 `backup_module_*_title` 资源填充。
- `GlobalTopBar.fallbackSecondaryTopBarSpecForRoute(...)` 对 backup route 逐项返回模块标题。
- 可新增 `AppBackupCreateOptions(includeProviderApiKeys: Boolean = false)` 或等价参数对象，避免多层布尔参数含义不清。
- `providerToJson(profile, includeApiKey)`：关闭时 `.put("apiKey", "")`，开启时 `.put("apiKey", profile.apiKey)`。
- 测试至少覆盖：默认 full/module provider 备份不包含真实 API-Key；开启 include 后包含真实 API-Key。

## 验证

至少尝试运行：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "*Backup*" --console=plain --no-daemon --stacktrace
```

如果无法运行或失败，报告准确原因和下一步建议，不要声称通过。

## 输出

最终回复必须包含：

- 状态：DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED
- 修改的文件列表
- 验证命令与结果
- 任何未完成项或风险

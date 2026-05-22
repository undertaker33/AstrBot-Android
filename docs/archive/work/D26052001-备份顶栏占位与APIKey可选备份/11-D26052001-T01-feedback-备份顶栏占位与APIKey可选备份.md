# D26052001-T01 Feedback：备份顶栏占位与 API-Key 可选备份

## 结论

- 状态：已完成。
- 场景：`uth-dev` formal-dev，worker 实施，主窗口复核修正，evaluator 只读验收。
- Git 写入：未执行。

## 已完成内容

1. `SettingsSubPageScaffold` 改为复用全局 secondary top bar 占位：
   - `topBar = { SecondaryTopBarPlaceholder() }`
   - `contentWindowInsets = WindowInsets.safeDrawing`
   - 不再绘制页面内部重复 back/title 顶栏。
2. 全局顶栏按备份 route 显示模块标题：
   - `backup-hub` -> 数据备份
   - `backup/bots` -> Bot 备份
   - `backup/models` -> 模型备份
   - `backup/personas` -> 人格备份
   - `backup/conversations` -> 对话备份
   - `backup/configs` -> 配置备份
   - `backup/tts` -> TTS 音色备份
   - `backup/full` -> 完整备份
3. 完整备份与模型备份点击“立即备份”后先打开确认弹窗。
   - 弹窗内保留一个默认关闭的“是否备份 API-Key”开关。
   - 关闭时 Provider JSON 的 `apiKey` 写为空字符串。
   - 开启时 Provider JSON 的 `apiKey` 写出原值。
4. `includeProviderApiKeys` 已从 UI 透传至 settings API、app adapter、`AppBackupService` 与 `AppBackupRepository`。
5. 保留了本轮开始前已有的 D26051901 `pluginCommandsAdminOnlyEnabled` / 管理员插件指令相关改动。

## 主要改动文件

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
- `app/src/test/java/com/elymbot/android/data/backup/AppBackupApiKeyExportTest.kt`
- `app/src/test/java/com/elymbot/android/ui/app/GlobalTopBarTest.kt`

## 验证

已运行：

```powershell
.\gradlew.bat :feature:settings:presentation:compileDebugKotlin --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Backup*" --tests "com.elymbot.android.ui.app.GlobalTopBarTest" --console=plain --no-daemon --stacktrace
.\gradlew.bat moduleSettingsCheck architectureCheck --console=plain --no-daemon --stacktrace
.\gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

日志：

- `build/reports/D26052001-settings-presentation-compileDebugKotlin.log`：`BUILD SUCCESSFUL in 36s`
- `build/reports/D26052001-backup-api-key-app-testDebugUnitTest-final.log`：`BUILD SUCCESSFUL in 1m 55s`
- `build/reports/D26052001-module-settings-architecture-check.log`：`BUILD SUCCESSFUL in 2m 17s`
- `build/reports/D26052001-clean-assembleDebug-final.log`：`BUILD SUCCESSFUL in 3m 47s`

关键日志扫描：

- `warning`：0
- `deprecated`：0
- `exception`：0
- `BUILD FAILED`：0

## Evaluator 验收

只读 evaluator 结论：`PASS`。

验收覆盖：

- 顶部占位已落地。
- 备份子路由标题映射完整。
- `backup/models` 当前仍正确对应 `SettingsBackupModuleKind.PROVIDERS`。
- 完整备份和模型备份弹窗、默认关闭开关、参数透传、repository 写出逻辑均满足要求。
- D26051901 管理员插件指令改动未被覆盖。
- 验证日志均为成功，扫描无 warning / deprecated / exception。

## 残余风险

- 本轮未做真机或模拟器截图验收；顶栏遮挡通过代码结构、编译、单测、构建和 evaluator 只读检查确认。
- 工作区仍包含本轮开始前已有的 D26051901 等未提交改动，本任务未清理或回退这些改动。

# 开发

## 环境

- JDK 17：根 `build.gradle.kts` 设置 JVM target `17`。
- Android SDK：根 `build.gradle.kts` 设置 `compileSdk=36`、`compileSdkMinor=1`、`minSdk=29`；`app/build.gradle.kts` 设置 `targetSdk=36`。
- Android Gradle Plugin：根 `build.gradle.kts` 使用 `9.2.0`。
- Gradle Wrapper：`gradle/wrapper/gradle-wrapper.properties` 使用 Gradle `9.4.1`。
- Kotlin：根 `build.gradle.kts` 使用 `2.2.10`，Android 模块走 AGP 9 built-in Kotlin 路线。
- KSP：根 `build.gradle.kts` 使用 `2.3.8`。
- Hilt：根 `build.gradle.kts` 使用 `2.59.2`，`app/build.gradle.kts` 应用 Hilt plugin。
- Room：`app/build.gradle.kts` 使用 `2.8.4`，`core/db/.../ElymBotDatabase.kt` 当前 DB version 为 `24`。
- App 版本：`app/build.gradle.kts` 当前为 `versionName = "1.1.2"`、`versionCode = 83`。

## Run

```bash
```

## Build

```bash
./gradlew.bat clean assembleDebug --console=plain --no-daemon --stacktrace
```

模块组构建入口见 `docs/module-build-guide.md`，根任务包括 `allModuleGroupsBuild`、`allModuleGroupsCheck` 和各 `module<ModuleName>Build/Check`。

生成产物默认写入 `artifacts/` 或各模块 `build/` 下。`artifacts/` 已由 `.gitignore` 排除，CI 的 `Verify generated artifacts are not tracked` 步骤会阻断 `artifacts/**` 被提交。

## Test

```bash
./gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

关键验证入口：

```bash
./gradlew.bat :architecture-tests:test --console=plain --no-daemon --stacktrace
./gradlew.bat :build-logic:check --console=plain --no-daemon --stacktrace
./gradlew.bat :app:testDebugUnitTest --console=plain --no-daemon --stacktrace
./gradlew.bat :app:assembleDebug --console=plain --no-daemon --stacktrace
```

## Notes

- `uth-docs` 场景只做文档治理，不运行 Gradle 命令；构建、单测或 APK 通过结论必须来自 `uth-dev`、`uth-debug`、`uth-review` 或既有可追溯验证记录。
- `a9aace9^..HEAD` 范围后，`v0.9.3` 已完成 AGP 9 构建链升级，`v1.0.0` 已完成 ElymBot 包名与应用标识统一；当前 `HEAD` 为 `b069daf release: v1.1.2`。本地已发现 `v1.0.1`、`v1.0.2`、`v1.0.3`、`v1.1.0` tag；未发现本地 `v1.0.4`、`v1.1.1`、`v1.1.2` tag。
- 涉及入口、Hilt、依赖图、Manifest、plugin runtime、QQ、Cron、backup 等跨模块边界时，至少补 `architectureCheck`，交付前按仓库规则跑 `clean assembleDebug`。

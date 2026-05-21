# D26052102-T03 Provider 与模型只读查询 Todo

更新时间：2026-05-21 10:46 +08:00

## 场景

- Scene：`uth-dev`
- Mode：`todo-breakdown`
- 来源 Design：`docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
- 前置 Todo：`T01 宿主 API 异步桥与权限底座`
- 执行方式：待用户决定；本轮不写 worker prompt，不实现代码。

## 目标

提供插件侧只读查询 Provider / Model 的能力，为后续 `hostApi.callLlm` 提供安全的模型选择输入，同时不泄露 API key、base URL secret、headers secret 或 credential。

## 非目标

- 不允许插件新增、编辑、删除 Provider。
- 不暴露 provider credential、secret headers、raw config snapshot。
- 不实现 LLM 调用；`callLlm` 属于 `T06`。
- 不实现 provider UI。

## 允许修改范围

- `feature/plugin/api/src/main/java/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/**`
- `feature/provider/api/src/main/java/**`，仅限只读 port / DTO。
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `feature/plugin/runtime/src/test/java/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`
- `app/src/test/java/com/elymbot/android/architecture/**`

## 必须实现

1. Host API
   - 提供 `hostApi.providers.list()`。
   - 提供 `hostApi.providers.models({ providerId })`。
   - 不提供 AstrBot 风格别名。

2. 只读 DTO
   - Provider 返回 `providerId`、`displayName`、`enabled`、`capabilities`、`defaultModelId`、`modelCount`。
   - Model 返回 `modelId`、`displayName`、`capabilities`、`contextWindow`、`supportsToolCalling`、`supportsStreaming`。
   - 字段为空时返回空字符串、空列表或 `null`，不得返回内部 entity。

3. 权限
   - 使用 `provider_read` permission。
   - 权限拒绝返回 structured error。

4. 宿主 port
   - 通过 `feature/provider/api` 或 app-integration adapter 暴露只读快照。
   - 不从 plugin runtime 直接读取 Room DAO。
   - 不复用 static repository facade。

5. 审计
   - 记录 provider 查询次数和失败原因。
   - 日志不包含 secret。

## 必须补测试

- `PluginV2ProviderReadApiTest`
  - list 返回 enabled provider 摘要。
  - models 返回指定 provider 的模型摘要。
  - 未授权时拒绝。
  - provider 不存在时返回 structured error 或空结果，语义固定。
  - 结果不包含 `apiKey`、`baseUrl`、`headers`、`credential` 等敏感字段。

- provider api adapter test
  - fake provider store 可生成只读 snapshot。
  - app-integration Hilt wiring 可提供 port。

- architecture contract
  - plugin runtime 不直接依赖 provider data implementation。
  - 不新增 static repository usage allowlist。

## 验证命令

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*Provider*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*Plugin*" --tests "*Provider*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

## 完成判定

- 插件可安全读取 provider/model 摘要。
- 返回数据不含任何 secret。
- 后续 `T06` 可直接复用该只读 port 做模型选择和校验。

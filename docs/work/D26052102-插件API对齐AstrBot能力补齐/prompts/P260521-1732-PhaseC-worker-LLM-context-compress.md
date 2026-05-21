# P260521-1732 Phase C worker：T06/T07 LLM 与上下文压缩

## 场景

- Scene：`uth-dev`
- Mode：`formal-dev`
- Task package：`docs/work/D26052102-插件API对齐AstrBot能力补齐/`
- Phase：Phase C
- Todo：`T06 直接 LLM 调用`、`T07 上下文压缩落地`
- Worker 角色：实现 worker
- Git 写入：禁止。不得 commit、push、tag、merge、rebase、checkout 或 reset。

你不是代码库里唯一的 worker。不要回退或覆盖其他人的编辑；如果看到非本任务范围的变更，保留并绕开。当前窗口是总控，负责最终整合、Feedback/current-state 和 phase evaluator。

## 必读输入

1. `AGENTS.md`
2. `docs/README.md`
3. `docs/current-state.md`
4. `docs/context/09-插件平台.md`
5. `docs/context/06-Provider配置Bot与Persona.md`
6. `docs/context/07-聊天与会话.md`
7. `docs/work/D26052102-插件API对齐AstrBot能力补齐/00-D26052102-design.md`
8. `docs/work/D26052102-插件API对齐AstrBot能力补齐/60-D26052102-T06-todo-直接LLM调用.md`
9. `docs/work/D26052102-插件API对齐AstrBot能力补齐/70-D26052102-T07-todo-上下文压缩落地.md`

## 允许写入范围

- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/**`
- `feature/plugin/runtime/src/main/java/com/elymbot/android/feature/plugin/runtime/toolsource/ContextStrategyToolSourceProvider.kt`
- `feature/plugin/runtime/src/test/java/**`
- `app-integration/src/main/java/com/elymbot/android/di/hilt/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/**`
- `app/src/test/java/com/elymbot/android/runtime/plugin/toolsource/**`
- `app/src/test/java/com/elymbot/android/architecture/**`

## 禁止范围

- 不新增 AstrBot 风格别名。
- 不开放 `runPluginHooks` 开关。
- 不实现 streaming、Agent、Web API、平台 adapter 注册、DB 直连、文本转图或 HTML 渲染。
- 不让插件读取 provider secret、baseUrl secret、API key 或内部 credential。
- 不恢复 static repository facade、service locator、手写 runtime subgraph 或生产 global registry。
- 不修改 `docs/context/**`；如果实现改变当前事实，回传 `Needs uth-docs scoped-sync`。

## 实施要求

按 TDD：先写能失败的测试，再写生产代码，再跑到 green。

### T06

实现 canonical JS API：

- `hostApi.callLlm(request)`
- `hostApi.llm.generate(request)`

实现 Kotlin host API：

- 新增或等价实现 `PluginV2HostLlmApi` / `PluginV2HostLlmPort`。
- 支持 `providerId`、`modelId`、`messages`、`systemPrompt`、`temperature`、`topP`、`maxTokens`、`tools`。
- message role 仅允许 `system`、`user`、`assistant`；插件直接写入 `tool` role 必须拒绝。
- `temperature`、`topP`、`maxTokens` 必须做范围校验。
- 默认绕过插件 LLM hook pipeline；不要经 `PluginV2LlmPipelineCoordinator.runLlmPipeline`。
- 返回 `text`、`finishReason`、`providerId`、`modelId`、`usage`、`toolCalls`。
- 权限使用 `call_model`。

### T07

实现：

- `hostApi.context.compress(request)`
- `context_compress` permission。
- 只允许当前会话。
- `ContextStrategyToolSourceProvider.invoke()` 不再返回 `context_compress_not_implemented`。
- `contextLimitStrategy != "llm_compress"` 时保持不可用 / denied 语义。
- 压缩执行复用 T06 host LLM port，使用固定宿主压缩 prompt，默认绕过插件 LLM hooks。
- 输入消息来自 T05 history port，不直连 DB。
- 返回 `summary`、`sourceMessageCount`、`truncated`、`usage`。

## 最小生产落点提示

已有 Phase A/B 可复用：

- `PluginV2HostApiFoundation.kt`：permission / async / audit。
- `PluginV2ProviderReadApi.kt`：provider 只读模型。
- `PluginV2MessageSendApi.kt`、`PluginV2ConversationHistoryApi.kt`：当前会话约束和 port 风格。
- `PluginV2BootstrapHostApi.kt` + `ExternalPluginScriptExecutor.kt`：JS bridge surface。
- `PluginHostCapabilityModule.kt`：Hilt provider wiring。
- `MessageConverters.kt`：`PluginProviderMessageDto` 与 conversation message 映射。
- `ContextStrategyToolSourceProvider.kt`：当前 TODO 点。

## 必补测试

优先补：

- `PluginV2HostLlmApiTest`
- `PluginV2ContextCompressApiTest`
- `ContextStrategyToolSourceProviderTest`
- QuickJS capability test 中补 `hostApi.callLlm` / `hostApi.llm.generate` / `hostApi.context.compress` 可 await。

至少覆盖：

- fake provider 返回 text 与 usage。
- 未授权拒绝。
- providerId/modelId 不存在拒绝。
- 参数范围非法拒绝。
- role=tool 拒绝。
- provider secret 不进入 JS 返回值或日志。
- `compress_context` 成功、未激活不可用、fake LLM 失败 structured error。
- 跨会话 context compress 被拒绝。

## 建议验证命令

```powershell
.\gradlew.bat :feature:plugin:runtime:testDebugUnitTest --tests "*Llm*" --tests "*Context*Compress*" --console=plain --no-daemon --stacktrace
.\gradlew.bat :app:testDebugUnitTest --tests "*ContextStrategyToolSourceProviderTest*" --tests "*Plugin*" --tests "*Provider*" --console=plain --no-daemon --stacktrace
.\gradlew.bat architectureCheck --console=plain --no-daemon --stacktrace
```

如果时间不足，至少运行最聚焦的新增测试并清楚回报未验证项；不要声称未运行的命令已通过。

## 回传格式

请用以下格式回复：

- Status：`DONE` / `DONE_WITH_CONCERNS` / `NEEDS_CONTEXT` / `BLOCKED`
- Changed files：逐项列出
- Implementation summary：简述实现
- Tests run：命令 + 结果
- TDD evidence：列出先失败后通过的测试，若没有做到，明确说明
- Risks / remaining work：列出风险
- Needs uth-docs scoped-sync：yes/no
## Rework 1 - 2026-05-21 18:20

Reason:

Coordinator review found Phase C is close but not ready for evaluator acceptance.

Additional instructions:

- Add real QuickJS capability coverage for `hostApi.callLlm(request)`, `hostApi.llm.generate(request)`, and `hostApi.context.compress(request)`. The current `PluginV2HostApiQuickJsCapabilitiesTest` still only covers provider/message/conversation APIs.
- Ensure `hostApi.llm.generate` is audited/reported as `hostApi.llm.generate`, not as `hostApi.callLlm`. Keep both APIs on the same permission `call_model`.
- Verify `ContextStrategyToolSourceProvider` production injection reaches the Hilt-created `PluginV2ContextCompressApi`. `FutureToolSourceRegistry.empty()` may remain unavailable for context compression, but production `FutureToolSourceRegistry` must not instantiate `ContextStrategyToolSourceProvider` without the compressor.
- Keep the write scope exactly inside the original Phase C allowed files. Do not start Phase D/E work.

Validation:

- Run the focused QuickJS capability test that covers all three new JS surfaces.
- Rerun focused T06/T07 tests if touched.
- Do not claim full build or architecture gates unless you actually rerun them.

Return requirements:

- Use the original return format.
- Include whether each rework bullet is resolved.

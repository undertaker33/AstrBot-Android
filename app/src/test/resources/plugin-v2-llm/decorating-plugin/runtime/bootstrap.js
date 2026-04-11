export default async function bootstrap(hostApi) {
  await Promise.resolve();

  if (!hostApi || typeof hostApi.registerLlmHook !== "function") {
    return;
  }

  hostApi.registerLlmHook({
    hook: "on_decorating_result",
    key: "decorating.result",
    priority: 180,
    metadata: {
      fixture: "plugin-v2-llm/decorating-plugin",
      behavior: "append_decorated_text",
      mutation: "result_text_suffix",
    },
  });
}

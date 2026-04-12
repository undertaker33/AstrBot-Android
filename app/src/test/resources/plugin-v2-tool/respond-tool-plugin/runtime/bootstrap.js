export default async function bootstrap(hostApi) {
  await Promise.resolve();

  if (!hostApi || typeof hostApi.registerLlmHook !== "function") {
    return;
  }

  hostApi.registerLlmHook({
    key: "respond-tool.respond",
    registrationKey: "respond-tool.respond",
    hook: "on_llm_tool_respond",
    priority: 150,
    metadata: {
      fixture: "plugin-v2-tool/respond-tool-plugin",
      behavior: "post_tool_hook",
    },
    handler: function onLlmToolRespond() {},
  });
}

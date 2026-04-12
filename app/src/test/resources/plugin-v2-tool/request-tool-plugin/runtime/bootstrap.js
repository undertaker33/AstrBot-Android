export default async function bootstrap(hostApi) {
  await Promise.resolve();

  if (!hostApi || typeof hostApi.registerTool !== "function") {
    return;
  }

  const metadata = typeof hostApi.getPluginMetadata === "function"
    ? hostApi.getPluginMetadata()
    : { pluginId: "com.astrbot.samples.request-tool-plugin" };
  const pluginId = metadata && metadata.pluginId
    ? metadata.pluginId
    : "com.astrbot.samples.request-tool-plugin";

  hostApi.registerTool({
    pluginId,
    name: "quick_reply",
    description: "QuickJS fixture tool that returns a short reply.",
    visibility: "LLM_VISIBLE",
    sourceKind: "PLUGIN_V2",
    inputSchema: {
      type: "object",
      properties: {
        topic: { type: "string" },
      },
      required: ["topic"],
      additionalProperties: false,
    },
    metadata: {
      fixture: "plugin-v2-tool/request-tool-plugin",
      behavior: "tool_registration",
    },
  }, function quickReplyTool() {});

  if (typeof hostApi.registerLlmHook === "function") {
    hostApi.registerLlmHook({
      key: "request-tool.using",
      registrationKey: "request-tool.using",
      hook: "on_using_llm_tool",
      priority: 200,
      metadata: {
        fixture: "plugin-v2-tool/request-tool-plugin",
        behavior: "pre_tool_hook",
      },
      handler: function onUsingLlmTool() {},
    });
  }
}

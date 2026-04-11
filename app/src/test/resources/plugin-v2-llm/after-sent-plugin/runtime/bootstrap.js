export default async function bootstrap(hostApi) {
  await Promise.resolve();

  if (!hostApi || typeof hostApi.registerLlmHook !== "function") {
    return;
  }

  hostApi.registerLlmHook({
    hook: "after_message_sent",
    key: "after-sent.observe",
    priority: 90,
    metadata: {
      fixture: "plugin-v2-llm/after-sent-plugin",
      behavior: "read_only_observer",
      note: "after_sent_view_observation",
    },
  });
}

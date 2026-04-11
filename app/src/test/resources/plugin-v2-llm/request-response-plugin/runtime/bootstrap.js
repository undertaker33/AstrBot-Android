export default async function bootstrap(hostApi) {
  await Promise.resolve();

  if (!hostApi || typeof hostApi.registerLlmHook !== "function") {
    return;
  }

  hostApi.registerLlmHook({
    hook: "on_llm_request",
    key: "request-response.request",
    priority: 220,
    metadata: {
      fixture: "plugin-v2-llm/request-response-plugin",
      behavior: "request_mutation",
      mutation: "selected_provider_and_prompt",
    },
  });

  hostApi.registerLlmHook({
    hook: "on_llm_response",
    key: "request-response.response",
    priority: 120,
    metadata: {
      fixture: "plugin-v2-llm/request-response-plugin",
      behavior: "response_observer",
      note: "response_stage_observation_only",
    },
  });
}

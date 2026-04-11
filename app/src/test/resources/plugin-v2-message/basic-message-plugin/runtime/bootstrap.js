export default async function bootstrap(hostApi) {
  await Promise.resolve();

  if (!hostApi) return;

  if (typeof hostApi.registerMessageHandler === "function") {
    hostApi.registerMessageHandler({
      stage: "message",
      key: "basic-message.message",
      metadata: {
        fixture: "plugin-v2-message/basic-message-plugin",
      },
      filters: [
        "event_message_type:group",
      ],
    });
  }

  if (typeof hostApi.registerCommandHandler === "function") {
    hostApi.registerCommandHandler({
      stage: "command",
      key: "basic-message.command",
      command: "echo",
      metadata: {
        fixture: "plugin-v2-message/basic-message-plugin",
      },
      filters: [
        "platform_adapter_type:onebot",
        "custom_filter:allow",
      ],
    });
  }

  if (typeof hostApi.registerRegexHandler === "function") {
    hostApi.registerRegexHandler({
      stage: "regex",
      key: "basic-message.regex",
      pattern: "echo",
      flags: ["IGNORE_CASE"],
      metadata: {
        fixture: "plugin-v2-message/basic-message-plugin",
      },
      filters: [
        "permission_type:net.access",
      ],
    });
  }
}

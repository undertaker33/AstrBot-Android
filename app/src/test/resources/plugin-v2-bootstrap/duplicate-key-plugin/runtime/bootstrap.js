export default function bootstrap(hostApi) {
  if (hostApi && typeof hostApi.registerMessageHandler === "function") {
    hostApi.registerMessageHandler({
      stage: "message",
      key: "duplicate.echo",
      metadata: {
        fixture: "duplicate-key-plugin",
        order: "first",
      },
    });
    hostApi.registerMessageHandler({
      stage: "message",
      key: "duplicate.echo",
      metadata: {
        fixture: "duplicate-key-plugin",
        order: "second",
      },
    });
  }
}

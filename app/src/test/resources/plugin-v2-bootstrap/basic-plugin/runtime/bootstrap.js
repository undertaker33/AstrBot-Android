export default async function bootstrap(hostApi) {
  await Promise.resolve();
  if (hostApi && typeof hostApi.registerMessageHandler === "function") {
    hostApi.registerMessageHandler({
      stage: "message",
      key: "basic.echo",
      metadata: {
        fixture: "basic-plugin",
      },
    });
  }
}

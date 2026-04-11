export default async function bootstrap(hostApi) {
  await Promise.resolve();

  if (!hostApi) return;

  if (typeof hostApi.onPluginLoaded === "function") {
    hostApi.onPluginLoaded({
      key: "lifecycle.loaded",
      metadata: {
        fixture: "plugin-v2-message/lifecycle-plugin",
      },
    });
  }

  if (typeof hostApi.onPluginUnloaded === "function") {
    hostApi.onPluginUnloaded({
      key: "lifecycle.unloaded",
      metadata: {
        fixture: "plugin-v2-message/lifecycle-plugin",
      },
    });
  }

  if (typeof hostApi.onPluginError === "function") {
    hostApi.onPluginError({
      key: "lifecycle.error",
      metadata: {
        fixture: "plugin-v2-message/lifecycle-plugin",
      },
    });
  }
}

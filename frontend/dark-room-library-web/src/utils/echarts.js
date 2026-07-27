const runtimePromises = new Map();

function loadRuntime(type, loader) {
  if (!runtimePromises.has(type)) {
    const promise = loader().then((module) => module.default).catch((error) => {
      runtimePromises.delete(type);
      throw error;
    });
    runtimePromises.set(type, promise);
  }
  return runtimePromises.get(type);
}

export const loadLineEcharts = () => loadRuntime(
  "line", () => import("@/utils/echartsLineRuntime.js")
);

export const loadBarEcharts = () => loadRuntime(
  "bar", () => import("@/utils/echartsBarRuntime.js")
);

export const loadPieEcharts = () => loadRuntime(
  "pie", () => import("@/utils/echartsPieRuntime.js")
);

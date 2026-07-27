let runtimePromise;

export function loadEcharts() {
  if (!runtimePromise) {
    runtimePromise = import("@/utils/echartsRuntime.js").then(
      (module) => module.default
    ).catch((error) => {
      runtimePromise = undefined;
      throw error;
    });
  }
  return runtimePromise;
}

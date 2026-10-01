export function returnToPreviousOr(router, fallback, historyState = globalThis.history?.state) {
  const back = historyState?.back;
  if (typeof back === "string" && back.startsWith("/") && !back.startsWith("//")) {
    router.back();
    return "history";
  }
  router.replace(fallback);
  return "fallback";
}

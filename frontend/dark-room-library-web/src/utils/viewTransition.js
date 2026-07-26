function prefersReducedMotion() {
  return typeof window !== "undefined"
    && typeof window.matchMedia === "function"
    && window.matchMedia("(prefers-reduced-motion: reduce)").matches;
}

export async function runViewTransition(update) {
  if (typeof update !== "function") return;

  if (
    typeof document === "undefined"
    || typeof document.startViewTransition !== "function"
    || prefersReducedMotion()
  ) {
    await update();
    return;
  }

  let transition;
  try {
    transition = document.startViewTransition(update);
  } catch {
    await update();
    return;
  }

  try {
    await transition.finished;
  } catch (error) {
    if (error?.name !== "AbortError") throw error;
  }
}

export { prefersReducedMotion };

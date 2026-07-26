import { prefersReducedMotion, runViewTransition } from "../../src/utils/viewTransition.js";

describe("viewTransition", () => {
  const originalWindow = global.window;
  const originalDocument = global.document;

  beforeEach(() => {
    global.window = {
      matchMedia: vi.fn().mockReturnValue({ matches: false }),
    };
    global.document = {};
  });

  afterEach(() => {
    if (originalWindow) {
      global.window = originalWindow;
    } else {
      delete global.window;
    }
    if (originalDocument) {
      global.document = originalDocument;
    } else {
      delete global.document;
    }
  });

  test("falls back to a direct update when the API is unavailable", async () => {
    delete document.startViewTransition;
    window.matchMedia = vi.fn().mockReturnValue({ matches: false });
    const update = vi.fn();

    await runViewTransition(update);

    expect(update).toHaveBeenCalledTimes(1);
  });

  test("skips motion when reduced motion is requested", async () => {
    window.matchMedia = vi.fn().mockReturnValue({ matches: true });
    document.startViewTransition = vi.fn();
    const update = vi.fn();

    await runViewTransition(update);

    expect(prefersReducedMotion()).toBe(true);
    expect(update).toHaveBeenCalledTimes(1);
    expect(document.startViewTransition).not.toHaveBeenCalled();
  });

  test("uses the browser transition and waits for completion", async () => {
    window.matchMedia = vi.fn().mockReturnValue({ matches: false });
    const update = vi.fn();
    document.startViewTransition = vi.fn((callback) => ({
      finished: Promise.resolve().then(callback),
    }));

    await runViewTransition(update);

    expect(document.startViewTransition).toHaveBeenCalledTimes(1);
    expect(update).toHaveBeenCalledTimes(1);
  });
});

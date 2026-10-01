import { describe, expect, it, vi } from "vitest";

import { returnToPreviousOr } from "@/utils/navigation.js";

describe("returnToPreviousOr", () => {
  it("returns through app history when a previous route is recorded", () => {
    const router = { back: vi.fn(), replace: vi.fn() };

    expect(returnToPreviousOr(router, "/readerRoom", { back: "/readerRoom" }))
      .toBe("history");
    expect(router.back).toHaveBeenCalledOnce();
    expect(router.replace).not.toHaveBeenCalled();
  });

  it("uses the reader-room fallback for a direct deep link", () => {
    const router = { back: vi.fn(), replace: vi.fn() };

    expect(returnToPreviousOr(router, "/readerRoom", { back: null }))
      .toBe("fallback");
    expect(router.replace).toHaveBeenCalledWith("/readerRoom");
    expect(router.back).not.toHaveBeenCalled();
  });
});

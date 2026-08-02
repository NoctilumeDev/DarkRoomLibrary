import { describe, expect, it } from "vitest";
import { hasOlderMessages } from "../../src/utils/messagePagination.js";

describe("procurement message pagination", () => {
  it("does not offer another page when the total exactly fills the batch", () => {
    expect(hasOlderMessages(50, 50)).toBe(false);
  });

  it("offers another page only when matching messages remain", () => {
    expect(hasOlderMessages(51, 50)).toBe(true);
    expect(hasOlderMessages(10, 10)).toBe(false);
  });

  it("fails closed for invalid pagination metadata", () => {
    expect(hasOlderMessages(undefined, 50)).toBe(false);
    expect(hasOlderMessages(50, -1)).toBe(false);
  });
});

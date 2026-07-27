import { describe, expect, it } from "vitest";
import { toDayRange } from "@/utils/pageQuery.js";

describe("toDayRange", () => {
  it("returns an empty range when the picker is incomplete", () => {
    expect(toDayRange([])).toEqual({ startTime: null, endTime: null });
    expect(toDayRange([new Date(2026, 6, 26)])).toEqual({
      startTime: null,
      endTime: null,
    });
  });

  it("formats calendar dates without applying a UTC offset", () => {
    expect(
      toDayRange([new Date(2026, 6, 1), new Date(2026, 6, 26)])
    ).toEqual({
      startTime: "2026-07-01T00:00:00",
      endTime: "2026-07-26T23:59:59",
    });
  });
});

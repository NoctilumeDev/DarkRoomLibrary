import { describe, expect, it } from "vitest";
import {
  pageSizeFromQuery,
  positiveIntegerFromQuery,
  toDayRange,
} from "@/utils/pageQuery.js";

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

describe("pagination query", () => {
  it("accepts positive page numbers and rejects stale values", () => {
    expect(positiveIntegerFromQuery("3", 1)).toBe(3);
    expect(positiveIntegerFromQuery(["4"], 1)).toBe(4);
    expect(positiveIntegerFromQuery("0", 1)).toBe(1);
    expect(positiveIntegerFromQuery("not-a-page", 2)).toBe(2);
  });

  it("only restores supported page sizes", () => {
    expect(pageSizeFromQuery("10", [6, 10, 18], 6)).toBe(10);
    expect(pageSizeFromQuery("12", [6, 10, 18], 6)).toBe(6);
  });
});

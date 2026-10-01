import { describe, expect, it } from "vitest";
import {
  APP_TIME_ZONE,
  formatAppMonthDayTime,
  parseAppDateTime,
  remainingAppDays,
  toAppSqlDateTime,
} from "@/utils/dateTime.js";

describe("application time boundary", () => {
  it("formats instants in the configured Asia/Shanghai zone", () => {
    expect(APP_TIME_ZONE).toBe("Asia/Shanghai");
    expect(toAppSqlDateTime(new Date("2026-10-01T20:18:00Z")))
      .toBe("2026-10-02 04:18:00");
    expect(formatAppMonthDayTime("2026-10-02 04:18:00"))
      .toContain("10/2");
  });

  it("parses backend wall-clock values as application-zone time", () => {
    expect(parseAppDateTime("2026-10-02 04:18:00")?.toISOString())
      .toBe("2026-10-01T20:18:00.000Z");
    expect(remainingAppDays(
      "2026-11-01 04:18:00",
      new Date("2026-10-01T20:18:00Z")
    )).toBe(30);
  });
});

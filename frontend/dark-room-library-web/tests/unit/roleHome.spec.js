import { resolveRoleHome } from "../../src/utils/roleHome.js";
import {
  normalizeReaderTheme,
  READER_THEMES,
} from "../../src/utils/readerTheme.js";

describe("resolveRoleHome", () => {
  test.each([
    [0, "/admin"],
    [1, "/admin"],
    [2, "/user"],
    [3, "/procurement"],
    [4, "/procurement"],
    ["4", "/procurement"],
  ])("maps role %p to %s", (role, expected) => {
    expect(resolveRoleHome(role)).toBe(expected);
  });

  test.each([undefined, null, "", 5, "invalid"])(
    "rejects unsupported role %p",
    (role) => {
      expect(resolveRoleHome(role)).toBeNull();
    }
  );
});

describe("reader theme", () => {
  test("keeps the supported day theme", () => {
    expect(normalizeReaderTheme(READER_THEMES.DAY)).toBe(READER_THEMES.DAY);
  });

  test.each([undefined, null, "", "unknown"])(
    "falls back to night for %p",
    (value) => {
      expect(normalizeReaderTheme(value)).toBe(READER_THEMES.NIGHT);
    }
  );
});

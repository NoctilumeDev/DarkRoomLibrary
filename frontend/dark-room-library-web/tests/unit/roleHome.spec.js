import { resolveRoleHome } from "../../src/utils/roleHome.js";
import {
  normalizeReaderTheme,
  READER_THEMES,
} from "../../src/utils/readerTheme.js";
import {
  getUserRoleName,
  isAdministratorRole,
  USER_ROLE,
} from "../../src/utils/userRoles.js";

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

describe("user roles", () => {
  test("keeps role names aligned with backend role codes", () => {
    expect(getUserRoleName(USER_ROLE.SUPER_ADMIN)).toBe("超级管理员");
    expect(getUserRoleName(USER_ROLE.ADMIN)).toBe("管理员");
    expect(getUserRoleName(USER_ROLE.READER)).toBe("读者");
    expect(getUserRoleName(USER_ROLE.ACQUISITIONS)).toBe("采购员");
    expect(getUserRoleName(USER_ROLE.LOGISTICS)).toBe("物流员");
    expect(getUserRoleName(99)).toBeNull();
  });

  test("only the two administrator roles share admin capabilities", () => {
    expect(isAdministratorRole(USER_ROLE.SUPER_ADMIN)).toBe(true);
    expect(isAdministratorRole(USER_ROLE.ADMIN)).toBe(true);
    expect(isAdministratorRole(USER_ROLE.READER)).toBe(false);
    expect(isAdministratorRole(USER_ROLE.ACQUISITIONS)).toBe(false);
    expect(isAdministratorRole(USER_ROLE.LOGISTICS)).toBe(false);
  });
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

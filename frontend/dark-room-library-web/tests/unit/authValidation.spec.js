import { beforeEach, describe, expect, it, vi } from "vitest";
import { resolveAuthorizedRole } from "../../src/utils/authValidation.js";
import {
  getToken,
  getUserProfile,
  setToken,
} from "../../src/utils/storage.js";

describe("live authorization validation", () => {
  beforeEach(() => {
    sessionStorage.clear();
    vi.restoreAllMocks();
  });

  it("uses the current backend role and hydrates the session profile", async () => {
    setToken("current-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        code: 200,
        data: {
          id: 7,
          userName: "当前用户",
          userRole: 3,
          isCoordinatorAdmin: false,
        },
      }),
    }));

    await expect(resolveAuthorizedRole("current-token", 2)).resolves.toBe(3);
    expect(getUserProfile()).toMatchObject({ id: 7, role: 3 });
  });

  it("clears stale authentication when the backend rejects the token", async () => {
    setToken("stale-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      json: async () => ({ code: 401, msg: "身份认证异常" }),
    }));

    await expect(resolveAuthorizedRole("stale-token", 2)).resolves.toBeNull();
    expect(getToken()).toBeNull();
  });
});

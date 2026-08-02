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

  it("retains authentication during a transient network failure", async () => {
    setToken("current-token");
    vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("network unavailable")));

    await expect(resolveAuthorizedRole("current-token", 2)).resolves.toBe(2);
    expect(getToken()).toBe("current-token");
  });

  it("uses the token role during a temporary backend failure", async () => {
    setToken("current-token");
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue({
      ok: false,
      status: 503,
      json: async () => ({ code: 503, msg: "服务暂不可用" }),
    }));

    await expect(resolveAuthorizedRole("current-token", 3)).resolves.toBe(3);
    expect(getToken()).toBe("current-token");
  });
});

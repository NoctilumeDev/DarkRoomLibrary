import { beforeEach, describe, expect, it, vi } from "vitest";
import { resolveAuthorizedRole } from "../../src/utils/authValidation.js";
import {
  getToken,
  getUserProfile,
  setToken,
  setUserProfile,
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

  it("does not let a rejected old token clear a newer login", async () => {
    let resolveFetch;
    setToken("old-token");
    vi.stubGlobal("fetch", vi.fn(() => new Promise((resolve) => {
      resolveFetch = resolve;
    })));

    const pending = resolveAuthorizedRole("old-token", 2);
    setToken("new-token");
    resolveFetch({
      ok: false,
      status: 401,
      json: async () => ({ code: 401, msg: "身份认证异常" }),
    });

    await expect(pending).resolves.toBeNull();
    expect(getToken()).toBe("new-token");
  });

  it("does not let a successful old profile overwrite a newer login", async () => {
    let resolveFetch;
    setToken("old-token");
    vi.stubGlobal("fetch", vi.fn(() => new Promise((resolve) => {
      resolveFetch = resolve;
    })));

    const pending = resolveAuthorizedRole("old-token", 2);
    setToken("new-token");
    setUserProfile({ id: 9, name: "新会话", role: 3 });
    resolveFetch({
      ok: true,
      status: 200,
      json: async () => ({
        code: 200,
        data: { id: 7, userName: "旧会话", userRole: 2 },
      }),
    });

    await expect(pending).resolves.toBeNull();
    expect(getToken()).toBe("new-token");
    expect(getUserProfile()).toEqual({ id: 9, name: "新会话", role: 3 });
  });
});

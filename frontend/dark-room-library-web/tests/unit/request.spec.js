import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => {
  const state = {
    createdConfig: null,
  };
  const handlers = {
    requestFulfilled: null,
    requestRejected: null,
    responseFulfilled: null,
    responseRejected: null,
  };
  const instance = {
    interceptors: {
      request: {
        use: vi.fn((fulfilled, rejected) => {
          handlers.requestFulfilled = fulfilled;
          handlers.requestRejected = rejected;
        }),
      },
      response: {
        use: vi.fn((fulfilled, rejected) => {
          handlers.responseFulfilled = fulfilled;
          handlers.responseRejected = rejected;
        }),
      },
    },
  };
  return {
    state,
    handlers,
    instance,
    create: vi.fn((config) => {
      state.createdConfig = config;
      return instance;
    }),
    getToken: vi.fn(),
    clearAuthSession: vi.fn(),
    push: vi.fn(),
  };
});

vi.mock("axios", () => ({
  default: {
    create: mocks.create,
  },
}));

vi.mock("@/utils/storage.js", () => ({
  getToken: mocks.getToken,
  clearAuthSession: mocks.clearAuthSession,
}));

vi.mock("@/router", () => ({
  default: {
    push: mocks.push,
  },
}));

const { default: request } = await import("../../src/utils/request.js");

describe("Axios request boundary", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("creates the shared client with the API base and timeout", () => {
    expect(request).toBe(mocks.instance);
    expect(mocks.state.createdConfig).toEqual(
      expect.objectContaining({
        baseURL: "/api/dark-room-library/v1",
        timeout: 8000,
      })
    );
  });

  it("adds the bearer token only when a session token exists", () => {
    mocks.getToken.mockReturnValueOnce("current-token").mockReturnValueOnce(null);

    const authorized = mocks.handlers.requestFulfilled({ headers: {} });
    const anonymous = mocks.handlers.requestFulfilled({ headers: {} });

    expect(authorized.headers.Authorization).toBe("Bearer current-token");
    expect(anonymous.headers.Authorization).toBeUndefined();
  });

  it("preserves successful responses and propagates request setup failures", async () => {
    const response = { status: 200, data: { code: 200 } };
    expect(mocks.handlers.responseFulfilled(response)).toBe(response);

    const error = new Error("request setup failed");
    await expect(mocks.handlers.requestRejected(error)).rejects.toBe(error);
  });

  it.each([
    { status: 401, data: { code: 500, msg: "expired" } },
    { status: 200, data: { code: 401, msg: "expired" } },
    { status: 200, data: { code: 500, msg: "身份认证异常" } },
    { status: 200, data: { code: 500, msg: "请先登录后操作" } },
  ])("clears rejected authentication for %#", async ({ status, data }) => {
    const error = { response: { status, data } };

    await expect(mocks.handlers.responseRejected(error)).rejects.toBe(error);

    expect(mocks.clearAuthSession).toHaveBeenCalledTimes(1);
    expect(mocks.push).toHaveBeenCalledWith("/login");
  });

  it("retains the session for server and transport failures", async () => {
    const serverError = {
      response: { status: 503, data: { code: 503, msg: "服务暂不可用" } },
    };
    const networkError = new TypeError("network unavailable");

    await expect(mocks.handlers.responseRejected(serverError)).rejects.toBe(serverError);
    await expect(mocks.handlers.responseRejected(networkError)).rejects.toBe(networkError);

    expect(mocks.clearAuthSession).not.toHaveBeenCalled();
    expect(mocks.push).not.toHaveBeenCalled();
  });
});

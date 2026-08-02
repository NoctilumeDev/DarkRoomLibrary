import { createRouteGuard } from "../../src/utils/routeGuard.js";

function dependencies(overrides = {}) {
  return {
    getToken: vi.fn(() => "token"),
    clearAuthSession: vi.fn(),
    jwtDecode: vi.fn(() => ({ role: 2, exp: 200 })),
    resolveAuthorizedRole: vi.fn(async () => 2),
    resolveRoleHome: vi.fn(() => "/user"),
    nowInSeconds: vi.fn(() => 100),
    ...overrides,
  };
}

describe("route guard", () => {
  it("allows public routes without reading authentication state", async () => {
    const deps = dependencies();
    const guard = createRouteGuard(deps);

    await expect(guard({ meta: {} })).resolves.toBe(true);
    expect(deps.getToken).not.toHaveBeenCalled();
  });

  it("redirects missing sessions to login", async () => {
    const guard = createRouteGuard(dependencies({ getToken: vi.fn(() => null) }));

    await expect(guard({ meta: { requireAuth: true } })).resolves.toBe("/login");
  });

  it("clears expired sessions", async () => {
    const deps = dependencies({
      jwtDecode: vi.fn(() => ({ role: 2, exp: 99 })),
    });
    const guard = createRouteGuard(deps);

    await expect(guard({ meta: { requireAuth: true } })).resolves.toBe("/login");
    expect(deps.clearAuthSession).toHaveBeenCalledTimes(1);
  });

  it("retains a valid role on an allowed route", async () => {
    const deps = dependencies();
    const guard = createRouteGuard(deps);

    await expect(
      guard({ meta: { requireAuth: true, roles: [2] } })
    ).resolves.toBe(true);
    expect(deps.resolveAuthorizedRole).toHaveBeenCalledWith("token", 2);
  });

  it("redirects a valid but unauthorized role to its own home", async () => {
    const deps = dependencies({
      resolveAuthorizedRole: vi.fn(async () => 3),
      resolveRoleHome: vi.fn(() => "/procurement"),
    });
    const guard = createRouteGuard(deps);

    await expect(
      guard({ meta: { requireAuth: true, roles: [0, 1] } })
    ).resolves.toBe("/procurement");
  });

  it("redirects rejected server authorization to login", async () => {
    const guard = createRouteGuard(
      dependencies({ resolveAuthorizedRole: vi.fn(async () => null) })
    );

    await expect(guard({ meta: { requireAuth: true } })).resolves.toBe("/login");
  });

  it("clears malformed tokens before redirecting", async () => {
    const deps = dependencies({
      jwtDecode: vi.fn(() => {
        throw new Error("invalid token");
      }),
    });
    const guard = createRouteGuard(deps);

    await expect(guard({ meta: { requireAuth: true } })).resolves.toBe("/login");
    expect(deps.clearAuthSession).toHaveBeenCalledTimes(1);
  });
});

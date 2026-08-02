import jwtDecode from "jwt-decode";
import { resolveAuthorizedRole } from "@/utils/authValidation.js";
import { resolveRoleHome } from "@/utils/roleHome.js";
import { clearAuthSession, getToken } from "@/utils/storage.js";

export function createRouteGuard(overrides = {}) {
  const dependencies = {
    getToken,
    clearAuthSession,
    jwtDecode,
    resolveAuthorizedRole,
    resolveRoleHome,
    nowInSeconds: () => Math.floor(Date.now() / 1000),
    ...overrides,
  };

  return async function guard(to) {
    if (!to.meta.requireAuth) return true;

    const token = dependencies.getToken();
    if (!token) return "/login";

    try {
      const decoded = dependencies.jwtDecode(token);
      if (decoded.exp && decoded.exp < dependencies.nowInSeconds()) {
        dependencies.clearAuthSession();
        return "/login";
      }

      const authorizedRole = await dependencies.resolveAuthorizedRole(
        token,
        decoded.role
      );
      if (authorizedRole === null) return "/login";

      if (to.meta.roles && !to.meta.roles.includes(authorizedRole)) {
        return dependencies.resolveRoleHome(authorizedRole) || "/login";
      }
      return true;
    } catch {
      dependencies.clearAuthSession();
      return "/login";
    }
  };
}

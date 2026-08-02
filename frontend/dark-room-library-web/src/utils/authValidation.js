import { buildApiUrl } from "@/utils/fileUrl.js";
import {
  clearAuthSession,
  setUserProfile,
} from "@/utils/storage.js";

const AUTH_TIMEOUT_MS = 8000;

export async function resolveAuthorizedRole(token, tokenRole) {
  if (import.meta.env.VITE_DEMO_MODE === "true") {
    return Number(tokenRole);
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), AUTH_TIMEOUT_MS);
  const fallbackRole =
    tokenRole === null || tokenRole === undefined || tokenRole === ""
      ? Number.NaN
      : Number(tokenRole);
  const retainedRole = Number.isInteger(fallbackRole) ? fallbackRole : null;
  try {
    const response = await fetch(buildApiUrl("/user/auth"), {
      headers: { Authorization: `Bearer ${token}` },
      signal: controller.signal,
    });
    if (response.status === 401 || response.status === 403) {
      clearAuthSession();
      return null;
    }
    const payload = await response.json();
    if (payload?.code === 401 || payload?.code === 403) {
      clearAuthSession();
      return null;
    }
    if (!response.ok || payload?.code !== 200 || !payload?.data) {
      return retainedRole;
    }

    const user = payload.data;
    const role = Number(user.userRole);
    if (!Number.isInteger(role)) {
      clearAuthSession();
      return null;
    }
    setUserProfile({
      id: user.id,
      name: user.userName,
      email: user.userEmail,
      url: user.userAvatar,
      role,
      isCoordinatorAdmin: Boolean(user.isCoordinatorAdmin),
    });
    return role;
  } catch {
    return retainedRole;
  } finally {
    clearTimeout(timeout);
  }
}

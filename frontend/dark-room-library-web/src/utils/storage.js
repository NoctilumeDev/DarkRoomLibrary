import jwtDecode from "jwt-decode";

const SESSION_KEYS = Object.freeze({
  token: "token",
  userProfile: "userInfo",
  authIntroSeen: "auth-intro-seen",
  noticeOperation: "noticeOperation",
  noticeDraft: "noticeInfo",
});

function read(key) {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key, value) {
  try {
    if (value === null || value === undefined || value === "") {
      sessionStorage.removeItem(key);
    } else {
      sessionStorage.setItem(key, value);
    }
  } catch {
    // Storage may be disabled; the current view can continue in memory.
  }
}

function readJson(key) {
  const raw = read(key);
  if (!raw) return null;
  try {
    return JSON.parse(raw);
  } catch {
    write(key, null);
    return null;
  }
}

export function getToken() {
  return read(SESSION_KEYS.token);
}

export function setToken(token) {
  write(SESSION_KEYS.token, typeof token === "string" ? token.trim() : null);
}

export function getUserProfile() {
  return readJson(SESSION_KEYS.userProfile);
}

export function getSessionUserRole() {
  const profile = getUserProfile();
  if (profile && profile.role !== null && profile.role !== undefined) {
    return Number(profile.role);
  }

  const token = getToken();
  if (!token) return null;
  try {
    const role = jwtDecode(token)?.role;
    return role === null || role === undefined ? null : Number(role);
  } catch {
    return null;
  }
}

export function setUserProfile(profile) {
  write(
    SESSION_KEYS.userProfile,
    profile && typeof profile === "object" ? JSON.stringify(profile) : null
  );
}

export function clearAuthSession() {
  Object.values(SESSION_KEYS).forEach((key) => write(key, null));
}

import {
  clearAuthSession,
  setToken,
  setUserProfile,
} from "@/utils/storage.js";

const ACTIVE_IDENTITY_KEY = "drl-demo-active-identity";
export const DEMO_STATE_KEY = "drl-demo-state-v1";
export const DEMO_IDENTITY_EVENT = "drl-demo-identity-change";
export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === "true";

export const DEMO_IDENTITIES = Object.freeze([
  Object.freeze({
    key: "root",
    id: 100,
    role: 0,
    label: "超级管理员",
    name: "暗室总馆员",
    account: "drl_root_aurora",
    email: "drl_root_aurora@darkroomlibrary.local",
    avatar: "",
    isCoordinatorAdmin: false,
  }),
  Object.freeze({
    key: "coordinator",
    id: 101,
    role: 1,
    label: "馆务协调员",
    name: "守卷青梧",
    account: "drl_keeper_qingwu",
    email: "drl_keeper_qingwu@darkroomlibrary.local",
    avatar: "demo-media/coordinator-avatar.webp",
    isCoordinatorAdmin: true,
  }),
  Object.freeze({
    key: "admin",
    id: 105,
    role: 1,
    label: "普通管理员",
    name: "墨舟理卷",
    account: "drl_admin_mozhou",
    email: "drl_admin_mozhou@darkroomlibrary.local",
    avatar: "",
    isCoordinatorAdmin: false,
  }),
  Object.freeze({
    key: "reader",
    id: 102,
    role: 2,
    label: "读者",
    name: "砚灯拾页",
    account: "drl_reader_yandeng",
    email: "drl_reader_yandeng@darkroomlibrary.local",
    avatar: "demo-media/reader-avatar.webp",
    isCoordinatorAdmin: false,
  }),
  Object.freeze({
    key: "purchaser",
    id: 103,
    role: 3,
    label: "采购员",
    name: "采书星阑",
    account: "drl_buyer_xinglan",
    email: "drl_buyer_xinglan@darkroomlibrary.local",
    avatar: "",
    isCoordinatorAdmin: false,
  }),
  Object.freeze({
    key: "logistics",
    id: 104,
    role: 4,
    label: "物流员",
    name: "归架沉香",
    account: "drl_logistics_chenxiang",
    email: "drl_logistics_chenxiang@darkroomlibrary.local",
    avatar: "",
    isCoordinatorAdmin: false,
  }),
]);

function readSession(key) {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeSession(key, value) {
  try {
    if (value === null || value === undefined) {
      sessionStorage.removeItem(key);
    } else {
      sessionStorage.setItem(key, value);
    }
  } catch {
    // The demo can still run in memory when session storage is unavailable.
  }
}

function encodeTokenPart(value) {
  const json = JSON.stringify(value);
  const bytes = new TextEncoder().encode(json);
  let binary = "";
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte);
  });
  return btoa(binary)
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/g, "");
}

function createDemoToken(identity) {
  const header = encodeTokenPart({ alg: "none", typ: "JWT" });
  const payload = encodeTokenPart({
    id: identity.id,
    role: identity.role,
    demo: true,
    exp: 4102444800,
  });
  return `${header}.${payload}.browser-demo`;
}

export function resolveDemoAsset(path) {
  if (!path) return "";
  if (/^(https?:|data:|blob:|\/)/i.test(path)) return path;
  return `${import.meta.env.BASE_URL}${path}`;
}

export function findDemoIdentity(value) {
  if (!value) return null;
  const normalized = String(value).trim();
  return (
    DEMO_IDENTITIES.find(
      (identity) =>
        identity.key === normalized || identity.account === normalized
    ) || null
  );
}

export function getActiveDemoIdentity() {
  return findDemoIdentity(readSession(ACTIVE_IDENTITY_KEY));
}

export function toDemoUser(identity) {
  if (!identity) return null;
  return {
    id: identity.id,
    userName: identity.name,
    userAccount: identity.account,
    userEmail: identity.email,
    userAvatar: resolveDemoAsset(identity.avatar),
    userRole: identity.role,
    role: identity.role,
    isCoordinatorAdmin: identity.isCoordinatorAdmin,
  };
}

export function activateDemoIdentity(value) {
  const identity = findDemoIdentity(value);
  if (!identity) return null;

  const user = toDemoUser(identity);
  writeSession(ACTIVE_IDENTITY_KEY, identity.key);
  setToken(createDemoToken(identity));
  setUserProfile({
    id: user.id,
    name: user.userName,
    email: user.userEmail,
    url: user.userAvatar,
    role: user.userRole,
    isCoordinatorAdmin: user.isCoordinatorAdmin,
  });
  window.dispatchEvent(
    new CustomEvent(DEMO_IDENTITY_EVENT, { detail: { identity: identity.key } })
  );
  return identity;
}

export function resetDemoRuntime() {
  writeSession(ACTIVE_IDENTITY_KEY, null);
  writeSession(DEMO_STATE_KEY, null);
  clearAuthSession();
  window.dispatchEvent(
    new CustomEvent(DEMO_IDENTITY_EVENT, { detail: { identity: null } })
  );
}


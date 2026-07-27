import {
  clearAuthSession,
  getToken,
  getUserProfile,
  setToken,
  setUserProfile,
} from "../../src/utils/storage.js";

class MemoryStorage {
  constructor() {
    this.values = new Map();
  }

  clear() {
    this.values.clear();
  }

  getItem(key) {
    return this.values.has(key) ? this.values.get(key) : null;
  }

  removeItem(key) {
    this.values.delete(key);
  }

  setItem(key, value) {
    this.values.set(key, String(value));
  }
}

describe("storage", () => {
  beforeAll(() => {
    Object.defineProperty(global, "sessionStorage", {
      configurable: true,
      value: new MemoryStorage(),
    });
  });

  beforeEach(() => {
    sessionStorage.clear();
  });

  it("normalizes and removes authentication tokens", () => {
    setToken("  token-value  ");
    expect(getToken()).toBe("token-value");

    setToken("");
    expect(getToken()).toBeNull();
  });

  it("round-trips the current user profile", () => {
    const profile = { id: 7, name: "读者", role: 2 };
    setUserProfile(profile);

    expect(getUserProfile()).toEqual(profile);
  });

  it("resolves the role from the token before auth profile hydration", async () => {
    const { getSessionUserRole } = await import("../../src/utils/storage.js");
    const encode = (value) => Buffer.from(JSON.stringify(value)).toString("base64url");
    setToken(`${encode({ alg: "none", typ: "JWT" })}.${encode({ role: 0 })}.test`);

    expect(getSessionUserRole()).toBe(0);
  });

  it("discards malformed profile data", () => {
    sessionStorage.setItem("userInfo", "{broken");

    expect(getUserProfile()).toBeNull();
    expect(sessionStorage.getItem("userInfo")).toBeNull();
  });

  it("clears application session keys without deleting unrelated data", () => {
    sessionStorage.setItem("token", "secret");
    sessionStorage.setItem("userInfo", "{}");
    sessionStorage.setItem("noticeInfo", "{}");
    sessionStorage.setItem("external-key", "keep");

    clearAuthSession();

    expect(getToken()).toBeNull();
    expect(getUserProfile()).toBeNull();
    expect(sessionStorage.getItem("noticeInfo")).toBeNull();
    expect(sessionStorage.getItem("external-key")).toBe("keep");
  });
});

import { beforeEach, describe, expect, it } from "vitest";
import { demoAdapter, resetDemoStateForTest } from "../../src/demo/adapter.js";
import {
  activateDemoIdentity,
  DEMO_IDENTITIES,
  resetDemoRuntime,
} from "../../src/demo/runtime.js";

async function call(method, url, data, params) {
  const response = await demoAdapter({
    method,
    url,
    data: data === undefined ? undefined : JSON.stringify(data),
    params,
    headers: {},
  });
  return response.data;
}

describe("browser demo adapter", () => {
  beforeEach(() => {
    sessionStorage.clear();
    resetDemoRuntime();
    resetDemoStateForTest();
  });

  it("authenticates every fixed demo identity and enforces the shared-email limit", async () => {
    for (const identity of DEMO_IDENTITIES) {
      activateDemoIdentity(identity.key);
      const auth = await call("get", "/user/auth");
      expect(auth.code).toBe(200);
      expect(auth.data.id).toBe(identity.id);
      expect(auth.data.userRole).toBe(identity.role);
      expect(auth.data.userAccount).toBe(identity.account);
    }

    const sharedEmail = "shared-demo@example.test";
    for (const identity of DEMO_IDENTITIES.slice(0, 4)) {
      activateDemoIdentity(identity.key);
      const updated = await call("put", "/user/update", {
        userEmail: sharedEmail,
      });
      expect(updated.code).toBe(identity === DEMO_IDENTITIES[3] ? 400 : 200);
    }
  });

  it("keeps borrow and return inventory changes consistent", async () => {
    activateDemoIdentity("reader");

    const before = await call("post", "/book/query", {
      current: 1,
      size: 20,
    });
    const initial = before.data.find((book) => book.id === 1).availableCount;

    const borrowed = await call("post", "/borrowRecord/borrow/1");
    expect(borrowed.code).toBe(200);

    const afterBorrow = await call("post", "/book/query", {
      current: 1,
      size: 20,
    });
    expect(afterBorrow.data.find((book) => book.id === 1).availableCount).toBe(
      initial - 1
    );

    const records = await call("post", "/borrowRecord/query", {
      current: 1,
      size: 20,
      status: false,
    });
    const newRecord = records.data.find((record) => record.bookId === 1);
    expect(newRecord).toBeTruthy();

    const returned = await call(
      "post",
      `/borrowRecord/return/${newRecord.id}`
    );
    expect(returned.code).toBe(200);

    const afterReturn = await call("post", "/book/query", {
      current: 1,
      size: 20,
    });
    expect(afterReturn.data.find((book) => book.id === 1).availableCount).toBe(
      initial
    );
  });

  it("applies procurement stock exactly once", async () => {
    activateDemoIdentity("logistics");

    const before = await call("post", "/book/query", {
      current: 1,
      size: 20,
    });
    const initial = before.data.find((book) => book.id === 3).availableCount;

    const first = await call("put", "/procurement/updateLogistics", {
      orderId: 701,
      status: 3,
      carrier: "星河承运",
      trackingNo: "DRL-DEMO-701",
    });
    const second = await call("put", "/procurement/updateLogistics", {
      orderId: 701,
      status: 3,
      carrier: "星河承运",
      trackingNo: "DRL-DEMO-701",
    });
    expect(first.code).toBe(200);
    expect(second.code).toBe(200);

    const after = await call("post", "/book/query", {
      current: 1,
      size: 20,
    });
    expect(after.data.find((book) => book.id === 3).availableCount).toBe(
      initial + 7
    );
  });

  it("rejects unsupported file operations instead of faking success", async () => {
    activateDemoIdentity("root");
    const result = await call("post", "/file/cleanup");
    expect(result.code).toBe(409);
    expect(result.msg).toContain("不上传、下载或导出");
  });

  it("rejects unimplemented writes instead of returning an empty success", async () => {
    activateDemoIdentity("root");
    const result = await call("delete", "/book/batchDelete", [1]);
    expect(result.code).toBe(409);
    expect(result.msg).toContain("不会伪造成功");
  });
});

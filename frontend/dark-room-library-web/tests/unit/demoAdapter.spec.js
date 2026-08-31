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

    for (const status of [1, 2]) {
      const advanced = await call("put", "/procurement/updateLogistics", {
        orderId: 701,
        status,
        carrier: "星河承运",
        trackingNo: "DRL-DEMO-701",
      });
      expect(advanced.code).toBe(200);
    }
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

  it("keeps procurement and logistics state ownership aligned", async () => {
    activateDemoIdentity("purchaser");
    const bypass = await call("put", "/procurement/updateStatus", {
      id: 701,
      status: 3,
    });
    expect(bypass.code).toBe(409);
    expect(bypass.msg).toContain("物流进度");

    activateDemoIdentity("logistics");
    const transit = await call("put", "/procurement/updateLogistics", {
      orderId: 701,
      status: 1,
    });
    expect(transit.code).toBe(200);
    const orders = await call("post", "/procurement/query", {
      current: 1,
      size: 20,
    });
    expect(orders.data.find((order) => order.id === 701).status).toBe(3);
  });

  it("marks only procurement messages rendered to the receiver", async () => {
    activateDemoIdentity("logistics");
    const before = await call("post", "/procurement/message/query", {
      orderId: 701,
      channelType: 1,
      current: 1,
      size: 50,
    });
    expect(before.data[0].readStatus).toBe(false);

    await call("put", "/procurement/message/read", {
      orderId: 701,
      channelType: 1,
      messageIds: [999999],
    });
    const untouched = await call("post", "/procurement/message/query", {
      orderId: 701,
      channelType: 1,
      current: 1,
      size: 50,
    });
    expect(untouched.data[0].readStatus).toBe(false);

    await call("put", "/procurement/message/read", {
      orderId: 701,
      channelType: 1,
      messageIds: [before.data[0].id],
    });
    const after = await call("post", "/procurement/message/query", {
      orderId: 701,
      channelType: 1,
      current: 1,
      size: 50,
    });
    expect(after.data[0].readStatus).toBe(true);
  });

  it("keeps the explainable recommendation loop private and attributable", async () => {
    activateDemoIdentity("reader");

    const feed = await call("get", "/recommendation/feed", undefined, { size: 6 });
    expect(feed.code).toBe(200);
    expect(feed.data.mode).toBe("CONTENT");
    expect(feed.data.personalized).toBe(true);
    expect(feed.data.signalCount).toBe(3);
    expect(feed.data.items).toHaveLength(6);
    expect(feed.data.items.every((item) => item.reason)).toBe(true);
    expect(feed.data.items.every((item) => ![1, 5, 6].includes(item.bookId))).toBe(true);

    const dismissed = feed.data.items[1];
    expect((await call("post", `/recommendation/items/${dismissed.itemId}/events`, {
      eventType: "DISMISS",
    })).code).toBe(200);
    const feedAfterDismiss = await call("get", "/recommendation/feed", undefined, { size: 6 });
    expect(feedAfterDismiss.data.items.some((entry) => entry.bookId === dismissed.bookId)).toBe(false);

    const item = feed.data.items[0];
    expect((await call("post", `/recommendation/items/${item.itemId}/events`, {
      eventType: "CLICK",
    })).code).toBe(200);
    expect((await call("post", `/bookFavorite/add/${item.bookId}`)).code).toBe(200);

    const disabled = await call("put", "/recommendation/setting", { enabled: false });
    expect(disabled.data.enabled).toBe(false);
    const publicFeed = await call("get", "/recommendation/feed");
    expect(publicFeed.data.mode).toBe("PUBLIC");

    const cleared = await call("delete", "/recommendation/history");
    expect(cleared.code).toBe(200);
    const favorites = await call("post", "/bookFavorite/query", { current: 1, size: 20 });
    expect(favorites.data.some((favorite) => favorite.bookId === item.bookId)).toBe(true);
  });

  it("rejects unsupported file operations instead of faking success", async () => {
    activateDemoIdentity("root");
    const result = await call("post", "/file/cleanup");
    expect(result.code).toBe(409);
    expect(result.msg).toContain("不上传、下载或导出");
  });

  it("applies every visible demo list filter before pagination", async () => {
    activateDemoIdentity("root");

    const cases = [
      ["/category/query", { name: "文学" }, "文学"],
      ["/bookshelf/query", { location: "南侧" }, "青梧二架"],
      ["/book/query", { name: "不存在的书" }, null],
      ["/user/query", { userName: "纸月" }, "纸月听澜"],
      ["/notice/query", { name: "新书" }, "新书到馆"],
      ["/procurement/query", { bookName: "不存在的书" }, null],
      [
        "/bookReviewReport/query",
        { status: 0, bookName: "雾灯", reviewContent: "目录" },
        "雾灯索引",
      ],
      ["/messageBoard/query", { content: "地方史" }, "希望下一批新书"],
    ];

    for (const [url, query, expectedText] of cases) {
      const response = await call("post", url, {
        current: 1,
        size: 20,
        ...query,
      });
      expect(response.code).toBe(200);
      if (expectedText === null) {
        expect(response.data).toHaveLength(0);
        expect(response.total).toBe(0);
      } else {
        expect(JSON.stringify(response.data)).toContain(expectedText);
      }
    }

    const datedUsers = await call("post", "/user/query", {
      current: 1,
      size: 20,
      startTime: "2026-07-26T00:00:00",
      endTime: "2026-07-26T23:59:59",
    });
    expect(datedUsers.data.map((item) => item.userName)).toEqual(["纸月听澜"]);

    const datedNotices = await call("post", "/notice/query", {
      current: 1,
      size: 20,
      startTime: "2026-07-24T00:00:00",
      endTime: "2026-07-24T23:59:59",
    });
    expect(datedNotices.data.map((item) => item.name)).toEqual(["新书到馆"]);
  });

  it("keeps review-report actions and readback semantics distinct", async () => {
    activateDemoIdentity("root");

    const ignored = await call("post", "/bookReviewReport/ignore/901");
    expect(ignored.code).toBe(200);
    expect(ignored.msg).toContain("忽略");
    const ignoredReadback = await call("post", "/bookReviewReport/query", {
      current: 1,
      size: 20,
      status: 2,
    });
    expect(ignoredReadback.data[0]).toMatchObject({
      status: 2,
      reviewUserName: "纸月听澜",
      reportUserName: "砚灯拾页",
      reviewContent: expect.stringContaining("目录"),
    });
    expect(ignoredReadback.data[0].handleTime).toBeTruthy();

    resetDemoStateForTest();
    const hidden = await call("post", "/bookReviewReport/hideReview/901");
    expect(hidden.code).toBe(200);
    expect(hidden.msg).toContain("隐藏书评");
    const hiddenReadback = await call("post", "/bookReviewReport/query", {
      current: 1,
      size: 20,
      status: 1,
    });
    expect(hiddenReadback.data[0]).toMatchObject({
      status: 1,
      reviewStatus: 1,
    });
    expect(hiddenReadback.data[0].handleTime).toBeTruthy();
  });

  it("serves the dashboard datasets from the same in-memory state", async () => {
    activateDemoIdentity("root");

    const overview = await call("get", "/statistics/overview");
    const monthly = await call("get", "/statistics/monthlyBorrow/30");
    const hotBooks = await call("get", "/statistics/hotBooks");
    const lowStock = await call("get", "/statistics/lowStock");
    const overdue = await call("get", "/statistics/overdueUsers");
    const collection = await call("get", "/statistics/collectionAnalysis");
    const controls = await call("get", "/views/staticControls");
    const auditStatus = await call("get", "/adminWorkflow/auditStatus");
    const backendFlow = await call("get", "/adminWorkflow/backendFlow");

    expect(overview.data.totalBooks).toBeGreaterThan(0);
    expect(overview.data.totalUsers).toBeGreaterThan(0);
    expect(monthly.data).toHaveLength(9);
    expect(hotBooks.data.books).toHaveLength(5);
    expect(lowStock.data.books.every((book) => book.availableCount < 3)).toBe(true);
    expect(overdue.data[0].overdueCount).toBe(1);
    expect(collection.data.categories.length).toBeGreaterThan(0);
    expect(controls.data).toHaveLength(4);
    expect(auditStatus.data.activeProcurements).toBeGreaterThan(0);
    expect(backendFlow.data).toHaveLength(3);
  });

  it("rejects unimplemented writes instead of returning an empty success", async () => {
    activateDemoIdentity("root");
    const result = await call("delete", "/book/batchDelete", [1]);
    expect(result.code).toBe(409);
    expect(result.msg).toContain("不会伪造成功");
  });
});

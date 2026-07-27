import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright-core";

const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const outputDir = path.resolve(
  process.env.VISUAL_OUTPUT_DIR || "test-results/visual-baseline"
);
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

await fs.mkdir(outputDir, { recursive: true });

const browser = await chromium.launch({
  executablePath: edgePath,
  headless: true,
});

function createToken(role) {
  const encode = (value) =>
    Buffer.from(JSON.stringify(value))
      .toString("base64url");
  const expiresAt = Math.floor(Date.now() / 1000) + 60 * 60;
  return `${encode({ alg: "none", typ: "JWT" })}.${encode({
    id: role + 100,
    role,
    exp: expiresAt,
  })}.visual-baseline`;
}

function success(data, total) {
  return JSON.stringify({
    code: 200,
    msg: "OK",
    data,
    ...(total === undefined ? {} : { total }),
  });
}

function mockPayload(url, role) {
  if (url.includes("/user/auth")) {
    const roleNames = [
      "暗室总馆员",
      "守卷青梧",
      "砚灯拾页",
      "采书星阑",
      "归架沉香",
    ];
    const roleAccounts = [
      "drl_root_aurora",
      "drl_keeper_qingwu",
      "drl_reader_yandeng",
      "drl_buyer_xinglan",
      "drl_logistics_chenxiang",
    ];
    return success({
      id: role + 100,
      userName: roleNames[role] || "访客",
      userRole: role,
      userAccount: roleAccounts[role] || "drl_guest",
      userEmail: `${roleAccounts[role] || "drl_guest"}@darkroomlibrary.local`,
      userAvatar:
        role === 1
          ? "/demo-media/coordinator-avatar.webp"
          : role === 2
            ? "/demo-media/reader-avatar.webp"
            : "",
    });
  }
  if (url.includes("/captcha/generate")) {
    return success({ captchaId: "visual", expression: "1 + 1 = ?" });
  }
  if (url.includes("/book/queryByDays") || url.includes("/user/queryByDays")) {
    return success([
      { name: "07-21", count: 2 },
      { name: "07-22", count: 4 },
      { name: "07-23", count: 3 },
      { name: "07-24", count: 6 },
    ]);
  }
  if (url.includes("/views/staticControls")) {
    return success([
      { name: "存量用户（个）", count: 128 },
      { name: "公告（篇）", count: 16 },
    ]);
  }
  if (url.includes("/statistics/overview")) {
    return success({
      totalBooks: 386,
      totalUsers: 128,
      activeBorrows: 42,
      returnedBorrows: 764,
    });
  }
  if (url.includes("/statistics/monthlyBorrow/")) {
    return success([
      { day: 1, count: 3 },
      { day: 7, count: 8 },
      { day: 14, count: 5 },
      { day: 21, count: 11 },
      { day: 28, count: 7 },
    ]);
  }
  if (url.includes("/statistics/hotBooks")) {
    return success({ books: [
      { bookName: "暗室藏书", borrowCount: 26 },
      { bookName: "雾灯索引", borrowCount: 19 },
      { bookName: "归架之前", borrowCount: 14 },
    ] });
  }
  if (url.includes("/statistics/lowStock")) {
    return success({ books: [
      { id: 1, bookName: "雾灯索引", availableCount: 1 },
      { id: 2, bookName: "归架之前", availableCount: 2 },
    ] });
  }
  if (url.includes("/statistics/overdueUsers")) {
    return success([
      { userId: 102, userName: "砚灯拾页", overdueCount: 1, totalFine: 2.5 },
    ]);
  }
  if (url.includes("/statistics/collectionAnalysis")) {
    return success({ categories: [
      { category: "文学", totalCount: 138 },
      { category: "历史", totalCount: 92 },
      { category: "科学", totalCount: 84 },
      { category: "艺术", totalCount: 72 },
    ] });
  }
  if (url.includes("/category/queryAll")) {
    return success([
      { id: 1, name: "文学" },
      { id: 2, name: "历史" },
      { id: 3, name: "科学" },
      { id: 4, name: "哲学" },
      { id: 5, name: "艺术" },
    ]);
  }
  if (url.includes("/recommendation/feed")) {
    return success({
      mode: "HYBRID",
      personalized: true,
      enabled: true,
      signalCount: 3,
      generatedAt: "2026-07-27 23:58:00",
      privacyNotice: "只使用你主动留下的收藏、借阅与评分，不记录无关浏览行为。",
      items: [
        {
          itemId: 8101,
          bookId: 1,
          name: "暗室藏书",
          author: "岑夜录",
          cover: "/demo-media/dark-room-library-cover.webp",
          sourceType: "CONTENT",
          reason: "沿着你收藏的文学书签，灯下又出现了它。",
        },
        {
          itemId: 8102,
          bookId: 2,
          name: "雾灯索引",
          author: "江雾衡",
          cover: "",
          sourceType: "COLLABORATIVE",
          reason: "收藏过《归架之前》的读者，也常留下这本。",
        },
        {
          itemId: 8103,
          bookId: 3,
          name: "归架之前",
          author: "闻归舟",
          cover: "",
          sourceType: "DISCOVERY",
          reason: "它与已有书签稍远，留作一次偶然相遇。",
        },
      ],
    });
  }
  if (url.includes("/book/query")) {
    return success(
      [
        {
          id: 1,
          name: "暗室藏书",
          author: "岑夜录",
          cover: "/demo-media/dark-room-library-cover.webp",
        },
        { id: 2, name: "雾灯索引", author: "江雾衡", cover: "" },
        { id: 3, name: "归架之前", author: "闻归舟", cover: "" },
      ],
      3
    );
  }
  if (url.includes("/borrowRecord/query")) {
    return success(
      [
        {
          id: 1,
          status: false,
          dueDate: new Date(Date.now() + 2 * 86400000).toISOString(),
        },
      ],
      1
    );
  }
  if (url.includes("/bookReservation/query")) {
    return success([{ id: 1, status: 3 }], 1);
  }
  if (url.includes("/notice/query")) {
    return success(
      [
        { id: 1, name: "夏季开放时间调整", content: "测试公告" },
        { id: 2, name: "新书已到馆", content: "测试公告" },
      ],
      2
    );
  }
  return success([], 0);
}

async function newPage(role, viewport, authenticated = true) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript(
    ({ token, adminTheme, authenticated }) => {
      if (authenticated) {
        sessionStorage.setItem("token", token);
      } else {
        sessionStorage.removeItem("token");
      }
      localStorage.setItem("admin-theme", adminTheme);
    },
    { token: createToken(role), adminTheme: "day", authenticated }
  );
  await context.route("**/api/**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json;charset=UTF-8",
      body: mockPayload(route.request().url(), role),
    });
  });
  return { context, page: await context.newPage() };
}

async function capture(
  name,
  route,
  role,
  viewport,
  readySelector,
  authenticated = true
) {
  const { context, page } = await newPage(role, viewport, authenticated);
  const diagnostics = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      diagnostics.push(`console: ${message.text()}`);
    }
  });
  page.on("pageerror", (error) => diagnostics.push(`pageerror: ${error.message}`));
  page.on("requestfailed", (request) => {
    diagnostics.push(
      `requestfailed: ${request.url()} ${request.failure()?.errorText || ""}`
    );
  });
  await page.goto(`${baseUrl}/#${route}`, { waitUntil: "networkidle" });
  try {
    await page.locator(readySelector).waitFor();
  } catch (error) {
    await page.screenshot({
      path: path.join(outputDir, `${name}-failed.png`),
      fullPage: true,
    });
    const body = await page.locator("body").innerText().catch(() => "");
    throw new Error(
      `${name} did not render ${readySelector}\nURL: ${page.url()}\n` +
        `${diagnostics.join("\n")}\nBody: ${body.slice(0, 1000)}\n${error.message}`,
      { cause: error }
    );
  }
  await page.waitForTimeout(700);
  await page.screenshot({
    path: path.join(outputDir, `${name}.png`),
    fullPage: true,
  });
  await context.close();
}

try {
  await capture(
    "login-desktop",
    "/login",
    2,
    { width: 1440, height: 1000 },
    ".auth-world",
    false
  );
  await capture(
    "login-mobile",
    "/login",
    2,
    { width: 430, height: 932 },
    ".auth-world",
    false
  );
  await capture(
    "reader-desktop",
    "/readerRoom",
    2,
    { width: 1440, height: 1000 },
    ".reader-shell .room-intro"
  );
  await capture(
    "reader-mobile",
    "/readerRoom",
    2,
    { width: 430, height: 932 },
    ".reader-shell .room-intro"
  );
  await capture(
    "dashboard-desktop",
    "/dashboard",
    0,
    { width: 1440, height: 1000 },
    ".paper-workspace .admin-dashboard"
  );
  await capture(
    "dashboard-mobile",
    "/dashboard",
    0,
    { width: 430, height: 932 },
    ".paper-workspace .admin-dashboard"
  );
  await capture(
    "statistics-desktop",
    "/statisticsDashboard",
    0,
    { width: 1440, height: 1000 },
    ".paper-workspace .statistics-page"
  );
  await capture(
    "statistics-mobile",
    "/statisticsDashboard",
    0,
    { width: 430, height: 932 },
    ".paper-workspace .statistics-page"
  );
} finally {
  await browser.close();
}

console.log(`Visual baselines written to ${outputDir}`);

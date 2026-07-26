import fs from "node:fs/promises";
import path from "node:path";
import { chromium } from "playwright-core";
import { getAccount } from "./test-accounts.mjs";

const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const outputDir = path.resolve(
  process.env.DOCS_SCREENSHOT_DIR || "test-results/docs-screenshots"
);
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const readerTestAccount = getAccount("reader");

const identities = Object.freeze({
  0: {
    id: 100,
    userName: "暗室总馆员",
    userAccount: "drl_root_aurora",
    userEmail: "drl_root_aurora@darkroomlibrary.local",
  },
  1: {
    id: 101,
    userName: "守卷青梧",
    userAccount: "drl_keeper_qingwu",
    userEmail: "drl_keeper_qingwu@darkroomlibrary.local",
  },
  2: {
    id: 102,
    userName: "砚灯拾页",
    userAccount: "drl_reader_yandeng",
    userEmail: "drl_reader_yandeng@darkroomlibrary.local",
  },
  3: {
    id: 103,
    userName: "采书星阑",
    userAccount: "drl_buyer_xinglan",
    userEmail: "drl_buyer_xinglan@darkroomlibrary.local",
  },
  4: {
    id: 104,
    userName: "归架沉香",
    userAccount: "drl_logistics_chenxiang",
    userEmail: "drl_logistics_chenxiang@darkroomlibrary.local",
  },
});

const books = [
  {
    id: 1,
    name: "暗室藏书",
    author: "岑夜录",
    category: "文学",
    publisher: "暗室藏书局",
    isbn: "9900000000001",
    description: "记录一间夜间图书馆里，书与读者彼此抵达的六个片段。",
    availableCount: 3,
    totalCount: 6,
    cover: "/demo-media/dark-room-library-cover.webp",
  },
  {
    id: 2,
    name: "雾灯索引",
    author: "江雾衡",
    category: "历史",
    publisher: "雾桥文库",
    isbn: "9900000000002",
    description: "从散落档案中重建一座旧城阅读史的索引札记。",
    availableCount: 1,
    totalCount: 4,
    cover: "",
  },
  {
    id: 3,
    name: "归架之前",
    author: "闻归舟",
    category: "文学",
    publisher: "归架书坊",
    isbn: "9900000000003",
    description: "一本书在归架前经过的借阅、批注、等待与重逢。",
    availableCount: 0,
    totalCount: 3,
    cover: "",
  },
  {
    id: 4,
    name: "星阑采书札",
    author: "栖星社编",
    category: "科学",
    publisher: "星阑书社",
    isbn: "9900000000004",
    description: "用清单和短札解释馆藏补充、版本选择与库存判断。",
    availableCount: 2,
    totalCount: 5,
    cover: "",
  },
  {
    id: 5,
    name: "青梧守卷录",
    author: "青梧馆记",
    category: "哲学",
    publisher: "青梧文献馆",
    isbn: "9900000000005",
    description: "围绕保存、开放与秩序，讨论馆员如何守护公共阅读。",
    availableCount: 2,
    totalCount: 4,
    cover: "",
  },
  {
    id: 6,
    name: "砚灯拾页集",
    author: "砚灯读书会",
    category: "艺术",
    publisher: "砚灯小筑",
    isbn: "9900000000006",
    description: "收录读者在灯下留下的短评、页边批注与阅读路径。",
    availableCount: 1,
    totalCount: 3,
    cover: "",
  },
];

const reviews = [
  {
    id: 201,
    userId: 102,
    userName: "砚灯拾页",
    bookId: 1,
    bookName: "暗室藏书",
    rating: 5,
    content: "它把借阅写成一次有去有回的相遇，最喜欢其中关于等待归还的那一页。",
    createTime: "2026-07-24 20:18",
    likeCount: 12,
    liked: true,
    reported: false,
    replies: [
      {
        id: 301,
        userName: "守卷青梧",
        replyToUserName: "砚灯拾页",
        content: "这段批注已经收入本周馆员荐读，感谢你把归还之后的感受也留下来。",
      },
    ],
  },
  {
    id: 202,
    userId: 107,
    userName: "纸月听澜",
    bookId: 3,
    bookName: "雾灯索引",
    rating: 4,
    content: "目录看似安静，实际把一座城的阅读痕迹串得很清楚，适合慢慢翻。",
    createTime: "2026-07-23 18:42",
    likeCount: 9,
    liked: false,
    reported: false,
    replies: [],
  },
];

const users = [
  { role: 0, isCoordinatorAdmin: false, isLogin: false, createTime: "2026-07-01 08:30:00" },
  { role: 1, isCoordinatorAdmin: true, isLogin: false, createTime: "2026-07-02 09:12:00" },
  { role: 2, isCoordinatorAdmin: false, isLogin: false, createTime: "2026-07-03 10:25:00" },
  { role: 3, isCoordinatorAdmin: false, isLogin: false, createTime: "2026-07-04 11:40:00" },
  { role: 4, isCoordinatorAdmin: false, isLogin: false, createTime: "2026-07-05 14:08:00" },
].map((entry) => {
  const identity = identities[entry.role];
  return {
    id: identity.id,
    userAvatar:
      entry.role === 1
        ? "/demo-media/coordinator-avatar.webp"
        : entry.role === 2
          ? "/demo-media/reader-avatar.webp"
          : "",
    userName: identity.userName,
    userAccount: identity.userAccount,
    userEmail: identity.userEmail,
    userRole: entry.role,
    isCoordinatorAdmin: entry.isCoordinatorAdmin,
    isLogin: entry.isLogin,
    createTime: entry.createTime,
  };
});

const procurementOrders = [
  {
    id: 701,
    bookId: 3,
    bookName: "归架之前",
    requestCount: 7,
    status: 3,
    requesterId: 100,
    requesterName: "暗室总馆员",
    purchaserId: 103,
    purchaserName: "采书星阑",
    logisticsId: 104,
    logisticsName: "归架沉香",
    logisticsStatus: 0,
    trackingNo: "DRL-20260725-0701",
    updateTime: "2026-07-25 09:18:00",
    unreadCount: 1,
  },
  {
    id: 702,
    bookId: 6,
    bookName: "砚灯拾页集",
    requestCount: 4,
    status: 4,
    requesterId: 101,
    requesterName: "守卷青梧",
    purchaserId: 103,
    purchaserName: "采书星阑",
    logisticsId: 104,
    logisticsName: "归架沉香",
    logisticsStatus: 1,
    trackingNo: "DRL-20260725-0702",
    updateTime: "2026-07-25 10:06:00",
    unreadCount: 0,
  },
];

await fs.mkdir(outputDir, { recursive: true });

const browser = await chromium.launch({
  executablePath: edgePath,
  headless: true,
});

function createToken(role) {
  const encode = (value) =>
    Buffer.from(JSON.stringify(value)).toString("base64url");
  return `${encode({ alg: "none", typ: "JWT" })}.${encode({
    id: identities[role]?.id || 999,
    role,
    exp: 1893456000,
  })}.docs-screenshot`;
}

function success(data, total) {
  return JSON.stringify({
    code: 200,
    msg: "OK",
    data,
    ...(total === undefined ? {} : { total }),
  });
}

function roleIdentity(role) {
  return {
    ...identities[role],
    userRole: role,
    userAvatar:
      role === 1
        ? "/demo-media/coordinator-avatar.webp"
        : role === 2
          ? "/demo-media/reader-avatar.webp"
          : "",
  };
}

function mockPayload(url, role) {
  if (url.includes("/user/auth")) {
    return success(roleIdentity(role));
  }
  if (url.includes("/captcha/generate")) {
    return success({ captchaId: "docs-captcha", expression: "7 + 5 = ?" });
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
  if (url.includes("/book/queryByDays")) {
    return success([
      { name: "07-20", count: 1 },
      { name: "07-21", count: 2 },
      { name: "07-22", count: 4 },
      { name: "07-23", count: 3 },
      { name: "07-24", count: 5 },
      { name: "07-25", count: 6 },
    ]);
  }
  if (url.includes("/user/queryByDays")) {
    return success([
      { name: "07-20", count: 2 },
      { name: "07-21", count: 3 },
      { name: "07-22", count: 3 },
      { name: "07-23", count: 5 },
      { name: "07-24", count: 6 },
      { name: "07-25", count: 8 },
    ]);
  }
  if (url.includes("/views/staticControls")) {
    return success([
      { name: "注册读者", count: 128 },
      { name: "在馆图书", count: 386 },
      { name: "借阅中", count: 42 },
      { name: "采购流转", count: 9 },
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
      { day: 4, count: 6 },
      { day: 7, count: 5 },
      { day: 10, count: 8 },
      { day: 13, count: 7 },
      { day: 16, count: 10 },
      { day: 19, count: 12 },
      { day: 22, count: 9 },
      { day: 25, count: 14 },
    ]);
  }
  if (url.includes("/statistics/hotBooks")) {
    return success({
      books: [
        { bookName: "暗室藏书", borrowCount: 37 },
        { bookName: "归架之前", borrowCount: 31 },
        { bookName: "雾灯索引", borrowCount: 28 },
        { bookName: "星阑采书札", borrowCount: 22 },
        { bookName: "青梧守卷录", borrowCount: 17 },
      ],
    });
  }
  if (url.includes("/statistics/lowStock")) {
    return success({
      books: [
        { name: "归架之前", author: "闻归舟", availableCount: 0 },
        { name: "雾灯索引", author: "江雾衡", availableCount: 1 },
        { name: "砚灯拾页集", author: "砚灯读书会", availableCount: 1 },
      ],
    });
  }
  if (url.includes("/statistics/overdueUsers")) {
    return success([
      { userName: "砚灯拾页", overdueCount: 2, totalFine: 6.5 },
      { userName: "纸月听澜", overdueCount: 1, totalFine: 2.0 },
    ]);
  }
  if (url.includes("/statistics/collectionAnalysis")) {
    return success({
      categories: [
        { category: "文学", totalCount: 128 },
        { category: "历史", totalCount: 76 },
        { category: "科学", totalCount: 72 },
        { category: "哲学", totalCount: 54 },
        { category: "艺术", totalCount: 56 },
      ],
    });
  }
  if (url.includes("/book/query")) {
    return success(books, books.length);
  }
  if (url.includes("/borrowRecord/query")) {
    return success(
      [
        {
          id: 401,
          bookId: 2,
          bookName: "雾灯索引",
          status: false,
          dueDate: "2026-07-27T09:00:00",
        },
      ],
      1
    );
  }
  if (url.includes("/bookReservation/query")) {
    return success([{ id: 501, bookId: 3, status: 3 }], 1);
  }
  if (url.includes("/bookFavorite/isFavorited/")) {
    return success(false);
  }
  if (url.includes("/bookReview/query")) {
    return success(reviews, reviews.length);
  }
  if (url.includes("/notice/query")) {
    return success(
      [
        { id: 601, name: "暑期开放时间调整", content: "周末延长开放一小时。" },
        { id: 602, name: "新书到馆", content: "本周新增文学与科学类藏书。" },
        { id: 603, name: "归还提醒", content: "请留意借阅到期时间。" },
      ],
      3
    );
  }
  if (url.includes("/user/query")) {
    return success(users, users.length);
  }
  if (url.includes("/user/collaborationUsers")) {
    const requestedRole = Number(new URL(url).searchParams.get("role"));
    return success(users.filter((user) => user.userRole === requestedRole));
  }
  if (url.includes("/procurement/query")) {
    return success(procurementOrders, procurementOrders.length);
  }
  if (url.includes("/procurement/message/query")) {
    return success(
      [
        {
          id: 801,
          senderId: 103,
          senderName: "采书星阑",
          content: "两箱图书已交接，请按运单登记到馆时间。",
          createTime: "2026-07-25 09:30:00",
        },
      ],
      1
    );
  }
  return success([], 0);
}

async function createPage({
  role = 2,
  readerTheme = "night",
  adminTheme = "day",
  authenticated = true,
}) {
  const context = await browser.newContext({
    viewport: { width: 1440, height: 1000 },
    reducedMotion: "reduce",
    deviceScaleFactor: 1,
  });
  await context.addInitScript(
    ({ token, readerTheme, adminTheme, authenticated }) => {
      const NativeDate = Date;
      const fixedTime = new NativeDate("2026-07-26T10:30:00+08:00").getTime();
      class FixedDate extends NativeDate {
        constructor(...args) {
          super(...(args.length ? args : [fixedTime]));
        }
        static now() {
          return fixedTime;
        }
      }
      window.Date = FixedDate;

      if (authenticated) sessionStorage.setItem("token", token);
      else sessionStorage.removeItem("token");
      sessionStorage.setItem("auth-intro-seen", "1");
      localStorage.setItem("dark-room-reader-theme", readerTheme);
      localStorage.setItem("admin-theme", adminTheme);
    },
    {
      token: createToken(role),
      readerTheme,
      adminTheme,
      authenticated,
    }
  );
  await context.route("**/api/**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "application/json;charset=UTF-8",
      body: mockPayload(route.request().url(), role),
    });
  });
  const page = await context.newPage();
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
  return { context, page, diagnostics };
}

async function capture({
  name,
  route,
  readySelector,
  role,
  readerTheme,
  adminTheme,
  authenticated,
  prepare,
  waitForCharts = 0,
}) {
  const { context, page, diagnostics } = await createPage({
    role,
    readerTheme,
    adminTheme,
    authenticated,
  });
  try {
    await page.goto(`${baseUrl}/#${route}`, { waitUntil: "networkidle" });
    await page.locator(readySelector).first().waitFor({ timeout: 10000 });
    if (waitForCharts) {
      await page.waitForFunction(
        (minimum) => document.querySelectorAll("canvas").length >= minimum,
        waitForCharts,
        { timeout: 10000 }
      );
    }
    if (prepare) await prepare(page);
    await page.addStyleTag({
      content: `
        *, *::before, *::after {
          animation-duration: 0.001ms !important;
          animation-delay: 0ms !important;
          transition-duration: 0.001ms !important;
          caret-color: transparent !important;
        }
      `,
    });
    await page.waitForTimeout(800);
    await page.screenshot({
      path: path.join(outputDir, `${name}.png`),
      fullPage: true,
    });
    if (diagnostics.length) {
      throw new Error(`${name} emitted runtime errors:\n${diagnostics.join("\n")}`);
    }
  } catch (error) {
    await page
      .screenshot({
        path: path.join(outputDir, `${name}-failed.png`),
        fullPage: true,
      })
      .catch(() => {});
    throw error;
  } finally {
    await context.close();
  }
}

try {
  await capture({
    name: "reader-room-night",
    route: "/readerRoom",
    readySelector: ".reader-shell .room-intro",
    role: 2,
    readerTheme: "night",
  });
  await capture({
    name: "profile-dialog-day",
    route: "/readerRoom",
    readySelector: ".reader-shell .room-intro",
    role: 2,
    readerTheme: "day",
    prepare: async (page) => {
      await page.getByTitle("个人资料").click();
      await page.locator(".profile-dialog .profile-form").waitFor();
    },
  });
  await capture({
    name: "admin-dashboard-day",
    route: "/dashboard",
    readySelector: ".paper-workspace .admin-dashboard",
    role: 0,
    adminTheme: "day",
    waitForCharts: 3,
  });
  await capture({
    name: "book-search-day",
    route: "/bookSearch",
    readySelector: ".reader-shell .book-grid .book-card",
    role: 2,
    readerTheme: "day",
  });
  await capture({
    name: "book-reviews-day",
    route: "/bookReviews",
    readySelector: ".reader-shell .review-entry",
    role: 2,
    readerTheme: "day",
  });
  await capture({
    name: "logistics-workbench",
    route: "/procurementWorkbench",
    readySelector: ".staff-shell .procurement-page .order-table",
    role: 4,
  });
  await capture({
    name: "user-management-night",
    route: "/userManage",
    readySelector: ".admin-shell .admin-table-page .el-table__row",
    role: 0,
    adminTheme: "night",
  });
  await capture({
    name: "login-day",
    route: "/login",
    readySelector: ".auth-world .paper-sheet",
    role: 2,
    readerTheme: "day",
    authenticated: false,
    prepare: async (page) => {
      await page.getByPlaceholder("账号").fill(readerTestAccount.account);
      await page.getByPlaceholder("密码").fill(readerTestAccount.password);
      await page.getByPlaceholder("答案").fill("12");
    },
  });
  await capture({
    name: "register-day",
    route: "/register",
    readySelector: ".auth-world .auth-sheet",
    role: 2,
    readerTheme: "day",
    authenticated: false,
    prepare: async (page) => {
      await page
        .getByPlaceholder("设置登录账号")
        .fill(readerTestAccount.account);
      await page.getByPlaceholder("设置显示名称").fill("砚灯拾页");
      await page
        .getByPlaceholder("输入常用邮箱")
        .fill(`${readerTestAccount.account}@darkroomlibrary.local`);
      await page.getByPlaceholder("请输入验证码").fill("250725");
      await page
        .getByPlaceholder("设置登录密码")
        .fill(readerTestAccount.password);
      await page
        .getByPlaceholder("请再次输入密码")
        .fill(readerTestAccount.password);
      await page.getByRole("heading", { name: "创建读者账号" }).click();
    },
  });
  await capture({
    name: "statistics-dashboard-night",
    route: "/statisticsDashboard",
    readySelector: ".admin-shell .statistics-page",
    role: 0,
    adminTheme: "night",
    waitForCharts: 3,
  });
} finally {
  await browser.close();
}

console.log(`Documentation screenshots written to ${outputDir}`);

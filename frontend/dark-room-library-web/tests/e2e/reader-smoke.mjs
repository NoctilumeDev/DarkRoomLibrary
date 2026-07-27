import fs from "node:fs/promises";
import { chromium } from "playwright-core";

const token = process.env.E2E_TOKEN;
const loginAccount = process.env.E2E_ACCOUNT;
const loginPassword = process.env.E2E_PASSWORD;
const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const edgePath = process.env.EDGE_PATH || "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

if (!token) throw new Error("E2E_TOKEN is required");

const outputDir = "test-results/reader";
await fs.mkdir(outputDir, { recursive: true });
const browser = await chromium.launch({ executablePath: edgePath, headless: true });

async function authenticatedPage(viewport) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript((value) => sessionStorage.setItem("token", value), token);
  const page = await context.newPage();
  return { context, page };
}

async function assertNoHorizontalOverflow(page, label) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth
  );
  if (overflow > 1) throw new Error(`${label} has ${overflow}px horizontal overflow`);
}

function collectRuntimeErrors(page) {
  const errors = [];
  page.on("pageerror", (error) => errors.push(`pageerror: ${error.message}`));
  page.on("response", (response) => {
    if (response.status() === 429 || response.status() >= 500) {
      errors.push(`${response.status()} ${response.url()}`);
    }
  });
  return errors;
}

async function assertReaderSemanticPalette(page, label) {
  const defaults = new Set([
    "rgb(64, 158, 255)",
    "rgb(103, 194, 58)",
    "rgb(230, 162, 60)",
  ]);
  const colors = await page.locator(
    ".reader-action, .reader-status, .reader-segmented .is-active .el-radio-button__inner"
  ).evaluateAll((elements) => elements.flatMap((element) => {
    const style = getComputedStyle(element);
    return [style.color, style.backgroundColor];
  }));
  const leaked = colors.filter((color) => defaults.has(color));
  if (leaked.length) {
    throw new Error(`${label} uses Element Plus default semantic colors: ${leaked.join(", ")}`);
  }
}

try {
  const desktop = await authenticatedPage({ width: 1440, height: 1000 });
  const runtimeErrors = collectRuntimeErrors(desktop.page);
  await desktop.page.goto(`${baseUrl}/#/readerRoom`, { waitUntil: "networkidle" });
  await desktop.page.locator(".room-intro").waitFor();
  await desktop.page.waitForTimeout(500);
  await assertNoHorizontalOverflow(desktop.page, "reader room desktop");
  await desktop.page.screenshot({ path: `${outputDir}/room-night-v2.png`, fullPage: true });

  await desktop.page.locator(".quiet-actions button").first().click();
  await desktop.page.locator('.reader-shell[data-reader-theme="day"]').waitFor();
  await desktop.page.waitForTimeout(650);
  await desktop.page.screenshot({ path: `${outputDir}/room-day-v2.png`, fullPage: true });

  await desktop.page.goto(`${baseUrl}/#/bookSearch`, { waitUntil: "networkidle" });
  await desktop.page.getByRole("heading", { name: "检索台" }).waitFor();
  await desktop.page.waitForTimeout(350);
  await assertNoHorizontalOverflow(desktop.page, "book search desktop");
  const readerTokens = await desktop.page.locator(".reader-shell").evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      ink: style.getPropertyValue("--ink").trim(),
      light: style.getPropertyValue("--light").trim(),
    };
  });
  if (!readerTokens.ink || !readerTokens.light) {
    throw new Error(`reader design tokens are missing: ${JSON.stringify(readerTokens)}`);
  }
  await desktop.page.screenshot({ path: `${outputDir}/search-day-v2.png`, fullPage: true });

  const actionButtons = desktop.page.locator(".reader-action");
  if (await actionButtons.count()) {
    const blueActions = await actionButtons.evaluateAll((elements) =>
      elements.filter((element) => getComputedStyle(element).backgroundColor === "rgb(64, 158, 255)").length
    );
    if (blueActions) throw new Error(`${blueActions} reader actions fell back to Element Plus blue`);
  }

  const openButtons = desktop.page.getByRole("button", { name: "开卷" });
  const openButtonCount = await openButtons.count();
  for (let index = 0; index < openButtonCount; index += 1) {
    await openButtons.nth(index).click();
    await desktop.page.locator(".book-detail-sheet").waitFor();
    if (await desktop.page.evaluate(() => typeof document.startViewTransition === "function")) {
      const transitionName = await desktop.page.locator(".detail-cover").evaluate(
        (element) => element.style.viewTransitionName
      );
      if (transitionName !== "reader-book-cover") {
        throw new Error(`book cover transition identity is missing: ${transitionName}`);
      }
    }
    await desktop.page.waitForTimeout(600);
    if (index === 0) {
      await desktop.page.screenshot({ path: `${outputDir}/book-detail-v2.png` });
    }
    await desktop.page.getByRole("button", { name: "Close" }).click();
    await desktop.page.locator(".book-detail-sheet").waitFor({ state: "hidden" });
  }

  const readerRoutes = [
    ["/myBorrows", "我的借阅"],
    ["/myReservations", "我的预约"],
    ["/myFavorites", "我的收藏"],
    ["/bookReviews", "书评回廊"],
    ["/messageBoard", "留言处"],
  ];
  for (const [path, heading] of readerRoutes) {
    await desktop.page.goto(`${baseUrl}/#${path}`, { waitUntil: "networkidle" });
    await desktop.page.getByRole("heading", { name: heading }).waitFor();
    await desktop.page.waitForTimeout(220);
    await assertNoHorizontalOverflow(desktop.page, `${heading} desktop`);
    if (path === "/myReservations" && await desktop.page.locator(".el-table__row").count() === 0) {
      await desktop.page.getByText("还没有预约记录").waitFor();
    }
    if (path === "/bookReviews" && await desktop.page.locator(".review-entry").count() === 0) {
      await desktop.page.getByText("回廊里还没有留下墨迹").waitFor();
    }
    if (path === "/bookReviews") {
      const segmented = desktop.page.locator(".reader-segmented");
      await segmented.waitFor();
      const overflow = await segmented.evaluate((element) => getComputedStyle(element).overflow);
      if (overflow !== "hidden") throw new Error("reader review segmented control is not edge-clipped");
    }
    await desktop.page.screenshot({
      path: `${outputDir}/${path.slice(1)}-semantic-actions.png`,
      fullPage: true,
    });
  }

  await desktop.page.route("**/borrowRecord/query", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({
      code: 200,
      data: [
        { id: 9101, bookName: "纸上山河", borrowTime: "2026-07-01 10:00:00", dueDate: "2026-08-01", renewCount: 0, status: false },
        { id: 9102, bookName: "夜航札记", borrowTime: "2026-06-01 10:00:00", dueDate: "2026-06-30", renewCount: 1, status: false },
        { id: 9103, bookName: "旧藏目录", borrowTime: "2026-05-01 10:00:00", dueDate: "2026-06-01", returnTime: "2026-05-28", renewCount: 0, status: true },
      ],
      total: 3,
    }),
  }));
  await desktop.page.goto(`${baseUrl}/#/myBorrows`, { waitUntil: "networkidle" });
  await desktop.page.getByRole("heading", { name: "我的借阅" }).waitFor();
  await desktop.page.locator(".reader-status").first().waitFor();
  await desktop.page.waitForTimeout(800);
  await assertReaderSemanticPalette(desktop.page, "borrow states");
  await desktop.page.screenshot({ path: `${outputDir}/myBorrows-states.png`, fullPage: true });

  await desktop.page.route("**/bookReservation/query", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({
      code: 200,
      data: [
        { id: 9201, bookName: "等待的书", reserveTime: "2026-07-12", status: 0 },
        { id: 9202, bookName: "已借之书", reserveTime: "2026-07-10", status: 1 },
        { id: 9203, bookName: "到馆通知", reserveTime: "2026-07-09", notifyTime: "2026-07-14 08:00:00", status: 3 },
        { id: 9204, bookName: "过期旧约", reserveTime: "2026-06-01", status: 4 },
      ],
      total: 4,
    }),
  }));
  await desktop.page.goto(`${baseUrl}/#/myReservations`, { waitUntil: "networkidle" });
  await desktop.page.getByRole("heading", { name: "我的预约" }).waitFor();
  await desktop.page.locator(".reader-status").first().waitFor();
  await desktop.page.waitForTimeout(800);
  await assertReaderSemanticPalette(desktop.page, "reservation states");
  await desktop.page.screenshot({ path: `${outputDir}/myReservations-states.png`, fullPage: true });

  await desktop.page.route("**/bookFavorite/query", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: JSON.stringify({
      code: 200,
      data: [
        { id: 9301, bookId: 1, bookName: "可借藏书", bookAuthor: "佚名", availableCount: 2, createTime: "2026-07-12" },
        { id: 9302, bookId: 2, bookName: "候借藏书", bookAuthor: "佚名", availableCount: 0, createTime: "2026-07-11" },
      ],
      total: 2,
    }),
  }));
  await desktop.page.goto(`${baseUrl}/#/myFavorites`, { waitUntil: "networkidle" });
  await desktop.page.getByRole("heading", { name: "我的收藏" }).waitFor();
  await desktop.page.locator(".reader-action--danger").first().waitFor();
  await desktop.page.waitForTimeout(800);
  await assertReaderSemanticPalette(desktop.page, "favorite actions");
  await desktop.page.screenshot({ path: `${outputDir}/myFavorites-actions.png`, fullPage: true });

  await desktop.page.unroute("**/borrowRecord/query");
  await desktop.page.unroute("**/bookReservation/query");
  await desktop.page.unroute("**/bookFavorite/query");

  const rapidRoutes = [
    "/readerRoom",
    "/bookSearch",
    "/myBorrows",
    "/myReservations",
    "/myFavorites",
    "/bookReviews",
    "/messageBoard",
  ];
  for (let round = 0; round < 3; round += 1) {
    for (const path of rapidRoutes) {
      await desktop.page.evaluate((nextPath) => { window.location.hash = nextPath; }, path);
      await desktop.page.waitForTimeout(35);
    }
  }
  await desktop.page.getByRole("heading", { name: "留言处" }).waitFor();
  await desktop.page.waitForTimeout(500);
  if (runtimeErrors.length) {
    throw new Error(`reader runtime errors:\n${runtimeErrors.join("\n")}`);
  }

  await desktop.page.locator(".quiet-actions button").nth(1).click();
  await desktop.page.locator(".profile-dialog").waitFor();
  await desktop.page.waitForTimeout(250);
  const profileSaveColor = await desktop.page.locator(".profile-save-button").evaluate(
    (element) => getComputedStyle(element).backgroundColor
  );
  if (profileSaveColor !== "rgb(145, 72, 62)") {
    throw new Error(`profile save button color regressed: ${profileSaveColor}`);
  }
  await desktop.page.screenshot({ path: `${outputDir}/profile-v2.png` });
  await desktop.context.close();

  const mobile = await authenticatedPage({ width: 390, height: 844 });
  await mobile.page.goto(`${baseUrl}/#/readerRoom`, { waitUntil: "networkidle" });
  await mobile.page.locator(".mobile-nav").waitFor();
  await mobile.page.waitForTimeout(350);
  await assertNoHorizontalOverflow(mobile.page, "reader room mobile");
  await mobile.page.screenshot({ path: `${outputDir}/room-mobile-v2.png`, fullPage: true });
  await mobile.context.close();

  const guestContext = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  const guest = await guestContext.newPage();
  await guest.goto(`${baseUrl}/#/login`, { waitUntil: "networkidle" });
  await guest.locator(".threshold-entry").waitFor();
  await guest.locator("#reader-paper-grain").waitFor({ state: "attached" });
  const revealStyle = await guest.locator(".door-reveal").evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      display: style.display,
      mask: style.maskImage || style.webkitMaskImage,
    };
  });
  if (revealStyle.display === "none" || !revealStyle.mask || revealStyle.mask === "none") {
    throw new Error(`door reveal mask is unavailable: ${JSON.stringify(revealStyle)}`);
  }
  const sceneLayers = await guest.locator(".door-scene, .door-reveal, .door-mist").evaluateAll(
    (elements) => elements.map((element) => Number.parseInt(getComputedStyle(element).zIndex, 10))
  );
  if (sceneLayers.some((zIndex) => !Number.isFinite(zIndex) || zIndex < 0)) {
    throw new Error(`login scene uses an unstable negative layer: ${JSON.stringify(sceneLayers)}`);
  }
  await guest.mouse.move(1320, 120);
  await guest.waitForTimeout(700);
  const lightPosition = await guest.locator(".auth-page").evaluate((element) => {
    const style = getComputedStyle(element);
    return {
      x: Number.parseFloat(style.getPropertyValue("--reader-light-x")),
      y: Number.parseFloat(style.getPropertyValue("--reader-light-y")),
      lampX: Number.parseFloat(style.getPropertyValue("--lamp-x")),
      lampY: Number.parseFloat(style.getPropertyValue("--lamp-y")),
    };
  });
  if (
    Math.abs(lightPosition.x - lightPosition.lampX) > 6
    || Math.abs(lightPosition.y - lightPosition.lampY) > 4
  ) {
    throw new Error(`door light moved beyond the threshold area: ${JSON.stringify(lightPosition)}`);
  }
  await guest.mouse.click(1100, 500);
  if (!(await guest.locator(".threshold-entry").isVisible())) {
    throw new Error("clicking outside the light unexpectedly opened the login paper");
  }
  await guest.screenshot({ path: `${outputDir}/threshold-v2.png`, fullPage: true });
  await guest.locator(".auth-theme-toggle").click();
  await guest.locator('.auth-page[data-reader-theme="day"]').waitFor();
  await guest.waitForTimeout(320);
  const dayThemeTransition = await guest.locator(".door-mist").evaluate((element) => ({
    night: Number(getComputedStyle(element, "::before").opacity),
    day: Number(getComputedStyle(element, "::after").opacity),
    sceneDay: Number(getComputedStyle(document.querySelector(".door-scene"), "::after").opacity),
    revealDay: Number(getComputedStyle(document.querySelector(".door-reveal"), "::after").opacity),
  }));
  if (
    dayThemeTransition.night <= 0
    || dayThemeTransition.night >= 1
    || dayThemeTransition.day <= 0
    || dayThemeTransition.day >= 1
    || dayThemeTransition.sceneDay <= 0
    || dayThemeTransition.sceneDay >= 1
    || dayThemeTransition.revealDay <= 0
    || dayThemeTransition.revealDay >= 1
  ) {
    throw new Error(`day scene did not crossfade: ${JSON.stringify(dayThemeTransition)}`);
  }
  await guest.waitForTimeout(1100);
  await guest.screenshot({ path: `${outputDir}/threshold-day-v2.png`, fullPage: true });
  await guest.locator(".auth-theme-toggle").click();
  await guest.locator('.auth-page[data-reader-theme="night"]').waitFor();
  await guest.waitForTimeout(320);
  const nightThemeTransition = await guest.locator(".door-mist").evaluate((element) => ({
    night: Number(getComputedStyle(element, "::before").opacity),
    day: Number(getComputedStyle(element, "::after").opacity),
    sceneDay: Number(getComputedStyle(document.querySelector(".door-scene"), "::after").opacity),
    revealDay: Number(getComputedStyle(document.querySelector(".door-reveal"), "::after").opacity),
  }));
  if (
    nightThemeTransition.night <= 0
    || nightThemeTransition.night >= 1
    || nightThemeTransition.day <= 0
    || nightThemeTransition.day >= 1
    || nightThemeTransition.sceneDay <= 0
    || nightThemeTransition.sceneDay >= 1
    || nightThemeTransition.revealDay <= 0
    || nightThemeTransition.revealDay >= 1
  ) {
    throw new Error(`night scene did not crossfade: ${JSON.stringify(nightThemeTransition)}`);
  }
  await guest.waitForTimeout(1000);
  await guest.locator(".threshold-entry").click();
  await guest.locator(".paper-sheet").waitFor();
  await guest.waitForTimeout(800);
  await assertNoHorizontalOverflow(guest, "login paper desktop");
  const captchaLayout = await guest.locator(".captcha-question").evaluate((element) => {
    const refresh = element.querySelector("small");
    const expression = element.querySelector("span");
    const label = document.querySelector(".captcha-label");
    const originalExpression = expression.textContent;
    expression.textContent = "20 × 20 = ?";
    const longExpressionRefreshHeight = refresh.getBoundingClientRect().height;
    const longExpressionClipped = expression.scrollWidth > expression.clientWidth;
    expression.textContent = originalExpression;
    return {
      height: element.getBoundingClientRect().height,
      refreshHeight: refresh.getBoundingClientRect().height,
      longExpressionRefreshHeight,
      longExpressionClipped,
      refreshWhiteSpace: getComputedStyle(refresh).whiteSpace,
      labelWidth: label.getBoundingClientRect().width,
    };
  });
  if (captchaLayout.height !== 40 || captchaLayout.refreshHeight > 18
      || captchaLayout.longExpressionRefreshHeight > 18
      || captchaLayout.longExpressionClipped
      || captchaLayout.refreshWhiteSpace !== "nowrap" || captchaLayout.labelWidth < 47) {
    throw new Error(`login captcha layout is unstable: ${JSON.stringify(captchaLayout)}`);
  }
  await guest.setViewportSize({ width: 850, height: 900 });
  await guest.waitForTimeout(150);
  const narrowCaptcha = await guest.locator(".captcha-question").evaluate((element) => {
    const expression = element.querySelector("span");
    const refresh = element.querySelector("small");
    const accountInput = document.querySelector(".auth-card .el-input__inner");
    const answerInput = document.querySelector(".captcha-row > .el-input");
    const originalExpression = expression.textContent;
    expression.textContent = "20 × 20 = ?";
    const expressionRange = document.createRange();
    expressionRange.selectNodeContents(expression);
    const metrics = {
      questionWidth: element.clientWidth,
      expressionWidth: expression.clientWidth,
      expressionScrollWidth: expression.scrollWidth,
      expressionTextX: expressionRange.getBoundingClientRect().x,
      accountTextX: accountInput.getBoundingClientRect().x,
      answerWidth: answerInput.getBoundingClientRect().width,
      refreshHeight: refresh.getBoundingClientRect().height,
    };
    expression.textContent = originalExpression;
    return metrics;
  });
  if (narrowCaptcha.expressionScrollWidth > narrowCaptcha.expressionWidth
      || Math.abs(narrowCaptcha.expressionTextX - narrowCaptcha.accountTextX) > 1.5
      || Math.abs(narrowCaptcha.answerWidth - 76) > 1
      || narrowCaptcha.refreshHeight > 18) {
    throw new Error(`login captcha clips at 850px: ${JSON.stringify(narrowCaptcha)}`);
  }
  await guest.setViewportSize({ width: 1440, height: 1000 });
  await guest.waitForTimeout(150);
  await guest.screenshot({ path: `${outputDir}/login-paper-v2.png`, fullPage: true });
  await guest.getByRole("button", { name: "返回门外" }).click();
  await guest.locator(".threshold-entry").waitFor();
  await guest.locator(".threshold-entry").click();
  await guest.locator(".paper-sheet").waitFor();

  if (loginAccount && loginPassword) {
    await guest.getByPlaceholder("账号").fill(loginAccount);
    await guest.getByPlaceholder("密码").fill(loginPassword);
    await guest.waitForFunction(() => {
      const text = document.querySelector(".captcha-question span")?.textContent || "";
      return /-?\d+\s*[+\-×÷*]\s*-?\d+/.test(text);
    }, null, { timeout: 10000 });
    const expression = await guest.locator(".captcha-question span").innerText();
    const match = expression.match(/(-?\d+)\s*([+\-×÷*])\s*(-?\d+)/);
    if (!match) throw new Error(`unexpected captcha expression: ${expression}`);
    const left = Number(match[1]);
    const right = Number(match[3]);
    const answer = match[2] === "+"
      ? left + right
      : match[2] === "-"
        ? left - right
        : match[2] === "÷"
          ? left / right
          : left * right;
    await guest.getByPlaceholder("答案").fill(String(answer));
    await guest.getByRole("button", { name: "进入藏书室" }).click();
    await guest.waitForURL(/#\/readerRoom$/, { timeout: 10000 });
    await guest.locator(".room-intro h1").waitFor();
  }

  await guest.goto(`${baseUrl}/#/register`, { waitUntil: "networkidle" });
  const registerHeading = guest.getByRole("heading", { name: "创建读者账号" });
  await registerHeading.waitFor();
  await assertNoHorizontalOverflow(guest, "register desktop");
  const passwordInput = guest.getByPlaceholder("设置登录密码");
  await passwordInput.focus();
  await guest.locator(".password-guide").waitFor();
  await passwordInput.fill("Abcdef12");
  await guest.locator(".password-guide header").getByText("已经满足").waitFor();
  await guest.getByPlaceholder("请再次输入密码").fill("Abcdef12");
  await guest.locator(".password-guide").getByText("两次密码一致").waitFor();
  await guest.waitForTimeout(350);
  await guest.screenshot({ path: `${outputDir}/register-guide-v2.png`, fullPage: true });
  await registerHeading.click();
  await guest.locator(".password-guide").waitFor({ state: "hidden" });
  await guest.screenshot({ path: `${outputDir}/register-v2.png`, fullPage: true });
  await guestContext.close();

  const reducedContext = await browser.newContext({
    viewport: { width: 390, height: 844 },
    reducedMotion: "reduce",
  });
  const reducedPage = await reducedContext.newPage();
  await reducedPage.goto(`${baseUrl}/#/login`, { waitUntil: "networkidle" });
  await reducedPage.locator(".threshold-entry").click();
  await reducedPage.locator(".paper-sheet").waitFor();
  await assertNoHorizontalOverflow(reducedPage, "login paper reduced-motion mobile");
  const fieldBounds = await reducedPage.locator(".auth-card .el-form-item__content").evaluateAll(
    (elements) => elements.map((element) => {
      const bounds = element.getBoundingClientRect();
      return { left: bounds.left, right: bounds.right };
    })
  );
  const leftEdges = fieldBounds.map((bounds) => bounds.left);
  const rightEdges = fieldBounds.map((bounds) => bounds.right);
  if (Math.max(...leftEdges) - Math.min(...leftEdges) > 1
      || Math.max(...rightEdges) - Math.min(...rightEdges) > 1) {
    throw new Error(`login fields are not aligned: ${JSON.stringify(fieldBounds)}`);
  }
  const animationDuration = await reducedPage.locator(".paper-sheet").evaluate(
    (element) => getComputedStyle(element).animationDuration
  );
  const animationSeconds = Number.parseFloat(animationDuration);
  if (!Number.isFinite(animationSeconds) || animationSeconds > 0.001) {
    throw new Error(`reduced-motion fallback is not active: ${animationDuration}`);
  }
  await reducedPage.screenshot({ path: `${outputDir}/login-mobile-reduced.png`, fullPage: true });
  await reducedContext.close();
} finally {
  await browser.close();
}

console.log("READER_E2E_OK");

import fs from "node:fs/promises";
import { chromium } from "playwright-core";

const token = process.env.E2E_TOKEN;
const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";

if (!token) throw new Error("E2E_TOKEN is required");

const outputDir = "test-results/admin";
await fs.mkdir(outputDir, { recursive: true });
const browser = await chromium.launch({ executablePath: edgePath, headless: true });

async function authenticatedPage(viewport) {
  const context = await browser.newContext({ viewport });
  await context.addInitScript((value) => {
    sessionStorage.setItem("token", value);
    localStorage.setItem("admin-theme", "day");
  }, token);
  return { context, page: await context.newPage() };
}

async function assertNoHorizontalOverflow(page, label) {
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - document.documentElement.clientWidth
  );
  if (overflow > 1) throw new Error(`${label} has ${overflow}px horizontal overflow`);
}

async function assertChartSizes(page, minimumCount, label) {
  await page.waitForFunction(
    (expected) => document.querySelectorAll("canvas").length >= expected,
    minimumCount,
    { timeout: 8000 }
  );
  const charts = page.locator("canvas");
  const count = await charts.count();
  if (count < minimumCount) {
    throw new Error(`${label} expected ${minimumCount} charts, received ${count}`);
  }
  for (let index = 0; index < count; index += 1) {
    const box = await charts.nth(index).boundingBox();
    if (!box || box.width < 260 || box.height < 200) {
      throw new Error(`${label} chart ${index + 1} has invalid dimensions`);
    }
  }
}

async function waitForDeveloped(page) {
  await page.waitForFunction(() => {
    const pageRoot = document.querySelector(".paper-workspace > *");
    return !pageRoot || Number.parseFloat(getComputedStyle(pageRoot).opacity) >= 0.99;
  });
}

async function assertToolbarBaseline(page) {
  const controls = page.locator(".book-toolbar > .toolbar-field, .book-toolbar > .toolbar-action");
  const count = await controls.count();
  if (count !== 9) {
    throw new Error(`book toolbar expected 9 control groups, received ${count}`);
  }
  const bottoms = [];
  for (let index = 0; index < count; index += 1) {
    const box = await controls.nth(index).boundingBox();
    if (!box) throw new Error(`book toolbar control ${index + 1} is not visible`);
    bottoms.push(box.y + box.height);
  }
  if (Math.max(...bottoms) - Math.min(...bottoms) > 2) {
    throw new Error(`book toolbar controls are not bottom-aligned: ${bottoms.join(", ")}`);
  }
}

async function assertEditorDialog(page, {
  route,
  button,
  modifier,
  minimumHeight,
  screenshot,
}) {
  await page.goto(`${baseUrl}/#${route}`, { waitUntil: "networkidle" });
  await page.locator(".admin-table-page").waitFor();
  await waitForDeveloped(page);
  await page.getByRole("button", { name: button }).click();
  const dialog = page.locator(`.admin-editor-dialog.${modifier}`);
  await dialog.waitFor();
  await page.waitForTimeout(250);
  const box = await dialog.boundingBox();
  if (!box || box.height < minimumHeight || box.y < 0 || box.y + box.height > 970) {
    throw new Error(`${button} dialog is outside the usable viewport: ${JSON.stringify(box)}`);
  }
  const overflow = await page.locator(".admin-form-scroll").evaluate((element) => ({
    overflowY: getComputedStyle(element).overflowY,
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  if (overflow.overflowY !== "auto" || overflow.clientHeight <= 0) {
    throw new Error(`${button} dialog scroll area is invalid: ${JSON.stringify(overflow)}`);
  }
  const submitColor = await dialog.locator(".admin-dialog-submit").evaluate(
    (element) => getComputedStyle(element).backgroundColor
  );
  if (submitColor === "rgb(64, 158, 255)") {
    throw new Error(`${button} dialog fell back to Element Plus blue`);
  }
  await page.screenshot({ path: `${outputDir}/${screenshot}` });
  await dialog.getByRole("button", { name: "取消" }).click();
}

async function assertProcurementDialog(page) {
  await page.goto(`${baseUrl}/#/procurementManage`, { waitUntil: "networkidle" });
  await page.locator(".procurement-page").waitFor();
  await waitForDeveloped(page);
  await page.getByRole("button", { name: "新建采购单" }).click();
  const dialog = page.locator(".procurement-create-dialog");
  await dialog.waitFor();
  await page.waitForTimeout(250);
  const box = await dialog.boundingBox();
  if (!box || box.height < 600 || box.y < 0 || box.y + box.height > 970) {
    throw new Error(`procurement dialog is outside the usable viewport: ${JSON.stringify(box)}`);
  }
  const overflow = await dialog.locator(".procurement-form-scroll").evaluate((element) => ({
    overflowY: getComputedStyle(element).overflowY,
    clientHeight: element.clientHeight,
  }));
  if (overflow.overflowY !== "auto" || overflow.clientHeight < 400) {
    throw new Error(`procurement dialog scroll area is invalid: ${JSON.stringify(overflow)}`);
  }
  const submitColor = await dialog.locator(".admin-dialog-submit").evaluate(
    (element) => getComputedStyle(element).backgroundColor
  );
  if (submitColor === "rgb(64, 158, 255)") {
    throw new Error("procurement dialog fell back to Element Plus blue");
  }
  await page.screenshot({ path: `${outputDir}/procurement-create-dialog-night.png` });
  await dialog.getByRole("button", { name: "取消" }).click();
}

async function assertCirculationSemantics(page) {
  await page.route("**/borrowRecord/query", async (route) => {
    if (route.request().method() !== "POST") return route.continue();
    await route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        code: 200,
        data: [
          {
            id: 9101, userName: "砚灯拾页", bookName: "借阅中的书",
            borrowTime: "2026-07-01 09:00:00", dueDate: "2026-07-31 09:00:00",
            returnTime: null, status: false, fineAmount: 0,
          },
          {
            id: 9102, userName: "砚灯拾页", bookName: "已归还的书",
            borrowTime: "2026-06-01 09:00:00", dueDate: "2026-07-01 09:00:00",
            returnTime: "2026-06-28 11:00:00", status: true, fineAmount: 0,
          },
        ],
        total: 2,
      }),
    });
  });
  await page.goto(`${baseUrl}/#/borrowManage`, { waitUntil: "networkidle" });
  await page.locator(".circulation-status--borrowing").waitFor();
  await page.locator(".circulation-status--complete").waitFor();
  const action = page.locator(".circulation-action--return");
  await action.waitFor();
  const actionStyle = await action.evaluate((element) => ({
    color: getComputedStyle(element).color,
    radius: getComputedStyle(element).borderRadius,
  }));
  if (actionStyle.color === "rgb(64, 158, 255)" || actionStyle.radius !== "4px") {
    throw new Error(`circulation action style is inconsistent: ${JSON.stringify(actionStyle)}`);
  }
  await page.screenshot({ path: `${outputDir}/borrow-manage-semantics-night.png` });
  await page.unroute("**/borrowRecord/query");
}

async function assertNoticeEditor(page) {
  await page.goto(`${baseUrl}/#/createNotice`, { waitUntil: "networkidle" });
  const editor = page.locator(".notice-rich-editor");
  await editor.waitFor();
  const content = editor.locator(".editor-content");
  const box = await content.boundingBox();
  if (!box || box.height < 400) {
    throw new Error(`notice editor has invalid dimensions: ${JSON.stringify(box)}`);
  }
  await content.fill("公告编辑器回归检查");
  if ((await content.textContent()) !== "公告编辑器回归检查") {
    throw new Error("notice editor did not retain typed content");
  }
  if (await page.locator('[class*="w-e-"]').count()) {
    throw new Error("legacy WangEditor nodes remain in the notice editor");
  }
}

async function assertThemeContrast(page, label) {
  const failures = await page.evaluate(() => {
    const styles = getComputedStyle(document.querySelector(".admin-shell"));
    const read = (name) => styles.getPropertyValue(name).trim();
    const luminance = (hex) => {
      const channels = hex
        .slice(1)
        .match(/../g)
        .map((value) => Number.parseInt(value, 16) / 255)
        .map((value) =>
          value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4
        );
      return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
    };
    const ratio = (first, second) => {
      const one = luminance(first);
      const two = luminance(second);
      return (Math.max(one, two) + 0.05) / (Math.min(one, two) + 0.05);
    };
    const pairs = [
      ["--admin-text", "--admin-surface"],
      ["--admin-text-secondary", "--admin-surface"],
      ["--admin-muted", "--admin-surface-muted"],
      ["--admin-gold", "--admin-surface"],
      ["--admin-accent-solid", "#fffaf2"],
      ["--admin-jade-solid", "#fffaf2"],
      ["--admin-gold-solid", "#fffaf2"],
      ["--admin-danger-solid", "#fffaf2"],
    ];
    return pairs
      .map(([firstName, secondName]) => {
        const first = firstName.startsWith("--") ? read(firstName) : firstName;
        const second = secondName.startsWith("--") ? read(secondName) : secondName;
        return { firstName, secondName, value: ratio(first, second) };
      })
      .filter((item) => item.value < 4.5);
  });
  if (failures.length) {
    throw new Error(`${label} contrast failures: ${JSON.stringify(failures)}`);
  }
}

try {
  const desktop = await authenticatedPage({ width: 1440, height: 1000 });
  await desktop.page.goto(`${baseUrl}/#/dashboard`, { waitUntil: "networkidle" });
  await desktop.page.locator(".admin-dashboard").waitFor();
  await waitForDeveloped(desktop.page);
  await assertNoHorizontalOverflow(desktop.page, "admin dashboard day");
  await assertThemeContrast(desktop.page, "admin dashboard day");
  await assertChartSizes(desktop.page, 3, "admin dashboard day");
  await desktop.page.screenshot({
    path: `${outputDir}/dashboard-day.png`,
    fullPage: true,
  });

  await desktop.page.setViewportSize({ width: 430, height: 932 });
  await desktop.page.waitForTimeout(350);
  await assertNoHorizontalOverflow(desktop.page, "admin dashboard mobile");
  await desktop.page.screenshot({
    path: `${outputDir}/dashboard-mobile.png`,
    fullPage: true,
  });
  await desktop.page.setViewportSize({ width: 1440, height: 1000 });
  await desktop.page.waitForTimeout(250);

  await desktop.page.locator(".theme-toggle").click();
  await desktop.page.locator('.admin-shell[data-admin-theme="night"]').waitFor();
  await desktop.page.waitForTimeout(450);
  await assertThemeContrast(desktop.page, "admin dashboard night");
  await assertChartSizes(desktop.page, 3, "admin dashboard night");
  await desktop.page.screenshot({
    path: `${outputDir}/dashboard-night.png`,
    fullPage: true,
  });

  await desktop.page.goto(`${baseUrl}/#/statisticsDashboard`, {
    waitUntil: "networkidle",
  });
  await desktop.page.locator(".statistics-page").waitFor();
  await waitForDeveloped(desktop.page);
  await assertNoHorizontalOverflow(desktop.page, "statistics dashboard");
  await assertChartSizes(desktop.page, 3, "statistics dashboard");
  await desktop.page.screenshot({
    path: `${outputDir}/statistics-night.png`,
    fullPage: true,
  });

  await desktop.page.goto(`${baseUrl}/#/dataExport`, { waitUntil: "networkidle" });
  await desktop.page.locator(".export-page").waitFor();
  await waitForDeveloped(desktop.page);
  if ((await desktop.page.locator(".export-row").count()) !== 4) {
    throw new Error("data export should render four full-width export rows");
  }
  await assertNoHorizontalOverflow(desktop.page, "data export desktop");
  await desktop.page.screenshot({
    path: `${outputDir}/data-export-night.png`,
    fullPage: true,
  });

  await desktop.page.goto(`${baseUrl}/#/userManage`, { waitUntil: "networkidle" });
  await desktop.page.locator(".admin-table-page").waitFor();
  await waitForDeveloped(desktop.page);
  await assertNoHorizontalOverflow(desktop.page, "user management desktop");
  await desktop.page.screenshot({
    path: `${outputDir}/user-manage-night.png`,
    fullPage: true,
  });
  await assertEditorDialog(desktop.page, {
    route: "/userManage",
    button: "新增用户",
    modifier: "admin-editor-dialog--user",
    minimumHeight: 660,
    screenshot: "user-editor-dialog-night.png",
  });

  await desktop.page.goto(`${baseUrl}/#/bookManage`, { waitUntil: "networkidle" });
  await desktop.page.locator(".book-toolbar").waitFor();
  await waitForDeveloped(desktop.page);
  await assertToolbarBaseline(desktop.page);
  await assertNoHorizontalOverflow(desktop.page, "book management desktop");
  await desktop.page.screenshot({
    path: `${outputDir}/book-manage-night.png`,
    fullPage: true,
  });

  await desktop.page.getByRole("button", { name: "新增图书" }).click();
  const bookDialog = desktop.page.locator(".book-editor-dialog");
  await bookDialog.waitFor();
  await desktop.page.waitForTimeout(450);
  const dialogBox = await bookDialog.boundingBox();
  if (!dialogBox || dialogBox.height < 760 || dialogBox.y > 80
      || dialogBox.y + dialogBox.height > 950) {
    throw new Error(`book editor dialog is outside the usable viewport: ${JSON.stringify(dialogBox)}`);
  }
  const scrollMetrics = await desktop.page.locator(".book-form-scroll").evaluate((element) => ({
    clientHeight: element.clientHeight,
    scrollHeight: element.scrollHeight,
  }));
  if (scrollMetrics.clientHeight < 560 || scrollMetrics.scrollHeight <= scrollMetrics.clientHeight) {
    throw new Error(`book editor scroll area is invalid: ${JSON.stringify(scrollMetrics)}`);
  }
  await desktop.page.screenshot({
    path: `${outputDir}/book-editor-dialog-night.png`,
  });
  await desktop.page.getByRole("button", { name: "取消" }).click();

  await desktop.page.getByText("已删除", { exact: true }).click();
  await desktop.page.waitForTimeout(350);
  await assertToolbarBaseline(desktop.page);
  const toggleSeams = await desktop.page
    .locator(".collection-view-toggle .el-radio-button__inner")
    .evaluateAll((elements) => elements.map((element) => {
      const style = getComputedStyle(element);
      return { borderLeftWidth: style.borderLeftWidth, outlineWidth: style.outlineWidth };
    }));
  if (toggleSeams.some((style) => style.borderLeftWidth !== "0px" || style.outlineWidth !== "0px")) {
    throw new Error(`collection view toggle contains an internal seam: ${JSON.stringify(toggleSeams)}`);
  }
  await desktop.page.getByText("正常馆藏", { exact: true }).click();
  await desktop.page.waitForTimeout(350);
  await assertToolbarBaseline(desktop.page);

  await assertEditorDialog(desktop.page, {
    route: "/categoryManage",
    button: "新增分类",
    modifier: "admin-editor-dialog--compact",
    minimumHeight: 210,
    screenshot: "category-editor-dialog-night.png",
  });
  await assertEditorDialog(desktop.page, {
    route: "/bookshelfManage",
    button: "新增书架",
    modifier: "admin-editor-dialog--medium",
    minimumHeight: 560,
    screenshot: "bookshelf-editor-dialog-night.png",
  });

  await assertProcurementDialog(desktop.page);
  await assertCirculationSemantics(desktop.page);
  await assertNoticeEditor(desktop.page);

  const remainingRoutes = [
    "/categoryManage",
    "/bookshelfManage",
    "/borrowManage",
    "/noticeManage",
    "/createNotice",
    "/procurementManage",
    "/contentAudit",
    "/messageManage",
    "/operationLog",
    "/workflowStatus",
    "/fileManage",
  ];
  for (const route of remainingRoutes) {
    await desktop.page.goto(`${baseUrl}/#${route}`, { waitUntil: "networkidle" });
    await desktop.page.locator(".admin-shell").waitFor();
    await waitForDeveloped(desktop.page);
    await assertNoHorizontalOverflow(desktop.page, `${route} desktop`);
  }
  await desktop.context.close();
} finally {
  await browser.close();
}

console.log("Admin E2E smoke test passed.");

import fs from "node:fs/promises";
import { chromium } from "playwright-core";
import { getAccount } from "./test-accounts.mjs";

const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const apiBaseUrl =
  process.env.E2E_API_BASE_URL ||
  "http://localhost:20606/api/dark-room-library/v1";
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const validationDate = "2026-07-26";
const outputDir = "test-results/full-workflow";
const bookName = "暗室藏书";

const accounts = Object.freeze({
  root: getAccount("root"),
  coordinator: getAccount("coordinator"),
  reader: getAccount("reader"),
  purchaser: getAccount("purchaser"),
  logistics: getAccount("logistics"),
});

await fs.mkdir(outputDir, { recursive: true });

async function apiRequest(token, path, { method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`${method} ${path} returned invalid JSON: ${text.slice(0, 200)}`);
  }
  return { response, payload };
}

function solveCaptcha(expression) {
  const match = expression.match(/(-?\d+)\s*([+\-×*xX])\s*(-?\d+)/);
  if (!match) throw new Error(`Unsupported captcha expression: ${expression}`);
  const left = Number(match[1]);
  const right = Number(match[3]);
  if (match[2] === "+") return left + right;
  if (match[2] === "-") return left - right;
  return left * right;
}

async function login(identity) {
  const captcha = await apiRequest(null, "/captcha/generate");
  if (!captcha.response.ok || captcha.payload?.code !== 200) {
    throw new Error(`Captcha generation failed for ${identity.account}`);
  }
  const answer = solveCaptcha(captcha.payload.data.expression);
  const result = await apiRequest(null, "/user/login", {
    method: "POST",
    body: {
      userAccount: identity.account,
      userPwd: identity.password,
      captchaId: captcha.payload.data.captchaId,
      captchaAnswer: answer,
    },
  });
  if (!result.response.ok || result.payload?.code !== 200) {
    throw new Error(`${identity.account} login failed: ${result.payload?.msg}`);
  }
  if (Number(result.payload.data.role) !== identity.role) {
    throw new Error(`${identity.account} returned role ${result.payload.data.role}`);
  }
  return result.payload.data.token;
}

async function requireSuccess(token, path, options) {
  const result = await apiRequest(token, path, options);
  if (!result.response.ok || result.payload?.code !== 200) {
    throw new Error(
      `${options?.method || "GET"} ${path} failed: ${result.payload?.msg || result.response.status}`
    );
  }
  return result.payload;
}

async function queryOne(token, path, body, label) {
  const payload = await requireSuccess(token, path, { method: "POST", body });
  const rows = payload.data || [];
  if (rows.length !== 1) {
    throw new Error(`Expected one ${label}, received ${rows.length}`);
  }
  return rows[0];
}

function attachDiagnostics(page, label, report) {
  const errors = [];
  page.on("console", (message) => {
    if (message.type() === "error") {
      errors.push(`console ${message.text()}`);
    }
  });
  page.on("pageerror", (error) => errors.push(`pageerror ${error.message}`));
  page.on("requestfailed", (request) => {
    errors.push(
      `requestfailed ${request.method()} ${request.url()} ${request.failure()?.errorText || ""}`
    );
  });
  page.on("response", (response) => {
    if (response.url().startsWith(apiBaseUrl)) {
      report.apiResponses += 1;
      const key = `${response.request().method()} ${new URL(response.url()).pathname}`;
      report.apiEndpoints[key] = (report.apiEndpoints[key] || 0) + 1;
      if (response.status() >= 400) {
        errors.push(`network ${response.status()} ${response.url()}`);
      }
    }
  });
  return () => {
    if (errors.length) {
      throw new Error(`${label} browser diagnostics:\n${errors.join("\n")}`);
    }
  };
}

async function openAuthenticatedPage(browser, token, label, report) {
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 } });
  await context.addInitScript((value) => sessionStorage.setItem("token", value), token);
  const page = await context.newPage();
  const assertDiagnostics = attachDiagnostics(page, label, report);
  return { context, page, assertDiagnostics };
}

async function waitForApiResponse(page, method, path, action) {
  const responsePromise = page.waitForResponse(
    (response) =>
      response.request().method() === method &&
      response.url().includes(`${apiBaseUrl}${path}`)
  );
  await action();
  const response = await responsePromise;
  const payload = await response.json();
  if (!response.ok() || payload.code !== 200) {
    throw new Error(`${method} ${path} failed in UI: ${payload.msg || response.status()}`);
  }
  return payload;
}

async function findOrderRow(page, orderId) {
  const rows = page.locator(".order-table .el-table__row");
  await rows.first().waitFor();
  for (let index = 0; index < (await rows.count()); index += 1) {
    const firstCell = await rows.nth(index).locator("td").first().innerText();
    if (firstCell.trim() === String(orderId)) return rows.nth(index);
  }
  throw new Error(`Order ${orderId} is not visible in the workbench`);
}

async function chooseSelectOption(page, scope, label, optionName) {
  const field = scope.locator(".el-form-item").filter({ hasText: label }).first();
  await field.locator(".el-select").click();
  const dropdown = page.locator(".el-select-dropdown:visible");
  await dropdown.waitFor();
  await dropdown.getByText(optionName, { exact: false }).first().click();
}

async function waitForSwalToClose(page) {
  const container = page.locator(".swal2-container");
  if (await container.count()) {
    await container.waitFor({ state: "detached", timeout: 5000 }).catch(() => {});
  }
}

async function runReaderBorrowReturn(browser, tokens, report) {
  const session = await openAuthenticatedPage(browser, tokens.reader, "reader workflow", report);
  const { page } = session;
  try {
    await page.goto(`${baseUrl}/#/bookSearch`, { waitUntil: "networkidle" });
    const card = page.locator(".book-card").filter({ hasText: bookName }).first();
    await card.waitFor();
    const cover = card.locator(".cover img");
    await cover.waitFor();
    const coverLoaded = await cover.evaluate(
      (image) => image.complete && image.naturalWidth > 0 && image.naturalHeight > 0
    );
    if (!coverLoaded) throw new Error(`${bookName} cover did not load`);

    const borrowResponse = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        response.url().includes(`${apiBaseUrl}/borrowRecord/borrow/`)
    );
    await card.getByRole("button", { name: "借阅", exact: true }).click();
    await page.locator(".swal2-confirm").click();
    const borrowed = await borrowResponse;
    const borrowedPayload = await borrowed.json();
    if (!borrowed.ok() || borrowedPayload.code !== 200) {
      throw new Error(`Reader borrow failed: ${borrowedPayload.msg}`);
    }
    await card.getByRole("button", { name: "借阅中", exact: true }).waitFor();
    await page.screenshot({
      path: `${outputDir}/reader-borrowed.png`,
      fullPage: true,
    });
    await waitForSwalToClose(page);

    await page.goto(`${baseUrl}/#/myBorrows`, { waitUntil: "networkidle" });
    const row = page.locator(".el-table__row").filter({ hasText: bookName }).first();
    await row.waitFor();
    const returnResponse = page.waitForResponse(
      (response) =>
        response.request().method() === "POST" &&
        response.url().includes(`${apiBaseUrl}/borrowRecord/return/`)
    );
    await row.getByRole("button", { name: "还书", exact: true }).click();
    await page.locator(".swal2-confirm").click();
    const returned = await returnResponse;
    const returnedPayload = await returned.json();
    if (!returned.ok() || returnedPayload.code !== 200) {
      throw new Error(`Reader return failed: ${returnedPayload.msg}`);
    }
    await page.waitForTimeout(400);
    await page.screenshot({
      path: `${outputDir}/reader-returned.png`,
      fullPage: true,
    });
  } finally {
    session.assertDiagnostics();
    await session.context.close();
  }
}

async function setCoordinatorFlag(page, enabled) {
  await page.goto(`${baseUrl}/#/userManage`, { waitUntil: "networkidle" });
  const row = page.locator(".el-table__row").filter({ hasText: "守卷青梧" }).first();
  await row.waitFor();
  await row.getByText("编辑", { exact: true }).click();
  const dialog = page.locator(".admin-editor-dialog--user");
  await dialog.waitFor();
  const coordinatorSwitch = dialog.locator(".coordinator-admin-switch");
  const currentlyEnabled = await coordinatorSwitch.evaluate((element) =>
    element.classList.contains("is-checked")
  );
  if (currentlyEnabled !== enabled) {
    await coordinatorSwitch.click();
  }
  await waitForApiResponse(page, "PUT", "/user/backUpdate", async () => {
    await dialog.getByRole("button", { name: "修改", exact: true }).click();
  });
  await dialog.waitFor({ state: "hidden" });
  await waitForSwalToClose(page);
}

async function runCoordinatorToggle(browser, tokens, report) {
  const session = await openAuthenticatedPage(browser, tokens.root, "coordinator workflow", report);
  try {
    await setCoordinatorFlag(session.page, false);
    const disabled = await queryOne(
      tokens.root,
      "/user/query",
      { current: 1, size: 10, userAccount: accounts.coordinator.account },
      "coordinator account"
    );
    if (disabled.isCoordinatorAdmin) {
      throw new Error("Coordinator flag remained enabled after UI update");
    }
    await setCoordinatorFlag(session.page, true);
    const enabled = await queryOne(
      tokens.root,
      "/user/query",
      { current: 1, size: 10, userAccount: accounts.coordinator.account },
      "coordinator account"
    );
    if (!enabled.isCoordinatorAdmin) {
      throw new Error("Coordinator flag was not restored after UI update");
    }
    await session.page.screenshot({
      path: `${outputDir}/coordinator-restored.png`,
      fullPage: true,
    });
  } finally {
    session.assertDiagnostics();
    await session.context.close();
  }
}

async function createProcurementOrder(browser, tokens, report) {
  const session = await openAuthenticatedPage(browser, tokens.root, "procurement create", report);
  try {
    await session.page.goto(`${baseUrl}/#/procurementManage`, { waitUntil: "networkidle" });
    await session.page.getByRole("button", { name: "新建采购单" }).click();
    const dialog = session.page.locator(".procurement-create-dialog");
    await dialog.waitFor();
    await chooseSelectOption(session.page, dialog, "图书", bookName);
    const countInput = dialog
      .locator(".el-form-item")
      .filter({ hasText: "采购数量" })
      .locator("input");
    await countInput.fill("2");
    await chooseSelectOption(session.page, dialog, "采购员", "采书星阑");
    await dialog
      .locator(".el-form-item")
      .filter({ hasText: "申请说明" })
      .locator("textarea")
      .fill(`全链路验收 ${validationDate}：补充两册并核对消息、物流、入库和审计。`);
    await waitForApiResponse(session.page, "POST", "/procurement/save", async () => {
      await dialog.getByRole("button", { name: "创建", exact: true }).click();
    });
    await dialog.waitFor({ state: "hidden" });
    const payload = await requireSuccess(tokens.root, "/procurement/query", {
      method: "POST",
      body: { current: 1, size: 100, bookName },
    });
    const order = (payload.data || [])
      .filter(
        (item) =>
          Number(item.requestCount) === 2 &&
          Number(item.requesterId) > 0 &&
          item.requestNote?.includes(validationDate)
      )
      .sort((left, right) => Number(right.id) - Number(left.id))[0];
    if (!order) throw new Error("New procurement order was not found");
    return order;
  } finally {
    session.assertDiagnostics();
    await session.context.close();
  }
}

async function clickOrderAction(page, orderId, label, method, path) {
  const row = await findOrderRow(page, orderId);
  await waitForApiResponse(page, method, path, async () => {
    await row.getByRole("button", { name: label, exact: true }).click();
  });
}

async function assignLogistics(page, orderId) {
  const row = await findOrderRow(page, orderId);
  await row.getByRole("button", { name: "分配物流", exact: true }).click();
  const dialog = page.locator(".el-dialog").filter({ hasText: "分配物流员" }).last();
  await dialog.waitFor();
  await dialog.locator(".el-select").click();
  await page
    .locator(".el-select-dropdown:visible")
    .getByText("归架沉香", { exact: true })
    .click();
  await waitForApiResponse(page, "PUT", "/procurement/assignLogistics", async () => {
    await dialog.getByRole("button", { name: "确认", exact: true }).click();
  });
  await dialog.waitFor({ state: "hidden" });
}

async function sendProcurementMessage(page, orderId, content, logisticsChannel) {
  const row = await findOrderRow(page, orderId);
  await row.getByRole("button", { name: "沟通", exact: true }).click();
  const dialog = page.locator(".el-dialog").filter({ hasText: "采购协作消息" }).last();
  await dialog.waitFor();
  if (logisticsChannel && (await dialog.getByText("物流沟通", { exact: true }).count())) {
    await dialog.getByText("物流沟通", { exact: true }).click();
    await page.waitForTimeout(200);
  }
  await dialog.getByPlaceholder("输入协作消息").fill(content);
  await waitForApiResponse(page, "POST", "/procurement/message/send", async () => {
    await dialog.getByRole("button", { name: "发送", exact: true }).click();
  });
  await dialog.getByText(content, { exact: true }).waitFor();
  await dialog.locator(".el-dialog__headerbtn").click();
  await dialog.waitFor({ state: "hidden" });
}

async function runPurchaserSteps(browser, tokens, report, orderId) {
  const session = await openAuthenticatedPage(browser, tokens.purchaser, "purchaser workflow", report);
  try {
    await session.page.goto(`${baseUrl}/#/procurementWorkbench`, {
      waitUntil: "networkidle",
    });
    await clickOrderAction(
      session.page,
      orderId,
      "开始采购",
      "PUT",
      "/procurement/updateStatus"
    );
    await clickOrderAction(
      session.page,
      orderId,
      "确认下单",
      "PUT",
      "/procurement/updateStatus"
    );
    await assignLogistics(session.page, orderId);
    await sendProcurementMessage(
      session.page,
      orderId,
      `采购员消息 ${validationDate}：已下单，请准备接收两册。`,
      true
    );
    await session.page.screenshot({
      path: `${outputDir}/purchaser-assigned.png`,
      fullPage: true,
    });
  } finally {
    session.assertDiagnostics();
    await session.context.close();
  }
}

async function submitLogisticsStep(page, orderId, actionLabel, details) {
  const row = await findOrderRow(page, orderId);
  await row.getByRole("button", { name: actionLabel, exact: true }).click();
  const dialog = page.locator(".el-dialog").filter({ hasText: "物流流转" }).last();
  await dialog.waitFor();
  const fields = dialog.locator(".el-form-item");
  await fields.filter({ hasText: "承运方" }).locator("input").fill(details.carrier);
  await fields.filter({ hasText: "运单号" }).locator("input").fill(details.trackingNo);
  await fields.filter({ hasText: "备注" }).locator("textarea").fill(details.remark);
  await waitForApiResponse(page, "PUT", "/procurement/updateLogistics", async () => {
    await dialog.getByRole("button", { name: "确认流转", exact: true }).click();
  });
  await dialog.waitFor({ state: "hidden" });
}

async function runLogisticsSteps(browser, tokens, report, orderId) {
  const session = await openAuthenticatedPage(browser, tokens.logistics, "logistics workflow", report);
  const details = {
    carrier: "青梧馆配",
    trackingNo: "DRL-E2E-20260726",
    remark: `全链路物流验收 ${validationDate}`,
  };
  try {
    await session.page.goto(`${baseUrl}/#/procurementWorkbench`, {
      waitUntil: "networkidle",
    });
    await submitLogisticsStep(session.page, orderId, "开始运输", details);

    const row = await findOrderRow(session.page, orderId);
    await row.getByRole("button", { name: "沟通", exact: true }).click();
    const dialog = session.page
      .locator(".el-dialog")
      .filter({ hasText: "采购协作消息" })
      .last();
    await dialog.waitFor();
    await dialog
      .getByText(`采购员消息 ${validationDate}：已下单，请准备接收两册。`, {
        exact: true,
      })
      .waitFor();
    const reply = `物流员消息 ${validationDate}：已接单，按测试运单推进。`;
    await dialog.getByPlaceholder("输入协作消息").fill(reply);
    await waitForApiResponse(session.page, "POST", "/procurement/message/send", async () => {
      await dialog.getByRole("button", { name: "发送", exact: true }).click();
    });
    await dialog.getByText(reply, { exact: true }).waitFor();
    await dialog.locator(".el-dialog__headerbtn").click();
    await dialog.waitFor({ state: "hidden" });

    await submitLogisticsStep(session.page, orderId, "确认到馆", details);
    await submitLogisticsStep(session.page, orderId, "确认入库", details);
    await session.page.screenshot({
      path: `${outputDir}/logistics-warehoused.png`,
      fullPage: true,
    });
  } finally {
    session.assertDiagnostics();
    await session.context.close();
  }
}

async function completeProcurement(browser, tokens, report, orderId) {
  const session = await openAuthenticatedPage(browser, tokens.purchaser, "procurement completion", report);
  try {
    await session.page.goto(`${baseUrl}/#/procurementWorkbench`, {
      waitUntil: "networkidle",
    });
    await clickOrderAction(
      session.page,
      orderId,
      "完成采购",
      "PUT",
      "/procurement/updateStatus"
    );
    await session.page.screenshot({
      path: `${outputDir}/procurement-completed.png`,
      fullPage: true,
    });
  } finally {
    session.assertDiagnostics();
    await session.context.close();
  }
}

async function restoreCoordinator(tokens) {
  const coordinator = await queryOne(
    tokens.root,
    "/user/query",
    { current: 1, size: 10, userAccount: accounts.coordinator.account },
    "coordinator account"
  );
  if (!coordinator.isCoordinatorAdmin) {
    await requireSuccess(tokens.root, "/user/backUpdate", {
      method: "PUT",
      body: { id: coordinator.id, isCoordinatorAdmin: true },
    });
  }
}

async function returnOutstandingWorkflowBorrow(tokens) {
  const payload = await requireSuccess(tokens.reader, "/borrowRecord/query", {
    method: "POST",
    body: { current: 1, size: 100, status: false, bookName },
  });
  for (const record of payload.data || []) {
    await requireSuccess(tokens.reader, `/borrowRecord/return/${record.id}`, {
      method: "POST",
    });
  }
}

const report = {
  validationDate,
  apiResponses: 0,
  apiEndpoints: {},
  rolesLoggedIn: [],
  orderId: null,
  stockBefore: null,
  stockAfter: null,
};
const tokens = {};
let browser;

try {
  for (const [name, identity] of Object.entries(accounts)) {
    tokens[name] = await login(identity);
    report.rolesLoggedIn.push(identity.role);
  }

  const forbidden = await apiRequest(tokens.reader, "/user/query", {
    method: "POST",
    body: { current: 1, size: 10 },
  });
  if (forbidden.response.ok && forbidden.payload?.code === 200) {
    throw new Error("Reader unexpectedly accessed the administrator user query");
  }

  const initialBook = await queryOne(
    tokens.root,
    "/book/query",
    { current: 1, size: 10, name: bookName },
    bookName
  );
  report.stockBefore = {
    totalCount: initialBook.totalCount,
    availableCount: initialBook.availableCount,
  };

  browser = await chromium.launch({ executablePath: edgePath, headless: true });
  await runReaderBorrowReturn(browser, tokens, report);
  await runCoordinatorToggle(browser, tokens, report);
  const order = await createProcurementOrder(browser, tokens, report);
  report.orderId = order.id;
  await runPurchaserSteps(browser, tokens, report, order.id);
  await runLogisticsSteps(browser, tokens, report, order.id);
  await completeProcurement(browser, tokens, report, order.id);

  const completedOrders = await requireSuccess(tokens.root, "/procurement/query", {
    method: "POST",
    body: { current: 1, size: 100, bookName, status: 6 },
  });
  const completed = (completedOrders.data || []).find(
    (item) => Number(item.id) === Number(order.id)
  );
  if (!completed) {
    throw new Error(`Completed procurement order ${order.id} was not returned`);
  }
  if (
    Number(completed.id) !== Number(order.id) ||
    Number(completed.logisticsStatus) !== 3 ||
    !completed.stockApplied ||
    completed.trackingNo !== "DRL-E2E-20260726"
  ) {
    throw new Error(`Completed procurement state is invalid: ${JSON.stringify(completed)}`);
  }

  const finalBook = await queryOne(
    tokens.root,
    "/book/query",
    { current: 1, size: 10, name: bookName },
    bookName
  );
  report.stockAfter = {
    totalCount: finalBook.totalCount,
    availableCount: finalBook.availableCount,
  };
  if (
    Number(finalBook.totalCount) !== Number(initialBook.totalCount) + 2 ||
    Number(finalBook.availableCount) !== Number(initialBook.availableCount) + 2
  ) {
    throw new Error(
      `Procurement stock delta is invalid: ${JSON.stringify({
        before: report.stockBefore,
        after: report.stockAfter,
      })}`
    );
  }

  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
} finally {
  if (browser) await browser.close();
  if (tokens.root) await restoreCoordinator(tokens).catch(() => {});
  if (tokens.reader) await returnOutstandingWorkflowBorrow(tokens).catch(() => {});
}

console.log(
  `FULL_WORKFLOW_E2E_OK order=${report.orderId} stock=${report.stockBefore.totalCount}->${report.stockAfter.totalCount}`
);

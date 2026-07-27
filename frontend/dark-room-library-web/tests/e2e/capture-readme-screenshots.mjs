import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { chromium } from "playwright-core";
import { getAccount } from "./test-accounts.mjs";

const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const apiBaseUrl =
  process.env.E2E_API_BASE_URL ||
  "http://localhost:20606/api/dark-room-library/v1";
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const currentDir = path.dirname(fileURLToPath(import.meta.url));
const frontendRoot = path.resolve(currentDir, "../..");
const projectRoot = path.resolve(frontendRoot, "../..");
const outputDir = path.join(projectRoot, "docs", "images");
const reportPath = path.join(
  frontendRoot,
  "test-results",
  "readme-screenshots.json"
);

const accounts = {
  reader: getAccount("reader"),
  root: getAccount("root"),
  purchaser: getAccount("purchaser"),
};

function assert(condition, message) {
  if (!condition) throw new Error(message);
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

async function apiRequest(pathname, options = {}) {
  const response = await fetch(`${apiBaseUrl}${pathname}`, {
    ...options,
    signal: AbortSignal.timeout(15_000),
  });
  const text = await response.text();
  return {
    response,
    payload: text ? JSON.parse(text) : null,
  };
}

async function login(identity) {
  const captcha = await apiRequest("/captcha/generate");
  assert(captcha.response.ok && captcha.payload?.code === 200, "Captcha failed");
  const result = await apiRequest("/user/login", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      userAccount: identity.account,
      userPwd: identity.password,
      captchaId: captcha.payload.data.captchaId,
      captchaAnswer: solveCaptcha(captcha.payload.data.expression),
    }),
  });
  assert(
    result.response.ok && result.payload?.code === 200,
    `Login failed for ${identity.account}: ${result.payload?.msg}`
  );
  return result.payload.data.token;
}

function attachDiagnostics(page) {
  const diagnostics = {
    console: [],
    pageErrors: [],
    requestFailures: [],
    responseErrors: [],
    resources: [],
  };
  page.on("console", (message) => {
    if (["error", "warning", "warn"].includes(message.type())) {
      diagnostics.console.push({
        type: message.type(),
        text: message.text(),
      });
    }
  });
  page.on("pageerror", (error) => diagnostics.pageErrors.push(error.message));
  page.on("requestfailed", (request) => {
    diagnostics.requestFailures.push({
      method: request.method(),
      url: request.url(),
      error: request.failure()?.errorText || "unknown",
    });
  });
  page.on("response", (response) => {
    diagnostics.resources.push(response.url());
    if (response.status() >= 400) {
      diagnostics.responseErrors.push({
        status: response.status(),
        method: response.request().method(),
        url: response.url(),
      });
    }
  });
  return diagnostics;
}

async function capture(context, spec) {
  const page = await context.newPage();
  const diagnostics = attachDiagnostics(page);
  try {
    await page.goto(`${baseUrl}/#${spec.route}`, {
      waitUntil: "networkidle",
      timeout: 25_000,
    });
    await page.locator(spec.readySelector).first().waitFor({
      state: "visible",
      timeout: 15_000,
    });
    if (spec.prepare) {
      await spec.prepare(page);
    }
    if (spec.waitForCharts) {
      await page.waitForFunction(
        () =>
          [...document.querySelectorAll("canvas")].some(
            (canvas) => canvas.width > 100 && canvas.height > 100
          ),
        null,
        { timeout: 15_000 }
      );
    }
    await page.evaluate(async () => document.fonts.ready);
    await page.waitForTimeout(spec.settleMs || 700);

    const overflow = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth
    );
    assert(overflow <= 1, `${spec.route} has ${overflow}px horizontal overflow`);
    assert(
      diagnostics.console.length === 0,
      `${spec.route} console errors: ${JSON.stringify(diagnostics.console)}`
    );
    assert(
      diagnostics.pageErrors.length === 0,
      `${spec.route} page errors: ${JSON.stringify(diagnostics.pageErrors)}`
    );
    assert(
      diagnostics.requestFailures.length === 0,
      `${spec.route} request failures: ${JSON.stringify(
        diagnostics.requestFailures
      )}`
    );
    assert(
      diagnostics.responseErrors.length === 0,
      `${spec.route} response errors: ${JSON.stringify(
        diagnostics.responseErrors
      )}`
    );

    const outputPath = path.join(outputDir, spec.fileName);
    await page.screenshot({
      path: outputPath,
      type: "jpeg",
      quality: 88,
      fullPage: false,
    });
    return {
      route: spec.route,
      fileName: spec.fileName,
      chartResources: diagnostics.resources.filter((url) =>
        /echarts|zrender|\/charts(?:-renderer)?-[^/]+\.js(?:$|\?)/i.test(url)
      ),
    };
  } finally {
    await page.close();
  }
}

await fs.mkdir(outputDir, { recursive: true });
await fs.mkdir(path.dirname(reportPath), { recursive: true });

const tokens = {
  reader: await login(accounts.reader),
  root: await login(accounts.root),
  purchaser: await login(accounts.purchaser),
};

const browser = await chromium.launch({
  executablePath: edgePath,
  headless: true,
});
const report = {
  generatedAt: new Date().toISOString(),
  baseUrl,
  screenshots: [],
};

try {
  const guestContext = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    reducedMotion: "reduce",
  });
  await guestContext.addInitScript(() => {
    sessionStorage.setItem("auth-intro-seen", "1");
    localStorage.setItem("dark-room-reader-theme", "night");
  });
  report.screenshots.push(
    await capture(guestContext, {
      route: "/login",
      fileName: "login-night.jpg",
      readySelector: ".paper-sheet",
      settleMs: 900,
    })
  );
  await guestContext.close();

  const readerNightContext = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    reducedMotion: "reduce",
  });
  await readerNightContext.addInitScript((token) => {
    sessionStorage.setItem("token", token);
    localStorage.setItem("dark-room-reader-theme", "night");
  }, tokens.reader);
  report.screenshots.push(
    await capture(readerNightContext, {
      route: "/readerRoom",
      fileName: "reader-room.jpg",
      readySelector: ".room-page",
    })
  );
  await readerNightContext.close();

  const readerDayContext = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    reducedMotion: "reduce",
  });
  await readerDayContext.addInitScript((token) => {
    sessionStorage.setItem("token", token);
    localStorage.setItem("dark-room-reader-theme", "day");
  }, tokens.reader);
  report.screenshots.push(
    await capture(readerDayContext, {
      route: "/bookSearch",
      fileName: "book-borrow.jpg",
      readySelector: ".book-grid .book-card",
    })
  );
  await readerDayContext.close();

  const adminContext = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    reducedMotion: "reduce",
  });
  await adminContext.addInitScript((token) => {
    sessionStorage.setItem("token", token);
    localStorage.setItem("admin-theme", "day");
  }, tokens.root);
  const adminScreenshot = await capture(adminContext, {
    route: "/statisticsDashboard",
    fileName: "admin-dashboard.jpg",
    readySelector: ".statistics-page",
    waitForCharts: true,
    settleMs: 1_000,
    prepare: async (page) => {
      const monthInput = page.locator(
        '.statistics-page input[placeholder="选择月份"]'
      );
      await monthInput.click();
      await Promise.all([
        page.waitForResponse(
          (response) =>
            response.url().includes("/statistics/monthlyBorrow/2026/6") &&
            response.status() < 400
        ),
        page
          .locator(".el-picker-panel")
          .getByText("Jun", { exact: true })
          .click(),
      ]);
      await monthInput.waitFor({ state: "visible" });
      assert(
        (await monthInput.inputValue()) === "2026-06",
        "Statistics dashboard did not switch to the seeded June data"
      );
    },
  });
  assert(
    adminScreenshot.chartResources.length > 0,
    "Statistics dashboard did not load the ECharts runtime"
  );
  report.screenshots.push(adminScreenshot);
  await adminContext.close();

  const purchaserContext = await browser.newContext({
    viewport: { width: 1440, height: 960 },
    reducedMotion: "reduce",
  });
  await purchaserContext.addInitScript((token) => {
    sessionStorage.setItem("token", token);
    localStorage.setItem("admin-theme", "day");
  }, tokens.purchaser);
  report.screenshots.push(
    await capture(purchaserContext, {
      route: "/procurementWorkbench",
      fileName: "procurement-workbench.jpg",
      readySelector: ".procurement-page",
    })
  );
  await purchaserContext.close();

  for (const screenshot of report.screenshots) {
    if (screenshot.route !== "/statisticsDashboard") {
      assert(
        screenshot.chartResources.length === 0,
        `${screenshot.route} loaded ECharts before a chart page was opened`
      );
    }
  }

  report.status = "passed";
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  console.log(
    `README_SCREENSHOTS_OK count=${report.screenshots.length} output=${outputDir}`
  );
} catch (error) {
  report.status = "failed";
  report.error = error instanceof Error ? error.stack : String(error);
  await fs.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");
  throw error;
} finally {
  await browser.close();
}

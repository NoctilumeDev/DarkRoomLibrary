import fs from "node:fs/promises";
import { chromium } from "playwright-core";
import { getAccount } from "./test-accounts.mjs";

const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const apiBaseUrl =
  process.env.E2E_API_BASE_URL ||
  "http://localhost:20606/api/dark-room-library/v1";
const apiPathPrefix = new URL(apiBaseUrl).pathname.replace(/\/$/, "");
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const validationDate =
  process.env.E2E_VALIDATION_DATE || new Date().toISOString().slice(0, 10);
const outputDir = "test-results/browser-diagnostics";

const accounts = Object.freeze({
  root: getAccount("root"),
  coordinator: getAccount("coordinator"),
  admin: getAccount("admin"),
  reader: getAccount("reader"),
  purchaser: getAccount("purchaser"),
  logistics: getAccount("logistics"),
});

const adminRoutes = [
  "/dashboard",
  "/statisticsDashboard",
  "/bookManage",
  "/categoryManage",
  "/bookshelfManage",
  "/borrowManage",
  "/userManage",
  "/noticeManage",
  "/createNotice",
  "/procurementManage",
  "/contentAudit",
  "/operationLog",
  "/workflowStatus",
  "/dataExport",
  "/messageManage",
];

const roleRoutes = Object.freeze({
  root: [...adminRoutes, "/fileManage"],
  coordinator: adminRoutes,
  admin: adminRoutes,
  reader: [
    "/readerRoom",
    "/bookSearch",
    "/myBorrows",
    "/myFavorites",
    "/myReservations",
    "/bookReviews",
    "/messageBoard",
  ],
  purchaser: ["/procurementWorkbench"],
  logistics: ["/procurementWorkbench"],
});

const viewports = Object.freeze([
  { name: "desktop", width: 1440, height: 1000 },
  { name: "mobile", width: 430, height: 932 },
]);

const report = {
  validationDate,
  baseUrl,
  apiBaseUrl,
  browser: "Microsoft Edge",
  startedAt: new Date().toISOString(),
  setupApiResponses: [],
  roles: [],
  guestRoutes: [],
};

await fs.mkdir(outputDir, { recursive: true });

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

async function apiRequest(token, path, { method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15_000),
  });
  const text = await response.text();
  const payload = text ? JSON.parse(text) : null;
  report.setupApiResponses.push({
    method,
    path,
    httpStatus: response.status,
    businessCode: payload?.code ?? null,
  });
  return { response, payload };
}

function matchesApiPath(response) {
  return new URL(response.url()).pathname.startsWith(apiPathPrefix);
}

async function login(identity) {
  const captcha = await apiRequest(null, "/captcha/generate");
  assert(
    captcha.response.ok && captcha.payload?.code === 200,
    `Captcha generation failed for ${identity.account}`
  );
  const result = await apiRequest(null, "/user/login", {
    method: "POST",
    body: {
      userAccount: identity.account,
      userPwd: identity.password,
      captchaId: captcha.payload.data.captchaId,
      captchaAnswer: solveCaptcha(captcha.payload.data.expression),
    },
  });
  assert(
    result.response.ok && result.payload?.code === 200,
    `Login failed for ${identity.account}: ${result.payload?.msg}`
  );
  assert(
    Number(result.payload.data.role) === identity.role,
    `${identity.account} returned role ${result.payload.data.role}`
  );
  const token = result.payload.data.token;
  const profile = await apiRequest(token, "/user/auth");
  assert(
    profile.response.ok &&
      profile.payload?.code === 200 &&
      Number(profile.payload.data.userRole) === identity.role &&
      Boolean(profile.payload.data.isCoordinatorAdmin) === identity.isCoordinatorAdmin,
    `${identity.account} returned unexpected permission profile: ` +
      `${JSON.stringify(profile.payload?.data)}`
  );
  return token;
}

function attachDiagnostics(page, routeEntry) {
  const responseChecks = [];
  page.on("console", (message) => {
    if (["error", "warning", "warn"].includes(message.type())) {
      routeEntry.console.push({
        type: message.type(),
        text: message.text(),
      });
    }
  });
  page.on("pageerror", (error) => {
    routeEntry.pageErrors.push(error.message);
  });
  page.on("requestfailed", (request) => {
    routeEntry.requestFailures.push({
      method: request.method(),
      url: request.url(),
      error: request.failure()?.errorText || "unknown",
    });
  });
  page.on("response", (response) => {
    routeEntry.network.responses += 1;
    if (matchesApiPath(response)) {
      routeEntry.network.apiResponses += 1;
      const contentType = response.headers()["content-type"] || "";
      if (response.status() < 400 && contentType.includes("application/json")) {
        responseChecks.push(
          response
            .json()
            .then((payload) => {
              if (payload?.code !== undefined && Number(payload.code) !== 200) {
                routeEntry.network.businessErrors.push({
                  code: payload.code,
                  message: payload.msg,
                  method: response.request().method(),
                  url: response.url(),
                });
              }
            })
            .catch((error) => {
              routeEntry.network.responseInspectionErrors.push({
                message: error.message,
                method: response.request().method(),
                url: response.url(),
              });
            })
        );
      }
    }
    if (response.status() >= 400) {
      routeEntry.network.errors.push({
        status: response.status(),
        method: response.request().method(),
        url: response.url(),
      });
    }
  });
  return async () => {
    await Promise.all(responseChecks);
  };
}

async function inspectRoute(context, label, route, viewport) {
  const routeEntry = {
    label,
    route,
    viewport: viewport.name,
    width: viewport.width,
    height: viewport.height,
    console: [],
    pageErrors: [],
    requestFailures: [],
    network: {
      responses: 0,
      apiResponses: 0,
      errors: [],
      businessErrors: [],
      responseInspectionErrors: [],
    },
    horizontalOverflowPx: null,
    finalUrl: null,
    durationMs: null,
  };
  const page = await context.newPage();
  const flushResponseChecks = attachDiagnostics(page, routeEntry);
  const startedAt = Date.now();
  try {
    await page.setViewportSize({
      width: viewport.width,
      height: viewport.height,
    });
    await page.goto(`${baseUrl}/#${route}`, {
      waitUntil: "networkidle",
      timeout: 20_000,
    });
    await page.waitForTimeout(250);
    await flushResponseChecks();
    routeEntry.finalUrl = page.url();
    routeEntry.horizontalOverflowPx = await page.evaluate(
      () =>
        document.documentElement.scrollWidth -
        document.documentElement.clientWidth
    );
    routeEntry.durationMs = Date.now() - startedAt;

    assert(
      new URL(routeEntry.finalUrl).hash === `#${route}`,
      `${label} ${route} redirected to ${routeEntry.finalUrl}`
    );
    assert(
      routeEntry.horizontalOverflowPx <= 1,
      `${label} ${route} ${viewport.name} has ` +
        `${routeEntry.horizontalOverflowPx}px horizontal overflow`
    );
    assert(
      routeEntry.console.length === 0,
      `${label} ${route} console diagnostics: ${JSON.stringify(routeEntry.console)}`
    );
    assert(
      routeEntry.pageErrors.length === 0,
      `${label} ${route} page errors: ${JSON.stringify(routeEntry.pageErrors)}`
    );
    assert(
      routeEntry.requestFailures.length === 0,
      `${label} ${route} request failures: ` +
        `${JSON.stringify(routeEntry.requestFailures)}`
    );
    assert(
      routeEntry.network.errors.length === 0,
      `${label} ${route} network errors: ${JSON.stringify(routeEntry.network.errors)}`
    );
    assert(
      routeEntry.network.businessErrors.length === 0,
      `${label} ${route} API business errors: ` +
        `${JSON.stringify(routeEntry.network.businessErrors)}`
    );
    assert(
      routeEntry.network.responseInspectionErrors.length === 0,
      `${label} ${route} API response inspection errors: ` +
        `${JSON.stringify(routeEntry.network.responseInspectionErrors)}`
    );
    return routeEntry;
  } catch (error) {
    routeEntry.durationMs = Date.now() - startedAt;
    routeEntry.error = error instanceof Error ? error.message : String(error);
    const safeLabel = `${label}-${route.slice(1) || "root"}-${viewport.name}`
      .replaceAll(/[^a-zA-Z0-9_-]/g, "-");
    await page.screenshot({
      path: `${outputDir}/${safeLabel}-failed.png`,
      fullPage: true,
    }).catch(() => {});
    throw Object.assign(error, { routeEntry });
  } finally {
    await page.close();
  }
}

async function inspectAuthenticatedRole(browser, name, identity, token) {
  const roleEntry = {
    name,
    role: identity.role,
    isCoordinatorAdmin: identity.isCoordinatorAdmin,
    account: identity.account,
    routes: [],
  };
  for (const viewport of viewports) {
    const context = await browser.newContext({
      viewport: {
        width: viewport.width,
        height: viewport.height,
      },
    });
    await context.addInitScript((value) => {
      sessionStorage.setItem("token", value);
      localStorage.setItem("admin-theme", "day");
      localStorage.setItem("reader-theme", "day");
    }, token);
    try {
      for (const route of roleRoutes[name]) {
        roleEntry.routes.push(
          await inspectRoute(context, name, route, viewport)
        );
      }
    } finally {
      await context.close();
    }
  }
  return roleEntry;
}

async function inspectGuestRoutes(browser) {
  for (const viewport of viewports) {
    const context = await browser.newContext({
      viewport: {
        width: viewport.width,
        height: viewport.height,
      },
    });
    try {
      for (const route of ["/login", "/register", "/resetPwd"]) {
        report.guestRoutes.push(
          await inspectRoute(context, "guest", route, viewport)
        );
      }
    } finally {
      await context.close();
    }
  }
}

function summarizeReport() {
  const routes = [
    ...report.guestRoutes,
    ...report.roles.flatMap((role) => role.routes),
  ];
  const pageApiResponses = routes.reduce(
    (sum, route) => sum + route.network.apiResponses,
    0
  );
  const pageNetworkResponses = routes.reduce(
    (sum, route) => sum + route.network.responses,
    0
  );
  return {
    routeChecks: routes.length,
    setupApiResponses: report.setupApiResponses.length,
    pageApiResponses,
    totalApiResponses: pageApiResponses + report.setupApiResponses.length,
    pageNetworkResponses,
    totalNetworkResponses:
      pageNetworkResponses + report.setupApiResponses.length,
  };
}

let browser;
try {
  const tokens = {};
  for (const [name, identity] of Object.entries(accounts)) {
    tokens[name] = await login(identity);
  }

  browser = await chromium.launch({
    executablePath: edgePath,
    headless: true,
  });
  await inspectGuestRoutes(browser);
  for (const [name, identity] of Object.entries(accounts)) {
    report.roles.push(
      await inspectAuthenticatedRole(browser, name, identity, tokens[name])
    );
  }
  report.completedAt = new Date().toISOString();
  report.status = "passed";
  report.summary = summarizeReport();
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
  console.log(
    `BROWSER_DIAGNOSTICS_OK routeChecks=${report.summary.routeChecks} ` +
      `apiResponses=${report.summary.totalApiResponses} ` +
      `networkResponses=${report.summary.totalNetworkResponses}`
  );
} catch (error) {
  if (error?.routeEntry) {
    report.failedRoute = error.routeEntry;
  }
  report.completedAt = new Date().toISOString();
  report.status = "failed";
  report.summary = summarizeReport();
  report.error = error instanceof Error ? error.stack : String(error);
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
  throw error;
} finally {
  if (browser) await browser.close();
}

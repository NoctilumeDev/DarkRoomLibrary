import fs from "node:fs/promises";
import { chromium } from "playwright-core";

const baseUrl = process.env.E2E_BASE_URL || "http://localhost:5175";
const edgePath =
  process.env.EDGE_PATH ||
  "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe";
const outputDir = "test-results/session-generation-ownership";
const controlRoute = "/resetPwd";

const oldToken = createToken({ exp: 4_102_444_800, role: 1, sub: "old" });
const newToken = createToken({ exp: 4_102_444_800, role: 3, sub: "new" });
const newProfile = Object.freeze({
  id: 202,
  name: "new-session",
  email: "new-session@example.test",
  url: null,
  role: 3,
  isCoordinatorAdmin: false,
});

const report = {
  baseUrl,
  browser: "Microsoft Edge",
  startedAt: new Date().toISOString(),
  scenarios: [],
};

await fs.mkdir(outputDir, { recursive: true });

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function createToken(payload) {
  const encode = (value) =>
    Buffer.from(JSON.stringify(value), "utf8").toString("base64url");
  return `${encode({ alg: "none", typ: "JWT" })}.${encode(payload)}.`;
}

function waitWithTimeout(promise, label, timeoutMs = 10_000) {
  return Promise.race([
    promise,
    new Promise((_, reject) => {
      setTimeout(() => reject(new Error(`${label} timed out`)), timeoutMs);
    }),
  ]);
}

async function installSession(page, token, profile) {
  await page.evaluate(
    ({ value, user }) => {
      sessionStorage.setItem("token", value);
      sessionStorage.setItem("userInfo", JSON.stringify(user));
    },
    { value: token, user: profile }
  );
}

async function readSession(page) {
  return page.evaluate(async () => {
    const { default: router } = await import("/src/router/index.js");
    return {
      token: sessionStorage.getItem("token"),
      profile: JSON.parse(sessionStorage.getItem("userInfo") || "null"),
      hash: window.location.hash,
      currentRoute: router.currentRoute.value.fullPath,
    };
  });
}

async function holdNextRoute(page, pattern) {
  let releaseHandler;
  let resolveSeen;
  const seen = new Promise((resolve) => {
    resolveSeen = resolve;
  });
  await page.route(pattern, async (route) => {
    resolveSeen(route);
    await new Promise((resolve) => {
      releaseHandler = resolve;
    });
  });
  return {
    seen: waitWithTimeout(seen, `route ${pattern}`),
    release() {
      releaseHandler?.();
    },
  };
}

async function createPage(browser, route = controlRoute) {
  const context = await browser.newContext();
  const page = await context.newPage();
  const pageErrors = [];
  page.on("pageerror", (error) => pageErrors.push(error.message));
  await page.goto(`${baseUrl}/#${route}`, {
    waitUntil: "domcontentloaded",
    timeout: 20_000,
  });
  await page.evaluate(async () => {
    const { default: router } = await import("/src/router/index.js");
    await router.isReady();
  });
  return { context, page, pageErrors };
}

async function exerciseAxiosLate401(browser) {
  const { context, page, pageErrors } = await createPage(browser);
  try {
    await installSession(page, oldToken, { ...newProfile, name: "old-session" });
    const held = await holdNextRoute(page, "**/session-generation-race");
    await page.evaluate(() => {
      window.__sessionGenerationRequest = { settled: false, rejected: false };
      void import("/src/utils/request.js")
        .then(({ default: request }) => request.get("/session-generation-race"))
        .catch(() => {
          window.__sessionGenerationRequest.rejected = true;
        })
        .finally(() => {
          window.__sessionGenerationRequest.settled = true;
        });
    });
    const route = await held.seen;
    await installSession(page, newToken, newProfile);
    await route.fulfill({
      status: 401,
      contentType: "application/json",
      body: JSON.stringify({ code: 401, msg: "身份认证失败" }),
    });
    held.release();
    await page.waitForFunction(
      () => window.__sessionGenerationRequest?.settled === true
    );

    const session = await readSession(page);
    const requestState = await page.evaluate(
      () => window.__sessionGenerationRequest
    );
    assert(requestState.rejected, "The controlled Axios request did not reject");
    assert(session.token === newToken, "Late Axios 401 cleared the newer token");
    assert(
      session.profile?.name === newProfile.name,
      "Late Axios 401 cleared or replaced the newer profile"
    );
    assert(
      session.hash === `#${controlRoute}`,
      `Late Axios 401 redirected the newer session: ${session.hash}`
    );
    assert(pageErrors.length === 0, `Page errors: ${JSON.stringify(pageErrors)}`);
    report.scenarios.push({
      name: "late-axios-401-preserves-newer-session",
      status: "passed",
      finalHash: session.hash,
    });
  } finally {
    await context.close();
  }
}

async function exerciseRouteGuardLateResult(browser, result) {
  const { context, page, pageErrors } = await createPage(browser);
  try {
    await installSession(page, oldToken, { ...newProfile, name: "old-session" });
    const held = await holdNextRoute(page, "**/user/auth");
    await page.evaluate(() => {
      window.__sessionGenerationNavigation = { settled: false };
      void import("/src/router/index.js")
        .then(({ default: router }) => router.push("/dashboard"))
        .finally(() => {
          window.__sessionGenerationNavigation.settled = true;
        });
    });
    const route = await held.seen;
    await installSession(page, newToken, newProfile);
    await route.fulfill(result.response);
    held.release();
    await page.waitForFunction(
      () => window.__sessionGenerationNavigation?.settled === true
    );

    const session = await readSession(page);
    assert(session.token === newToken, `${result.label} cleared the newer token`);
    assert(
      session.profile?.name === newProfile.name,
      `${result.label} cleared or replaced the newer profile`
    );
    assert(
      session.currentRoute === controlRoute,
      `${result.label} allowed the stale navigation: ` +
        `route=${session.currentRoute}, hash=${session.hash}`
    );
    assert(pageErrors.length === 0, `Page errors: ${JSON.stringify(pageErrors)}`);
    report.scenarios.push({
      name: result.name,
      status: "passed",
      finalHash: session.hash,
      currentRoute: session.currentRoute,
    });
  } finally {
    await context.close();
  }
}

let browser;
try {
  browser = await chromium.launch({
    executablePath: edgePath,
    headless: true,
  });
  await exerciseAxiosLate401(browser);
  await exerciseRouteGuardLateResult(browser, {
    name: "late-auth-success-cannot-overwrite-newer-session",
    label: "Late auth success",
    response: {
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        code: 200,
        data: {
          id: 101,
          userName: "old-session",
          userEmail: "old-session@example.test",
          userAvatar: null,
          userRole: 1,
          isCoordinatorAdmin: true,
        },
      }),
    },
  });
  await exerciseRouteGuardLateResult(browser, {
    name: "late-auth-rejection-cannot-clear-newer-session",
    label: "Late auth rejection",
    response: {
      status: 403,
      contentType: "application/json",
      body: JSON.stringify({ code: 403, msg: "身份认证失败" }),
    },
  });
  report.status = "passed";
} catch (error) {
  report.status = "failed";
  report.error = error instanceof Error ? error.stack : String(error);
  throw error;
} finally {
  if (browser) await browser.close();
  report.finishedAt = new Date().toISOString();
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
}

console.log(
  `SESSION_GENERATION_OWNERSHIP_OK scenarios=${report.scenarios.length}`
);

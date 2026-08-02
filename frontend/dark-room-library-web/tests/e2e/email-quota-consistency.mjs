import fs from "node:fs/promises";
import { getAccount } from "./test-accounts.mjs";

const apiBaseUrls = Object.freeze(
  (
    process.env.E2E_API_BASE_URLS ||
    [
      "http://localhost:20606/api/dark-room-library/v1",
      "http://localhost:20607/api/dark-room-library/v1",
      "http://localhost:20608/api/dark-room-library/v1",
    ].join(",")
  )
    .split(",")
    .map((url) => url.trim().replace(/\/$/, ""))
    .filter(Boolean)
);
const burstSize = Number(process.env.E2E_EMAIL_QUOTA_BURST_SIZE || 100);
const runKey = String(
  process.env.E2E_EMAIL_QUOTA_RUN_KEY || Date.now()
)
  .replaceAll(/[^a-zA-Z0-9]/g, "")
  .slice(-8);
const sharedEmail = `quota-${runKey}@darkroomlibrary.local`;
const quotaMessage = "同一邮箱最多关联 3 个账号，请更换邮箱";
const outputDir = "test-results/email-quota";
const root = getAccount("root");

if (apiBaseUrls.length < 2) {
  throw new Error("Email quota consistency verification requires at least two API instances.");
}
if (!Number.isSafeInteger(burstSize) || burstSize < 4 || burstSize > 500) {
  throw new Error("E2E_EMAIL_QUOTA_BURST_SIZE must be an integer from 4 to 500.");
}

await fs.mkdir(outputDir, { recursive: true });

function assert(condition, message) {
  if (!condition) throw new Error(message);
}

function solveCaptcha(expression) {
  const match = expression.match(/(-?\d+)\s*([+\-×*xX])\s*(-?\d+)/);
  assert(match, `Unsupported captcha expression: ${expression}`);
  const left = Number(match[1]);
  const right = Number(match[3]);
  if (match[2] === "+") return left + right;
  if (match[2] === "-") return left - right;
  return left * right;
}

async function apiRequest(baseUrl, token, path, { method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const startedAt = Date.now();
  const response = await fetch(`${baseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20_000),
  });
  const payload = await response.json();
  return {
    baseUrl,
    httpStatus: response.status,
    payload,
    durationMs: Date.now() - startedAt,
  };
}

async function login() {
  const baseUrl = apiBaseUrls[0];
  const captcha = await apiRequest(baseUrl, null, "/captcha/generate");
  assert(
    captcha.httpStatus === 200 && captcha.payload?.code === 200,
    `Captcha generation failed: ${JSON.stringify(captcha)}`
  );
  const result = await apiRequest(baseUrl, null, "/user/login", {
    method: "POST",
    body: {
      userAccount: root.account,
      userPwd: root.password,
      captchaId: captcha.payload.data.captchaId,
      captchaAnswer: solveCaptcha(captcha.payload.data.expression),
    },
  });
  assert(
    result.httpStatus === 200 && result.payload?.code === 200,
    `Root login failed: ${JSON.stringify(result)}`
  );
  return result.payload.data.token;
}

async function runBurst(token) {
  let release;
  const gate = new Promise((resolve) => {
    release = resolve;
  });
  const jobs = Array.from({ length: burstSize }, (_, index) =>
    (async () => {
      await gate;
      const baseUrl = apiBaseUrls[index % apiBaseUrls.length];
      return apiRequest(baseUrl, token, "/user/insert", {
        method: "POST",
        body: {
          userAccount: `eq_${runKey}_${String(index).padStart(3, "0")}`,
          userName: `EQ ${runKey} ${String(index).padStart(3, "0")}`,
          userPwd: "EmailQuota@20606",
          userEmail: sharedEmail,
          userRole: 2,
          isCoordinatorAdmin: false,
        },
      });
    })()
  );
  release();
  return Promise.all(jobs);
}

function summarize(results) {
  const successes = results.filter(
    (result) => result.httpStatus === 200 && result.payload?.code === 200
  );
  const quotaRejections = results.filter(
    (result) =>
      result.httpStatus === 200 &&
      result.payload?.code === 400 &&
      result.payload?.msg === quotaMessage
  );
  const durations = results
    .map((result) => result.durationMs)
    .sort((left, right) => left - right);
  return {
    requests: results.length,
    successes: successes.length,
    quotaRejections: quotaRejections.length,
    otherFailures: results.length - successes.length - quotaRejections.length,
    instanceRequests: Object.fromEntries(
      apiBaseUrls.map((baseUrl) => [
        baseUrl,
        results.filter((result) => result.baseUrl === baseUrl).length,
      ])
    ),
    durationMs: {
      p95: durations[Math.max(0, Math.ceil(durations.length * 0.95) - 1)],
      max: durations.at(-1),
    },
  };
}

async function queryCreatedUsers(token) {
  const result = await apiRequest(apiBaseUrls[0], token, "/user/query", {
    method: "POST",
    body: { current: 1, size: 100, userEmail: sharedEmail },
  });
  assert(
    result.httpStatus === 200 && result.payload?.code === 200,
    `User query failed: ${JSON.stringify(result)}`
  );
  return result.payload.data || [];
}

const report = {
  validationDate:
    process.env.E2E_VALIDATION_DATE || new Date().toISOString().slice(0, 10),
  runKey,
  apiBaseUrls,
  burstSize,
  sharedEmail,
  startedAt: new Date().toISOString(),
};

try {
  const token = await login();
  const results = await runBurst(token);
  report.summary = summarize(results);
  assert(
    report.summary.successes === 3 &&
      report.summary.quotaRejections === burstSize - 3 &&
      report.summary.otherFailures === 0,
    `Email quota invariant failed: ${JSON.stringify(report.summary)}`
  );

  const users = await queryCreatedUsers(token);
  assert(users.length === 3, `Expected 3 stored users, received ${users.length}`);
  const deletion = await apiRequest(apiBaseUrls[0], token, "/user/batchDelete", {
    method: "POST",
    body: users.map((user) => Number(user.id)),
  });
  assert(
    deletion.httpStatus === 200 && deletion.payload?.code === 200,
    `Fixture cleanup failed: ${JSON.stringify(deletion)}`
  );
  assert(
    (await queryCreatedUsers(token)).length === 0,
    "Email quota fixtures remained after cleanup"
  );

  report.status = "passed";
  report.completedAt = new Date().toISOString();
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
  console.log(
    `EMAIL_QUOTA_E2E_OK requests=${burstSize} successes=3 ` +
      `quotaRejections=${burstSize - 3} instances=${apiBaseUrls.length}`
  );
} catch (error) {
  report.status = "failed";
  report.completedAt = new Date().toISOString();
  report.error = error instanceof Error ? error.stack : String(error);
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
  throw error;
}

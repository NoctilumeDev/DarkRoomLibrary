import fs from "node:fs/promises";
import { spawnSync } from "node:child_process";
import { getAccount } from "./test-accounts.mjs";

const apiBaseUrl = (
  process.env.E2E_API_BASE_URL ||
  "http://127.0.0.1:20606/api/dark-room-library/v1"
).replace(/\/$/, "");
const redisContainer =
  process.env.E2E_REDIS_CONTAINER || "drl-email-limit-test-redis-1";
const runKey = String(process.env.E2E_REGISTRATION_RUN_KEY || Date.now())
  .replaceAll(/[^a-zA-Z0-9]/g, "")
  .slice(-8);
const outputDir = "test-results/registration-email";
const password = "Registration@20606";
const sharedEmail = `rp-${runKey}@darkroomlibrary.local`;
const sourceEmail = `rp-source-${runKey}@darkroomlibrary.local`;
const alternateEmails = Array.from(
  { length: 3 },
  (_, index) => `rp-alt-${runKey}-${index + 1}@darkroomlibrary.local`
);
const quotaMessage = "同一邮箱最多关联 3 个账号，请更换邮箱";
const genericCodeMessage = "验证码错误或已过期";
const root = getAccount("root");

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

async function apiRequest(token, path, { method = "GET", body } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  if (body !== undefined) headers["Content-Type"] = "application/json";
  const response = await fetch(`${apiBaseUrl}${path}`, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(20_000),
  });
  const text = await response.text();
  let payload;
  try {
    payload = text ? JSON.parse(text) : null;
  } catch {
    throw new Error(`${method} ${path} returned invalid JSON: ${text.slice(0, 200)}`);
  }
  return { httpStatus: response.status, payload };
}

function assertBusiness(result, expectedCode, expectedMessage, label) {
  assert(
    result.httpStatus === 200 && result.payload?.code === expectedCode,
    `${label}: ${JSON.stringify(result)}`
  );
  if (expectedMessage) {
    assert(
      result.payload?.msg === expectedMessage,
      `${label} returned "${result.payload?.msg}"`
    );
  }
}

function seedCode(purpose, email, code) {
  const normalizedEmail = email.trim().toLowerCase();
  const key = `verification:code:${purpose}:${normalizedEmail}`;
  const result = spawnSync(
    "docker",
    ["exec", redisContainer, "redis-cli", "SETEX", key, "300", code],
    { encoding: "utf8", windowsHide: true }
  );
  assert(
    result.status === 0 && result.stdout.trim() === "OK",
    `Unable to seed ${purpose} code: ${result.stderr || result.stdout}`
  );
}

async function login(account, accountPassword) {
  const captcha = await apiRequest(null, "/captcha/generate");
  assertBusiness(captcha, 200, null, `captcha for ${account}`);
  const result = await apiRequest(null, "/user/login", {
    method: "POST",
    body: {
      userAccount: account,
      userPwd: accountPassword,
      captchaId: captcha.payload.data.captchaId,
      captchaAnswer: solveCaptcha(captcha.payload.data.expression),
    },
  });
  assertBusiness(result, 200, null, `login for ${account}`);
  return result.payload.data.token;
}

async function register({ account, name, email, code, accountPassword = password }) {
  return apiRequest(null, "/user/register", {
    method: "POST",
    body: {
      userAccount: account,
      userName: name,
      userPwd: accountPassword,
      userEmail: email,
      verificationCode: code,
    },
  });
}

async function queryUsersByEmail(rootToken, email) {
  const result = await apiRequest(rootToken, "/user/query", {
    method: "POST",
    body: { current: 1, size: 100, userEmail: email },
  });
  assertBusiness(result, 200, null, `query users for ${email}`);
  return result.payload.data || [];
}

async function deleteUsers(rootToken, users) {
  const ids = [...new Set(users.map((user) => Number(user.id)).filter(Number.isSafeInteger))];
  if (ids.length === 0) return;
  const result = await apiRequest(rootToken, "/user/batchDelete", {
    method: "POST",
    body: ids,
  });
  assertBusiness(result, 200, null, "fixture cleanup");
}

const accounts = {
  first: `rp_${runKey}_a1`,
  second: `rp_${runKey}_a2`,
  third: `rp_${runKey}_a3`,
  fourth: `rp_${runKey}_a4`,
  target: `rp_${runKey}_target`,
};
const names = {
  first: `RP ${runKey} A1`,
  second: `RP ${runKey} A2`,
  third: `RP ${runKey} A3`,
  fourth: `RP ${runKey} A4`,
  target: `RP ${runKey} Target`,
};
const report = {
  validationDate: "2026-07-27",
  runKey,
  apiBaseUrl,
  sharedEmail,
  startedAt: new Date().toISOString(),
  checks: [],
};
let rootToken;

try {
  rootToken = await login(root.account, root.password);

  for (const [index, identity] of ["first", "second", "third"].entries()) {
    const code = `31${String(index + 1).padStart(4, "0")}`;
    seedCode("REGISTER", sharedEmail, code);
    const created = await register({
      account: accounts[identity],
      name: names[identity],
      email: index === 0 ? sharedEmail.toUpperCase() : sharedEmail,
      code,
    });
    assertBusiness(created, 200, null, `shared registration ${identity}`);
  }
  report.checks.push("three-shared-email-accounts-created");

  const hiddenDuplicate = await register({
    account: accounts.first,
    name: `RP ${runKey} H`,
    email: alternateEmails[0],
    code: "000000",
  });
  assertBusiness(hiddenDuplicate, 400, genericCodeMessage, "unverified duplicate account");

  seedCode("REGISTER", alternateEmails[0], "320001");
  const duplicateSamePassword = await register({
    account: accounts.first,
    name: `RP ${runKey} DS`,
    email: alternateEmails[0],
    code: "320001",
  });
  assertBusiness(duplicateSamePassword, 400, "账号不可用", "duplicate account same password");

  seedCode("REGISTER", alternateEmails[1], "320002");
  const duplicateDifferentPassword = await register({
    account: accounts.first,
    name: `RP ${runKey} DD`,
    email: alternateEmails[1],
    code: "320002",
    accountPassword: "Different@20606",
  });
  assertBusiness(
    duplicateDifferentPassword,
    400,
    "账号不可用",
    "duplicate account different password"
  );

  seedCode("REGISTER", alternateEmails[2], "320003");
  const duplicateName = await register({
    account: `rp_${runKey}_name`,
    name: names.first,
    email: alternateEmails[2],
    code: "320003",
  });
  assertBusiness(
    duplicateName,
    400,
    "用户名已经被使用，请换一个",
    "duplicate display name"
  );
  report.checks.push("identity-conflicts-hidden-before-proof-and-exact-after-proof");

  const hiddenQuota = await register({
    account: accounts.fourth,
    name: names.fourth,
    email: sharedEmail,
    code: "000000",
  });
  assertBusiness(hiddenQuota, 400, genericCodeMessage, "unverified full email");

  seedCode("REGISTER", sharedEmail, "330001");
  const exactQuota = await register({
    account: accounts.fourth,
    name: names.fourth,
    email: sharedEmail,
    code: "330001",
  });
  assertBusiness(exactQuota, 400, quotaMessage, "verified full email");
  report.checks.push("quota-hidden-before-proof-and-exact-after-proof");

  const unauthorizedChangeCode = await apiRequest(null, "/user/sendEmailChangeCode", {
    method: "POST",
    body: { email: sharedEmail },
  });
  assert(
    unauthorizedChangeCode.httpStatus === 401,
    `email-change code endpoint was not protected: ${JSON.stringify(unauthorizedChangeCode)}`
  );

  const targetCreated = await apiRequest(rootToken, "/user/insert", {
    method: "POST",
    body: {
      userAccount: accounts.target,
      userName: names.target,
      userPwd: password,
      userEmail: sourceEmail,
      userRole: 2,
      isCoordinatorAdmin: false,
    },
  });
  assertBusiness(targetCreated, 200, null, "create email-change target");
  const targetToken = await login(accounts.target, password);

  const missingCode = await apiRequest(targetToken, "/user/update", {
    method: "PUT",
    body: { userEmail: sharedEmail },
  });
  assertBusiness(missingCode, 400, "请输入新邮箱验证码", "missing change code");

  const wrongCode = await apiRequest(targetToken, "/user/update", {
    method: "PUT",
    body: { userEmail: sharedEmail, verificationCode: "000000" },
  });
  assertBusiness(wrongCode, 400, genericCodeMessage, "wrong change code");

  seedCode("CHANGE_EMAIL", sharedEmail, "340001");
  const fullChange = await apiRequest(targetToken, "/user/update", {
    method: "PUT",
    body: { userEmail: sharedEmail, verificationCode: "340001" },
  });
  assertBusiness(fullChange, 400, quotaMessage, "verified change to full email");

  const sharedUsers = await queryUsersByEmail(rootToken, sharedEmail);
  assert(sharedUsers.length === 3, `Expected three shared users, received ${sharedUsers.length}`);
  await deleteUsers(rootToken, [sharedUsers[0]]);

  seedCode("CHANGE_EMAIL", sharedEmail, "340002");
  const acceptedChange = await apiRequest(targetToken, "/user/update", {
    method: "PUT",
    body: { userEmail: sharedEmail, verificationCode: "340002" },
  });
  assertBusiness(acceptedChange, 200, null, "verified change after quota release");

  const finalSharedUsers = await queryUsersByEmail(rootToken, sharedEmail);
  assert(
    finalSharedUsers.length === 3 &&
      finalSharedUsers.some((user) => user.userAccount === accounts.target),
    `Email move did not preserve the quota invariant: ${JSON.stringify(finalSharedUsers)}`
  );
  report.checks.push("verified-email-change-and-physical-delete-release");

  report.status = "passed";
  report.completedAt = new Date().toISOString();
  console.log(
    "REGISTRATION_EMAIL_E2E_OK shared=3 privacy=generic-before-proof " +
      "quota=exact-after-proof release=verified"
  );
} catch (error) {
  report.status = "failed";
  report.completedAt = new Date().toISOString();
  report.error = error instanceof Error ? error.stack : String(error);
} finally {
  if (rootToken) {
    try {
      const remaining = [
        ...(await queryUsersByEmail(rootToken, sharedEmail)),
        ...(await queryUsersByEmail(rootToken, sourceEmail)),
      ];
      await deleteUsers(rootToken, remaining);
      report.cleanup = "passed";
    } catch (cleanupError) {
      report.cleanup = cleanupError instanceof Error
        ? cleanupError.message
        : String(cleanupError);
      if (report.status === "passed") {
        report.status = "failed";
        report.error = `Fixture cleanup failed: ${report.cleanup}`;
      }
    }
  }
  await fs.writeFile(
    `${outputDir}/report.json`,
    `${JSON.stringify(report, null, 2)}\n`,
    "utf8"
  );
}

if (report.status !== "passed") {
  throw new Error(report.error || "Registration email E2E failed");
}
